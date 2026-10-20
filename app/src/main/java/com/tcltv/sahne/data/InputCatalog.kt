package com.tcltv.sahne.data

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Handler
import android.os.Looper
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.tcltv.sahne.R
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class DeviceLogo(@DrawableRes val icon: Int, @StringRes val label: Int, private val keywords: List<String>) {
    SATELLITE(R.drawable.ic_antenna, R.string.logo_satellite, listOf("digiturk", "d-smart", "dsmart", "tivibu", "uydu", "satellite", "receiver", "stb", "set-top", "kablo")),
    PLAYSTATION(R.drawable.ic_device_gamepad_2, R.string.logo_playstation, listOf("playstation", "ps5", "ps4", "ps3", "sony interactive")),
    XBOX(R.drawable.ic_device_gamepad_2, R.string.logo_xbox, listOf("xbox")),
    NINTENDO(R.drawable.ic_device_gamepad_2, R.string.logo_nintendo, listOf("nintendo", "switch")),
    PC(R.drawable.ic_device_desktop, R.string.logo_pc, listOf("pc", "windows", "desktop", "computer", "bilgisayar")),
    MAC(R.drawable.ic_device_desktop, R.string.logo_mac, listOf("mac", "macbook", "imac")),
    APPLE_TV(R.drawable.ic_box, R.string.logo_apple_tv, listOf("apple tv", "appletv")),
    FIRE_TV(R.drawable.ic_box, R.string.logo_fire_tv, listOf("fire tv", "firetv", "amazon")),
    TV_BOX(R.drawable.ic_box, R.string.logo_tv_box, listOf("tv box", "mi box", "chromecast", "shield", "android tv")),
    SOUNDBAR(R.drawable.ic_device_speaker, R.string.logo_soundbar, listOf("soundbar", "sonos", "bose", "yamaha", "denon", "onkyo", "av receiver")),
    CONSOLE(R.drawable.ic_device_gamepad_2, R.string.logo_console, listOf("console", "konsol")),
    OTHER(R.drawable.ic_hdmi, R.string.logo_other, emptyList());

    companion object {
        // Apple TV must win over the generic "apple"/"mac" match, so more specific entries come first.
        private val matchOrder = listOf(APPLE_TV, FIRE_TV, PLAYSTATION, XBOX, NINTENDO, SATELLITE, SOUNDBAR, TV_BOX, MAC, PC, CONSOLE)

        fun detect(name: String?): DeviceLogo? {
            val normalized = name?.lowercase()?.trim().orEmpty()
            if (normalized.isEmpty()) return null
            return matchOrder.firstOrNull { logo ->
                logo.keywords.any { keyword ->
                    if (keyword.length <= 3) Regex("\\b${Regex.escape(keyword)}\\b").containsMatchIn(normalized)
                    else normalized.contains(keyword)
                }
            }
        }
    }
}

enum class InputDetection { CEC, TV_LABEL, NONE }

enum class DevicePower { ON, STANDBY }

data class HdmiInput(
    val id: String,
    val portLabel: String,
    val detectedName: String?,
    val detectedLogo: DeviceLogo?,
    val detection: InputDetection,
    val connected: Boolean,
    /** On or in standby as the device itself reports over HDMI-CEC; null when it doesn't speak CEC. */
    val power: DevicePower?,
    val name: String,
    /** What the home row shows: the user's or the TV's name for the port, with the CEC device name beside it. */
    val chipTitle: String,
    val chipDevice: String?,
    val logo: DeviceLogo,
    val isOverridden: Boolean,
)

data class RawHdmiInput(
    val id: String,
    val portLabel: String,
    val tvCustomLabel: String?,
    val cecDeviceName: String?,
    val connected: Boolean,
    val power: DevicePower?,
)

class InputCatalog(context: Context) {

    private val appContext = context.applicationContext
    private val tvInputManager = appContext.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager

    val rawInputs: Flow<List<RawHdmiInput>> = callbackFlow {
        val manager = tvInputManager
        if (manager == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val callback = object : TvInputManager.TvInputCallback() {
            override fun onInputStateChanged(inputId: String, state: Int) { trySend(load(manager)) }
            override fun onInputAdded(inputId: String) { trySend(load(manager)) }
            override fun onInputRemoved(inputId: String) { trySend(load(manager)) }
            override fun onInputUpdated(inputId: String) { trySend(load(manager)) }
        }
        trySend(load(manager))
        manager.registerCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { manager.unregisterCallback(callback) }
    }

    private fun load(manager: TvInputManager): List<RawHdmiInput> {
        val all = manager.tvInputList
        val hdmi = all.filter { it.type == TvInputInfo.TYPE_HDMI }
        val ports = hdmi.filter { it.parentId == null }
        return ports.map { port ->
            val cecChild = hdmi.firstOrNull { it.parentId == port.id }
            val portLabel = port.loadLabel(appContext)?.toString().orEmpty().ifBlank { "HDMI" }
            val custom = port.loadCustomLabel(appContext)?.toString()?.takeIf { it.isNotBlank() && it != portLabel }
            val cecName = cecChild?.loadLabel(appContext)?.toString()?.takeIf { it.isNotBlank() && it != portLabel }
            val state = runCatching { manager.getInputState(port.id) }.getOrDefault(TvInputManager.INPUT_STATE_CONNECTED)
            val childState = cecChild?.let { runCatching { manager.getInputState(it.id) }.getOrNull() }
            RawHdmiInput(
                id = port.id,
                portLabel = portLabel,
                tvCustomLabel = custom,
                cecDeviceName = cecName,
                connected = state != TvInputManager.INPUT_STATE_DISCONNECTED ||
                    (childState != null && childState != TvInputManager.INPUT_STATE_DISCONNECTED),
                // The port's own state only reflects the cable, so power comes from the CEC device alone.
                power = when (childState) {
                    TvInputManager.INPUT_STATE_CONNECTED -> DevicePower.ON
                    TvInputManager.INPUT_STATE_CONNECTED_STANDBY -> DevicePower.STANDBY
                    else -> null
                },
            )
        }.sortedBy { it.portLabel }
    }

    fun launchIntent(inputId: String): Intent =
        Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(inputId))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    companion object {
        fun resolve(raw: List<RawHdmiInput>, settings: LauncherSettings): List<HdmiInput> = raw.map { input ->
            val detectedName = when {
                settings.autoDetectInputs && input.cecDeviceName != null -> input.cecDeviceName
                input.tvCustomLabel != null -> input.tvCustomLabel
                else -> null
            }
            val detection = when {
                settings.autoDetectInputs && input.cecDeviceName != null -> InputDetection.CEC
                input.tvCustomLabel != null -> InputDetection.TV_LABEL
                else -> InputDetection.NONE
            }
            val detectedLogo = DeviceLogo.detect(detectedName)
            val override = settings.inputOverrides[input.id]
            val logo = override?.logo ?: detectedLogo ?: DeviceLogo.OTHER
            val chipTitle = override?.name ?: input.tvCustomLabel ?: input.portLabel
            HdmiInput(
                id = input.id,
                portLabel = input.portLabel,
                detectedName = detectedName,
                detectedLogo = detectedLogo,
                detection = detection,
                connected = input.connected,
                power = input.power,
                name = override?.name ?: detectedName ?: input.portLabel,
                chipTitle = chipTitle,
                chipDevice = input.cecDeviceName?.takeIf { settings.autoDetectInputs && it != chipTitle },
                logo = logo,
                isOverridden = override?.name != null || override?.logo != null,
            )
        }
    }
}
