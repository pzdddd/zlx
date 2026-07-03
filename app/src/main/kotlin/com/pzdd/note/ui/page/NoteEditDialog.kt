package com.pzdd.note.ui.page

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NoteEditDialog(
    title: String,
    initialTitle: String,
    initialContent: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
    onHideBottomBar: (Boolean) -> Unit = {}
) {
    var t by remember { mutableStateOf(initialTitle) }
    var c by remember { mutableStateOf(initialContent) }
    var fullscreen by remember { mutableStateOf(false) }
    LaunchedEffect(fullscreen) { onHideBottomBar(fullscreen) }

    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val dismissWithAnim: () -> Unit = {
        visible = false
        onDismiss()
    }

    // 拦截系统返回键，关闭弹窗而不是退出 App
    androidx.activity.compose.BackHandler { dismissWithAnim() }

    val animProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "dialogAnim"
    )
    val sheetScale = 0.85f + 0.15f * animProgress

    // 毛玻璃面板颜色
    val baseColor = if (isLight) Color(0xFFF0F0F3) else Color(0xFF1C1C1E)
    val glassColor = baseColor.copy(alpha = 0.55f)
    val frostLayer = if (isLight) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f)
    val highlightColor = if (isLight) Color.White.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f)
    val borderColor = Color.White.copy(alpha = 0.6f)
    val shadowColor = if (isLight) Color.Black.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.4f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f * animProgress))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = dismissWithAnim
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(320.dp)
                .graphicsLayer {
                    scaleX = sheetScale
                    scaleY = sheetScale
                    alpha = animProgress
                }
                .shadow(24.dp, RoundedCornerShape(28.dp), ambientColor = shadowColor, spotColor = shadowColor)
                .clip(RoundedCornerShape(28.dp))
                .background(glassColor)
                .background(frostLayer)
                .background(Brush.verticalGradient(listOf(highlightColor, Color.Transparent, Color.Transparent)))
                .border(0.5.dp, borderColor, RoundedCornerShape(28.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // 拦截点击，防止穿透到遮罩
                )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 标题
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isLight) Color.Black else Color.White
                )

                // 标题输入框
                OutlinedTextField(
                    value = t,
                    onValueChange = { t = it },
                    label = { Text("标题") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )

                // 内容输入框
                OutlinedTextField(
                    value = c,
                    onValueChange = { c = it },
                    label = { Text("内容") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 300.dp),
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 按钮行：全屏编辑(左) | 取消 | 保存(右)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 全屏编辑按钮（左侧）
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (isLight) Color.Black.copy(alpha = 0.05f)
                                else Color.White.copy(alpha = 0.08f)
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { fullscreen = true }
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Filled.Fullscreen,
                                contentDescription = "全屏编辑",
                                tint = if (isLight) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "全屏",
                                fontSize = 14.sp,
                                color = if (isLight) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }

                    // 右侧：取消 + 保存
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 取消按钮
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (isLight) Color.Black.copy(alpha = 0.05f)
                                    else Color.White.copy(alpha = 0.08f)
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = dismissWithAnim
                                )
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "取消",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isLight) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.7f)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // 保存按钮
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onConfirm(t, c) }
                                )
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "保存",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // ==================== 全屏编辑面板 ====================
        if (fullscreen) {
            val fsAnim by animateFloatAsState(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "fsAnim"
            )
            val fsBaseColor = if (isLight) Color(0xFFF0F0F3) else Color(0xFF1C1C1E)
            val fsBorderColor = Color.White.copy(alpha = 0.6f)

            val fsFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(300)
                runCatching { fsFocusRequester.requestFocus() }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f * fsAnim))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { fullscreen = false }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 0.92f + 0.08f * fsAnim
                            scaleY = 0.92f + 0.08f * fsAnim
                            alpha = fsAnim
                        }
                        .shadow(0.dp, RoundedCornerShape(0.dp))
                        .clip(RoundedCornerShape(0.dp))
                        .background(fsBaseColor.copy(alpha = 0.95f))
                        .background(Brush.verticalGradient(listOf(
                            if (isLight) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.05f),
                            Color.Transparent
                        )))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "内容全屏编辑",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isLight) Color.Black else Color.White
                            )
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (isLight) Color.Black.copy(alpha = 0.05f)
                                        else Color.White.copy(alpha = 0.08f)
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { fullscreen = false }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "关闭",
                                    tint = if (isLight) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 全屏文本框（占据剩余空间）
                        OutlinedTextField(
                            value = c,
                            onValueChange = { c = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .focusRequester(fsFocusRequester),
                            shape = RoundedCornerShape(16.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // 底部按钮行：取消 | 保存
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 取消按钮
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (isLight) Color.Black.copy(alpha = 0.05f)
                                        else Color.White.copy(alpha = 0.08f)
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { fullscreen = false }
                                    )
                                    .padding(horizontal = 24.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "取消",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isLight) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.7f)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // 保存按钮
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            fullscreen = false
                                            onConfirm(t, c)
                                        }
                                    )
                                    .padding(horizontal = 24.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "保存",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}