package com.tcltv.sahne.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.catch
import java.io.IOException
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class TemperatureUnit { CELSIUS, FAHRENHEIT }

enum class CardTheme { DARK, LIGHT }

/** ROW: large title with one app row and last played. GRID: a favorites shelf above a grid of every app. */
enum class HomeLayout { ROW, GRID }

data class WeatherLocation(val name: String, val latitude: Double, val longitude: Double)

data class InputOverride(val name: String?, val logo: DeviceLogo?)

data class LauncherSettings(
    val use24Hour: Boolean = true,
    val showSeconds: Boolean = false,
    val showGreeting: Boolean = true,
    val reduceMotion: Boolean = false,
    val adaptiveBackground: Boolean = true,
    val glassEffects: Boolean = true,
    val cardTheme: CardTheme = CardTheme.LIGHT,
    val homeLayout: HomeLayout = HomeLayout.GRID,
    val showClock: Boolean = true,
    val showWeather: Boolean = true,
    val showAirPlay: Boolean = true,
    val showMemory: Boolean = true,
    val showInputs: Boolean = true,
    val showNotifications: Boolean = true,
    val showChromecast: Boolean = true,
    val showLastPlayed: Boolean = true,
    val profileName: String = "",
    val profileColor: Int = 0,
    val showPlayUpdates: Boolean = true,
    val weatherLocation: WeatherLocation = DEFAULT_LOCATION,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val weatherRefreshMinutes: Int = 30,
    val autoDetectInputs: Boolean = true,
    val hideDisconnectedInputs: Boolean = true,
    val inputOverrides: Map<String, InputOverride> = emptyMap(),
    val appOrder: List<String> = emptyList(),
    val hiddenApps: Set<String> = emptySet(),
    val shownApps: Set<String> = emptySet(),
    val showSystemApps: Boolean = false,
    val autoCleanMemory: Boolean = false,
    val memoryThresholdPercent: Int = 80,
    val memoryCleanExclusions: Set<String> = emptySet(),
    val appOverrides: Map<String, AppOverride> = emptyMap(),
    val folders: List<Folder> = emptyList(),
    val parental: Parental = Parental(),
) {
    companion object {
        val DEFAULT_LOCATION = WeatherLocation("İstanbul", 41.0138, 28.9497)
    }
}

