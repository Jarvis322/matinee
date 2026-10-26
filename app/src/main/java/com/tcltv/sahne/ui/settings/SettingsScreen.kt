@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.tcltv.sahne.ui.settings

import android.Manifest
import android.app.Activity
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tcltv.sahne.CitySearchState
import com.tcltv.sahne.LauncherViewModel
import com.tcltv.sahne.R
import com.tcltv.sahne.data.AppLanguage
import com.tcltv.sahne.data.BuiltinWallpaper
import com.tcltv.sahne.data.AppCatalog
import com.tcltv.sahne.ui.home.AddAppDialog
import com.tcltv.sahne.data.DeviceProfile
import com.tcltv.sahne.data.PhotoWallpaper
import com.tcltv.sahne.data.CardTheme
import com.tcltv.sahne.data.DeviceLogo
import com.tcltv.sahne.data.HdmiInput
import com.tcltv.sahne.data.HomeLayout
import com.tcltv.sahne.data.InputDetection
import com.tcltv.sahne.data.TemperatureUnit
import com.tcltv.sahne.data.WallpaperKey
import com.tcltv.sahne.ui.GlassStyle
import com.tcltv.sahne.ui.ImagePicker
import com.tcltv.sahne.ui.KeyCap
import com.tcltv.sahne.ui.Palette
import com.tcltv.sahne.ui.PinSetup
import com.tcltv.sahne.ui.TextInput
import com.tcltv.sahne.ui.TintedIcon
import com.tcltv.sahne.ui.glass
import com.tcltv.sahne.ui.home.CinematicBackground
import com.tcltv.sahne.ui.home.NeutralAccent
import com.tcltv.sahne.ui.home.TopBar
import com.tcltv.sahne.ui.home.profileColorCount
import com.tcltv.sahne.ui.interStyle
import com.tcltv.sahne.ui.longPressClickable

private enum class Section(@StringRes val title: Int, @StringRes val subtitle: Int, @DrawableRes val icon: Int) {
    NETWORK(R.string.settings_network, R.string.settings_network_sub, R.drawable.ic_wifi),
    DISPLAY_SOUND(R.string.settings_display_sound, R.string.settings_display_sound_sub, R.drawable.ic_device_desktop),
    APPS(R.string.settings_apps, R.string.settings_apps_sub, R.drawable.ic_layout_grid),
    INPUTS(R.string.settings_inputs, R.string.settings_inputs_sub, R.drawable.ic_hdmi),
    HOME(R.string.settings_home, R.string.settings_home_sub, R.drawable.ic_home),
    PARENTAL(R.string.settings_parental, R.string.settings_parental_sub, R.drawable.ic_lock),
    DEVICE(R.string.settings_device, R.string.settings_device_sub, R.drawable.ic_cpu),
    BLUETOOTH(R.string.settings_bluetooth, R.string.settings_bluetooth_sub, R.drawable.ic_bluetooth),
    ABOUT(R.string.settings_about, R.string.settings_about_sub, R.drawable.ic_info_circle),
    TV_SETTINGS(R.string.settings_tv, R.string.settings_tv_sub, R.drawable.ic_device_tv),
}

private sealed interface SubPage {
    data object CitySearch : SubPage
    data object ProfileName : SubPage
    data class InputEditor(val inputId: String) : SubPage
    data object CleanExclusions : SubPage
    data object Language : SubPage
    data object Licenses : SubPage
    data object LockedApps : SubPage
    data object Wallpaper : SubPage
}

private val Divider = Color.White.copy(alpha = 0.06f)

/** Gives each sub-page's entry row a focus requester, so closing the page puts focus back on the row that opened it. */
private class PageFocus {
    private val requesters = mutableMapOf<SubPage, FocusRequester>()

    fun of(page: SubPage): FocusRequester = requesters.getOrPut(page) { FocusRequester() }
}

private val LocalPageFocus = compositionLocalOf { PageFocus() }

@Composable
private fun Modifier.opensPage(page: SubPage): Modifier = focusRequester(LocalPageFocus.current.of(page))

@Composable
fun SettingsScreen(viewModel: LauncherViewModel, onClose: () -> Unit, onEditApps: () -> Unit) {
    var section by rememberSaveable { mutableStateOf(Section.HOME) }
    var subPage by remember { mutableStateOf<SubPage?>(null) }
    val navRequesters = remember { Section.entries.associateWith { FocusRequester() } }
    val panelRequester = remember { FocusRequester() }
    val pageFocus = remember { PageFocus() }
    var returningFrom by remember { mutableStateOf<SubPage?>(null) }
    fun closePage() {
        returningFrom = subPage
        subPage = null
    }
    // Once the section's rows are back, focus returns to the one that opened the page instead of being lost.
    LaunchedEffect(subPage, returningFrom) {
        val page = returningFrom
        if (subPage == null && page != null) {
            returningFrom = null
            runCatching { pageFocus.of(page).requestFocus() }.onFailure { runCatching { panelRequester.requestFocus() } }
        }
    }

    BackHandler {
        if (subPage != null) closePage() else onClose()
    }
    LaunchedEffect(Unit) { runCatching { navRequesters.getValue(section).requestFocus() } }

    CompositionLocalProvider(LocalPageFocus provides pageFocus) {
    Box(Modifier.fillMaxSize()) {
        CinematicBackground(NeutralAccent)
        TopBar(viewModel, onOpenSettings = {}, modifier = Modifier.padding(start = 48.dp, end = 48.dp, top = 30.dp))

        Header(section, Modifier.padding(start = 48.dp, top = 88.dp))

        Column(Modifier.padding(start = 48.dp, top = 140.dp).width(291.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Section.entries.forEach { item ->
                NavItem(
                    section = item,
                    selected = item == section,
                    focusRequester = navRequesters.getValue(item),
                    onFocused = {
                        section = item
                        subPage = null
                    },
                    onClick = {
                        // The TV's own settings open straight from the menu; every other section moves into its panel.
                        if (item == Section.TV_SETTINGS) {
                            viewModel.openSystemSettings(Settings.ACTION_SETTINGS)
                        } else {
                            runCatching { panelRequester.requestFocus() }
                        }
                    },
                )
            }
        }

        Column(
            Modifier
                .padding(start = 348.dp, end = 48.dp, top = 137.dp, bottom = 59.dp)
                .fillMaxSize()
                // Left out of the panel goes back to the section it belongs to, not to whichever menu row happens to
                // sit level with the focused row.
                .focusProperties { exit = { direction -> if (direction == FocusDirection.Left) navRequesters.getValue(section) else FocusRequester.Default } }
                .focusGroup()
                .glass(RoundedCornerShape(30.dp), GlassStyle.Panel)
                .padding(start = 22.dp, end = 22.dp, top = 20.dp),
        ) {
            val page = subPage
            PanelHeader(
                title = when (page) {
                    null -> stringResource(section.title)
                    SubPage.CitySearch -> stringResource(R.string.setting_city)
                    SubPage.ProfileName -> stringResource(R.string.setting_profile_name)
                    SubPage.CleanExclusions -> stringResource(R.string.setting_exclusions)
                    SubPage.Language -> stringResource(R.string.setting_language)
                    SubPage.Licenses -> stringResource(R.string.setting_licenses)
                    SubPage.LockedApps -> stringResource(R.string.setting_locked_apps)
                    SubPage.Wallpaper -> stringResource(R.string.setting_wallpaper)
                    is SubPage.InputEditor -> stringResource(R.string.edit_input)
                },
                subtitle = stringResource(section.subtitle),
            )
            Spacer(Modifier.height(10.dp))
            when (page) {
                null -> SectionContent(section, viewModel, panelRequester, onEditApps) { subPage = it }
                SubPage.CitySearch -> CitySearchPage(viewModel, panelRequester) { closePage() }
                is SubPage.InputEditor -> InputEditorPage(viewModel, page.inputId, panelRequester)
                SubPage.ProfileName -> ProfileNamePage(viewModel, panelRequester)
                SubPage.CleanExclusions -> CleanExclusionsPage(viewModel, panelRequester)
                SubPage.Language -> LanguagePage(panelRequester)
                SubPage.Licenses -> LicensesPage(panelRequester)
                SubPage.LockedApps -> LockedAppsPage(viewModel, panelRequester)
                SubPage.Wallpaper -> WallpaperPage(viewModel, panelRequester)
            }
        }

        Row(Modifier.padding(start = 48.dp, top = 494.dp), verticalAlignment = Alignment.CenterVertically) {
            Hint("◀", stringResource(R.string.hint_back))
            Spacer(Modifier.width(16.dp))
            Hint("▲▼", stringResource(R.string.hint_navigate))
            Spacer(Modifier.width(16.dp))
            Hint(stringResource(R.string.key_ok), stringResource(R.string.hint_select))
        }
    }
    }
}

@Composable
private fun Hint(key: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        KeyCap(key)
        Spacer(Modifier.width(7.dp))
        BasicText(label, style = interStyle(11.sp, 500, Palette.TextMuted))
    }
}

