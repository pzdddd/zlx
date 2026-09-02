package com.fuck.zlx

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var showHelp by remember { mutableStateOf(false) }
    // 帮助页打开时隐藏底部导航栏
    DisposableEffect(showHelp) {
        SubPageState.covering = showHelp
        onDispose { SubPageState.covering = false }
    }
    BackHandler(enabled = showHelp) { showHelp = false }
    if (showHelp) {
        HelpScreen(onBack = { showHelp = false })
        return
    }

    var useLocalDownload by remember { mutableStateOf(prefs.getBoolean("use_local_download", false)) }
    var downloadThreads by remember { mutableFloatStateOf(prefs.getInt("download_threads", 8).toFloat()) }
    
    var downloadDirUri by remember { mutableStateOf(prefs.getString("download_dir_uri", null)) }
    var downloadDirName by remember { mutableStateOf("默认 (系统下载文件夹)") }

    var lazyLoadVideo by remember { mutableStateOf(prefs.getBoolean("lazy_load_video", true)) }
    var floatingBottomBar by remember { mutableStateOf(prefs.getBoolean("floating_bottom_bar", true)) }
    var liquidGlass by remember { mutableStateOf(prefs.getBoolean("liquid_glass", true)) }


    LaunchedEffect(downloadDirUri) {
        if (downloadDirUri != null) {
            try {
                val uri = Uri.parse(downloadDirUri)
                val docFile = DocumentFile.fromTreeUri(context, uri)
                downloadDirName = docFile?.name ?: "自定义目录"
            } catch (e: Exception) {
                downloadDirName = "自定义目录"
            }
        } else {
            downloadDirName = "默认 (系统下载文件夹)"
        }
    }

    val dirLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            val uriStr = uri.toString()
            downloadDirUri = uriStr
            prefs.edit().putString("download_dir_uri", uriStr).apply()
        }
    }
    // 【修复】：移除 rememberLayerBackdrop()。原先创建了 Backdrop 对象却没有用
    // layerBackdrop() 修饰符将其附加到组件树，导致悬空的 Backdrop 在 Compose
    // 重组时触发异常闪退。设置页改为纯 Material3 渲染，不再依赖液态玻璃采样。
    val backdrop: Backdrop? = null

    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }) }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // --- 帮助 ---
                Column {
                    LiquidGlassSectionTitle("帮助")
                    LiquidGlassListCard(backdrop = backdrop) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showHelp = true }
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.HelpOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("使用帮助", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "各页面功能与操作说明",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // --- 下载引擎与网络 ---
                Column {
                    LiquidGlassSectionTitle("下载引擎与网络")
                    LiquidGlassListCard(backdrop = backdrop) {
                        LiquidGlassSettingSwitchItem(
                            title = "开启内置下载引擎",
                            subtitle = "开启后极速并发下载，并自动转换为 MP4",
                            checked = useLocalDownload,
                            onCheckedChange = {
                                useLocalDownload = it
                                prefs.edit().putBoolean("use_local_download", it).apply()
                            },
                            backdrop = backdrop,
                            showDivider = true
                        )

                        // 滑块部分
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("多线程并发数量", style = MaterialTheme.typography.bodyLarge)
                                Text("${downloadThreads.toInt()} 线程", color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "大幅提高下载速度，但过高可能导致手机发热",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = downloadThreads,
                                onValueChange = { downloadThreads = it },
                                onValueChangeFinished = { prefs.edit().putInt("download_threads", downloadThreads.toInt()).apply() },
                                valueRange = 1f..32f,
                                steps = 30
                            )
                        }
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 20.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )

                        // 目录选择部分
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { dirLauncher.launch(null) }
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("自定义下载目录", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    downloadDirName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // --- 嗅探与网页行为 ---
                Column {
                    LiquidGlassSectionTitle("嗅探与网页行为")
                    LiquidGlassListCard(backdrop = backdrop) {
                        LiquidGlassSettingSwitchItem(
                            title = "滑动加载视频 (网页原生)",
                            subtitle = "开启: 滑动时才加载(省流防卡)\n关闭: 强制一次性加载全部隐藏视频(方便提取)",
                            checked = lazyLoadVideo,
                            onCheckedChange = {
                                lazyLoadVideo = it
                                prefs.edit().putBoolean("lazy_load_video", it).apply()
                            },
                            backdrop = backdrop,
                            showDivider = false 
                        )
                    }
                }

                // --- 外观与视觉 ---
                Column {
                    LiquidGlassSectionTitle("外观与视觉")
                    LiquidGlassListCard(backdrop = backdrop) {
                        LiquidGlassSettingSwitchItem(
                            title = "悬浮底栏",
                            subtitle = "开启: 悬浮胶囊造型 关闭: 贴底通栏\n切换标签页后生效",
                            checked = floatingBottomBar,
                            onCheckedChange = {
                                floatingBottomBar = it
                                prefs.edit().putBoolean("floating_bottom_bar", it).apply()
                            },
                            backdrop = backdrop,
                            showDivider = true
                        )

                        LiquidGlassSettingSwitchItem(
                            title = "液态玻璃",
                            subtitle = "底栏毛玻璃效果，关闭则纯色底栏更省电\n切换标签页后生效",
                            checked = liquidGlass,
                            onCheckedChange = {
                                liquidGlass = it
                                prefs.edit().putBoolean("liquid_glass", it).apply()
                            },
                            backdrop = backdrop,
                            showDivider = false
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// =========================================================================
// 👇 Q弹液态开关与列表项基础组件
// =========================================================================

@Composable
fun LiquidGlassSettingSwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    backdrop: Backdrop?,
    showDivider: Boolean = true
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text(
                    text = title, 
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle, 
                        style = MaterialTheme.typography.bodyMedium, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            LiquidGlassSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                backdrop = backdrop
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 20.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            )
        }
    }
}

@Composable
fun LiquidGlassSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: LiquidGlassSwitchColors = LiquidGlassSwitchDefaults.colors(),
    backdrop: Backdrop? 
) {
    // ✅ 彻底解决“卡死” Bug：使用原生 interactionSource 获取按压状态
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val trackHeight = 32.dp
    val trackWidth = 56.dp
    val thumbSize = 28.dp
    val thumbPadding = (trackHeight - thumbSize) / 2

    // 🌟 Q弹果冻核心 1：挤压形变动画 (阻尼调为 0.45 产生明显回弹)
    val thumbScaleX by animateFloatAsState(
        targetValue = if (isPressed) 1.25f else 1.0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 400f)
    )
    val thumbScaleY by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1.0f, // 按下时上下被压扁
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 400f)
    )

    val animatedThumbWidth = thumbSize * thumbScaleX
    val animatedThumbHeight = thumbSize * thumbScaleY

    // 🌟 Q弹果冻核心 2：滑动位移动画
    val thumbOffsetTarget = if (checked) trackWidth - thumbSize - thumbPadding else thumbPadding
    val thumbOffset by animateDpAsState(
        targetValue = thumbOffsetTarget,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 300f)
    )

    // 颜色渐变
    val trackColor by animateColorAsState(
        targetValue = if (checked) colors.checkedTrackColor else colors.uncheckedTrackColor
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) colors.checkedThumbColor else colors.uncheckedThumbColor
    )
    val glowColor by animateColorAsState(
        targetValue = if (checked) colors.checkedGlowColor else colors.uncheckedGlowColor
    )
    
    val glowRadiusTarget = if (checked) 16.dp * thumbScaleX else 8.dp * thumbScaleX
    val glowRadius by animateDpAsState(
        targetValue = glowRadiusTarget,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f)
    )

    Box(
        modifier = modifier
            .size(trackWidth, trackHeight)
            .clickable(enabled = enabled) {
                if (onCheckedChange != null) {
                    onCheckedChange(!checked)
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        LiquidGlassBase(
            color = trackColor,
            backdrop = backdrop,
            modifier = Modifier.fillMaxSize(),
            cornerRadius = trackHeight / 2
        )

        Box(
            modifier = Modifier
                .offset(x = thumbOffset - glowRadius + thumbSize / 2, y = 0.dp)
                .size(glowRadius * 2),
            contentAlignment = Alignment.Center
        ) {
            LiquidGlassGlow(
                color = glowColor,
                radius = glowRadius,
                alpha = 0.6f * thumbScaleX
            )
        }

        Box(
            modifier = Modifier
                // 动态计算偏移量，确保变形时滑块依然居中
                .offset(
                    x = thumbOffset - (animatedThumbWidth - thumbSize) / 2, 
                    y = thumbPadding + (thumbSize - animatedThumbHeight) / 2
                )
                .size(width = animatedThumbWidth, height = animatedThumbHeight)
        ) {
            LiquidGlassMask(
                color = thumbColor,
                backdrop = backdrop,
                cornerRadius = animatedThumbHeight / 2,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

data class LiquidGlassSwitchColors(
    val checkedThumbColor: Color,
    val checkedTrackColor: Color,
    val checkedGlowColor: Color,
    val uncheckedThumbColor: Color,
    val uncheckedTrackColor: Color,
    val uncheckedGlowColor: Color
)

object LiquidGlassSwitchDefaults {
    @Composable
    fun colors(
        checkedThumbColor: Color = MaterialTheme.colorScheme.primary,
        checkedTrackColor: Color = MaterialTheme.colorScheme.primaryContainer,
        checkedGlowColor: Color = MaterialTheme.colorScheme.primary,
        uncheckedThumbColor: Color = MaterialTheme.colorScheme.outline,
        uncheckedTrackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
        uncheckedGlowColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    ): LiquidGlassSwitchColors = LiquidGlassSwitchColors(
        checkedThumbColor = checkedThumbColor,
        checkedTrackColor = checkedTrackColor,
        checkedGlowColor = checkedGlowColor,
        uncheckedThumbColor = uncheckedThumbColor,
        uncheckedTrackColor = uncheckedTrackColor,
        uncheckedGlowColor = uncheckedGlowColor
    )
}

// =========================================================================
// 👇 底部基础 UI 零件 (已补齐)
// =========================================================================

@Composable
fun LiquidGlassSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun LiquidGlassListCard(
    backdrop: Backdrop?, 
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp)),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f), // 玻璃半透明底色
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(content = content)
    }
}

@Composable
fun LiquidGlassBase(
    color: Color,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp
) {
    Box(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius))
            .background(color.copy(alpha = 0.4f)) // 液态果冻的半透明基底
    )
}

@Composable
fun LiquidGlassMask(
    color: Color,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp
) {
    Box(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius))
            .background(color) // 按钮实色遮罩层
    )
}

@Composable
fun LiquidGlassGlow(
    color: Color,
    radius: Dp,
    alpha: Float
) {
    // 液态按钮底部的光晕扩散层
    Box(
        modifier = Modifier
            .size(radius * 2)
            .background(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = alpha), 
                        androidx.compose.ui.graphics.Color.Transparent
                    )
                ),
                shape = androidx.compose.foundation.shape.CircleShape
            )
    )
}
