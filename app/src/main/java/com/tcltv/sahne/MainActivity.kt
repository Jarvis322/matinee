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

    /** A remote key assigned to "Matinee settings" opens Matinee with this; the settings PIN still applies. */
    private fun handleRequest(intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)) {
            intent.removeExtra(EXTRA_OPEN_SETTINGS)
            viewModel.requestSettings()
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
    }
}