@Composable
private fun Header(section: Section, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF8A8D96), Color(0xFF4B4E57)))),
            contentAlignment = Alignment.Center,
        ) {
            TintedIcon(R.drawable.ic_settings, 18.dp, Color.White)
        }
        Spacer(Modifier.width(11.dp))
        BasicText(stringResource(R.string.settings_title), style = interStyle(31.sp, 800, letterSpacing = (-1.2).sp))
        Spacer(Modifier.width(12.dp))
        BasicText("/ " + stringResource(section.title), style = interStyle(13.sp, 500, Color.White.copy(alpha = 0.55f)))
    }
}

@Composable
private fun NavItem(
    section: Section,
    selected: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val highlighted = focused
    val label = if (highlighted) Palette.Background else Color.White.copy(alpha = if (selected) 1f else 0.88f)
    Row(
        Modifier
            .fillMaxWidth()
            .height(33.dp)
            .then(
                when {
                    highlighted -> Modifier.glass(CircleShape, GlassStyle.Bright)
                    selected -> Modifier.glass(CircleShape, GlassStyle.Selected)
                    else -> Modifier
                },
            )
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            // Right always enters the panel at its first row, as in the TV's own settings.
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(start = 11.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .background(if (highlighted) Palette.Background else Color.White.copy(alpha = 0.06f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            TintedIcon(section.icon, 12.dp, Color.White.copy(alpha = if (highlighted) 1f else 0.85f))
        }
        Spacer(Modifier.width(11.dp))
        BasicText(
            stringResource(section.title),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = interStyle(14.sp, if (highlighted) 600 else 500, label),
            modifier = Modifier.weight(1f),
        )
        if (highlighted) TintedIcon(R.drawable.ic_chevron_right, 12.dp, Palette.Background.copy(alpha = 0.6f))
    }
}

@Composable
private fun PanelHeader(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BasicText(title, style = interStyle(19.sp, 800, letterSpacing = (-0.5).sp), modifier = Modifier.weight(1f))
        BasicText(subtitle, style = interStyle(11.sp, 500, Color.White.copy(alpha = 0.55f)))
    }
}

@Composable
private fun SectionContent(
    section: Section,
    viewModel: LauncherViewModel,
    firstRow: FocusRequester,
    onEditApps: () -> Unit,
    open: (SubPage) -> Unit,
) {
    when (section) {
        Section.NETWORK -> SystemPanel(
            viewModel,
            firstRow,
            listOf(SystemLink(R.string.system_network, R.string.system_network_sub, Settings.ACTION_WIFI_SETTINGS)),
        )
        Section.DISPLAY_SOUND -> SystemPanel(
            viewModel,
            firstRow,
            listOf(
                SystemLink(R.string.system_display, R.string.system_display_sub, Settings.ACTION_DISPLAY_SETTINGS),
                SystemLink(R.string.system_sound, R.string.system_sound_sub, Settings.ACTION_SOUND_SETTINGS),
                // Every Android TV has this screen, though some vendors tuck it away; the fallback is the main settings.
                SystemLink(R.string.system_accessibility, R.string.system_accessibility_sub, Settings.ACTION_ACCESSIBILITY_SETTINGS),
            ),
        )
        Section.APPS -> SystemPanel(
            viewModel,
            firstRow,
            listOf(SystemLink(R.string.system_apps, R.string.system_apps_sub, Settings.ACTION_APPLICATION_SETTINGS)),
            extra = {
                val s by viewModel.settings.collectAsStateWithLifecycle()
                val allApps by viewModel.appsForSettings.collectAsStateWithLifecycle()
                var adding by remember { mutableStateOf(false) }
                val hidden = allApps.filter { AppCatalog.isHidden(it, s) }
                ValueRow(
                    R.string.setting_add_app,
                    R.string.setting_add_app_sub,
                    if (hidden.isEmpty()) stringResource(R.string.none) else stringResource(R.string.app_count, hidden.size),
                ) { adding = true }
                if (adding) {
                    AddAppDialog(
                        hiddenApps = hidden,
                        onAdd = { app ->
                            viewModel.toggleHidden(app.packageName)
                            adding = false
                        },
                        onDismiss = { adding = false },
                    )
                }
                ToggleRow(R.string.setting_show_add_tile, R.string.setting_show_add_tile_sub, s.showAddTile) {
                    viewModel.update { setShowAddTile(it) }
                }
                ValueRow(R.string.setting_app_order, R.string.setting_app_order_sub, null, onClick = onEditApps)
                ToggleRow(R.string.setting_show_system_apps, R.string.setting_show_system_apps_sub, s.showSystemApps) {
                    viewModel.update { setShowSystemApps(it) }
                }
            },
        )
        Section.BLUETOOTH -> SystemPanel(
            viewModel,
            firstRow,
            listOf(SystemLink(R.string.system_bluetooth, R.string.system_bluetooth_sub, Settings.ACTION_BLUETOOTH_SETTINGS)),
        )
        Section.HOME -> HomePanel(viewModel, firstRow, onEditApps, open)
        Section.PARENTAL -> ParentalPanel(viewModel, firstRow, open)
        Section.INPUTS -> InputsPanel(viewModel, firstRow, open)
        Section.DEVICE -> DevicePanel(viewModel, firstRow, open)
        Section.ABOUT -> AboutPanel(viewModel, firstRow, open)
        Section.TV_SETTINGS -> SystemPanel(
            viewModel,
            firstRow,
            listOf(SystemLink(R.string.system_tv_settings, R.string.system_tv_settings_sub, Settings.ACTION_SETTINGS)),
        )
    }
}

private data class SystemLink(@StringRes val title: Int, @StringRes val subtitle: Int, val action: String)

@Composable
private fun SystemPanel(
    viewModel: LauncherViewModel,
    firstRow: FocusRequester,
    links: List<SystemLink>,
    extra: @Composable () -> Unit = {},
) {
    var failed by remember { mutableStateOf(false) }
    PanelList {
        links.forEachIndexed { index, link ->
            ValueRow(
                link.title,
                link.subtitle,
                stringResource(R.string.open_in_tv_settings),
                modifier = if (index == 0) Modifier.focusRequester(firstRow) else Modifier,
            ) {
                failed = !viewModel.openSystemSettings(link.action)
            }
        }
        extra()
        if (failed) Note(R.string.note_system_settings_missing)
        Note(R.string.note_system_settings)
    }
}

@Composable
private fun HomePanel(viewModel: LauncherViewModel, firstRow: FocusRequester, onEditApps: () -> Unit, open: (SubPage) -> Unit) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val airPlay by viewModel.airPlay.collectAsStateWithLifecycle()
    val avatar by viewModel.avatar.collectAsStateWithLifecycle()
    PanelList {
        DefaultHomeRow(viewModel, Modifier.focusRequester(firstRow))
        HomeKeyRow(viewModel)
        WifiNameRow(viewModel)
        ValueRow(
            R.string.setting_wallpaper,
            null,
            wallpaperName(s.wallpaper),
            Modifier.opensPage(SubPage.Wallpaper),
        ) { open(SubPage.Wallpaper) }
        ToggleRow(R.string.setting_adaptive_background, R.string.setting_adaptive_background_sub, s.adaptiveBackground) {
            viewModel.update { setAdaptiveBackground(it) }
        }
        ToggleRow(R.string.setting_glass_effects, R.string.setting_glass_effects_sub, s.glassEffects) {
            viewModel.update { setGlassEffects(it) }
        }
        ToggleRow(R.string.setting_airplay_receiver, R.string.setting_airplay_receiver_sub, s.airplayReceiver) {
            viewModel.update { setAirplayReceiver(it) }
        }
        ValueRow(
            R.string.setting_home_layout,
            R.string.setting_home_layout_sub,
            stringResource(if (s.homeLayout == HomeLayout.GRID) R.string.home_layout_grid else R.string.home_layout_row),
        ) {
            viewModel.update { setHomeLayout(if (s.homeLayout == HomeLayout.GRID) HomeLayout.ROW else HomeLayout.GRID) }
        }
        ValueRow(
            R.string.setting_card_theme,
            null,
            stringResource(if (s.cardTheme == CardTheme.LIGHT) R.string.card_theme_light else R.string.card_theme_dark),
        ) {
            viewModel.update { setCardTheme(if (s.cardTheme == CardTheme.LIGHT) CardTheme.DARK else CardTheme.LIGHT) }
        }
        ValueRow(R.string.setting_clock_format, null, stringResource(if (s.use24Hour) R.string.clock_24 else R.string.clock_12)) {
            viewModel.update { setUse24Hour(!s.use24Hour) }
        }
        ValueRow(R.string.setting_city, null, s.weatherLocation.name.substringBefore(','), Modifier.opensPage(SubPage.CitySearch)) { open(SubPage.CitySearch) }
        ValueRow(R.string.setting_app_order, R.string.setting_app_order_sub, null, onClick = onEditApps)
        ToggleRow(R.string.setting_widget_inputs, R.string.setting_widget_inputs_sub, s.showInputs) { viewModel.update { setShowInputs(it) } }
        ValueRow(R.string.setting_memory_warning, R.string.setting_memory_warning_sub, stringResource(R.string.percent_value, s.memoryThresholdPercent)) {
            val next = when (s.memoryThresholdPercent) { 70 -> 80; 80 -> 90; else -> 70 }
            viewModel.update { setMemoryThreshold(next) }
        }
        ToggleRow(R.string.setting_notifications, R.string.setting_notifications_sub, s.showNotifications) {
            viewModel.update { setShowNotifications(it) }
        }
        NotificationAccessRow(viewModel)
        ToggleRow(
            R.string.setting_play_updates,
            if (viewModel.hasNotificationAccess()) R.string.setting_play_updates_sub_ok else R.string.setting_play_updates_sub_missing,
            s.showPlayUpdates,
        ) { viewModel.update { setShowPlayUpdates(it) } }
        ToggleRow(R.string.setting_last_played, R.string.setting_last_played_sub, s.showLastPlayed) {
            viewModel.update { setShowLastPlayed(it) }
        }
        var historyCleared by remember { mutableStateOf(false) }
        ValueRow(R.string.setting_clear_history, null, if (historyCleared) stringResource(R.string.history_cleared) else null) {
            viewModel.clearMediaHistory()
            historyCleared = true
        }
        ToggleRow(R.string.setting_show_seconds, null, s.showSeconds) { viewModel.update { setShowSeconds(it) } }
        ToggleRow(R.string.setting_greeting, null, s.showGreeting) { viewModel.update { setShowGreeting(it) } }
        ToggleRow(R.string.setting_widget_clock, null, s.showClock) { viewModel.update { setShowClock(it) } }
        ToggleRow(R.string.setting_widget_weather, null, s.showWeather) { viewModel.update { setShowWeather(it) } }
        ValueRow(
            R.string.setting_unit,
            null,
            stringResource(if (s.temperatureUnit == TemperatureUnit.CELSIUS) R.string.unit_celsius else R.string.unit_fahrenheit),
        ) { viewModel.cycleTemperatureUnit() }
        ValueRow(R.string.setting_refresh, null, stringResource(R.string.every_minutes, s.weatherRefreshMinutes)) {
            val next = when (s.weatherRefreshMinutes) { 15 -> 30; 30 -> 60; else -> 15 }
            viewModel.update { setWeatherRefreshMinutes(next) }
        }
        ToggleRow(
            R.string.setting_widget_airplay,
            if (airPlay.canDetectPlayback) R.string.setting_widget_airplay_sub_full else R.string.setting_widget_airplay_sub_limited,
            s.showAirPlay,
        ) { viewModel.update { setShowAirPlay(it) } }
        ToggleRow(R.string.setting_widget_memory, null, s.showMemory) { viewModel.update { setShowMemory(it) } }
        ToggleRow(R.string.setting_chromecast, R.string.setting_chromecast_sub, s.showChromecast) { viewModel.update { setShowChromecast(it) } }
        ValueRow(R.string.setting_profile_name, null, s.profileName.ifBlank { stringResource(R.string.not_set) }, Modifier.opensPage(SubPage.ProfileName)) { open(SubPage.ProfileName) }
        ValueRow(R.string.setting_profile_color, null, stringResource(R.string.color_number, s.profileColor.mod(profileColorCount) + 1)) {
            viewModel.update { setProfileColor((s.profileColor + 1).mod(profileColorCount)) }
        }
        InfoRow(
            R.string.setting_profile_photo,
            R.string.setting_profile_photo_sub,
            stringResource(if (avatar != null) R.string.photo_present else R.string.photo_missing),
        )
        ToggleRow(R.string.setting_reduce_motion, null, s.reduceMotion) { viewModel.update { setReduceMotion(it) } }
        InfoRow(R.string.setting_phone_bridge, R.string.setting_phone_bridge_sub, stringResource(R.string.coming_soon))
    }
}

