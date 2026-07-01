package com.pzdd.note.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pzdd.note.ui.page.FavoritesPage
import com.pzdd.note.ui.page.HomePage
import com.pzdd.note.ui.page.SettingsPage
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop

/**
 * 悬浮底栏可见性状态：由页面滚动方向驱动。
 * - 向上滚动（查看更多内容）时隐藏底栏
 * - 向下滚动时重新显示底栏
 */
enum class BottomBarVisibility { VISIBLE, HIDDEN }

@Composable
fun AppRoot() {
    val noteVm: NoteViewModel = viewModel()
    val settingsVm: SettingsViewModel = viewModel()
    val settings by settingsVm.settings.collectAsStateSafe()

    var selected by rememberSaveable { mutableIntStateOf(0) }

    val floatingBottomBar = settings.floatingBottomBar
    val liquidGlass = settings.liquidGlassBottomBar

    // 液态玻璃 backdrop：采样底栏下方的页面内容用于折射/模糊
    val backdrop = rememberLiquidGlassBackdrop()
    val liquidGlassEnabled = floatingBottomBar && liquidGlass

    // 悬浮底栏的显示/隐藏状态，由页面滚动驱动
    var bottomBarVisibility by remember { mutableStateOf(BottomBarVisibility.VISIBLE) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                // 仅标准底栏放入 Scaffold 槽以预留底部空间；
                // 悬浮底栏不占 Scaffold 布局，改为 Box 叠加在内容上方
                if (!floatingBottomBar) {
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
                paddingValues = paddingValues,
                backdrop = backdrop,
                liquidGlassEnabled = liquidGlassEnabled,
                floatingBottomBar = floatingBottomBar,
                bottomBarVisible = floatingBottomBar && bottomBarVisibility == BottomBarVisibility.VISIBLE,
                onScrollDirectionChanged = { isScrollingUp ->
                    if (floatingBottomBar) {
                        bottomBarVisibility =
                            if (isScrollingUp) BottomBarVisibility.HIDDEN else BottomBarVisibility.VISIBLE
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 悬浮底栏叠加在 Scaffold 内容之上
        if (floatingBottomBar) {
            FloatingBottomBar(
                selected = selected,
                onSelect = { selected = it },
                liquidGlass = liquidGlass,
                backdrop = backdrop,
                visible = bottomBarVisibility == BottomBarVisibility.VISIBLE,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 12.dp)
            )
        }
    }
}

@Composable
private fun AppContent(
    selected: Int,
    noteVm: NoteViewModel,
    settingsVm: SettingsViewModel,
    paddingValues: PaddingValues,
    backdrop: LayerBackdrop,
    liquidGlassEnabled: Boolean,
    floatingBottomBar: Boolean,
    bottomBarVisible: Boolean,
    onScrollDirectionChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val baseModifier = if (liquidGlassEnabled) {
        modifier.statusBarsPadding().layerBackdrop(backdrop)
    } else {
        modifier.statusBarsPadding()
    }

    // 使用 Crossfade 替代 AnimatedContent：
    // 纯 alpha 淡入淡出，不做位移，避免新旧页面同时布局+绘制造成的掉帧
    Crossfade(
        targetState = selected,
        animationSpec = tween(durationMillis = 200),
        modifier = baseModifier,
        label = "pageTransition",
    ) { page ->
        when (page) {
            0 -> HomePage(
                vm = noteVm,
                paddingValues = paddingValues,
                floatingBottomBar = floatingBottomBar,
                bottomBarVisible = bottomBarVisible,
                onScrollDirectionChanged = onScrollDirectionChanged
            )
            1 -> FavoritesPage(
                vm = noteVm,
                paddingValues = paddingValues,
                onScrollDirectionChanged = onScrollDirectionChanged
            )
            2 -> SettingsPage(vm = settingsVm, paddingValues = paddingValues)
        }
    }
}

@Composable
private fun AppContent(
    selected: Int,
    noteVm: NoteViewModel,
    settingsVm: SettingsViewModel,
    paddingValues: PaddingValues,
    backdrop: LayerBackdrop,
    liquidGlassEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val baseModifier = if (liquidGlassEnabled) {
        modifier.statusBarsPadding().layerBackdrop(backdrop)
    } else {
        modifier.statusBarsPadding()
    }

    // 使用 Crossfade 替代 AnimatedContent：
    // 纯 alpha 淡入淡出，不做位移，避免新旧页面同时布局+绘制造成的掉帧
    Crossfade(
        targetState = selected,
        animationSpec = tween(durationMillis = 200),
        modifier = baseModifier,
        label = "pageTransition",
    ) { page ->
        when (page) {
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
    backdrop: LayerBackdrop,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    // 向上滚动时底栏向下滑出屏幕隐藏，向下滑动时滑回显示
    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bottomBarOffset"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = offsetY * (size.height + 100.dp.toPx()) }
    ) {
        if (liquidGlass) {
            // 液态玻璃 + 果冻弹性底栏（基于 Kyant Backdrop 库）
            LiquidGlassBottomBar(
                selected = selected,
                onSelect = onSelect,
                backdrop = backdrop,
                modifier = Modifier,
            )
        } else {
            // 普通浮动底栏
            Surface(
                modifier = Modifier.fillMaxWidth(),
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