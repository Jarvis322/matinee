package com.tcltv.sahne.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException

enum class PressType { SHORT, LONG, DOUBLE }

/** What a remote key does when pressed a given way; a press with no action keeps the key's own behaviour. */
sealed interface KeyAction {
    data class OpenApp(val packageName: String) : KeyAction
    data class OpenInput(val inputId: String) : KeyAction
    data object MatineeHome : KeyAction
    data object MatineeSettings : KeyAction
    data object ScreenOff : KeyAction
    data object DoNothing : KeyAction

    fun encode(): String = when (this) {
        is OpenApp -> "app:$packageName"
        is OpenInput -> "input:$inputId"
        MatineeHome -> "home"
        MatineeSettings -> "settings"
        ScreenOff -> "screen_off"
        DoNothing -> "nothing"
    }

    companion object {
        fun decode(value: String): KeyAction? = when {
            value.startsWith("app:") -> OpenApp(value.removePrefix("app:"))
            value.startsWith("input:") -> OpenInput(value.removePrefix("input:"))
            value == "home" -> MatineeHome
            value == "settings" -> MatineeSettings
            value == "screen_off" -> ScreenOff
            value == "nothing" -> DoNothing
            else -> null
        }
    }
}

data class KeyBinding(val keyCode: Int, val actions: Map<PressType, KeyAction>)

/**
 * The remote keys the user gave actions to. Kept in a plain file rather than the settings DataStore because the Home
 * button service, which runs them, lives in its own process and DataStore allows only one.
 */
class KeyBindingStore(context: Context) {

    private val file = AtomicFile(File(context.applicationContext.filesDir, FILE_NAME))
    private var cached: Map<Int, KeyBinding> = emptyMap()
    private var cachedStamp = Long.MIN_VALUE

    fun load(): List<KeyBinding> = try {
        parse(String(file.readFully(), Charsets.UTF_8))
    } catch (e: IOException) {
        // No file yet: nothing is assigned.
        emptyList()
    }

    fun save(bindings: List<KeyBinding>) {
        val array = JSONArray()
        bindings.filter { it.actions.isNotEmpty() }.forEach { binding ->
            val actions = JSONObject()
            binding.actions.forEach { (press, action) -> actions.put(press.name, action.encode()) }
            array.put(JSONObject().put("key", binding.keyCode).put("actions", actions))
        }
        val out = file.startWrite()
        try {
            out.write(array.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: IOException) {
            file.failWrite(out)
            throw e
        }
    }

    /** The bindings by key for the service, read from disk again only after the settings screen changed them. */
    fun current(): Map<Int, KeyBinding> {
        val stamp = file.baseFile.lastModified()
        if (stamp != cachedStamp) {
            cached = load().associateBy { it.keyCode }
            cachedStamp = stamp
        }
        return cached
    }

    /** Drops the cached copy; the settings screen announces each save, since a file's time can't tell saves apart. */
    fun invalidate() {
        cachedStamp = Long.MIN_VALUE
    }

    private fun parse(text: String): List<KeyBinding> = try {
        val array = JSONArray(text)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val actionsJson = item.optJSONObject("actions") ?: return@mapNotNull null
            val actions = PressType.entries.mapNotNull { press ->
                actionsJson.optString(press.name).takeIf { it.isNotEmpty() }?.let(KeyAction::decode)?.let { press to it }
            }.toMap()
            KeyBinding(item.optInt("key"), actions).takeIf { it.keyCode > 0 && actions.isNotEmpty() }
        }
    } catch (e: JSONException) {
        // A damaged file loses the assignments rather than leaving every remote key dead.
        emptyList()
    }

    private companion object {
        const val FILE_NAME = "key_bindings.json"
    }
}