@Composable
private fun InputsPanel(viewModel: LauncherViewModel, firstRow: FocusRequester, open: (SubPage) -> Unit) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val inputs by viewModel.inputs.collectAsStateWithLifecycle()
    PanelList {
        inputs.forEachIndexed { index, input ->
            InputRow(input, (if (index == 0) Modifier.focusRequester(firstRow) else Modifier).opensPage(SubPage.InputEditor(input.id))) { open(SubPage.InputEditor(input.id)) }
        }
        ToggleRow(
            R.string.setting_auto_detect,
            R.string.setting_auto_detect_sub,
            s.autoDetectInputs,
            if (inputs.isEmpty()) Modifier.focusRequester(firstRow) else Modifier,
        ) { viewModel.update { setAutoDetectInputs(it) } }
        ToggleRow(R.string.setting_hide_disconnected, null, s.hideDisconnectedInputs) { viewModel.update { setHideDisconnectedInputs(it) } }
        if (inputs.isEmpty()) Note(R.string.note_no_inputs)
        Note(R.string.note_inputs)
    }
}

@Composable
private fun DevicePanel(viewModel: LauncherViewModel, firstRow: FocusRequester, open: (SubPage) -> Unit) {
    val context = LocalContext.current
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val memory by viewModel.memory.collectAsStateWithLifecycle()
    val cleaning by viewModel.cleaning.collectAsStateWithLifecycle()
    val lastClean by viewModel.lastClean.collectAsStateWithLifecycle()
    val storage = remember {
        runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
    }
    val result = lastClean
    PanelList {
        InfoRow(
            R.string.setting_ram_usage,
            null,
            stringResource(R.string.memory_usage, memory.usedPercent, Formatter.formatShortFileSize(context, memory.availableBytes)),
            modifier = Modifier.focusRequester(firstRow),
        )
        val profile = remember { DeviceProfile.of(context) }
        InfoRow(
            R.string.setting_chip,
            null,
            listOf(
                profile.chip.ifBlank { null },
                pluralStringResource(R.plurals.cpu_cores, profile.cores, profile.cores),
                Formatter.formatShortFileSize(context, profile.memoryBytes),
            ).filterNotNull().joinToString(" · "),
        )
        InfoRow(R.string.setting_performance, R.string.setting_performance_sub, stringResource(profile.tier.label))
        ValueRow(
            R.string.setting_clean_now,
            R.string.setting_clean_now_sub,
            when {
                cleaning -> stringResource(R.string.memory_cleaning)
                result != null -> stringResource(R.string.memory_freed_mb, (result.freedBytes / (1024 * 1024)).toInt())
                else -> null
            },
        ) { viewModel.cleanMemory() }
        ToggleRow(R.string.setting_auto_clean, R.string.setting_auto_clean_sub, s.autoCleanMemory) { viewModel.update { setAutoCleanMemory(it) } }
        ValueRow(
            R.string.setting_exclusions,
            null,
            if (s.memoryCleanExclusions.isEmpty()) stringResource(R.string.none) else stringResource(R.string.app_count, s.memoryCleanExclusions.size),
            Modifier.opensPage(SubPage.CleanExclusions),
        ) { open(SubPage.CleanExclusions) }
        if (storage != null) {
            ValueRow(
                R.string.setting_storage,
                null,
                stringResource(
                    R.string.storage_value,
                    Formatter.formatShortFileSize(context, storage.availableBytes),
                    Formatter.formatShortFileSize(context, storage.totalBytes),
                ),
            ) { viewModel.openSystemSettings(Settings.ACTION_INTERNAL_STORAGE_SETTINGS) }
        }
        Note(R.string.note_memory)
    }
}