// A corrupt settings file resets to defaults instead of crash-looping the Home app.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "launcher_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val use24Hour = booleanPreferencesKey("use_24_hour")
        val showSeconds = booleanPreferencesKey("show_seconds")
        val showGreeting = booleanPreferencesKey("show_greeting")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val adaptiveBackground = booleanPreferencesKey("adaptive_background")
        val glassEffects = booleanPreferencesKey("glass_effects")
        val cardTheme = stringPreferencesKey("card_theme")
        val homeLayout = stringPreferencesKey("home_layout")
        val showClock = booleanPreferencesKey("show_clock")
        val showWeather = booleanPreferencesKey("show_weather")
        val showAirPlay = booleanPreferencesKey("show_airplay")
        val showMemory = booleanPreferencesKey("show_memory")
        val showInputs = booleanPreferencesKey("show_inputs")
        val showNotifications = booleanPreferencesKey("show_notifications")
        val showChromecast = booleanPreferencesKey("show_google_home")
        val showLastPlayed = booleanPreferencesKey("show_last_played")
        val profileName = stringPreferencesKey("profile_name")
        val profileColor = intPreferencesKey("profile_color")
        val showPlayUpdates = booleanPreferencesKey("show_play_updates")
        val locationName = stringPreferencesKey("weather_location_name")
        val latitude = doublePreferencesKey("weather_latitude")
        val longitude = doublePreferencesKey("weather_longitude")
        val temperatureUnit = stringPreferencesKey("temperature_unit")
        val weatherRefreshMinutes = intPreferencesKey("weather_refresh_minutes")
        val autoDetectInputs = booleanPreferencesKey("auto_detect_inputs")
        val hideDisconnectedInputs = booleanPreferencesKey("hide_disconnected_inputs")
        val appOrder = stringPreferencesKey("app_order")
        val hiddenApps = stringSetPreferencesKey("hidden_apps")
        val shownApps = stringSetPreferencesKey("shown_apps")
        val showSystemApps = booleanPreferencesKey("show_system_apps")
        val autoCleanMemory = booleanPreferencesKey("auto_clean_memory")
        val memoryThreshold = intPreferencesKey("memory_threshold_percent")
        val memoryExclusions = stringSetPreferencesKey("memory_clean_exclusions")
        val appOverrides = stringPreferencesKey("app_overrides")
        val folders = stringPreferencesKey("folders")
        val pinHash = stringPreferencesKey("pin_hash")
        val pinSalt = stringPreferencesKey("pin_salt")
        val protectSettings = booleanPreferencesKey("protect_settings")
        val pinFailures = intPreferencesKey("pin_failures")
        val pinLockedUntil = longPreferencesKey("pin_locked_until")
        const val INPUT_NAME_PREFIX = "input_name:"
        const val INPUT_LOGO_PREFIX = "input_logo:"
    }

    val settings: Flow<LauncherSettings> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSettings() }

    private fun Preferences.toSettings(): LauncherSettings {
        val defaults = LauncherSettings()
        val overrides = mutableMapOf<String, InputOverride>()
        asMap().forEach { (key, value) ->
            val name = key.name
            when {
                name.startsWith(Keys.INPUT_NAME_PREFIX) -> {
                    val id = name.removePrefix(Keys.INPUT_NAME_PREFIX)
                    overrides[id] = (overrides[id] ?: InputOverride(null, null)).copy(name = value as String)
                }
                name.startsWith(Keys.INPUT_LOGO_PREFIX) -> {
                    val id = name.removePrefix(Keys.INPUT_LOGO_PREFIX)
                    val logo = DeviceLogo.entries.firstOrNull { it.name == value }
                    overrides[id] = (overrides[id] ?: InputOverride(null, null)).copy(logo = logo)
                }
            }
        }
        return LauncherSettings(
            use24Hour = this[Keys.use24Hour] ?: defaults.use24Hour,
            showSeconds = this[Keys.showSeconds] ?: defaults.showSeconds,
            showGreeting = this[Keys.showGreeting] ?: defaults.showGreeting,
            reduceMotion = this[Keys.reduceMotion] ?: defaults.reduceMotion,
            adaptiveBackground = this[Keys.adaptiveBackground] ?: defaults.adaptiveBackground,
            glassEffects = this[Keys.glassEffects] ?: defaults.glassEffects,
            cardTheme = CardTheme.entries.firstOrNull { it.name == this[Keys.cardTheme] } ?: defaults.cardTheme,
            homeLayout = HomeLayout.entries.firstOrNull { it.name == this[Keys.homeLayout] } ?: defaults.homeLayout,
            showClock = this[Keys.showClock] ?: defaults.showClock,
            showWeather = this[Keys.showWeather] ?: defaults.showWeather,
            showAirPlay = this[Keys.showAirPlay] ?: defaults.showAirPlay,
            showMemory = this[Keys.showMemory] ?: defaults.showMemory,
            showInputs = this[Keys.showInputs] ?: defaults.showInputs,
            showNotifications = this[Keys.showNotifications] ?: defaults.showNotifications,
            showChromecast = this[Keys.showChromecast] ?: defaults.showChromecast,
            showLastPlayed = this[Keys.showLastPlayed] ?: defaults.showLastPlayed,
            profileName = this[Keys.profileName] ?: defaults.profileName,
            profileColor = this[Keys.profileColor] ?: defaults.profileColor,
            showPlayUpdates = this[Keys.showPlayUpdates] ?: defaults.showPlayUpdates,
            weatherLocation = WeatherLocation(
                name = this[Keys.locationName] ?: defaults.weatherLocation.name,
                latitude = this[Keys.latitude] ?: defaults.weatherLocation.latitude,
                longitude = this[Keys.longitude] ?: defaults.weatherLocation.longitude,
            ),
            temperatureUnit = TemperatureUnit.entries.firstOrNull { it.name == this[Keys.temperatureUnit] }
                ?: defaults.temperatureUnit,
            weatherRefreshMinutes = this[Keys.weatherRefreshMinutes] ?: defaults.weatherRefreshMinutes,
            autoDetectInputs = this[Keys.autoDetectInputs] ?: defaults.autoDetectInputs,
            hideDisconnectedInputs = this[Keys.hideDisconnectedInputs] ?: defaults.hideDisconnectedInputs,
            inputOverrides = overrides,
            appOrder = this[Keys.appOrder]?.split(',')?.filter { it.isNotBlank() } ?: defaults.appOrder,
            hiddenApps = this[Keys.hiddenApps] ?: defaults.hiddenApps,
            shownApps = this[Keys.shownApps] ?: defaults.shownApps,
            showSystemApps = this[Keys.showSystemApps] ?: defaults.showSystemApps,
            autoCleanMemory = this[Keys.autoCleanMemory] ?: defaults.autoCleanMemory,
            memoryThresholdPercent = this[Keys.memoryThreshold] ?: defaults.memoryThresholdPercent,
            memoryCleanExclusions = this[Keys.memoryExclusions] ?: defaults.memoryCleanExclusions,
            appOverrides = CustomizationJson.decodeOverrides(this[Keys.appOverrides]),
            folders = CustomizationJson.decodeFolders(this[Keys.folders]),
            parental = Parental(
                pinHash = this[Keys.pinHash],
                pinSalt = this[Keys.pinSalt],
                protectSettings = this[Keys.protectSettings] ?: defaults.parental.protectSettings,
                failures = this[Keys.pinFailures] ?: 0,
                lockedUntilMillis = this[Keys.pinLockedUntil] ?: 0L,
            ),
        )
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        try {
            context.dataStore.edit { block(it) }
        } catch (e: IOException) {
            // A failed write (e.g. full storage) keeps the previous value; there is nothing better to fall back to.
            Log.w("SettingsStore", "Couldn't save settings", e)
        }
    }

    suspend fun setUse24Hour(value: Boolean) = edit { it[Keys.use24Hour] = value }
    suspend fun setShowSeconds(value: Boolean) = edit { it[Keys.showSeconds] = value }
    suspend fun setShowGreeting(value: Boolean) = edit { it[Keys.showGreeting] = value }
    suspend fun setReduceMotion(value: Boolean) = edit { it[Keys.reduceMotion] = value }
    suspend fun setAdaptiveBackground(value: Boolean) = edit { it[Keys.adaptiveBackground] = value }
    suspend fun setGlassEffects(value: Boolean) = edit { it[Keys.glassEffects] = value }
    suspend fun setCardTheme(value: CardTheme) = edit { it[Keys.cardTheme] = value.name }
    suspend fun setHomeLayout(value: HomeLayout) = edit { it[Keys.homeLayout] = value.name }
    suspend fun setShowClock(value: Boolean) = edit { it[Keys.showClock] = value }
    suspend fun setShowWeather(value: Boolean) = edit { it[Keys.showWeather] = value }
    suspend fun setShowAirPlay(value: Boolean) = edit { it[Keys.showAirPlay] = value }
    suspend fun setShowMemory(value: Boolean) = edit { it[Keys.showMemory] = value }
    suspend fun setShowInputs(value: Boolean) = edit { it[Keys.showInputs] = value }
    suspend fun setShowNotifications(value: Boolean) = edit { it[Keys.showNotifications] = value }
    suspend fun setShowChromecast(value: Boolean) = edit { it[Keys.showChromecast] = value }
    suspend fun setShowLastPlayed(value: Boolean) = edit { it[Keys.showLastPlayed] = value }
    suspend fun setProfileName(value: String) = edit { it[Keys.profileName] = value.trim() }
    suspend fun setProfileColor(value: Int) = edit { it[Keys.profileColor] = value }
    suspend fun setShowPlayUpdates(value: Boolean) = edit { it[Keys.showPlayUpdates] = value }

    suspend fun setWeatherLocation(location: WeatherLocation) = edit {
        it[Keys.locationName] = location.name
        it[Keys.latitude] = location.latitude
        it[Keys.longitude] = location.longitude
    }

    suspend fun setTemperatureUnit(value: TemperatureUnit) = edit { it[Keys.temperatureUnit] = value.name }
    suspend fun setWeatherRefreshMinutes(value: Int) = edit { it[Keys.weatherRefreshMinutes] = value }
    suspend fun setAutoDetectInputs(value: Boolean) = edit { it[Keys.autoDetectInputs] = value }
    suspend fun setHideDisconnectedInputs(value: Boolean) = edit { it[Keys.hideDisconnectedInputs] = value }

    suspend fun setInputOverride(inputId: String, name: String?, logo: DeviceLogo?) = edit {
        val nameKey = stringPreferencesKey(Keys.INPUT_NAME_PREFIX + inputId)
        val logoKey = stringPreferencesKey(Keys.INPUT_LOGO_PREFIX + inputId)
        if (name.isNullOrBlank()) it.remove(nameKey) else it[nameKey] = name.trim()
        if (logo == null) it.remove(logoKey) else it[logoKey] = logo.name
    }

    suspend fun setAppOrder(order: List<String>) = edit { it[Keys.appOrder] = order.joinToString(",") }
    suspend fun setAppVisibility(hidden: Set<String>, shown: Set<String>) = edit {
        it[Keys.hiddenApps] = hidden
        it[Keys.shownApps] = shown
    }
    suspend fun setShowSystemApps(value: Boolean) = edit { it[Keys.showSystemApps] = value }
    suspend fun setAutoCleanMemory(value: Boolean) = edit { it[Keys.autoCleanMemory] = value }
    suspend fun setMemoryThreshold(value: Int) = edit { it[Keys.memoryThreshold] = value }
    suspend fun setMemoryCleanExclusions(value: Set<String>) = edit { it[Keys.memoryExclusions] = value }

    suspend fun setAppOverrides(value: Map<String, AppOverride>) = edit { it[Keys.appOverrides] = CustomizationJson.encodeOverrides(value) }
    suspend fun setFolders(value: List<Folder>) = edit { it[Keys.folders] = CustomizationJson.encodeFolders(value) }

    suspend fun setPin(hash: String, salt: String) = edit {
        it[Keys.pinHash] = hash
        it[Keys.pinSalt] = salt
        it[Keys.pinFailures] = 0
        it[Keys.pinLockedUntil] = 0L
    }

    suspend fun clearPin() = edit {
        it.remove(Keys.pinHash)
        it.remove(Keys.pinSalt)
        it[Keys.pinFailures] = 0
        it[Keys.pinLockedUntil] = 0L
    }

    suspend fun setProtectSettings(value: Boolean) = edit { it[Keys.protectSettings] = value }

    suspend fun recordPinFailure(now: Long) = edit {
        val failures = (it[Keys.pinFailures] ?: 0) + 1
        it[Keys.pinFailures] = failures
        it[Keys.pinLockedUntil] = now + PinHasher.lockoutMillisAfter(failures)
    }

    suspend fun clearPinFailures() = edit {
        it[Keys.pinFailures] = 0
        it[Keys.pinLockedUntil] = 0L
    }

    suspend fun reset() = edit { it.clear() }
}
