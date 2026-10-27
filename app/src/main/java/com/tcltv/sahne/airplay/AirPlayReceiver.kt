package com.tcltv.sahne.airplay

import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.security.SecureRandom

private const val TAG = "AirPlayReceiver"
private const val MAX_PENDING_FRAMES = 240

enum class AirPlayState { OFF, READY, MIRRORING, FAILED }

/**
 * Lets iPhones, iPads and Macs send their screen or sound to this TV. The protocol runs in native code (UxPlay's
 * library); this class announces the service on the network, decodes what arrives and draws it on [MirrorActivity].
 */
class AirPlayReceiver private constructor(private val context: Context) : AirPlayNative.Listener {

    private val native = AirPlayNative(this)
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val services = HashMap<String, NsdManager.RegistrationListener>()
    private var multicastLock: WifiManager.MulticastLock? = null

    private val lock = Any()
    private var video: VideoDecoder? = null
    private val pendingFrames = ArrayDeque<Pair<ByteArray, Long>>()
    private val audio = AudioPlayer()
    private var openConnections = 0
    private var activityRequested = false

    private val _state = MutableStateFlow(AirPlayState.OFF)
    val state: StateFlow<AirPlayState> = _state.asStateFlow()

    @Synchronized
    fun start(name: String) {
        if (_state.value != AirPlayState.OFF && _state.value != AirPlayState.FAILED) return
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifi.createMulticastLock("matinee-airplay").apply {
            setReferenceCounted(false)
            acquire()
        }
        val port = native.start(name, hardwareAddress(), File(context.filesDir, "airplay_key").path)
        if (port < 0) {
            Log.e(TAG, "native start failed: $port")
            multicastLock?.release()
            _state.value = AirPlayState.FAILED
        } else {
            Log.i(TAG, "listening on port $port")
            _state.value = AirPlayState.READY
        }
    }

    @Synchronized
    fun stop() {
        if (_state.value == AirPlayState.OFF) return
        endSession()
        native.stop()
        services.keys.toList().forEach { unregister(it) }
        multicastLock?.release()
        multicastLock = null
        _state.value = AirPlayState.OFF
    }

    /** The mirror screen is ready to draw; frames that arrived while it opened are replayed first. */
    fun attachSurface(surface: Surface, onVideoSize: (width: Int, height: Int) -> Unit) {
        synchronized(lock) {
            video?.stop()
            val decoder = VideoDecoder(surface, onVideoSize)
            video = decoder
            while (pendingFrames.isNotEmpty()) {
                val (data, time) = pendingFrames.removeFirst()
                decoder.queue(data, time)
            }
        }
    }

    fun detachSurface() {
        synchronized(lock) {
            video?.stop()
            video = null
            activityRequested = false
        }
    }

    /** The user left the mirror screen: drop the phone's connection. */
    fun disconnect() {
        native.disconnect()
    }

    private fun endSession() {
        synchronized(lock) {
            pendingFrames.clear()
            audio.stop()
        }
        if (_state.value == AirPlayState.MIRRORING) _state.value = AirPlayState.READY
    }

    private fun hardwareAddress(): ByteArray {
        val prefs = context.getSharedPreferences("airplay", Context.MODE_PRIVATE)
        val saved = prefs.getString("device_id", null)
        if (saved != null) return saved.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        // Android hides the real MAC address, and AirPlay only needs a stable, unique id: a random locally
        // administered unicast address does the job.
        val bytes = ByteArray(6).also { SecureRandom().nextBytes(it) }
        bytes[0] = ((bytes[0].toInt() or 0x02) and 0xFE).toByte()
        prefs.edit().putString("device_id", bytes.joinToString("") { "%02x".format(it) }).apply()
        return bytes
    }

    override fun onService(registered: Boolean, name: String, type: String, port: Int, txt: ByteArray) {
        val key = type
        if (!registered) {
            unregister(key)
            return
        }
        val info = NsdServiceInfo().apply {
            serviceName = name
            serviceType = type.trimEnd('.') + "."
            setPort(port)
            parseTxt(txt).forEach { (k, v) -> setAttribute(k, v) }
        }
        val listener = object : NsdManager.RegistrationListener {
            // The callback's own NsdServiceInfo often comes back without its type, so log what was registered.
            override fun onServiceRegistered(registered: NsdServiceInfo) {
                Log.i(TAG, "announced ${info.serviceType} as ${registered.serviceName} on port ${info.port}")
            }

            override fun onRegistrationFailed(failed: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "announce failed $errorCode for ${info.serviceType} as ${info.serviceName}")
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }
        services[key] = listener
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun unregister(type: String) {
        services.remove(type)?.let { runCatching { nsd.unregisterService(it) } }
    }

    /** Splits a DNS-SD TXT record (length-prefixed "key=value" entries) into pairs. */
    private fun parseTxt(txt: ByteArray): List<Pair<String, String>> {
        val pairs = ArrayList<Pair<String, String>>()
        var offset = 0
        while (offset < txt.size) {
            val length = txt[offset].toInt() and 0xFF
            val end = minOf(offset + 1 + length, txt.size)
            val entry = String(txt, offset + 1, end - offset - 1, Charsets.UTF_8)
            val equals = entry.indexOf('=')
            if (equals > 0) pairs += entry.substring(0, equals) to entry.substring(equals + 1)
            offset = end
        }
        return pairs
    }

    override fun onVideo(data: ByteArray, remoteTimeNanos: Long) {
        var open = false
        synchronized(lock) {
            val decoder = video
            if (decoder != null) {
                decoder.queue(data, remoteTimeNanos)
            } else {
                // A keyframe makes everything before it useless, and the decoder can't start without one.
                if (VideoDecoder.spsOf(data) != null) pendingFrames.clear()
                if (pendingFrames.size >= MAX_PENDING_FRAMES) pendingFrames.removeFirst()
                pendingFrames.addLast(data to remoteTimeNanos)
                if (!activityRequested) {
                    activityRequested = true
                    open = true
                }
            }
        }
        if (_state.value != AirPlayState.MIRRORING) _state.value = AirPlayState.MIRRORING
        if (open) {
            context.startActivity(
                Intent(context, MirrorActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
    }

    override fun onAudio(data: ByteArray, codecType: Int, remoteTimeNanos: Long, sequence: Int) {
        audio.queue(data, codecType, remoteTimeNanos)
    }

    override fun onConnection(opened: Boolean) {
        val remaining = synchronized(lock) {
            openConnections = (openConnections + if (opened) 1 else -1).coerceAtLeast(0)
            openConnections
        }
        if (remaining == 0) {
            endSession()
            context.sendBroadcast(Intent(MirrorActivity.ACTION_FINISH).setPackage(context.packageName))
        }
    }

    override fun onVolume(decibels: Float) = audio.setVolume(decibels)

    override fun onFlush(audio: Boolean) {
        if (audio) this.audio.flush() else synchronized(lock) { video?.flush() }
    }

    override fun onAudioFormat(codecType: Int, samplesPerFrame: Int) {
        Log.i(TAG, "audio format $codecType, $samplesPerFrame samples per frame")
        audio.start(codecType)
    }

    override fun onClient(name: String, model: String) {
        Log.i(TAG, "connection request from $name ($model)")
    }

    companion object {
        @Volatile
        private var instance: AirPlayReceiver? = null

        fun get(context: Context): AirPlayReceiver =
            instance ?: synchronized(this) {
                instance ?: AirPlayReceiver(context.applicationContext).also { instance = it }
            }
    }
}