@Composable
private fun AboutPanel(viewModel: LauncherViewModel, firstRow: FocusRequester, open: (SubPage) -> Unit) {
    var confirmReset by remember { mutableStateOf(false) }
    PanelList {
        InfoRow(R.string.setting_version, null, viewModel.versionName, modifier = Modifier.focusRequester(firstRow))
        LanguageRow(Modifier.opensPage(SubPage.Language)) { open(SubPage.Language) }
        DefaultHomeRow(viewModel, Modifier)
        ValueRow(R.string.setting_reset, null, stringResource(if (confirmReset) R.string.reset_confirm else R.string.reset_action)) {
            if (confirmReset) {
                viewModel.update { reset() }
                confirmReset = false
            } else {
                confirmReset = true
            }
        }
        ValueRow(R.string.setting_licenses, null, null, Modifier.opensPage(SubPage.Licenses)) { open(SubPage.Licenses) }
        Credits()
    }
}

@Composable
private fun LanguageRow(modifier: Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    val current = remember { AppLanguage.current(context) }
    ValueRow(R.string.setting_language, null, stringResource(current.label), modifier, onClick)
}

@Composable
private fun LanguagePage(firstRow: FocusRequester) {
    val context = LocalContext.current
    val current = remember { AppLanguage.current(context) }
    LaunchedEffect(Unit) { runCatching { firstRow.requestFocus() } }
    PanelList {
        AppLanguage.entries.forEach { language ->
            ChoiceRow(
                label = stringResource(language.label),
                selected = language == current,
                modifier = if (language == current) Modifier.focusRequester(firstRow) else Modifier,
            ) {
                if (language != current) {
                    AppLanguage.save(context, language)
                    // Resources are resolved when the activity is created, so the new language needs a fresh one.
                    (context as? Activity)?.recreate()
                }
            }
        }
    }
}

