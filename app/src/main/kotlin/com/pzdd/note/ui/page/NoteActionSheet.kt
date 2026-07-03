package com.pzdd.note.ui.page

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.pzdd.note.data.Note
/**
 * iOS 26 风格液态玻璃笔记操作菜单（居中弹窗）。
 *
 * 设计要点：
 * - 半透明遮罩 + 居中缩放弹出动画（手动 graphicsLayer，不使用 AnimatedVisibility）
 * - 基于 Kyant Backdrop 的真实液态玻璃面板和按钮
 * - 按钮透过面板可见背景内容的折射/模糊
 * - 按压时有果冻形变 + 触觉反馈
 */
@Composable
fun NoteActionSheet(
    note: Note,
    onDismiss: () -> Unit,
    onCopyTitle: () -> Unit,
    onCopyContent: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    backdrop: LayerBackdrop? = null
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val haptic = LocalHapticFeedback.current

    // 弹出动画进度（0→1），手动驱动 graphicsLayer
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val animProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "sheetAnim"
    )
    val sheetScale = 0.85f + (1f - 0.85f) * animProgress
    val sheetAlpha = animProgress

    val dismissWithAnim: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        visible = false
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f * animProgress))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = dismissWithAnim
            ),
        contentAlignment = Alignment.Center
    ) {
        FrostedGlassPanel(
            isLight = isLight,
            backdrop = backdrop,
            modifier = Modifier.graphicsLayer {
                scaleX = sheetScale
                scaleY = sheetScale
                alpha = sheetAlpha
            }
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LiquidGlassButton(
                    icon = Icons.Filled.Title,
                    label = "复制标题",
                    isLight = isLight,
                    haptic = haptic,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onCopyTitle
                )
                LiquidGlassButton(
                    icon = Icons.Filled.ContentCopy,
                    label = "复制内容",
                    isLight = isLight,
                    haptic = haptic,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onCopyContent
                )
                LiquidGlassButton(
                    icon = if (note.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                    label = if (note.isFavorite) "取消收藏" else "收藏",
                    isLight = isLight,
                    haptic = haptic,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onToggleFavorite
                )
                LiquidGlassButton(
                    icon = if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    label = if (note.isPinned) "取消置顶" else "置顶",
                    isLight = isLight,
                    haptic = haptic,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onTogglePin
                )
                LiquidGlassButton(
                    icon = Icons.Filled.Delete,
                    label = "删除",
                    isLight = isLight,
                    haptic = haptic,
                    destructive = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDelete
                )
            }
        }
    }
}

/**
 * 高透亚克力玻璃面板：大圆角，半透明，柔和光影。
 */
@Composable
private fun FrostedGlassPanel(
    isLight: Boolean,
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val glassColor = if (isLight) Color.White.copy(alpha = 0.2f) else Color(0xFF2C2C2E).copy(alpha = 0.2f)
    val highlightColor = if (isLight) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f)
    val borderColor = if (isLight) Color.White.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f)
    val shadowColor = if (isLight) Color.Black.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.35f)

    Box(
        modifier = modifier
            .width(300.dp)
            .shadow(20.dp, RoundedCornerShape(28.dp), ambientColor = shadowColor, spotColor = shadowColor)
            .clip(RoundedCornerShape(28.dp))
            .background(glassColor)
            .background(Brush.verticalGradient(listOf(highlightColor, Color.Transparent)))
            .border(0.5.dp, borderColor, RoundedCornerShape(28.dp))
    ) {
        content()
    }
}

/**
 * 高透亚克力玻璃胶囊按钮（参考 log.txt 规格）。
 * - 横向拉满，大胶囊圆角
 * - 高透亚克力底色 + 细亮反光边框
 * - 左侧彩色图标 + 居中文字
 * - 按压时果冻形变 + 触觉反馈
 */
@Composable
private fun LiquidGlassButton(
    icon: ImageVector,
    label: String,
    isLight: Boolean,
    haptic: HapticFeedback,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    destructive: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressProgress by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "jelly"
    )

    val jellyScaleX = 1f - pressProgress * 0.02f
    val jellyScaleY = 1f - pressProgress * 0.04f

    val iconTint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val textColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface

    // 高透亚克力底色
    val restingColor = if (isLight) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.06f)
    val pressedColor = if (isLight) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.12f)
    val btnColor = lerp(restingColor, pressedColor, pressProgress)

    // 细亮反光边框
    val borderAlpha = if (isLight) 0.5f + 0.2f * pressProgress else 0.1f + 0.06f * pressProgress
    val borderColor = if (isLight) Color.White.copy(alpha = borderAlpha) else Color.White.copy(alpha = borderAlpha)

    // 顶部高光
    val highlightAlpha = if (isLight) 0.35f else 0.1f

    val capsuleShape = RoundedCornerShape(50)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = jellyScaleX
                scaleY = jellyScaleY
                shadowElevation = if (isPressed) 6f else 1f
                shape = capsuleShape
                clip = true
            }
            .clip(capsuleShape)
            .background(btnColor)
            .background(Brush.verticalGradient(listOf(
                Color.White.copy(alpha = highlightAlpha),
                Color.Transparent,
                Color.White.copy(alpha = highlightAlpha * 0.2f)
            )))
            .border(1.dp, borderColor, capsuleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧图标
            Icon(
                icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
            // 居中文字
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = textColor,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            // 右侧占位（与左侧图标对称）
            Spacer(modifier = Modifier.size(22.dp))
        }
    }
}