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
import com.pzdd.note.data.Note
/**
 * iOS 26 风格液态玻璃笔记操作菜单（居中弹窗）。
 *
 * 设计要点：
 * - 半透明遮罩 + 居中缩放弹出动画
 * - 毛玻璃容器：模糊背景 + 高光边缘渐变 + 镜面边框
 * - 液态玻璃按钮：每个操作项是独立的圆角玻璃按钮
 * - 按钮按压时有果冻缩放反馈 + 触觉反馈
 */
@Composable
fun NoteActionSheet(
    note: Note,
    onDismiss: () -> Unit,
    onCopyTitle: () -> Unit,
    onCopyContent: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val haptic = LocalHapticFeedback.current

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val dismissWithAnim: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        visible = false
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = dismissWithAnim
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(
                initialScale = 0.85f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(tween(200)),
            exit = scaleOut(
                targetScale = 0.9f,
                animationSpec = tween(180)
            ) + fadeOut(tween(150)),
        ) {
            FrostedGlassPanel(isLight = isLight) {
                Column(
                    modifier = Modifier
                        .padding(10.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
}

/**
 * 紧凑毛玻璃面板：居中弹窗外壳。
 * - 适配内容宽度，圆角更小更紧凑
 * - 半透明玻璃底色 + 高光渐变 + 镜面边框
 * - 柔和投影
 */
@Composable
private fun FrostedGlassPanel(
    isLight: Boolean,
    content: @Composable () -> Unit
) {
    val glassColor = if (isLight) {
        Color.White.copy(alpha = 0.7f)
    } else {
        Color(0xFF2C2C2E).copy(alpha = 0.7f)
    }
    val highlightColor = if (isLight) {
        Color.White.copy(alpha = 0.4f)
    } else {
        Color.White.copy(alpha = 0.08f)
    }
    val borderColor = if (isLight) {
        Color.White.copy(alpha = 0.5f)
    } else {
        Color.White.copy(alpha = 0.08f)
    }
    val shadowColor = if (isLight) {
        Color.Black.copy(alpha = 0.12f)
    } else {
        Color.Black.copy(alpha = 0.4f)
    }

    Box(
        modifier = Modifier
            .width(220.dp)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = shadowColor,
                spotColor = shadowColor
            )
            .clip(RoundedCornerShape(24.dp))
            .background(glassColor)
            .background(
                Brush.verticalGradient(
                    colors = listOf(highlightColor, Color.Transparent)
                )
            )
            .border(
                width = 0.5.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        borderColor,
                        borderColor.copy(alpha = borderColor.alpha * 0.3f),
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
    ) {
        content()
    }
}

/**
 * 透明液态果冻按钮：纵向排列的操作项。
 * - 几乎透明的玻璃底色，按压时出现光泽
 * - 按压时夸张果冻形变（X 拉伸 Y 压缩）+ 触觉反馈
 * - 图标在左、文字在右，水平排列
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

    // 果冻形变：按压时横向拉伸、纵向压缩
    val pressProgress by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "jelly"
    )
    val jellyScaleX = 1f + pressProgress * 0.12f
    val jellyScaleY = 1f - pressProgress * 0.08f

    // 透明底色，按压时浮现微弱光泽
    val restingColor = if (isLight) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color.White.copy(alpha = 0.04f)
    }
    val pressedColor = if (isLight) {
        Color.White.copy(alpha = 0.35f)
    } else {
        Color.White.copy(alpha = 0.15f)
    }
    val btnColor = lerp(restingColor, pressedColor, pressProgress)

    val highlightColor = if (isLight) {
        Color.White.copy(alpha = 0.25f * pressProgress)
    } else {
        Color.White.copy(alpha = 0.08f * pressProgress)
    }
    val borderColor = if (isLight) {
        Color.White.copy(alpha = 0.3f + 0.2f * pressProgress)
    } else {
        Color.White.copy(alpha = 0.06f + 0.06f * pressProgress)
    }

    val iconTint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val textColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = modifier
            .graphicsLayer {
                this.scaleX = jellyScaleX
                this.scaleY = jellyScaleY
            }
            .clip(RoundedCornerShape(16.dp))
            .background(btnColor)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(highlightColor, Color.Transparent)
                )
            )
            .border(0.5.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            )
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}