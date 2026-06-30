package com.pzdd.note.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pzdd.note.ui.page.FavoritesPage
import com.pzdd.note.ui.page.HomePage
import com.pzdd.note.ui.page.SettingsPage
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun AppRoot() {
    val noteVm: NoteViewModel = viewModel()
    val settingsVm: SettingsViewModel = viewModel()
    val settings by settingsVm.settings.collectAsStateSafe()

    var selected by rememberSaveable { mutableIntStateOf(0) }

    val floatingBottomBar = settings.floatingBottomBar
    val liquidGlass = settings.liquidGlassBottomBar

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (floatingBottomBar) {
                FloatingBottomBar(
                    selected = selected,
                    onSelect = { selected = it },
                    liquidGlass = liquidGlass,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 12.dp)
                )
            } else {
                StandardBottomBar(
                    selected = selected,
                    onSelect = { selected = it },
                    modifier = Modifier
                )
            }
        }
    ) { paddingValues: PaddingValues ->
        AppContent(
            selected = selected,
            noteVm = noteVm,
            settingsVm = settingsVm,
            paddingValues = paddingValues
        )
    }
}

@Composable
private fun AppContent(
    selected: Int,
    noteVm: NoteViewModel,
    settingsVm: SettingsViewModel,
    paddingValues: PaddingValues
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (selected) {
            0 -> HomePage(vm = noteVm, paddingValues = paddingValues)
            1 -> FavoritesPage(vm = noteVm, paddingValues = paddingValues)
            2 -> SettingsPage(vm = settingsVm, paddingValues = paddingValues)
        }
    }
}

@Composable
private fun StandardBottomBar(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(modifier = modifier.fillMaxWidth()) {
        BottomItems(selected, onSelect)
    }
}

@Composable
private fun FloatingBottomBar(
    selected: Int,
    onSelect: (Int) -> Unit,
    liquidGlass: Boolean,
    modifier: Modifier = Modifier
) {
    if (liquidGlass) {
        // 液态玻璃 + 果冻弹性底栏
        LiquidGlassBottomBar(
            selected = selected,
            onSelect = onSelect,
            modifier = modifier,
        )
    } else {
        // 普通浮动底栏
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
            tonalElevation = 3.dp
        ) {
            NavigationBar(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                BottomItems(selected, onSelect)
            }
        }
    }
}

@Composable
private fun RowScope.BottomItems(selected: Int, onSelect: (Int) -> Unit) {
    NavigationBarItem(
        selected = selected == 0,
        onClick = { onSelect(0) },
        icon = { Icon(Icons.Filled.Home, contentDescription = "首页") },
        label = { Text("首页") }
    )
    NavigationBarItem(
        selected = selected == 1,
        onClick = { onSelect(1) },
        icon = { Icon(Icons.Filled.Star, contentDescription = "收藏") },
        label = { Text("收藏") }
    )
    NavigationBarItem(
        selected = selected == 2,
        onClick = { onSelect(2) },
        icon = { Icon(Icons.Filled.Settings, contentDescription = "设置") },
        label = { Text("设置") }
    )
}