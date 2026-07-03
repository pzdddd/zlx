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
 * iOS 风格毛玻璃面板（参考 log.txt）。
 * - 显著毛玻璃模糊效果（多层半透明叠加模拟）
 * - 大圆角，细白色边框
 * - 柔和光影，干净专业
 */
@Composable
private fun FrostedGlassPanel(
    isLight: Boolean,
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // 毛玻璃底色：多层叠加模拟 frosted glass 模糊感
    val baseColor = if (isLight) Color(0xFFF0F0F3) else Color(0xFF1C1C1E)
    val glassColor = baseColor.copy(alpha = 0.55f)
    val frostLayer = if (isLight) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f)
    val highlightColor = if (isLight) Color.White.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f)
    val borderColor = Color.White.copy(alpha = 0.6f)
    val shadowColor = if (isLight) Color.Black.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.4f)

    Box(
        modifier = modifier
            .width(300.dp)
            .shadow(24.dp, RoundedCornerShape(28.dp), ambientColor = shadowColor, spotColor = shadowColor)
            .clip(RoundedCornerShape(28.dp))
            // 底层：半透明玻璃底色
            .background(glassColor)
            // 中层：磨砂层（模拟 frosted glass 的朦胧感）
            .background(frostLayer)
            // 顶层：顶部高光渐变
            .background(Brush.verticalGradient(listOf(highlightColor, Color.Transparent, Color.Transparent)))
            // 细白色边框
            .border(0.5.dp, borderColor, RoundedCornerShape(28.dp))
    ) {
        content()
    }
}

/**
 * 胶囊形玻璃按钮（参考 log.txt 规格）。
 * - 胶囊形圆角，细白色边框
 * - 左侧彩色图标 + 旁边左对齐文字
 * - 半透明玻璃质感，按压时缩放反馈
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
    val scale = 1f - pressProgress * 0.03f

    // 图标颜色：蓝色 / 红色（log.txt 明确要求）
    val iconColor = if (destructive) Color(0xFFFF3B30) else Color(0xFF007AFF)
    // 文字颜色：黑色（log.txt: "清晰的中文文本"）
    val textColor = if (destructive) Color(0xFFFF3B30) else if (isLight) Color.Black else Color.White

    // 半透明玻璃底色
    val restingColor = if (isLight) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.06f)
    val pressedColor = if (isLight) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.12f)
    val btnColor = lerp(restingColor, pressedColor, pressProgress)

    // 细白色边框
    val borderAlpha = 0.6f + 0.2f * pressProgress
    val borderColor = Color.White.copy(alpha = borderAlpha)

    // 顶部高光
    val highlightAlpha = if (isLight) 0.3f else 0.08f

    val capsuleShape = RoundedCornerShape(50)

    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = if (isPressed) 4f else 0f
                shape = capsuleShape
                clip = true
            }
            .clip(capsuleShape)
            .background(btnColor)
            .background(Brush.verticalGradient(listOf(
                Color.White.copy(alpha = highlightAlpha),
                Color.Transparent,
                Color.White.copy(alpha = highlightAlpha * 0.15f)
            )))
            .border(0.5.dp, borderColor, capsuleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            )
            .padding(vertical = 13.dp, horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 左侧彩色图标
        Icon(
            icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(22.dp)
        )
        // 旁边左对齐文字
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}