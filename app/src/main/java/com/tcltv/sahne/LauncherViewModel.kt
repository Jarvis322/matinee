package com.tcltv.sahne

import android.app.Application
import android.app.ActivityOptions
import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tcltv.sahne.data.AppCatalog
import com.tcltv.sahne.data.CleanResult
import com.tcltv.sahne.data.CurrentWeather
import com.tcltv.sahne.data.DeviceLogo
import com.tcltv.sahne.data.HdmiInput
import com.tcltv.sahne.data.InputCatalog
import com.tcltv.sahne.data.RawHdmiInput
import com.tcltv.sahne.data.LastPlayed
import com.tcltv.sahne.data.LiveTvCatalog
import com.tcltv.sahne.data.LiveTvInput
import com.tcltv.sahne.data.MediaHistory
import com.tcltv.sahne.data.ProfilePhoto
import com.tcltv.sahne.system.Connection
import com.tcltv.sahne.system.HomeKeyService
import com.tcltv.sahne.system.NetworkStatus
import com.tcltv.sahne.system.SpeedTest
import com.tcltv.sahne.system.PlayStoreUpdate
import com.tcltv.sahne.system.ReceiverMonitor
import com.tcltv.sahne.system.ReceiverState
import com.tcltv.sahne.system.ReceiverStatus
import com.tcltv.sahne.system.PlayStoreUpdates
import com.tcltv.sahne.system.TvNotificationListener
import com.tcltv.sahne.data.LaunchableApp
import com.tcltv.sahne.data.LauncherSettings
import com.tcltv.sahne.data.MemorySnapshot
import com.tcltv.sahne.data.MemoryStatus
import com.tcltv.sahne.data.SettingsStore
import com.tcltv.sahne.data.TemperatureUnit
import com.tcltv.sahne.data.WeatherClient
import com.tcltv.sahne.data.WeatherLocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.Locale

sealed interface WeatherUiState {
    data object Loading : WeatherUiState
    data class Ready(val weather: CurrentWeather) : WeatherUiState
    data object Unavailable : WeatherUiState
}

sealed interface Notice {
    val id: String
    val time: Long

    data class PlayUpdate(val update: PlayStoreUpdate) : Notice {
        override val id: String get() = update.id
        override val time: Long get() = update.postedAt
    }

    data class NewDevice(
        override val id: String,
        override val time: Long,
        val inputId: String,
        val deviceName: String,
        val portLabel: String,
        val logo: DeviceLogo,
    ) : Notice

    data class MemoryHigh(override val id: String, override val time: Long, val percent: Int, val threshold: Int) : Notice
}

sealed interface SpeedState {
    data object Idle : SpeedState
    data object Measuring : SpeedState
    data class Done(val mbps: Double, val measuredAt: Long) : SpeedState
    data object Failed : SpeedState
}

sealed interface CitySearchState {
    data object Idle : CitySearchState
    data object Searching : CitySearchState
    data class Results(val cities: List<WeatherLocation>) : CitySearchState
    data object Failed : CitySearchState
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = SettingsStore(application)
    private val appCatalog = AppCatalog(application)
    private val inputCatalog = InputCatalog(application)
    private val liveTvCatalog = LiveTvCatalog(application)
    private val weatherClient = WeatherClient()
    private val memoryStatus = MemoryStatus(application)
    private val airPlayMonitor = ReceiverMonitor(application, ReceiverMonitor.AIRPLAY_PACKAGES)
    private val googleCastMonitor = ReceiverMonitor(application, ReceiverMonitor.GOOGLE_CAST_PACKAGES)
    private val profilePhoto = ProfilePhoto(application)
    private val networkStatus = NetworkStatus(application)
    private val speedTest = SpeedTest()
    private val mediaHistory = MediaHistory.get(application)