/** Who made Matinee and where to find them: a TV has no browser to open links in, so they are shown, not linked. */
@Composable
private fun Credits() {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .glass(shape, if (focused) GlassStyle.Lit else GlassStyle.Selected)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(R.drawable.ic_launcher),
                contentDescription = null,
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.width(14.dp))
            Column {
                BasicText(stringResource(R.string.app_name), style = interStyle(16.sp, 800))
                BasicText(stringResource(R.string.credits_made_by, DEVELOPER_NAME), style = interStyle(12.sp, 500, Palette.TextSecondary))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CreditChip(R.drawable.ic_world, DEVELOPER_SITE)
            CreditChip(R.drawable.ic_brand_x, DEVELOPER_X)
            CreditChip(R.drawable.ic_brand_github, DEVELOPER_GITHUB)
        }
    }
}

@Composable
private fun CreditChip(@DrawableRes icon: Int, label: String) {
    Row(
        Modifier
            .background(Color.White.copy(alpha = 0.1f), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(icon), contentDescription = null, colorFilter = ColorFilter.tint(Palette.TextPrimary), modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(7.dp))
        BasicText(label, maxLines = 1, style = interStyle(12.5.sp, 650))
    }
}

private const val DEVELOPER_NAME = "Yiğit Can Polat"
private const val DEVELOPER_SITE = "yigitech.dev"
private const val DEVELOPER_X = "@yigitech"
private const val DEVELOPER_GITHUB = "github.com/Jarvis322/matinee"

@Composable
private fun LicensesPage(firstRow: FocusRequester) {
    LaunchedEffect(Unit) { runCatching { firstRow.requestFocus() } }
    PanelList {
        InfoTextBlock(R.string.license_text, Modifier.focusRequester(firstRow))
    }
}

/** A focusable block of plain text, so long read-only content can still be reached and scrolled with the remote. */
@Composable
private fun InfoTextBlock(@StringRes res: Int, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    BasicText(
        stringResource(res),
        style = interStyle(12.sp, 500, Palette.TextSecondary).copy(lineHeight = 18.sp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, if (focused) Color.White.copy(alpha = 0.45f) else Color.Transparent, RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(12.dp),
    )
}

@Composable
private fun ParentalPanel(viewModel: LauncherViewModel, firstRow: FocusRequester, open: (SubPage) -> Unit) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val parental = s.parental
    var showSetup by remember { mutableStateOf(false) }
    var setupClosed by remember { mutableStateOf(false) }
    // The row that opened the PIN screen may be gone by the time it closes (Set a PIN becomes Change PIN, a moment
    // after the PIN is saved), so focus returns to the first row of the panel, and again once that row is replaced.
    LaunchedEffect(setupClosed, parental.hasPin) {
        if (setupClosed) runCatching { firstRow.requestFocus() }
    }
    PanelList {
        if (!parental.hasPin) {
            ValueRow(R.string.setting_pin_set, R.string.setting_pin_set_sub, null, Modifier.focusRequester(firstRow)) {
                setupClosed = false
                showSetup = true
            }
        } else {
            // Changing or removing the PIN always asks for the current one, whatever the "protect settings" choice.
            ValueRow(R.string.setting_pin_change, null, null, Modifier.focusRequester(firstRow)) {
                viewModel.gate.requireForApp(R.string.pin_reason_admin, locked = true) {
                    setupClosed = false
                    showSetup = true
                }
            }
            ValueRow(R.string.setting_pin_remove, null, null) {
                viewModel.gate.requireForApp(R.string.pin_reason_admin, locked = true) { viewModel.removePin() }
            }
            ToggleRow(R.string.setting_protect_settings, R.string.setting_protect_settings_sub, parental.protectSettings) {
                viewModel.update { setProtectSettings(it) }
            }
        }
        ValueRow(
            R.string.setting_locked_apps,
            null,
            s.appOverrides.values.count { it.locked }.let { if (it == 0) stringResource(R.string.none) else stringResource(R.string.app_count, it) },
            Modifier.opensPage(SubPage.LockedApps),
        ) { open(SubPage.LockedApps) }
        Note(R.string.note_pin_forgot)
    }
    if (showSetup) {
        PinSetup(
            title = stringResource(R.string.pin_new_title),
            onSet = { pin, kind ->
                viewModel.setPin(pin, kind)
                showSetup = false
                setupClosed = true
            },
            onCancel = {
                showSetup = false
                setupClosed = true
            },
        )
    }
}

@Composable
private fun wallpaperName(key: String): String = when {
    key.startsWith(WallpaperKey.BUILTIN_PREFIX) ->
        BuiltinWallpaper.byId(key.removePrefix(WallpaperKey.BUILTIN_PREFIX))?.let { stringResource(it.label) } ?: stringResource(R.string.wallpaper_adaptive)
    key.startsWith(WallpaperKey.PHOTO_PREFIX) ->
        PhotoWallpaper.byId(key.removePrefix(WallpaperKey.PHOTO_PREFIX))?.let { stringResource(it.label) } ?: stringResource(R.string.wallpaper_adaptive)
    key.startsWith(WallpaperKey.USER_PREFIX) -> stringResource(R.string.wallpaper_yours)
    else -> stringResource(R.string.wallpaper_adaptive)
}

