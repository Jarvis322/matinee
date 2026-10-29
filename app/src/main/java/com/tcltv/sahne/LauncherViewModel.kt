package com.tcltv.sahne

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Application
import android.app.ActivityOptions
import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.asImageBitmap
import com.tcltv.sahne.update.SelfUpdate
import com.tcltv.sahne.update.SelfUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tcltv.sahne.data.AppCatalog
import com.tcltv.sahne.data.UserImages
import com.tcltv.sahne.data.HomeApps
import com.tcltv.sahne.data.FolderEdits
import com.tcltv.sahne.airplay.AirPlayReceiver
import com.tcltv.sahne.airplay.AirPlayService
import com.tcltv.sahne.airplay.AirPlayState
import com.tcltv.sahne.data.ImageImport
import com.tcltv.sahne.data.PhotoWallpaper
import com.tcltv.sahne.data.PickedImage
import com.tcltv.sahne.data.PinKind
import com.tcltv.sahne.data.AppOverride
import com.tcltv.sahne.data.BuiltinWallpaper
import com.tcltv.sahne.data.ResolvedWallpaper
import com.tcltv.sahne.data.WallpaperKey
import com.tcltv.sahne.data.WallpaperResolver
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
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

    /** A newer Matinee on GitHub; only the GitHub build ever has one. */
    data class AppUpdate(val update: SelfUpdate) : Notice {
        override val id: String get() = "self-update:${update.version}"
        override val time: Long get() = update.publishedAt
    }
}

/** Why the Home button no longer opens Matinee although the user turned it on. */
enum class HomeKeyProblem {
    /** Still switched on, but Android stopped the service and won't start it again. */
    STOPPED,

    /** Taken off Android's list of enabled services without the user. */
    SWITCHED_OFF,
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

    private val userImages = UserImages(application)
    private val imageImport = ImageImport(application)
    private val wallpaperResolver = WallpaperResolver(application, userImages)
    private val homeApps = HomeApps(application, userImages)
    val gate = ParentalGate(settings, settingsStore)

