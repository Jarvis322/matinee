package com.tcltv.sahne.ui.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tcltv.sahne.LauncherViewModel
import com.tcltv.sahne.Notice
import com.tcltv.sahne.SpeedState
import com.tcltv.sahne.R
import com.tcltv.sahne.WeatherUiState
import android.provider.Settings
import com.tcltv.sahne.system.Connection
import com.tcltv.sahne.system.ReceiverState
import com.tcltv.sahne.data.TemperatureUnit
import com.tcltv.sahne.ui.GlassStyle
import com.tcltv.sahne.ui.LocalReduceMotion
import com.tcltv.sahne.ui.Palette
import com.tcltv.sahne.ui.TintedIcon
import com.tcltv.sahne.ui.glass
import com.tcltv.sahne.ui.interStyle
import com.tcltv.sahne.update.SelfUpdate
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun TopBar(viewModel: LauncherViewModel, onOpenSettings: () -> Unit, modifier: Modifier) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var notificationsOpen by remember { mutableStateOf(false) }
    var profileOpen by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
            if (settings.showClock || settings.showWeather) {
                ClockWeatherPill(viewModel, onOpenSettings)
            }
            Spacer(Modifier.weight(1f))
            if (settings.showNotifications) {
                // Each popup lives in its button's box so it opens right under that circle.
                Box {
                    BellButton(viewModel, open = notificationsOpen) {
                        notificationsOpen = true
                        viewModel.markNoticesSeen()
                    }
                    if (notificationsOpen) {
                        MenuPopup(onDismiss = { notificationsOpen = false }) {
                            NotificationPanel(viewModel, onClose = { notificationsOpen = false })
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
            WifiButton(viewModel)
            Spacer(Modifier.width(8.dp))
            Box {
                ProfileButton(viewModel, open = profileOpen) { profileOpen = true }
                if (profileOpen) {
                    MenuPopup(onDismiss = { profileOpen = false }) {
                        ProfileMenu(
                            viewModel = viewModel,
                            onOpenSettings = {
                                profileOpen = false
                                onOpenSettings()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuPopup(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val offsetY = with(LocalDensity.current) { 42.dp.roundToPx() }
    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(0, offsetY),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) { content() }
}

@Composable
private fun ClockWeatherPill(viewModel: LauncherViewModel, onOpenSettings: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val weather by viewModel.weather.collectAsStateWithLifecycle()
    val now = rememberNow(settings.showSeconds)
    var focused by remember { mutableStateOf(false) }
    val locale = Locale.getDefault()
    // The locale's own order for weekday, day and month ("Mi. 21. Okt.", "10月21日(水)") instead of a fixed English order.
    val datePattern = remember(locale) { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale) }
    val timePattern = when {
        settings.use24Hour && settings.showSeconds -> "HH:mm:ss"
        settings.use24Hour -> "HH:mm"
        settings.showSeconds -> "h:mm:ss a"
        else -> "h:mm a"
    }
    val openSettings = stringResource(R.string.a11y_open_launcher_settings)

    Row(
        Modifier
            .height(32.dp)
            .glass(CircleShape, if (focused) GlassStyle.Lit else GlassStyle.Pill)
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = openSettings }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onOpenSettings)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (settings.showClock) {
            BasicText(now.format(DateTimeFormatter.ofPattern(timePattern, locale)), style = interStyle(19.sp, 600, letterSpacing = (-0.3).sp))
            Spacer(Modifier.width(9.dp))
            BasicText(now.format(datePattern), style = interStyle(11.5.sp, 500, Palette.TextSecondary))
        }
        if (settings.showClock && settings.showWeather) {
            Spacer(Modifier.width(10.dp))
            Box(Modifier.width(1.dp).height(15.dp).background(Color.White.copy(alpha = 0.16f)))
            Spacer(Modifier.width(10.dp))
        }
        if (settings.showWeather) {
            when (val state = weather) {
                is WeatherUiState.Ready -> {
                    val w = state.weather
                    TintedIcon(if (w.isDay) w.condition.dayIcon else w.condition.nightIcon, 14.dp, Palette.TextPrimary)
                    Spacer(Modifier.width(6.dp))
                    val unit = if (w.unit == TemperatureUnit.CELSIUS) "°" else "°F"
                    BasicText("${w.temperature}$unit", style = interStyle(11.5.sp, 600))
                }
                WeatherUiState.Loading -> BasicText("…", style = interStyle(11.5.sp, 600, Palette.TextSecondary))
                WeatherUiState.Unavailable -> BasicText(stringResource(R.string.weather_unavailable), style = interStyle(11.5.sp, 500, Palette.TextSecondary))
            }
            BasicText(" · ${settings.weatherLocation.name.substringBefore(',')}", style = interStyle(11.5.sp, 500, Palette.TextSecondary))
        }
    }
}

/**
 * The current time, kept fresh by the system's own minute tick and by the screen turning on. A plain delay isn't
 * enough: its clock stops while the TV sleeps in standby, so after waking the time it showed was the time it slept.
 */
@Composable
private fun rememberNow(withSeconds: Boolean): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    val context = LocalContext.current
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                now = LocalDateTime.now()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    LifecycleResumeEffect(Unit) {
        now = LocalDateTime.now()
        onPauseOrDispose { }
    }
    if (withSeconds) {
        LaunchedEffect(Unit) {
            while (true) {
                now = LocalDateTime.now()
                delay(1_000 - System.currentTimeMillis() % 1_000)
            }
        }
    }
    return now
}

private val ProfileColors = listOf(
    Color(0xFFE8A21A),
    Color(0xFFE5674A),
    Color(0xFF1D9E75),
    Color(0xFF2F6FE0),
    Color(0xFF7F5AD8),
    Color(0xFFD4537E),
)

internal val profileColorCount: Int get() = ProfileColors.size

private fun initialsOf(name: String): String =
    name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase(Locale.getDefault())

@Composable
internal fun ProfileAvatar(viewModel: LauncherViewModel, size: Dp) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val photo by viewModel.avatar.collectAsStateWithLifecycle()
    val fill = ProfileColors[settings.profileColor.mod(ProfileColors.size)]
    val content = if (fill.luminance() > 0.45f) Color(0xFF2A1A02) else Color.White
    val image = photo
    Box(Modifier.size(size).clip(CircleShape).background(fill), contentAlignment = Alignment.Center) {
        val initials = initialsOf(settings.profileName)
        when {
            image != null -> Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
            initials.isNotEmpty() -> BasicText(initials, style = interStyle((size.value * 0.4f).sp, 700, content))
            else -> TintedIcon(R.drawable.ic_user, size * 0.5f, content)
        }
    }
}

@Composable
private fun ProfileButton(viewModel: LauncherViewModel, open: Boolean, onClick: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var focused by remember { mutableStateOf(false) }
    val highlighted = focused || open
    val hour = rememberNow(withSeconds = false).hour
    val name = settings.profileName.trim()
    val greeting = if (name.isEmpty()) stringResource(greetingRes(hour)) else stringResource(greetingWithNameRes(hour), name)
    val description = stringResource(R.string.a11y_profile_menu)
    Row(
        Modifier
            .height(32.dp)
            .then(if (highlighted || settings.showGreeting) Modifier.glass(CircleShape, if (highlighted) GlassStyle.Lit else GlassStyle.Pill) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = description }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(start = if (settings.showGreeting) 13.dp else 3.dp, end = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (settings.showGreeting) {
            BasicText(greeting, maxLines = 1, overflow = TextOverflow.Ellipsis, style = interStyle(12.sp, 500))
            Spacer(Modifier.width(9.dp))
        }
        ProfileAvatar(viewModel, 26.dp)
    }
}

private fun greetingRes(hour: Int): Int = when (hour) {
    in 5..11 -> R.string.greeting_morning
    in 12..17 -> R.string.greeting_day
    in 18..21 -> R.string.greeting_evening
    else -> R.string.greeting_night
}

private fun greetingWithNameRes(hour: Int): Int = when (hour) {
    in 5..11 -> R.string.greeting_morning_name
    in 12..17 -> R.string.greeting_day_name
    in 18..21 -> R.string.greeting_evening_name
    else -> R.string.greeting_night_name
}

@Composable
private fun ProfileMenu(viewModel: LauncherViewModel, onOpenSettings: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val airPlay by viewModel.airPlay.collectAsStateWithLifecycle()
    val cast by viewModel.googleCast.collectAsStateWithLifecycle()
    val memory by viewModel.memory.collectAsStateWithLifecycle()
    val cleaning by viewModel.cleaning.collectAsStateWithLifecycle()
    val lastClean by viewModel.lastClean.collectAsStateWithLifecycle()
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    // Receiver rows only report status, so focus starts on the first row that does something.
    val rows = buildList {
        if (settings.showMemory) add("memory")
        add("settings")
    }
    fun focusFor(key: String) = if (key == rows.first()) Modifier.focusRequester(firstFocus) else Modifier

    Column(
        Modifier
            .width(280.dp)
            .glass(RoundedCornerShape(22.dp), GlassStyle.Dense)
            .padding(12.dp),
    ) {
        Row(Modifier.padding(start = 6.dp, top = 2.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(viewModel, 36.dp)
            Spacer(Modifier.width(10.dp))
            BasicText(
                settings.profileName.ifBlank { stringResource(R.string.profile_default_name) },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = interStyle(14.sp, 700),
            )
        }
        MenuDivider()
        if (settings.showAirPlay) {
            ReceiverRow(R.drawable.ic_device_tv, stringResource(R.string.airplay), airPlay.state, airPlayLabels())
        }
        if (settings.showChromecast) {
            ReceiverRow(R.drawable.ic_cast, stringResource(R.string.chromecast), cast.state, castLabels())
        }
        if (settings.showMemory) {
            val result = lastClean
            MenuRow(
                icon = R.drawable.ic_cpu,
                title = stringResource(R.string.memory_title, memory.usedPercent),
                modifier = focusFor("memory"),
                onClick = viewModel::cleanMemory,
                below = {
                    val over = memory.usedPercent >= settings.memoryThresholdPercent
                    Box(Modifier.padding(top = 5.dp).width(96.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.18f))) {
                        Box(Modifier.fillMaxWidth(memory.usedPercent / 100f).height(3.dp).background(if (over) Palette.Warning else Palette.Amber))
                    }
                    if (result != null && !cleaning) {
                        BasicText(
                            stringResource(R.string.memory_freed_mb, (result.freedBytes / (1024 * 1024)).toInt()),
                            style = interStyle(10.5.sp, 500, Palette.TextSecondary),
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                },
                trailing = {
                    Box(Modifier.background(Palette.Amber, RoundedCornerShape(11.dp)).padding(horizontal = 10.dp, vertical = 3.dp)) {
                        BasicText(
                            stringResource(if (cleaning) R.string.memory_cleaning else R.string.memory_clean),
                            style = interStyle(10.5.sp, 600, Palette.OnAmber),
                        )
                    }
                },
            )
        }
        MenuDivider()
        MenuRow(
            icon = R.drawable.ic_settings,
            title = stringResource(R.string.settings_title),
            modifier = focusFor("settings"),
            onClick = onOpenSettings,
            trailing = { TintedIcon(R.drawable.ic_chevron_right, 12.dp, Palette.TextSecondary) },
        )
    }
}

private data class ReceiverLabels(val notSupported: String, val off: String, val ready: String, val playing: String)

@Composable
private fun airPlayLabels() = ReceiverLabels(
    stringResource(R.string.receiver_not_supported),
    stringResource(R.string.receiver_off),
    stringResource(R.string.airplay_ready_long),
    stringResource(R.string.airplay_playing_long),
)

@Composable
private fun castLabels() = ReceiverLabels(
    stringResource(R.string.receiver_not_supported),
    stringResource(R.string.receiver_off),
    stringResource(R.string.cast_ready),
    stringResource(R.string.cast_playing),
)

@Composable
private fun ReceiverRow(icon: Int, title: String, state: ReceiverState, labels: ReceiverLabels) {
    val (label, dot) = when (state) {
        ReceiverState.PLAYING -> labels.playing to Palette.Amber
        ReceiverState.READY -> labels.ready to Palette.Online
        ReceiverState.UNAVAILABLE -> labels.off to Palette.TextMuted
        ReceiverState.NOT_SUPPORTED -> labels.notSupported to Palette.TextMuted
    }
    MenuRow(
        icon = icon,
        title = title,
        subtitle = label,
        modifier = Modifier,
        onClick = null,
        trailing = { Box(Modifier.size(7.dp).background(dot, CircleShape)) },
    )
}

@Composable
private fun MenuRow(
    icon: Int,
    title: String,
    modifier: Modifier,
    onClick: (() -> Unit)?,
    subtitle: String? = null,
    below: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(if (focused) Color.White.copy(alpha = 0.1f) else Color.Transparent)
            .border(1.dp, if (focused) Color.White.copy(alpha = 0.4f) else Color.Transparent, RoundedCornerShape(9.dp))
            .then(
                if (onClick != null) {
                    Modifier
                        .onFocusChanged { focused = it.isFocused }
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp).background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            TintedIcon(icon, 15.dp, Color.White)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            BasicText(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = interStyle(12.5.sp, 600))
            if (subtitle != null) BasicText(subtitle, maxLines = 1, style = interStyle(11.sp, 500, Palette.TextSecondary))
            below()
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

@Composable
private fun MenuDivider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp).height(1.dp).background(Color.White.copy(alpha = 0.08f)))
}

@Composable
private fun BellButton(viewModel: LauncherViewModel, open: Boolean, onClick: () -> Unit) {
    val unread by viewModel.unreadNotices.collectAsStateWithLifecycle()
    var focused by remember { mutableStateOf(false) }
    val highlighted = focused || open
    val description = pluralStringResource(R.plurals.a11y_notifications, unread, unread)
    Box {
        Box(
            Modifier
                .size(32.dp)
                .glass(CircleShape, if (highlighted) GlassStyle.Bright else GlassStyle.Pill)
                .onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = description }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            TintedIcon(R.drawable.ic_bell, 15.dp, if (highlighted) Palette.Background else Palette.TextPrimary)
        }
        if (unread > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .heightIn(min = 15.dp)
                    .widthIn(min = 15.dp)
                    .background(Palette.Amber, CircleShape)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(if (unread > 9) "9+" else unread.toString(), style = interStyle(10.5.sp, 700, Palette.OnAmber))
            }
        }
    }
}

@Composable
private fun NotificationPanel(viewModel: LauncherViewModel, onClose: () -> Unit) {
    val notices by viewModel.notices.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val firstFocus = remember { FocusRequester() }
    val hasAccess = remember { viewModel.hasNotificationAccess() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    Column(
        Modifier
            .width(300.dp)
            .glass(RoundedCornerShape(22.dp), GlassStyle.Dense)
            .padding(14.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(stringResource(R.string.notifications_title), style = interStyle(14.sp, 700), modifier = Modifier.weight(1f))
            if (notices.isNotEmpty()) {
                SmallTextButton(stringResource(R.string.notifications_clear_all)) { viewModel.clearNotices() }
            }
        }
        if (notices.isEmpty()) {
            NoticeEmpty(firstFocus, onClose)
        }
        notices.take(MAX_VISIBLE).forEachIndexed { index, notice ->
            NoticeRow(
                notice = notice,
                modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
            ) {
                viewModel.openNotice(notice)
                onClose()
            }
        }
        if (settings.showPlayUpdates && !hasAccess) {
            BasicText(
                stringResource(R.string.notifications_access_needed),
                style = interStyle(10.5.sp, 400, Palette.TextMuted).copy(lineHeight = 15.sp),
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, end = 4.dp),
            )
        }
    }
}

private const val MAX_VISIBLE = 5

@Composable
private fun NoticeEmpty(focus: FocusRequester, onClose: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(if (focused) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .focusRequester(focus)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
            .padding(horizontal = 8.dp, vertical = 12.dp),
    ) {
        BasicText(stringResource(R.string.notifications_empty), style = interStyle(12.sp, 500, Palette.TextSecondary))
    }
}

@Composable
private fun NoticeRow(notice: Notice, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val (icon, tile) = when (notice) {
        is Notice.PlayUpdate -> R.drawable.ic_refresh to Color(0xFF1A73E8)
        is Notice.NewDevice -> notice.logo.icon to Color(0xFF5B2A8C)
        is Notice.MemoryHigh -> R.drawable.ic_cpu to Color(0xFF8A2A20)
        is Notice.AppUpdate -> R.drawable.ic_brand_github to Color(0xFF24292F)
    }
    val (title, subtitle) = when (notice) {
        is Notice.PlayUpdate -> notice.update.title to (notice.update.text ?: stringResource(R.string.notice_play_source))
        is Notice.NewDevice -> stringResource(R.string.notice_new_device, notice.deviceName) to
            stringResource(R.string.notice_new_device_sub, notice.portLabel)
        is Notice.MemoryHigh -> stringResource(R.string.notice_memory_high, notice.percent) to
            stringResource(R.string.notice_memory_high_sub, notice.threshold)
        is Notice.AppUpdate -> stringResource(R.string.notice_self_update, notice.update.version) to when (notice.update.stage) {
            SelfUpdate.Stage.AVAILABLE -> stringResource(R.string.notice_self_update_sub)
            SelfUpdate.Stage.DOWNLOADING -> stringResource(R.string.notice_self_update_downloading, notice.update.percent)
            SelfUpdate.Stage.CONFIRMING -> stringResource(R.string.notice_self_update_confirming)
            SelfUpdate.Stage.FAILED -> stringResource(R.string.notice_self_update_failed)
        }
    }
    val action = when (notice) {
        is Notice.PlayUpdate -> stringResource(R.string.notice_action_play)
        is Notice.NewDevice -> stringResource(R.string.notice_action_device)
        is Notice.MemoryHigh -> stringResource(R.string.notice_action_memory)
        is Notice.AppUpdate -> stringResource(
            if (notice.update.stage == SelfUpdate.Stage.FAILED) R.string.notice_action_retry else R.string.notice_action_install,
        )
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(if (focused) Color.White.copy(alpha = 0.1f) else Color.Transparent)
            .border(1.dp, if (focused) Color.White.copy(alpha = 0.4f) else Color.Transparent, RoundedCornerShape(9.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
    ) {
        Box(Modifier.size(28.dp).background(tile, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            TintedIcon(icon, 15.dp, Color.White)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            BasicText(title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = interStyle(12.5.sp, 600))
            BasicText(subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis, style = interStyle(11.sp, 500, Palette.TextSecondary))
            if (focused) {
                Spacer(Modifier.height(5.dp))
                Box(Modifier.background(Palette.Amber, RoundedCornerShape(10.dp)).padding(horizontal = 9.dp, vertical = 3.dp)) {
                    BasicText(action, style = interStyle(10.5.sp, 600, Palette.OnAmber))
                }
            }
        }
    }
}

@Composable
private fun SmallTextButton(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Color.White else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        BasicText(label, style = interStyle(11.sp, 500, if (focused) Palette.Background else Palette.TextSecondary))
    }
}

@Composable
private fun WifiButton(viewModel: LauncherViewModel) {
    val state by viewModel.connection.collectAsStateWithLifecycle()
    val speed by viewModel.speed.collectAsStateWithLifecycle()
    val connection = state
    var focused by remember { mutableStateOf(false) }
    val icon = when (connection) {
        Connection.Offline, Connection.NoInternet -> R.drawable.ic_wifi_off
        Connection.Ethernet -> R.drawable.ic_network
        is Connection.Wifi -> when (connection.bars) {
            0 -> R.drawable.ic_wifi_0
            1 -> R.drawable.ic_wifi_1
            2 -> R.drawable.ic_wifi_2
            else -> R.drawable.ic_wifi
        }
    }
    val description = stringResource(
        when (connection) {
            Connection.Offline -> R.string.network_offline
            Connection.NoInternet -> R.string.network_no_internet
            Connection.Ethernet -> R.string.network_ethernet
            is Connection.Wifi -> R.string.network_wifi
        },
    )
    // Offline is the one state worth a warning color; otherwise the icon shape carries the state.
    val problem = connection == Connection.Offline || connection == Connection.NoInternet
    Box {
        Box(
            Modifier
                .size(32.dp)
                .glass(CircleShape, if (focused) GlassStyle.Bright else GlassStyle.Pill)
                .onFocusChanged {
                    focused = it.isFocused
                    viewModel.onNetworkFocusChanged(it.isFocused)
                }
                .semantics { contentDescription = description }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    viewModel.openSystemSettings(Settings.ACTION_WIFI_SETTINGS)
                },
            contentAlignment = Alignment.Center,
        ) {
            TintedIcon(
                icon,
                15.dp,
                when {
                    focused -> Palette.Background
                    problem -> Palette.Warning
                    else -> Palette.TextPrimary
                },
            )
        }
        if (focused) {
            val offsetY = with(LocalDensity.current) { 42.dp.roundToPx() }
            // Not focusable: the details follow focus and must never steal it from the remote.
            Popup(alignment = Alignment.TopEnd, offset = IntOffset(0, offsetY), properties = PopupProperties(focusable = false)) {
                NetworkDetails(connection, speed)
            }
        }
    }
}

@Composable
private fun NetworkDetails(connection: Connection, speed: SpeedState) {
    val name = when (connection) {
        Connection.Offline -> stringResource(R.string.network_details_offline)
        Connection.NoInternet -> stringResource(R.string.network_details_no_internet)
        Connection.Ethernet -> stringResource(R.string.network_details_ethernet)
        is Connection.Wifi -> connection.ssid ?: stringResource(R.string.network_details_wifi)
    }
    val online = connection is Connection.Wifi || connection == Connection.Ethernet
    val speedText = when {
        !online -> null
        speed is SpeedState.Done -> stringResource(R.string.network_speed_value, speed.mbps.roundToInt())
        speed == SpeedState.Failed -> stringResource(R.string.network_speed_failed)
        else -> stringResource(R.string.network_speed_measuring)
    }
    Column(
        Modifier
            .widthIn(min = 180.dp, max = 260.dp)
            .glass(RoundedCornerShape(18.dp), GlassStyle.Dense)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        BasicText(name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = interStyle(12.5.sp, 700))
        if (speedText != null) {
            BasicText(speedText, style = interStyle(11.sp, 500, Palette.TextSecondary))
        }
        if (connection is Connection.Wifi && connection.linkMbps != null) {
            BasicText(stringResource(R.string.network_link_value, connection.linkMbps), style = interStyle(11.sp, 500, Palette.TextMuted))
        }
    }
}