@Composable
private fun WallpaperPage(viewModel: LauncherViewModel, firstRow: FocusRequester) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { firstRow.requestFocus() } }
    // The previews are drawn once per page visit; each is a small picture, so this is cheap.
    val builtinPreviews = remember { BuiltinWallpaper.entries.associateWith { viewModel.wallpaperPreview(it, PREVIEW_WIDTH) } }
    val photoPreviews = remember { viewModel.photoWallpapers.associateWith { viewModel.photoWallpaperPreview(it, PREVIEW_WIDTH) } }
    PanelList {
        if (s.wallpaper.startsWith(WallpaperKey.BUILTIN_PREFIX)) {
            ToggleRow(R.string.setting_wallpaper_animated, R.string.setting_wallpaper_animated_sub, s.wallpaperAnimated, Modifier.focusRequester(firstRow)) {
                viewModel.update { setWallpaperAnimated(it) }
            }
        }
        val tiles = buildList {
            add(WallpaperTile(stringResource(R.string.wallpaper_adaptive), null, "", false))
            BuiltinWallpaper.entries.forEach { add(WallpaperTile(stringResource(it.label), builtinPreviews[it], WallpaperKey.builtin(it), false)) }
            photoPreviews.forEach { (photo, preview) -> add(WallpaperTile(stringResource(photo.label), preview, WallpaperKey.photo(photo), false)) }
            s.userWallpapers.forEach { file ->
                add(WallpaperTile(stringResource(R.string.wallpaper_yours), viewModel.userWallpaperPreview(file, PREVIEW_WIDTH), WallpaperKey.user(file), true, file))
            }
        }
        tiles.chunked(3).forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { col, tile ->
                    Box(Modifier.weight(1f)) {
                        WallpaperChoice(
                            tile = tile,
                            selected = tile.key == s.wallpaper,
                            modifier = if (rowIndex == 0 && col == 0 && !s.wallpaper.startsWith(WallpaperKey.BUILTIN_PREFIX)) Modifier.focusRequester(firstRow) else Modifier,
                            onChoose = { viewModel.setWallpaper(tile.key) },
                            onRemove = { tile.file?.let(viewModel::removeWallpaper) },
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Box(Modifier.padding(top = 4.dp)) {
            ValueRow(R.string.wallpaper_add, null, null) { adding = true }
        }
        Note(R.string.wallpaper_hint)
    }
    if (adding) {
        ImagePicker(
            onPicked = {
                viewModel.importWallpaper(it)
                adding = false
            },
            onDismiss = { adding = false },
        )
    }
}

private class WallpaperTile(val label: String, val preview: ImageBitmap?, val key: String, val removable: Boolean, val file: String? = null)

@Composable
private fun WallpaperChoice(tile: WallpaperTile, selected: Boolean, modifier: Modifier, onChoose: () -> Unit, onRemove: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(shape)
                .background(Color.White.copy(alpha = 0.06f))
                .onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = tile.label }
                .then(if (tile.removable) Modifier.longPressClickable(onClick = onChoose, onLongPress = onRemove) else Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onChoose)),
            contentAlignment = Alignment.Center,
        ) {
            tile.preview?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            if (tile.preview == null) BasicText(tile.label, style = interStyle(12.sp, 600, Palette.TextSecondary))
            // Focus and the current choice both read from across the room: a bright ring, and a check on the choice.
            // A zero-width border would still draw a hairline, so an unmarked tile gets none at all.
            if (focused || selected) {
                Box(Modifier.fillMaxSize().border(if (focused) 3.dp else 2.dp, if (focused) Color.White else Palette.ToggleOn, shape))
            }
            if (selected) {
                Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp).background(Palette.ToggleOn, CircleShape), contentAlignment = Alignment.Center) {
                    TintedIcon(R.drawable.ic_check, 12.dp, Color.White)
                }
            }
        }
        BasicText(tile.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = interStyle(11.sp, if (focused) 700 else 500, if (focused) Palette.TextPrimary else Palette.TextSecondary), modifier = Modifier.padding(top = 5.dp))
    }
}

private const val PREVIEW_WIDTH = 300

@Composable
private fun LockedAppsPage(viewModel: LauncherViewModel, firstRow: FocusRequester) {
    val apps by viewModel.appsForSettings.collectAsStateWithLifecycle()
    val s by viewModel.settings.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { runCatching { firstRow.requestFocus() } }
    PanelList {
        Note(if (s.parental.hasPin) R.string.note_locked_apps else R.string.note_locked_needs_pin)
        apps.forEachIndexed { index, app ->
            ToggleRow(
                label = app.label,
                subtitle = null,
                checked = app.isLocked,
                modifier = if (index == 0) Modifier.focusRequester(firstRow) else Modifier,
            ) { viewModel.setAppLocked(app.packageName, !app.isLocked) }
        }
    }
}

@Composable
private fun CleanExclusionsPage(viewModel: LauncherViewModel, firstRow: FocusRequester) {
    val apps by viewModel.appsForSettings.collectAsStateWithLifecycle()
    val s by viewModel.settings.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { runCatching { firstRow.requestFocus() } }
    PanelList {
        Note(R.string.note_exclusions)
        apps.forEachIndexed { index, app ->
            ToggleRow(
                label = app.label,
                subtitle = null,
                checked = app.packageName in s.memoryCleanExclusions,
                modifier = if (index == 0) Modifier.focusRequester(firstRow) else Modifier,
            ) { viewModel.toggleCleanExclusion(app.packageName) }
        }
    }
}

@Composable
private fun CitySearchPage(viewModel: LauncherViewModel, firstRow: FocusRequester, onDone: () -> Unit) {
    val search by viewModel.citySearch.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { runCatching { firstRow.requestFocus() } }
    PanelList {
        TextInput(value = query, placeholder = stringResource(R.string.city_placeholder), modifier = Modifier.focusRequester(firstRow)) {
            query = it
            viewModel.searchCities(it)
        }
        when (val state = search) {
            CitySearchState.Idle -> Note(R.string.note_city_search)
            CitySearchState.Searching -> Note(R.string.searching)
            CitySearchState.Failed -> Note(R.string.search_failed)
            is CitySearchState.Results -> {
                if (state.cities.isEmpty()) Note(R.string.no_results)
                state.cities.forEach { city ->
                    ValueRow(city.name, null, null) {
                        viewModel.setWeatherLocation(city)
                        onDone()
                    }
                }
            }
        }
    }
}

