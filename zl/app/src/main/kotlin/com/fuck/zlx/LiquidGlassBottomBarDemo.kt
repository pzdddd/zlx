// 液态玻璃 Switch 开关 + 列表卡片
// 基于 Kyant0/AndroidLiquidGlass backdrop 库
//
// 依赖 (build.gradle.kts):
//   implementation("io.github.kyant0:backdrop:1.0.6")
//   implementation("io.github.kyant0:shapes:1.2.0")
//
// 包含两个组件：
//  1. LiquidGlassSwitch  —— 液态玻璃风格的开关
//     - 轨道(track)和滑块(thumb)都用 drawBackdrop 应用折射/模糊/高光
//     - 滑块切换时有位置 + 颜色渐变动画，按下时有果冻缩放
//  2. LiquidGlassListCard / LiquidGlassListItem —— 功能列表分组卡片
//     - 整个卡片用液态玻璃背景(模糊+鲜明度+折射)
//     - 列表项之间用细分割线
//
// 这两个组件都需要一个 backdrop（用 rememberLayerBackdrop() 在
// 页面背景层创建，并用 Modifier.layerBackdrop(backdrop) 标记背景内容）。
//
// 典型页面结构：
//
// val backdrop = rememberLayerBackdrop()
// Box(Modifier.fillMaxSize()) {
//     // 背景：壁纸/图片/渐变色等，注册为 backdrop 源
//     Image(
//         painter = ...,
//         modifier = Modifier.fillMaxSize().layerBackdrop(backdrop)
//     )
//     // 前景：功能中心页面，卡片和开关会折射/模糊上面这层背景
//     FeatureCenterScreenExample(backdrop = backdrop)
// }
//
// 如果背景就是纯色（没有图片/滚动内容），液态玻璃的"折射"效果会
// 不明显——折射的前提是背景里有可被扭曲的内容（图案、文字、图片等）。

package com.fuck.zlx.liquidglass

import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule

// =====================================================================
// 1. 液态玻璃 Switch 开关
// =====================================================================

private val SwitchTrackWidth = 52.dp
private val SwitchTrackHeight = 30.dp
private val SwitchThumbSize = 24.dp
private val SwitchThumbPadding = 3.dp

/**
 * 液态玻璃风格的开关。
 *
 * @param checked 是否选中
 * @param onCheckedChange 状态变化回调
 * @param backdrop 用于采样背景内容的 backdrop（与页面背景共享）
 * @param checkedTrackColor 选中态轨道颜色（半透明色调，会叠加在液态玻璃效果上）
 * @param uncheckedTrackColor 未选中态轨道颜色
 */
@Composable
fun LiquidGlassSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    checkedTrackColor: Color = Color(0xFF34C759).copy(alpha = 0.55f), // 类似图中的绿色
    uncheckedTrackColor: Color = Color.Gray.copy(alpha = 0.25f),
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // 轨道颜色随选中状态渐变
    val trackTint by animateColorAsState(
        targetValue = if (checked) checkedTrackColor else uncheckedTrackColor,
        label = "switchTrackTint",
    )

    // 滑块从左到右的偏移
    val thumbTravel = SwitchTrackWidth - SwitchThumbSize - SwitchThumbPadding * 2
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) thumbTravel else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "switchThumbOffset",
    )

    // 按下时滑块"果冻"放大
    val thumbScale by animateFloatAsState(
        targetValue = if (isPressed) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "switchThumbScale",
    )

    Box(
        modifier = modifier
            .width(SwitchTrackWidth)
            .height(SwitchTrackHeight)
            // --- 轨道：液态玻璃背景 ---
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(4.dp.toPx())
                    lens(6.dp.toPx(), 8.dp.toPx())
                },
                highlight = { Highlight.Default },
                shadow = { Shadow() },
                onDrawSurface = { drawRect(trackTint) },
            )
            .toggleable(
                value = checked,
                onValueChange = { onCheckedChange?.invoke(it) },
                enabled = enabled
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        // --- 滑块：更强折射 + 内阴影，营造"液滴"立体感 ---
        Box(
            modifier = Modifier
                .padding(start = SwitchThumbPadding)
                .offset(x = thumbOffset)
                .size(SwitchThumbSize)
                .graphicsLayer {
                    scaleX = thumbScale
                    scaleY = thumbScale
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        lens(
                            4.dp.toPx(),
                            6.dp.toPx(),
                            chromaticAberration = true,
                        )
                    },
                    highlight = { Highlight.Default },
                    shadow = { Shadow() },
                    innerShadow = { InnerShadow(radius = 4.dp) },
                    onDrawSurface = { drawRect(Color.White.copy(alpha = 0.9f)) },
                ),
        )
    }
}

// =====================================================================
// 2. 液态玻璃列表卡片（分组容器 + 列表项）
// =====================================================================

