package com.pzdd.note.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
 * 液态玻璃 + 果冻弹性的浮动底栏。
 *
 * 设计参考 log.txt 中的 com.kyant.backdrop 思路：
 * - blur()       → 真实背景模糊（API31+ RenderEffect，低版本降级半透明）
 * - Highlight    → 顶部高光渐变 + 边缘亮线
 * - Shadow       → 底部柔和投影
 * - lens/vibrancy→ 用渐变色调叠加模拟折射饱和感
 * - 果冻动画     → spring(DampingRatioMediumBouncy) 缩放 + 按压挤压
 *
 * 不依赖外部库，兼容 minSdk 28。
 */
@Composable
fun LiquidGlassBottomBar(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    items: List<BottomBarItem> = defaultBottomItems(),
    cornerRadius: Dp = 28.dp,
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f

    val glassColor = if (isLight) {
        Color.White.copy(alpha = 0.55f)
    } else {
        Color(0xFF1C1C1E).copy(alpha = 0.45f)
    }
    val borderColor = if (isLight) {
        Color.White.copy(alpha = 0.6f)
    } else {
        Color.White.copy(alpha = 0.12f)
    }

    // 投影层和玻璃主体必须是平级兄弟，不能父子嵌套，
    // 否则父级的 blur() 会把子级内容（按钮）也一起模糊掉。
    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        // 投影层（模拟 Shadow）— 纯色块 + blur，放在底层
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(cornerRadius))
                .background(Color.Black.copy(alpha = if (isLight) 0.10f else 0.35f))
                .blur(16.dp),
        )
        // 玻璃主体 — 不加 blur，按钮内容清晰可见
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(cornerRadius))
                .background(glassColor)
                .border(1.dp, borderColor, RoundedCornerShape(cornerRadius))
                .drawBehind {
                    // 顶部高光渐变（模拟 Highlight.Default）
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (isLight) 0.45f else 0.18f),
                                Color.Transparent,
                            ),
                            startY = 0f,
                            endY = size.height * 0.5f,
                        ),
                    )
                    // 底部微暗渐变，增加厚度感
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = if (isLight) 0.04f else 0.15f),
                            ),
                            startY = size.height * 0.5f,
                            endY = size.height,
                        ),
                    )
                },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { index, item ->
                    JellyNavItem(
                        item = item,
                        selected = selected == index,
                        onClick = { onSelect(index) },
                        isLight = isLight,
                    )
                }
            }
        }
    }
}
/**
 * 单个果冻导航项。
 *
 * 果冻效果：
 * - 选中时图标 + 文字用弹性 spring 放大
 * - 按压时 graphicsLayer scaleX/Y 挤压（像捏果冻）
 * - 选中指示器为胶囊高亮，带弹性位移
 */
@Composable
private fun RowScope.JellyNavItem(
    item: BottomBarItem,
    selected: Boolean,
    onClick: () -> Unit,
    isLight: Boolean,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    }

    // 选中时整体放大
    val selectedScale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "selectedScale",
    )
    // 按压时挤压：横向拉伸、纵向压缩
    val pressScaleX by animateFloatAsState(
        targetValue = if (isPressed) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "pressScaleX",
    )
    val pressScaleY by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "pressScaleY",
    )
    // 选中指示器（胶囊）宽度弹性变化
    val indicatorWidth by animateDpAsState(
        targetValue = if (selected) 40.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "indicatorWidth",
    )

    val indicatorColor = if (isLight) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .weight(1f)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 6.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            // 选中胶囊指示器
            if (indicatorWidth > 0.dp) {
                Box(
                    modifier = Modifier
                        .width(indicatorWidth)
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(indicatorColor),
                )
            }
            // 图标 + 文字，套 graphicsLayer 做果冻形变
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    scaleX = selectedScale * pressScaleX
                    scaleY = selectedScale * pressScaleY
                },
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp),
                )
                if (selected) {
                    Text(
                        text = item.label,
                        color = contentColor,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        modifier = Modifier.padding(top = 2.dp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}