@Composable
private fun InputEditorPage(viewModel: LauncherViewModel, inputId: String, firstRow: FocusRequester) {
    val inputs by viewModel.inputs.collectAsStateWithLifecycle()
    val input = inputs.firstOrNull { it.id == inputId } ?: return
    var name by remember(inputId) { mutableStateOf(input.name) }
    LaunchedEffect(inputId) { runCatching { firstRow.requestFocus() } }
    Column {
        BasicText(
            when (input.detection) {
                InputDetection.CEC -> stringResource(R.string.detected_as, input.portLabel, input.detectedName.orEmpty())
                InputDetection.TV_LABEL -> stringResource(R.string.tv_named_as, input.portLabel, input.detectedName.orEmpty())
                InputDetection.NONE -> stringResource(R.string.not_detected_long, input.portLabel)
            },
            style = interStyle(12.sp, 500, Palette.TextSecondary),
        )
        Spacer(Modifier.height(12.dp))
        FieldLabel(R.string.field_name)
        TextInput(value = name, placeholder = input.portLabel, modifier = Modifier.focusRequester(firstRow)) {
            name = it
            viewModel.setInputOverride(input, it, input.logo)
        }
        Spacer(Modifier.height(12.dp))
        FieldLabel(R.string.field_logo)
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
            modifier = Modifier.heightIn(max = 200.dp),
        ) {
            items(DeviceLogo.entries) { logo ->
                LogoOption(logo, selected = logo == input.logo) {
                    val customName = name.isNotBlank() && name != (input.detectedName ?: input.portLabel)
                    viewModel.setInputOverride(input, if (customName) name else null, logo)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        ValueRow(R.string.reset_detected, null, null) {
            viewModel.resetInputOverride(input)
            name = input.detectedName ?: input.portLabel
        }
    }
}

@Composable
private fun InputRow(input: HdmiInput, modifier: Modifier, onClick: () -> Unit) {
    FocusRow(onClick = onClick, modifier = modifier) {
        Box(Modifier.size(28.dp).background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            TintedIcon(input.logo.icon, 15.dp, Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            BasicText(input.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = interStyle(14.sp, 600))
            BasicText(input.portLabel, style = interStyle(10.5.sp, 500, Color.White.copy(alpha = 0.6f)))
        }
        val (label, fill, text) = when {
            input.isOverridden -> Triple(R.string.badge_manual, Color.White.copy(alpha = 0.08f), Palette.TextPrimary)
            input.detection == InputDetection.CEC -> Triple(R.string.badge_auto, Color(0x331FB954), Color(0xFF8FE3AD))
            input.detection == InputDetection.TV_LABEL -> Triple(R.string.badge_tv_name, Color.White.copy(alpha = 0.08f), Palette.TextPrimary)
            else -> Triple(R.string.badge_not_detected, Color.White.copy(alpha = 0.08f), Palette.TextSecondary)
        }
        ValuePill(stringResource(label), fill, text)
        Spacer(Modifier.width(10.dp))
        TintedIcon(R.drawable.ic_chevron_right, 11.dp, Color.White.copy(alpha = 0.6f))
    }
}

@Composable
private fun LogoOption(logo: DeviceLogo, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val label = stringResource(logo.label)
    val content = if (selected) Palette.OnAmber else Palette.TextPrimary
    Column(
        Modifier
            .height(56.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    selected -> Palette.Amber
                    focused -> Color.White.copy(alpha = 0.22f)
                    else -> Color.White.copy(alpha = 0.08f)
                },
            )
            .border(2.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            .semantics {
                contentDescription = label
                role = Role.RadioButton
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TintedIcon(logo.icon, 18.dp, content)
        Spacer(Modifier.height(4.dp))
        BasicText(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = interStyle(10.5.sp, 600, content))
    }
}

@Composable
private fun PanelList(content: @Composable () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 20.dp)) {
        item { Column { content() } }
    }
}

@Composable
private fun FocusRow(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .then(if (focused) Modifier.glass(RoundedCornerShape(16.dp), GlassStyle.Lit) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
        Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(1.dp).background(Divider))
    }
}

@Composable
private fun RowLabel(label: String, subtitle: String?, modifier: Modifier) {
    Column(modifier) {
        BasicText(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = interStyle(14.sp, 600))
        if (subtitle != null) {
            BasicText(subtitle, maxLines = 2, style = interStyle(10.5.sp, 500, Color.White.copy(alpha = 0.6f)))
        }
    }
}

@Composable
private fun ToggleRow(
    @StringRes label: Int,
    @StringRes subtitle: Int?,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onToggle: (Boolean) -> Unit,
) = ToggleRow(stringResource(label), subtitle?.let { stringResource(it) }, checked, modifier, onToggle)

@Composable
private fun ToggleRow(label: String, subtitle: String?, checked: Boolean, modifier: Modifier = Modifier, onToggle: (Boolean) -> Unit) {
    val on = stringResource(R.string.switch_on)
    val off = stringResource(R.string.switch_off)
    FocusRow(
        onClick = { onToggle(!checked) },
        modifier = modifier.semantics {
            role = Role.Switch
            stateDescription = if (checked) on else off
        },
    ) {
        RowLabel(label, subtitle, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .width(40.dp)
                .height(22.dp)
                .background(if (checked) Palette.ToggleOn else Color.White.copy(alpha = 0.2f), RoundedCornerShape(11.dp))
                .padding(2.dp),
        ) {
            Box(Modifier.size(18.dp).align(if (checked) Alignment.CenterEnd else Alignment.CenterStart).background(Color.White, CircleShape))
        }
    }
}

@Composable
private fun ValueRow(
    @StringRes label: Int,
    @StringRes subtitle: Int?,
    value: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) = ValueRow(stringResource(label), subtitle?.let { stringResource(it) }, value, modifier, onClick)

@Composable
private fun ValueRow(label: String, subtitle: String?, value: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusRow(onClick = onClick, modifier = modifier) {
        RowLabel(label, subtitle, Modifier.weight(1f))
        if (value != null) {
            Spacer(Modifier.width(12.dp))
            ValuePill(value)
        }
        Spacer(Modifier.width(10.dp))
        TintedIcon(R.drawable.ic_chevron_right, 11.dp, Color.White.copy(alpha = 0.6f))
    }
}

/** One option of a single-choice list, marked with a filled ring when selected. */
@Composable
private fun ChoiceRow(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusRow(
        onClick = onClick,
        modifier = modifier.semantics {
            role = Role.RadioButton
            this.selected = selected
        },
    ) {
        RowLabel(label, null, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier.size(20.dp).border(2.dp, if (selected) Palette.ToggleOn else Color.White.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(10.dp).background(Palette.ToggleOn, CircleShape))
        }
    }
}

/** Read-only row: focusable so it can be scrolled to and read, but without a chevron since it leads nowhere. */
@Composable
private fun InfoRow(@StringRes label: Int, @StringRes subtitle: Int?, value: String, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .then(if (focused) Modifier.glass(RoundedCornerShape(16.dp), GlassStyle.Lit) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RowLabel(stringResource(label), subtitle?.let { stringResource(it) }, Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            ValuePill(value)
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(1.dp).background(Divider))
    }
}

@Composable
private fun ValuePill(text: String, fill: Color = Color.White.copy(alpha = 0.08f), color: Color = Palette.TextPrimary) {
    Box(Modifier.background(fill, RoundedCornerShape(7.dp)).padding(horizontal = 9.dp, vertical = 3.dp)) {
        BasicText(text, maxLines = 1, style = interStyle(12.sp, 600, color))
    }
}

@Composable
private fun FieldLabel(@StringRes res: Int) {
    BasicText(stringResource(res), style = interStyle(11.sp, 600, Palette.TextMuted), modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun Note(@StringRes res: Int) {
    BasicText(
        stringResource(res),
        style = interStyle(11.sp, 400, Palette.TextMuted).copy(lineHeight = 16.sp),
        modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 2.dp, end = 12.dp),
    )
}

@Composable
private fun ProfileNamePage(viewModel: LauncherViewModel, firstRow: FocusRequester) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf(s.profileName) }
    LaunchedEffect(Unit) { runCatching { firstRow.requestFocus() } }
    PanelList {
        TextInput(value = name, placeholder = stringResource(R.string.profile_name_placeholder), modifier = Modifier.focusRequester(firstRow)) {
            name = it
            viewModel.update { setProfileName(it) }
        }
        Note(R.string.note_profile_name)
    }
}

