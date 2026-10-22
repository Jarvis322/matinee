package com.tcltv.sahne.airplay

/**
 * The JNI side of the AirPlay receiver. The `on*` methods are called from native threads, by name, so they must keep
 * their signatures (see proguard-rules.pro).
 */
class AirPlayNative(private val listener: Listener) {

    interface Listener {
        fun onService(registered: Boolean, name: String, type: String, port: Int, txt: ByteArray)
        fun onVideo(data: ByteArray, remoteTimeNanos: Long)
        fun onAudio(data: ByteArray, codecType: Int, remoteTimeNanos: Long, sequence: Int)
        fun onConnection(opened: Boolean)
        fun onVolume(decibels: Float)
        fun onFlush(audio: Boolean)
        fun onAudioFormat(codecType: Int, samplesPerFrame: Int)
        fun onClient(name: String, model: String)
    }

    /** Starts the server and returns its port, or a negative error code. */
    external fun start(name: String, hardwareAddress: ByteArray, keyFile: String): Int

    external fun stop()

    /** Drops the connected phone, which ends the mirroring session. */
    external fun disconnect()

    private fun onService(registered: Boolean, name: String, type: String, port: Int, txt: ByteArray) =
        listener.onService(registered, name, type, port, txt)

    private fun onVideo(data: ByteArray, remoteTimeNanos: Long) = listener.onVideo(data, remoteTimeNanos)

    private fun onAudio(data: ByteArray, codecType: Int, remoteTimeNanos: Long, sequence: Int) =
        listener.onAudio(data, codecType, remoteTimeNanos, sequence)

    private fun onConnection(opened: Boolean) = listener.onConnection(opened)

    private fun onVolume(decibels: Float) = listener.onVolume(decibels)

    private fun onFlush(audio: Boolean) = listener.onFlush(audio)

    private fun onAudioFormat(codecType: Int, samplesPerFrame: Int) = listener.onAudioFormat(codecType, samplesPerFrame)

    private fun onClient(name: String, model: String) = listener.onClient(name, model)

    companion object {
        init {
            System.loadLibrary("matinee_airplay")
        }
    }
}
