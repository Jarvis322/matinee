package com.tcltv.sahne

import android.content.Context
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
}