/**
 * 一组功能列表的液态玻璃卡片容器。
 *
 * 用法：
 * ```
 * LiquidGlassListCard(backdrop = backdrop) {
 *     LiquidGlassListItem(title = "去除启动/信息流广告") {
 *         LiquidGlassSwitch(checked = ..., onCheckedChange = ..., backdrop = backdrop)
 *     }
 *     LiquidGlassListItem(title = "净化页面") { ... }
 * }
 * ```
 */
@Composable
fun LiquidGlassListCard(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val surfaceColor = if (isLightTheme) {
        Color.White.copy(alpha = 0.35f)
    } else {
        Color(0xFF1C1C1E).copy(alpha = 0.4f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(cornerRadius) },
                effects = {
                    vibrancy()
                    blur(10.dp.toPx())
                    lens(16.dp.toPx(), 16.dp.toPx())
                },
                highlight = { Highlight.Default },
                shadow = { Shadow() },
                onDrawSurface = { drawRect(surfaceColor) },
            ),
        content = content,
    )
}

/**
 * 列表卡片内的一行：标题 + 右侧操作区（通常是 LiquidGlassSwitch）。
 * 行与行之间自动绘制细分割线（除最后一行）。
 *
 * @param showDivider 是否显示底部分割线，默认 true；最后一项可传 false
 */
@Composable
fun LiquidGlassListItem(
    title: String,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    trailingContent: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            trailingContent()
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 20.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            )
        }
    }
}

/**
 * 分组标题（比如"辅助与净化"、"隐私与特权"）。
 */
@Composable
fun LiquidGlassSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

// =====================================================================
// 3. 使用示例（"功能中心"页面）
// =====================================================================

/**
 * 对应你截图中"功能中心"页面的示例实现。
 * 需要把整个页面背景内容用 Modifier.layerBackdrop(backdrop) 标记，
 * 这里假设 backdrop 已经在页面根 Composable 创建并传入。
 */
@Composable
fun FeatureCenterScreenExample(backdrop: Backdrop) {
    var adRemoval by remember { mutableStateOf(true) }
    var purifyPage by remember { mutableStateOf(true) }
    var blockLiveRequest by remember { mutableStateOf(true) }

    var antiRecall by remember { mutableStateOf(true) }
    var flashToPhoto by remember { mutableStateOf(true) }
    var removeScreenshotLimit by remember { mutableStateOf(true) }
    var quietView by remember { mutableStateOf(true) }
    var privateAlbum by remember { mutableStateOf(true) }
    var unlockVip by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "功能中心",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
        )

        // --- 辅助与净化 ---
        Column {
            LiquidGlassSectionTitle("辅助与净化")
            LiquidGlassListCard(backdrop = backdrop) {
                LiquidGlassListItem(title = "去除启动/信息流广告") {
                    LiquidGlassSwitch(
                        checked = adRemoval,
                        onCheckedChange = { adRemoval = it },
                        backdrop = backdrop,
                    )
                }
                LiquidGlassListItem(title = "净化页面") {
                    LiquidGlassSwitch(
                        checked = purifyPage,
                        onCheckedChange = { purifyPage = it },
                        backdrop = backdrop,
                    )
                }
                LiquidGlassListItem(title = "拦截直播请求", showDivider = false) {
                    LiquidGlassSwitch(
                        checked = blockLiveRequest,
                        onCheckedChange = { blockLiveRequest = it },
                        backdrop = backdrop,
                    )
                }
            }
        }

        // --- 隐私与特权 ---
        Column {
            LiquidGlassSectionTitle("隐私与特权")
            LiquidGlassListCard(backdrop = backdrop) {
                LiquidGlassListItem(title = "消息防撤回") {
                    LiquidGlassSwitch(
                        checked = antiRecall,
                        onCheckedChange = { antiRecall = it },
                        backdrop = backdrop,
                    )
                }
                LiquidGlassListItem(title = "闪照转照片") {
                    LiquidGlassSwitch(
                        checked = flashToPhoto,
                        onCheckedChange = { flashToPhoto = it },
                        backdrop = backdrop,
                    )
                }
                LiquidGlassListItem(title = "去除截屏限制") {
                    LiquidGlassSwitch(
                        checked = removeScreenshotLimit,
                        onCheckedChange = { removeScreenshotLimit = it },
                        backdrop = backdrop,
                    )
                }
                LiquidGlassListItem(title = "悄悄查看") {
                    LiquidGlassSwitch(
                        checked = quietView,
                        onCheckedChange = { quietView = it },
                        backdrop = backdrop,
                    )
                }
                LiquidGlassListItem(title = "查看私密相册") {
                    LiquidGlassSwitch(
                        checked = privateAlbum,
                        onCheckedChange = { privateAlbum = it },
                        backdrop = backdrop,
                    )
                }
                LiquidGlassListItem(title = "解锁本地 VIP", showDivider = false) {
                    LiquidGlassSwitch(
                        checked = unlockVip,
                        onCheckedChange = { unlockVip = it },
                        backdrop = backdrop,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}