@Composable
private fun DefaultHomeRow(viewModel: LauncherViewModel, modifier: Modifier) {
    var isDefault by remember { mutableStateOf(viewModel.isDefaultHome()) }
    var requestFailed by remember { mutableStateOf(false) }
    val roleRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = viewModel.isDefaultHome()
    }
    ValueRow(
        R.string.setting_default_home,
        if (isDefault) R.string.setting_default_home_sub_set else R.string.setting_default_home_sub,
        stringResource(if (isDefault) R.string.default_home_self else R.string.default_home_other),
        modifier = modifier,
    ) {
        val intent = viewModel.defaultHomeRequestIntent()
        requestFailed = when {
            isDefault -> !viewModel.openSystemSettings(Settings.ACTION_HOME_SETTINGS)
            intent != null -> runCatching { roleRequest.launch(intent) }.isFailure
            else -> !viewModel.openSystemSettings(Settings.ACTION_HOME_SETTINGS)
        }
    }
    if (requestFailed) Note(R.string.note_home_settings_missing)
}

/** In-app disclosure and entry point for notification access, which Play installs can't get any other way. */
@Composable
private fun NotificationAccessRow(viewModel: LauncherViewModel) {
    var granted by remember { mutableStateOf(viewModel.hasNotificationAccess()) }
    LifecycleResumeEffect(Unit) {
        granted = viewModel.hasNotificationAccess()
        onPauseOrDispose { }
    }
    ValueRow(
        R.string.setting_notification_access,
        R.string.setting_notification_access_sub,
        stringResource(if (granted) R.string.switch_on else R.string.switch_off),
    ) {
        viewModel.openSystemSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }
}

@Composable
private fun HomeKeyRow(viewModel: LauncherViewModel) {
    var enabled by remember { mutableStateOf(viewModel.isHomeKeyServiceEnabled()) }
    var showDisclosure by remember { mutableStateOf(false) }
    var showRestricted by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        enabled = viewModel.isHomeKeyServiceEnabled()
        onPauseOrDispose { }
    }
    ValueRow(
        R.string.setting_home_key,
        R.string.setting_home_key_sub,
        stringResource(if (enabled) R.string.switch_on else R.string.switch_off),
    ) {
        // Turning it on needs explicit consent first (Play's accessibility policy); turning it off doesn't.
        when {
            enabled -> viewModel.openSystemSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            viewModel.isAccessibilityRestricted() -> showRestricted = true
            else -> showDisclosure = true
        }
    }
    if (showRestricted) {
        // Shown before the consent so the user knows why the switch may not stay on; they can still try.
        ConsentDialog(
            title = R.string.home_key_restricted_title,
            body = R.string.home_key_restricted_body,
            accept = R.string.home_key_restricted_try,
            onAccept = {
                showRestricted = false
                showDisclosure = true
            },
            onDismiss = { showRestricted = false },
        )
    }
    if (showDisclosure) {
        ConsentDialog(
            title = R.string.home_key_disclosure_title,
            body = R.string.home_key_disclosure_body,
            accept = R.string.home_key_disclosure_accept,
            onAccept = {
                showDisclosure = false
                viewModel.openSystemSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            },
            onDismiss = { showDisclosure = false },
        )
    }
}

@Composable
private fun ConsentDialog(@StringRes title: Int, @StringRes body: Int, @StringRes accept: Int, onAccept: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val cancelFocus = remember { FocusRequester() }
        // Focus starts on Cancel so a stray OK press never grants the permission.
        LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }
        Column(
            Modifier
                .width(440.dp)
                .glass(RoundedCornerShape(28.dp), GlassStyle.Dense)
                .padding(22.dp),
        ) {
            BasicText(stringResource(title), style = interStyle(17.sp, 700))
            Spacer(Modifier.height(10.dp))
            BasicText(stringResource(body), style = interStyle(12.5.sp, 500, Palette.TextSecondary).copy(lineHeight = 18.sp))
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogButton(stringResource(R.string.cancel), Modifier.focusRequester(cancelFocus), onDismiss)
                DialogButton(stringResource(accept), Modifier, onAccept)
            }
        }
    }
}

@Composable
private fun DialogButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.08f))
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        BasicText(label, style = interStyle(13.sp, 600, if (focused) Palette.Background else Palette.TextPrimary))
    }
}

/** Asks for location only when the user turns this on: Android gives the Wi-Fi name to nothing else. */
@Composable
private fun WifiNameRow(viewModel: LauncherViewModel) {
    var granted by remember { mutableStateOf(viewModel.hasLocationPermission()) }
    val activity = LocalContext.current as? Activity
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        viewModel.onLocationPermissionChanged()
        granted = viewModel.hasLocationPermission()
        // Android won't show the dialog again after "Don't ask again", and approximate location can't read the
        // network name; in both cases only the app's permission screen can change it.
        val coarseOnly = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true && !granted
        val blocked = activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)
        if (!granted && (coarseOnly || blocked)) viewModel.openOwnAppDetails()
    }
    LifecycleResumeEffect(Unit) {
        viewModel.onLocationPermissionChanged()
        granted = viewModel.hasLocationPermission()
        onPauseOrDispose { }
    }
    ValueRow(
        R.string.setting_wifi_name,
        R.string.setting_wifi_name_sub,
        stringResource(if (granted) R.string.switch_on else R.string.switch_off),
    ) {
        if (granted) {
            viewModel.openOwnAppDetails()
        } else {
            request.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
}