    /** Every app the catalog found, with the user's names, pictures and locks applied. */
    private val decoratedApps: StateFlow<List<LaunchableApp>> = combine(
        appCatalog.apps,
        settings.map { it.appOverrides }.distinctUntilChanged(),
    ) { apps, overrides -> homeApps.decorate(apps, overrides) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** What the home screen orders and shows: apps that sit in a folder are replaced by the folder's own card. */
    private val allApps: StateFlow<List<LaunchableApp>> = combine(
        decoratedApps,
        settings.map { it.folders }.distinctUntilChanged(),
    ) { apps, folders -> homeApps.withFolders(apps, folders) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val apps: StateFlow<List<LaunchableApp>> = combine(allApps, settings) { apps, s -> AppCatalog.ordered(apps, s) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val appsForSettings: StateFlow<List<LaunchableApp>> = combine(decoratedApps, settings) { apps, s -> AppCatalog.orderedIncludingHidden(apps, s) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The chosen wallpaper, drawn once at screen size; null when the colour follows the focused app. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val wallpaper: StateFlow<ResolvedWallpaper?> = settings.map { it.wallpaper }.distinctUntilChanged()
        .mapLatest { key -> if (key.isEmpty()) null else wallpaperResolver.resolve(key) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun wallpaperPreview(wallpaper: BuiltinWallpaper, width: Int) = wallpaperResolver.preview(wallpaper, width)

    fun userWallpaperPreview(file: String, width: Int) = wallpaperResolver.userPreview(file, width)


    fun photoWallpaperPreview(wallpaper: PhotoWallpaper, width: Int) = wallpaperResolver.photoPreview(wallpaper, width)

    val photoWallpapers: List<PhotoWallpaper> = PhotoWallpaper.available(application)

    fun setWallpaper(key: String) = update { setWallpaper(key) }

    /** Adds a picture from the gallery to the wallpapers and makes it the current one. */
    fun importWallpaper(image: PickedImage) {
        viewModelScope.launch {
            val name = "wallpaper_${System.currentTimeMillis()}.jpg"
            // 2560 on the long side keeps a photo sharp on a 4K TV without holding the original's megapixels.
            if (!imageImport.importTo(image, userImages.file(name), WALLPAPER_MAX_SIDE, keepAlpha = false)) return@launch
            customizeLock.withLock {
                settingsStore.setUserWallpapers(settings.first().userWallpapers + name)
                settingsStore.setWallpaper(WallpaperKey.user(name))
            }
        }
    }

    fun removeWallpaper(file: String) = update {
        customizeLock.withLock {
            val current = settings.first()
            setUserWallpapers(current.userWallpapers - file)
            if (current.wallpaper == WallpaperKey.user(file)) setWallpaper("")
            userImages.delete(file)
        }
    }

    private val _openFolder = MutableStateFlow<String?>(null)
    val openFolderId: StateFlow<String?> = _openFolder.asStateFlow()

    /** The apps inside the open folder, in folder order. */
    val openFolderApps: StateFlow<List<LaunchableApp>> = combine(openFolderId, decoratedApps, settings) { id, apps, s ->
        if (id == null) emptyList() else homeApps.membersOf(id, apps, s.folders)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun closeFolder() {
        _openFolder.value = null
    }

    private val rawInputs: StateFlow<List<RawHdmiInput>> = inputCatalog.rawInputs
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val inputs: StateFlow<List<HdmiInput>> = combine(rawInputs, settings) { raw, s -> InputCatalog.resolve(raw, s) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Bumped on every resume: notification access or the receiver app may have changed while away.
    private val receiverRefresh = MutableStateFlow(0)

    /**
     * Matinee's own receiver when it is on, otherwise the TV's built-in one: with ours running, the TV can take AirPlay
     * even if the maker left it out.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val airPlay: StateFlow<ReceiverStatus> = combine(
        receiverRefresh.flatMapLatest { airPlayMonitor.status },
        AirPlayReceiver.get(application).state,
    ) { builtIn, own ->
        when (own) {
            AirPlayState.MIRRORING -> ReceiverStatus(ReceiverState.PLAYING, canDetectPlayback = true)
            AirPlayState.READY -> ReceiverStatus(ReceiverState.READY, canDetectPlayback = true)
            AirPlayState.OFF, AirPlayState.FAILED -> builtIn
        }
    }
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

    val notices: StateFlow<List<Notice>> = combine(
        PlayStoreUpdates.updates,
        SelfUpdater.update,
        localNotices,
        dismissedNotices,
        settings,
    ) { play, self, local, dismissed, s ->
        val playNotices = if (s.showPlayUpdates) play.map { Notice.PlayUpdate(it) } else emptyList()
        val selfNotice = listOfNotNull(self?.let { Notice.AppUpdate(it) })
        (selfNotice + playNotices + local).filter { it.id !in dismissed }.sortedByDescending { it.time }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val unreadNotices: StateFlow<Int> = combine(notices, seenNotices) { list, seen -> list.count { it.id !in seen } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    init {
        viewModelScope.launch { runWeatherLoop() }
        viewModelScope.launch { watchForNewDevices() }
        viewModelScope.launch { runAirPlayReceiver() }
    }

    // A foreground service, so the receiver outlives this screen: phones can only find the TV while it runs.
    private suspend fun runAirPlayReceiver() {
        settings.map { it.airplayReceiver }.distinctUntilChanged().collect { enabled ->
            AirPlayService.setRunning(getApplication(), enabled, AirPlayService.receiverName(getApplication()))
        }
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
            is Notice.AppUpdate -> {
                // Kept in the list, so its progress and any failure stay visible.
                viewModelScope.launch { SelfUpdater.install(getApplication()) }
                return
            }
        }
        dismissedNotices.value = dismissedNotices.value + notice.id
    }

    /**
     * Play's "Manage apps & games" page, where pending updates are listed. Play tells other apps nothing about pending
     * updates unless it posts a notification, so the panel links here instead of counting them.
     */
    fun openPlayUpdates() {
        val intent = Intent(PLAY_MY_DOWNLOADS)
            .setPackage(TvNotificationListener.PLAY_STORE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!startSafely(intent)) openPlayStore()
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

    /**
     * Whether Android has the Home button service running. It can be switched on and still stopped: Android doesn't
     * restart an accessibility service whose process died until it is switched off and on again.
     */
    fun isHomeKeyServiceRunning(): Boolean {
        val app = getApplication<Application>()
        val component = ComponentName(app, HomeKeyService::class.java)
        return app.getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo?.serviceInfo?.let { info -> ComponentName(info.packageName, info.name) } == component }
    }

    /** The home screen told the user the Home button stopped and they closed it; it isn't repeated this session. */
    var homeKeyStoppedPromptDismissed = false

    private val homeKeyPrefs by lazy { getApplication<Application>().getSharedPreferences("home_key", Context.MODE_PRIVATE) }

    /**
     * What keeps the Home button from opening Matinee after the user turned it on, or null when nothing does. A TV that
     * force-stops Matinee (some clean up apps in standby) also takes it off Android's list of enabled services, so
     * having seen it run once is remembered here.
     */
    fun homeKeyProblem(): HomeKeyProblem? {
        val enabled = isHomeKeyServiceEnabled()
        if (enabled && isHomeKeyServiceRunning()) {
            if (!homeKeyPrefs.getBoolean(KEY_HOME_KEY_WANTED, false)) homeKeyPrefs.edit().putBoolean(KEY_HOME_KEY_WANTED, true).apply()
            return null
        }
        return when {
            enabled -> HomeKeyProblem.STOPPED
            homeKeyPrefs.getBoolean(KEY_HOME_KEY_WANTED, false) -> HomeKeyProblem.SWITCHED_OFF
            else -> null
        }
    }

    /** The user doesn't want the Home button back; stop asking until it runs again. */
    fun forgetHomeKey() = homeKeyPrefs.edit().putBoolean(KEY_HOME_KEY_WANTED, false).apply()

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
        viewModelScope.launch { SelfUpdater.checkIfDue(getApplication()) }
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

    /** A folder opens its contents; a locked app asks for the PIN first. */
    fun launchApp(app: LaunchableApp) {
        if (app.folderId != null) {
            _openFolder.value = app.folderId
        } else {
            gate.requireForApp(R.string.pin_reason_app, app.isLocked) { startSafely(appCatalog.launchIntent(app)) }
        }
    }

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

    private val customizeLock = Mutex()

    private fun changeOverride(packageName: String, change: (AppOverride) -> AppOverride) = update {
        customizeLock.withLock {
            val current = settings.first().appOverrides
            val next = change(current[packageName] ?: AppOverride())
            setAppOverrides(if (next.isDefault) current - packageName else current + (packageName to next))
        }
    }

    /** A blank name goes back to the app's own name. */
    fun renameApp(packageName: String, name: String) {
        val app = allApps.value.firstOrNull { it.packageName == packageName } ?: return
        if (app.folderId != null) renameFolder(app.folderId, name) else changeOverride(packageName) { it.copy(label = name.trim().ifEmpty { null }) }
    }

    fun setAppLocked(packageName: String, locked: Boolean) = changeOverride(packageName) { it.copy(locked = locked) }

    /** Stores a picture the user chose as the app's card, replacing any earlier one. */
    fun setAppIcon(packageName: String, picture: java.io.File?) = update {
        customizeLock.withLock {
            val current = settings.first().appOverrides
            val previous = current[packageName]?.icon
            val name = picture?.let { "icon_${packageName.hashCode().toUInt()}_${System.currentTimeMillis()}.img" }
            if (picture != null && name != null) picture.renameTo(userImages.file(name))
            if (previous != null) userImages.delete(previous)
            val next = (current[packageName] ?: AppOverride()).copy(icon = name)
            setAppOverrides(if (next.isDefault) current - packageName else current + (packageName to next))
        }
    }

    /** Takes a picture the user picked in the gallery and makes it the app's card. */
    fun importAppIcon(packageName: String, image: PickedImage) {
        viewModelScope.launch {
            val incoming = userImages.file("incoming_${System.currentTimeMillis()}.img")
            // PNG keeps the transparent corners of a logo; a photo shrinks to a card-sized picture.
            if (imageImport.importTo(image, incoming, ICON_MAX_SIDE, keepAlpha = true)) setAppIcon(packageName, incoming)
        }
    }

    fun resetAppIcon(packageName: String) = setAppIcon(packageName, null)

    private suspend fun currentOrder(): List<String> =
        AppCatalog.orderedIncludingHidden(allApps.value, settings.first()).map { it.packageName }

    fun createFolder(packageName: String, name: String) = update {
        customizeLock.withLock {
            val current = settings.first()
            val result = FolderEdits.create(currentOrder(), current.folders, packageName, name)
            setFolders(result.folders)
            setAppOrder(result.order)
        }
    }

    fun addToFolder(folderId: String, packageName: String) = update {
        customizeLock.withLock {
            val result = FolderEdits.add(currentOrder(), settings.first().folders, folderId, packageName)
            setFolders(result.folders)
            setAppOrder(result.order)
        }
    }

    fun removeFromFolder(packageName: String) = update {
        customizeLock.withLock {
            val result = FolderEdits.remove(currentOrder(), settings.first().folders, packageName)
            setFolders(result.folders)
            setAppOrder(result.order)
            // The folder may have just emptied; its open view would have nothing left to show.
            if (result.folders.none { it.id == _openFolder.value }) _openFolder.value = null
        }
    }

    fun deleteFolder(folderId: String) = update {
        customizeLock.withLock {
            val result = FolderEdits.delete(currentOrder(), settings.first().folders, folderId)
            setFolders(result.folders)
            setAppOrder(result.order)
            if (_openFolder.value == folderId) _openFolder.value = null
        }
    }

    fun renameFolder(folderId: String, name: String) = update {
        customizeLock.withLock { setFolders(FolderEdits.rename(settings.first().folders, folderId, name)) }
    }

    fun setPin(pin: String, kind: PinKind) {
        viewModelScope.launch { gate.setPin(pin, kind) }
    }

    fun removePin() {
        viewModelScope.launch { gate.removePin() }
    }

    fun toggleCleanExclusion(packageName: String) = update {
        val excluded = settings.first().memoryCleanExclusions
        setMemoryCleanExclusions(if (packageName in excluded) excluded - packageName else excluded + packageName)
    }

    private companion object {
        // Play's own action for its "Manage apps & games" page; checked on Play Store for Google TV (Android 14).
        const val PLAY_MY_DOWNLOADS = "com.google.android.finsky.VIEW_MY_DOWNLOADS"
        const val MEMORY_POLL_MILLIS = 15_000L
        const val SEARCH_DEBOUNCE_MILLIS = 400L
        const val SPEED_CACHE_MILLIS = 5 * 60_000L
        const val ARTWORK_CACHE_BYTES = 6 * 1024 * 1024
        const val ICON_MAX_SIDE = 640
        const val WALLPAPER_MAX_SIDE = 2560
        const val WEATHER_RETRY_MILLIS = 60_000L
        const val MEMORY_ALERT_REARM_PERCENT = 5
        const val MAX_LOCAL_NOTICES = 20
        const val KEY_HOME_KEY_WANTED = "wanted"
    }
}
