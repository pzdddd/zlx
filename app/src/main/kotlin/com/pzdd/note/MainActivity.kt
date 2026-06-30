package com.pzdd.note

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pzdd.note.ui.AppRoot
import com.pzdd.note.ui.SettingsViewModel
import com.pzdd.note.ui.collectAsStateSafe
import com.pzdd.note.ui.theme.ComposeEmptyActivityTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsVm: SettingsViewModel = viewModel()
            val settings by settingsVm.settings.collectAsStateSafe()
            ComposeEmptyActivityTheme(
                themeMode = settings.themeMode,
                themeColorKey = settings.themeColorKey
            ) {
                AppRoot()
            }
        }
    }
}