    val settings: StateFlow<LauncherSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, LauncherSettings())

    private val allApps: StateFlow<List<LaunchableApp>> = appCatalog.apps
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val apps: StateFlow<List<LaunchableApp>> = combine(allApps, settings) { apps, s -> AppCatalog.ordered(apps, s) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val appsForSettings: StateFlow<List<LaunchableApp>> = combine(allApps, settings) { apps, s -> AppCatalog.orderedIncludingHidden(apps, s) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val rawInputs: StateFlow<List<RawHdmiInput>> = inputCatalog.rawInputs
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val inputs: StateFlow<List<HdmiInput>> = combine(rawInputs, settings) { raw, s -> InputCatalog.resolve(raw, s) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Bumped on every resume: notification access or the receiver app may have changed while away.
    private val receiverRefresh = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val airPlay: StateFlow<ReceiverStatus> = receiverRefresh.flatMapLatest { airPlayMonitor.status }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReceiverStatus(ReceiverState.UNAVAILABLE, canDetectPlayback = false))

    @OptIn(ExperimentalCoroutinesApi::class)
    val googleCast: StateFlow<ReceiverStatus> = receiverRefresh.flatMapLatest { googleCastMonitor.status }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReceiverStatus(ReceiverState.UNAVAILABLE, canDetectPlayback = false))

    /** Last played item per package, empty when the user turned the feature off. */
    val lastPlayed: StateFlow<Map<String, LastPlayed>> = combine(mediaHistory.entries, settings) { entries, s ->
        if (s.showLastPlayed) entries else emptyMap()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun clearMediaHistory() = mediaHistory.clear()

    val liveTvInputs: StateFlow<List<LiveTvInput>> = liveTvCatalog.tuners
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun openLiveTv(input: LiveTvInput) = startSafely(liveTvCatalog.tunerIntent(input))

    // The panel, its mini items and the grid hero show the same files, and focus moves ask again each time.
    private val artworkCache = object : LruCache<String, ImageBitmap>(ARTWORK_CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    suspend fun loadArtwork(entry: LastPlayed): ImageBitmap? {
        val path = entry.artworkPath ?: return null
        // The file is rewritten in place when the item changes, so the update time is part of the key.
        val key = "$path@${entry.updatedAt}"
        artworkCache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
            ?.also { artworkCache.put(key, it) }
    }

    val connection: StateFlow<Connection> = networkStatus.connection
        .stateIn(viewModelScope, SharingStarted.Eagerly, Connection.Offline)

    private val _speed = MutableStateFlow<SpeedState>(SpeedState.Idle)
    val speed: StateFlow<SpeedState> = _speed.asStateFlow()
    private var speedJob: Job? = null

    /** Runs the speed test while the network icon has focus, reusing a recent result instead of re-downloading. */
    fun onNetworkFocusChanged(focused: Boolean) {
        if (!focused) {
            speedJob?.cancel()
            if (_speed.value == SpeedState.Measuring) _speed.value = SpeedState.Idle
            return
        }
        val last = _speed.value
        val fresh = last is SpeedState.Done && System.currentTimeMillis() - last.measuredAt < SPEED_CACHE_MILLIS
        val online = connection.value is Connection.Wifi || connection.value == Connection.Ethernet
        if (fresh || !online || speedJob?.isActive == true) return
        _speed.value = SpeedState.Measuring
        speedJob = viewModelScope.launch {
            _speed.value = try {
                SpeedState.Done(speedTest.measureDownloadMbps(), System.currentTimeMillis())
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                SpeedState.Failed
            }
        }
    }

    fun hasLocationPermission(): Boolean = networkStatus.hasLocationPermission()

    fun openOwnAppDetails(): Boolean {
        val app = getApplication<Application>()
        return startSafely(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun onLocationPermissionChanged() = networkStatus.onLocationPermissionChanged()

    private val _avatar = MutableStateFlow<ImageBitmap?>(null)
    val avatar: StateFlow<ImageBitmap?> = _avatar.asStateFlow()
    val avatarPath: String = profilePhoto.expectedPath

    private val _weather = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val weather: StateFlow<WeatherUiState> = _weather.asStateFlow()

    private val _memory = MutableStateFlow(memoryStatus.snapshot())
    val memory: StateFlow<MemorySnapshot> = _memory.asStateFlow()

    private val _lastClean = MutableStateFlow<CleanResult?>(null)
    val lastClean: StateFlow<CleanResult?> = _lastClean.asStateFlow()

    private val _cleaning = MutableStateFlow(false)
    val cleaning: StateFlow<Boolean> = _cleaning.asStateFlow()

    private val _citySearch = MutableStateFlow<CitySearchState>(CitySearchState.Idle)
    val citySearch: StateFlow<CitySearchState> = _citySearch.asStateFlow()
    private var citySearchJob: Job? = null

    private val weatherRefreshRequest = MutableStateFlow(0)

    private val localNotices = MutableStateFlow<List<Notice>>(emptyList())
    private val dismissedNotices = MutableStateFlow<Set<String>>(emptySet())
    private val seenNotices = MutableStateFlow<Set<String>>(emptySet())

    val notices: StateFlow<List<Notice>> = combine(PlayStoreUpdates.updates, localNotices, dismissedNotices, settings) { play, local, dismissed, s ->
        val playNotices = if (s.showPlayUpdates) play.map { Notice.PlayUpdate(it) } else emptyList()
        (playNotices + local).filter { it.id !in dismissed }.sortedByDescending { it.time }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val unreadNotices: StateFlow<Int> = combine(notices, seenNotices) { list, seen -> list.count { it.id !in seen } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    init {
        viewModelScope.launch { runWeatherLoop() }
        viewModelScope.launch { watchForNewDevices() }
    }

    // Polled only while the home screen is visible; nothing shows the value while another app is in front.
    private var memoryPollJob: Job? = null

    // Re-armed only after usage falls clearly below the threshold, so hovering around it doesn't repeat the notice.
    private var memoryAlertArmed = true

    private fun updateMemory(snapshot: MemorySnapshot) {
        val threshold = settings.value.memoryThresholdPercent
        _memory.value = snapshot
        when {
            snapshot.usedPercent >= threshold && memoryAlertArmed -> {
                memoryAlertArmed = false
                val now = System.currentTimeMillis()
                localNotices.value = localNotices.value.filterNot { it is Notice.MemoryHigh } +
                    Notice.MemoryHigh(id = "memory@$now", time = now, percent = snapshot.usedPercent, threshold = threshold)
            }
            snapshot.usedPercent < threshold - MEMORY_ALERT_REARM_PERCENT -> memoryAlertArmed = true
        }
    }

    private suspend fun watchForNewDevices() {
        // Every (port, device name) seen this session. A device whose CEC entry drops while it sleeps and returns on
        // wake is the same device, so it is announced once, not on every power cycle.
        var announced: Set<Pair<String, String>>? = null
        // Raw CEC names, so toggling "auto-detect" in settings doesn't look like devices arriving.
        rawInputs.collect { raw ->
            if (raw.isEmpty() && announced == null) return@collect
            val present = raw.mapNotNull { input -> input.cecDeviceName?.takeIf { input.connected }?.let { input.id to it } }.toSet()
            val previous = announced
            if (previous != null) {
                // Resolved from this emission, not inputs.value, which still holds the previous list at this point.
                val resolved = InputCatalog.resolve(raw, settings.value).associateBy { it.id }
                (present - previous).forEach { (inputId, name) ->
                    val input = resolved[inputId] ?: return@forEach
                    val now = System.currentTimeMillis()
                    localNotices.value = (localNotices.value + Notice.NewDevice(
                        id = "device:$inputId@$now",
                        time = now,
                        inputId = inputId,
                        deviceName = name,
                        portLabel = input.portLabel,
                        logo = input.logo,
                    )).takeLast(MAX_LOCAL_NOTICES)
                }
            }
            announced = previous.orEmpty() + present
        }
    }

    fun markNoticesSeen() {
        seenNotices.value = seenNotices.value + notices.value.map { it.id }
    }

    fun clearNotices() {
        dismissedNotices.value = dismissedNotices.value + notices.value.map { it.id }
        localNotices.value = emptyList()
    }

    fun openNotice(notice: Notice) {
        when (notice) {
            is Notice.PlayUpdate -> {
                val sent = notice.update.openIntent?.let { intent ->
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            // From Android 14 a PendingIntent no longer inherits our right to start activities.
                            val options = ActivityOptions.makeBasic()
                                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                            intent.send(getApplication(), 0, null, null, null, null, options.toBundle())
                        } else {
                            intent.send()
                        }
                        true
                    } catch (e: PendingIntent.CanceledException) {
                        false
                    }
                } ?: false
                if (!sent) openPlayStore()
            }
            is Notice.NewDevice -> inputs.value.firstOrNull { it.id == notice.inputId }?.let(::openInput)
            is Notice.MemoryHigh -> cleanMemory()
        }
        dismissedNotices.value = dismissedNotices.value + notice.id
    }

    private fun openPlayStore(): Boolean {
        val pm = getApplication<Application>().packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(TvNotificationListener.PLAY_STORE)
            ?: pm.getLaunchIntentForPackage(TvNotificationListener.PLAY_STORE)
            ?: return false
        return startSafely(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Role holder only: a system launcher with a higher HOME filter priority can still win the Home key. */
    fun isDefaultHome(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val roles = getApplication<Application>().getSystemService(RoleManager::class.java) ?: return false
        return roles.isRoleAvailable(RoleManager.ROLE_HOME) && roles.isRoleHeld(RoleManager.ROLE_HOME)
    }

    /** Whether the Home key opens Sahne; the Home role alone doesn't guarantee it on TVs whose stock launcher has a higher filter priority. */
    fun isHomeKeyTarget(): Boolean {
        val app = getApplication<Application>()
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return app.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == app.packageName
    }

    /** Null when the TV has no Home role; callers fall back to the Home settings screen. */
    fun defaultHomeRequestIntent(): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val roles = getApplication<Application>().getSystemService(RoleManager::class.java) ?: return null
        return if (roles.isRoleAvailable(RoleManager.ROLE_HOME)) roles.createRequestRoleIntent(RoleManager.ROLE_HOME) else null
    }

    /**
     * Android 13+ blocks accessibility permissions for apps installed from a local or downloaded APK ("restricted
     * settings"); on TVs the switch just turns itself back off. Play and adb installs are not affected.
     */
    fun isAccessibilityRestricted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val app = getApplication<Application>()
        val source = runCatching { app.packageManager.getInstallSourceInfo(app.packageName).packageSource }.getOrNull()
        return source == PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE || source == PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE
    }

    fun isHomeKeyServiceEnabled(): Boolean {
        val app = getApplication<Application>()
        val component = ComponentName(app, HomeKeyService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return enabled.split(':').any { it.equals(component, ignoreCase = true) }
    }

    fun hasNotificationAccess(): Boolean {
        val app = getApplication<Application>()
        return NotificationManagerCompat.getEnabledListenerPackages(app).contains(app.packageName)
    }

    private suspend fun runWeatherLoop() {
        combine(
            settings.map { Triple(it.weatherLocation, it.temperatureUnit, it.weatherRefreshMinutes) }.distinctUntilChanged(),
            weatherRefreshRequest,
        ) { config, _ -> config }.collectLatest { (location, unit, refreshMinutes) ->
            while (true) {
                val succeeded = try {
                    _weather.value = WeatherUiState.Ready(weatherClient.current(location, unit))
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    if (_weather.value !is WeatherUiState.Ready) _weather.value = WeatherUiState.Unavailable
                    false
                }
                // The launcher starts before the network after a reboot, so a failed fetch retries soon, not after the full interval.
                delay(if (succeeded) refreshMinutes * 60_000L else WEATHER_RETRY_MILLIS)
            }
        }
    }

    fun onHomeResumed() {
        receiverRefresh.value++
        updateMemory(memoryStatus.snapshot())
        memoryPollJob?.cancel()
        memoryPollJob = viewModelScope.launch {
            while (isActive) {
                delay(MEMORY_POLL_MILLIS)
                updateMemory(memoryStatus.snapshot())
            }
        }
        viewModelScope.launch {
            _avatar.value = profilePhoto.load()
            profilePhoto.consumePendingName()?.let { settingsStore.setProfileName(it) }
        }
        val s = settings.value
        if (s.autoCleanMemory && _memory.value.usedPercent >= s.memoryThresholdPercent) cleanMemory()
    }

    fun onHomePaused() {
        memoryPollJob?.cancel()
        memoryPollJob = null
    }

    fun cleanMemory() {
        if (_cleaning.value) return
        _cleaning.value = true
        viewModelScope.launch {
            try {
                val candidates = allApps.value.map { it.packageName }
                _lastClean.value = memoryStatus.clean(candidates, settings.value.memoryCleanExclusions)
                _memory.value = memoryStatus.snapshot()
            } finally {
                _cleaning.value = false
            }
        }
    }

    fun refreshWeather() {
        _weather.value = WeatherUiState.Loading
        weatherRefreshRequest.value++
    }

    fun searchCities(query: String) {
        citySearchJob?.cancel()
        if (query.trim().length < 2) {
            _citySearch.value = CitySearchState.Idle
            return
        }
        citySearchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            _citySearch.value = CitySearchState.Searching
            _citySearch.value = try {
                CitySearchState.Results(weatherClient.searchCities(query, Locale.getDefault().language))
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                CitySearchState.Failed
            }
        }
    }

    fun clearCitySearch() {
        citySearchJob?.cancel()
        _citySearch.value = CitySearchState.Idle
    }

    fun launchApp(app: LaunchableApp) = startSafely(appCatalog.launchIntent(app))

    fun openInput(input: HdmiInput) = startSafely(inputCatalog.launchIntent(input.id))

    fun openAppInfo(app: LaunchableApp) = startSafely(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    /** Opens a specific system settings screen, falling back to the main TV settings when the TV lacks that screen. */
    fun openSystemSettings(action: String): Boolean =
        startSafely(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) ||
            startSafely(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    val versionName: String = runCatching {
        application.packageManager.getPackageInfo(application.packageName, 0).versionName
    }.getOrNull().orEmpty()

    private fun startSafely(intent: Intent): Boolean = try {
        getApplication<Application>().startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    fun update(block: suspend SettingsStore.() -> Unit) {
        viewModelScope.launch { settingsStore.block() }
    }

    fun setWeatherLocation(location: WeatherLocation) {
        update { setWeatherLocation(location) }
        clearCitySearch()
    }

    fun cycleTemperatureUnit() = update {
        val current = settings.first().temperatureUnit
        setTemperatureUnit(if (current == TemperatureUnit.CELSIUS) TemperatureUnit.FAHRENHEIT else TemperatureUnit.CELSIUS)
    }

    fun setInputOverride(input: HdmiInput, name: String?, logo: DeviceLogo?) = update {
        val defaultName = input.detectedName ?: input.portLabel
        val defaultLogo = input.detectedLogo ?: DeviceLogo.OTHER
        setInputOverride(
            inputId = input.id,
            name = name?.takeIf { it.isNotBlank() && it.trim() != defaultName },
            logo = logo?.takeIf { it != defaultLogo },
        )
    }

    fun resetInputOverride(input: HdmiInput) = update { setInputOverride(input.id, null, null) }

    private val orderLock = Mutex()

    /**
     * Moves among the cards the user can see; hidden apps keep their relative order after them.
     * Serialized so quick repeated presses each start from the previous move's saved order.
     */
    fun moveApp(packageName: String, delta: Int) = update {
        orderLock.withLock {
            val current = settings.first()
            val visible = AppCatalog.ordered(allApps.value, current).map { it.packageName }.toMutableList()
            val from = visible.indexOf(packageName)
            // Checked before coerceIn, which throws on the empty range an empty list gives.
            if (from < 0) return@withLock
            val to = (from + delta).coerceIn(0, visible.lastIndex)
            if (from == to) return@withLock
            visible.add(to, visible.removeAt(from))
            val hidden = AppCatalog.orderedIncludingHidden(allApps.value, current).map { it.packageName }.filter { it !in visible }
            setAppOrder(visible + hidden)
        }
    }

    fun toggleHidden(packageName: String) = update {
        val current = settings.first()
        val app = allApps.value.firstOrNull { it.packageName == packageName } ?: return@update
        if (AppCatalog.isHidden(app, current)) {
            setAppVisibility(hidden = current.hiddenApps - packageName, shown = current.shownApps + packageName)
        } else {
            setAppVisibility(hidden = current.hiddenApps + packageName, shown = current.shownApps - packageName)
        }
    }

    fun toggleCleanExclusion(packageName: String) = update {
        val excluded = settings.first().memoryCleanExclusions
        setMemoryCleanExclusions(if (packageName in excluded) excluded - packageName else excluded + packageName)
    }

    private companion object {
        const val MEMORY_POLL_MILLIS = 15_000L
        const val SEARCH_DEBOUNCE_MILLIS = 400L
        const val SPEED_CACHE_MILLIS = 5 * 60_000L
        const val ARTWORK_CACHE_BYTES = 6 * 1024 * 1024
        const val WEATHER_RETRY_MILLIS = 60_000L
        const val MEMORY_ALERT_REARM_PERCENT = 5
        const val MAX_LOCAL_NOTICES = 20
    }
}
