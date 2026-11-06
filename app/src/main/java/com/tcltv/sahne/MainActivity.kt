package com.tcltv.sahne

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.mutableIntStateOf
import com.tcltv.sahne.data.AppLanguage

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()
    private val resumeTick = mutableIntStateOf(0)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SahneApp(viewModel = viewModel, resumeTick = resumeTick.intValue)
        }
        if (savedInstanceState == null) handleRequest(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleRequest(intent)
    }

    /** Remote keys open Matinee with these: "Matinee settings" (the settings PIN still applies) and the app switcher. */
    private fun handleRequest(intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)) {
            intent.removeExtra(EXTRA_OPEN_SETTINGS)
            viewModel.requestSettings()
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_SWITCHER, false)) {
            intent.removeExtra(EXTRA_OPEN_SWITCHER)
            viewModel.openSwitcher()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onHomeResumed()
        resumeTick.intValue++
    }

    override fun onPause() {
        viewModel.gate.endAdminSession()
        viewModel.onHomePaused()
        super.onPause()
    }

    companion object {
        const val EXTRA_OPEN_SETTINGS = "com.tcltv.sahne.extra.OPEN_SETTINGS"
        const val EXTRA_OPEN_SWITCHER = "com.tcltv.sahne.extra.OPEN_SWITCHER"
    }
}
