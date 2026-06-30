package com.pzdd.note.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

data class BottomBarItem(
    val icon: ImageVector,
    val label: String,
)

fun defaultBottomItems() = listOf(
    BottomBarItem(Icons.Filled.Home, "首页"),
    BottomBarItem(Icons.Filled.Star, "收藏"),
    BottomBarItem(Icons.Filled.Settings, "设置"),
)

/**
 * 基于 Kyant Backdrop 库的液态玻璃底栏。
 *
 * 参考 log.txt 中的实现思路：
 * - [图层1] 整个底栏背景：drawBackdrop + vibrancy + blur + lens + Highlight + Shadow
 * - [图层2] 液态玻璃滑块：跟随选中位置滑动，按压时果冻形变 + 色散折射
 * - [图层3] 图标与文字：渲染在最上层，保证清晰不被模糊
 * - [图层4] 隐形拖拽层：支持左右拖拽切换 Tab
 *
 * 需要调用方提供 backdrop（通过 rememberLayerBackdrop() 创建）。
 */
@Composable
fun LiquidGlassBottomBar(
    selected: Int,
    onSelect: (Int) -> Unit,
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    items: List<BottomBarItem> = defaultBottomItems(),
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val tabCount = items.size

    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(72.dp)) {
        val density = LocalDensity.current
        val tabWidthPx = with(density) { maxWidth.toPx() } / tabCount
        val tabWidth = maxWidth / tabCount
        val scope = rememberCoroutineScope()

        // 滑块位置动画
        val position = remember { Animatable(selected.toFloat()) }

        var isPressed by remember { mutableStateOf(false) }
        var dragVelocity by remember { mutableFloatStateOf(0f) }

        // 同步外部选中变化
        LaunchedEffect(selected) {
            if (!isPressed) {
                position.animateTo(
                    selected.toFloat(),
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow,
                    ),
                )
            }
        }

        // 果冻按压膨胀
        val jellyScale by animateFloatAsState(
            targetValue = if (isPressed) 1.22f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
            label = "jellyScale",
        )
        // 拖拽拉伸
        val stretchAmount by animateFloatAsState(
            targetValue = if (isPressed) (abs(dragVelocity) * 0.0015f).coerceIn(0f, 0.25f) else 0f,
            animationSpec = spring(stiffness = Spring.StiffnessHigh),
            label = "stretchAmount",
        )
        val stretchDirection = if (dragVelocity >= 0f) 1f else -1f

        val sliderOffsetPx = position.value * tabWidthPx

        // ==================== [图层1] 底栏整体液态玻璃背景 ====================
        val containerColor = if (isLight) {
            Color(0xFFFAFAFA).copy(alpha = 0.4f)
        } else {
            Color(0xFF121212).copy(alpha = 0.4f)
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(12.dp.toPx())
                        lens(24.dp.toPx(), 24.dp.toPx())
                    },
                    highlight = { Highlight.Default },
                    shadow = { Shadow() },
                    onDrawSurface = { drawRect(containerColor) },
                ),
        )

        // ==================== [图层2] 液态玻璃滑块 ====================
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset(sliderOffsetPx.roundToInt(), 0) }
                .width(tabWidth)
                .fillMaxHeight()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .graphicsLayer {
                    scaleX = jellyScale * (1f + stretchAmount * stretchDirection * 0.3f)
                    scaleY = jellyScale * (1f - stretchAmount * 0.15f)
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        val pressProgress = if (isPressed) 1f else 0f
                        lens(
                            10.dp.toPx() + 6.dp.toPx() * pressProgress,
                            14.dp.toPx() + 8.dp.toPx() * pressProgress,
                            chromaticAberration = true,
                        )
                    },
                    highlight = { Highlight.Default },
                    shadow = { Shadow() },
                    innerShadow = { InnerShadow(radius = 8.dp) },
                    onDrawSurface = {
                        drawRect(
                            if (isLight) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.10f),
                        )
                    },
                ),
        )
        // ==================== [图层3] 图标与文字 ====================
        Row(modifier = Modifier.fillMaxSize()) {
            items.forEachIndexed { index, item ->
                val isSelected = selected == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(index) {
                            detectTapGestures { onSelect(index) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = contentColor,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = contentColor,
                        )
                    }
                }
            }
        }

        // ==================== [图层4] 隐形拖拽层 ====================
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset(sliderOffsetPx.roundToInt(), 0) }
                .width(tabWidth)
                .fillMaxHeight()
                .pointerInput(tabCount, tabWidthPx) {
                    detectDragGestures(
                        onDragStart = { isPressed = true },
                        onDragEnd = {
                            isPressed = false
                            dragVelocity = 0f
                            val target = position.value.roundToInt().coerceIn(0, tabCount - 1)
                            scope.launch {
                                position.animateTo(
                                    target.toFloat(),
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow,
                                    ),
                                )
                            }
                            onSelect(target)
                        },
                        onDragCancel = {
                            isPressed = false
                            dragVelocity = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragVelocity = dragAmount.x
                            val delta = dragAmount.x / tabWidthPx
                            scope.launch {
                                position.snapTo(
                                    (position.value + delta).coerceIn(0f, (tabCount - 1).toFloat()),
                                )
                            }
                        },
                    )
                },
        )
    }
}

/**
 * 创建液态玻璃底栏所需的 backdrop。
 * 在页面根布局中使用，并将背景内容用 Modifier.layerBackdrop(backdrop) 标记。
 */
@Composable
fun rememberLiquidGlassBackdrop(): LayerBackdrop = rememberLayerBackdrop()