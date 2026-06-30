package com.pzdd.note.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pzdd.note.ui.page.FavoritesPage
import com.pzdd.note.ui.page.HomePage
import com.pzdd.note.ui.page.SettingsPage

@Composable
fun AppRoot() {
    val settingsVm: SettingsViewModel = viewModel()
    val settings by settingsVm.settings.collectAsStateSafe()
    AppContent(
        settingsVm = settingsVm,
        floatingBottomBar = settings.floatingBottomBar,
        liquidGlass = settings.liquidGlassBottomBar
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppContent(
    settingsVm: SettingsViewModel,
    floatingBottomBar: Boolean,
    liquidGlass: Boolean
) {
    val vm: NoteViewModel = viewModel()
    var selected by rememberSaveable { mutableStateOf(0) }

    val titles = listOf("首页", "收藏", "设置")
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(titles[selected]) },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (selected) {
                0 -> HomePage(vm = vm, paddingValues = paddingValues)
                1 -> FavoritesPage(vm = vm, paddingValues = paddingValues)
                2 -> SettingsPage(paddingValues = paddingValues, settingsVm = settingsVm)
            }

            if (floatingBottomBar) {
                FloatingBottomBar(
                    selected = selected,
                    onSelect = { selected = it },
                    liquidGlass = liquidGlass,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 16.dp)
                )
            } else {
                StandardBottomBar(
                    selected = selected,
                    onSelect = { selected = it },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = if (liquidGlass) MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)
        else MaterialTheme.colorScheme.surface,
        shadowElevation = if (liquidGlass) 10.dp else 4.dp,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 8.dp, vertical = 4.dp)
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