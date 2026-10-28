package com.tcltv.sahne

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tcltv.sahne.ui.DesignCanvas
import com.tcltv.sahne.ui.LocalGlassEffects
import com.tcltv.sahne.ui.LocalWallpaper
import com.tcltv.sahne.ui.LocalWallpaperAnimated
import com.tcltv.sahne.ui.LocalReduceMotion
import com.tcltv.sahne.ui.PinGate
import com.tcltv.sahne.ui.home.HomeScreen
import com.tcltv.sahne.ui.settings.SettingsScreen

@Composable
fun SahneApp(viewModel: LauncherViewModel, resumeTick: Int) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    // Saveable: changing the language recreates the activity, and the user should land back in settings.
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var editRequest by remember { mutableIntStateOf(0) }

    fun openSettings() = viewModel.gate.requireForAdmin(R.string.pin_reason_admin) { showSettings = true }

    // As the Home app, Back has nowhere to go; opened from the app list, Back returns to the TV's own home. With the
    // Home button service on, the TV's own home only sends the user straight back here, so Back stays put as well.
    val isHome = remember(resumeTick) { viewModel.isHomeKeyTarget() || viewModel.isHomeKeyServiceRunning() }
    BackHandler(enabled = !showSettings && isHome) { }

    val wallpaper by viewModel.wallpaper.collectAsStateWithLifecycle()
    CompositionLocalProvider(
        LocalReduceMotion provides settings.reduceMotion,
        LocalGlassEffects provides settings.glassEffects,
        LocalWallpaper provides wallpaper,
        LocalWallpaperAnimated provides settings.wallpaperAnimated,
    ) {
        DesignCanvas {
            Box(
                Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { event ->
                        if (!showSettings && event.type == KeyEventType.KeyUp && event.key == Key.Menu) {
                            openSettings()
                            true
                        } else {
                            false
                        }
                    },
            ) {
                if (showSettings) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onClose = { showSettings = false },
                        onEditApps = {
                            showSettings = false
                            editRequest++
                        },
                    )
                } else {
                    HomeScreen(
                        viewModel = viewModel,
                        resumeTick = resumeTick,
                        editRequest = editRequest,
                        onOpenSettings = ::openSettings,
                    )
                }
                PinGate(viewModel.gate, settings.parental)
            }
        }
    }
}
