package com.fuck.zlx

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// ======== 导入 Kyant Backdrop 真正的液态玻璃引擎 ========
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule

// ======== 修复闪退：提供 IndicationNodeFactory 兼容旧版 LocalIndication ========
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode

// ======================= 1. 路由和数据模型 =======================
enum class NavRoute(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    Home("首页", Icons.Filled.Home, Icons.Outlined.Home),
    Resources("资源", Icons.Filled.List, Icons.Outlined.List),
    Settings("设置", Icons.Filled.Settings, Icons.Outlined.Settings)
}

data class SniffedItem(
    val id: String,
    val m3u8Url: String,
    val thumbUrl: String,
    val isComplete: Boolean
)

class SniffJsInterface(
    private val onAdd: (String, String, String, Boolean) -> Unit,
    private val onUpdate: (String, String) -> Unit
) {
    @JavascriptInterface
    fun onAddItem(id: String, url: String, thumbUrl: String, isComplete: Boolean) {
        Handler(Looper.getMainLooper()).post { onAdd(id, url, thumbUrl, isComplete) }
    }

    @JavascriptInterface
    fun onUpdateItem(id: String, realUrl: String) {
        Handler(Looper.getMainLooper()).post { onUpdate(id, realUrl) }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        super.onCreate(savedInstanceState)
        setContent {
            val isDarkTheme = isSystemInDarkTheme()
            val blueColorScheme = if (isDarkTheme) {
                darkColorScheme(
                    primary = Color(0xFF90CAF9),
                    onPrimary = Color(0xFF000000),
                    primaryContainer = Color(0xFF1976D2),
                    onPrimaryContainer = Color(0xFFFFFFFF),
                    secondary = Color(0xFF64B5F6),
                    onSecondary = Color(0xFF000000),
                    secondaryContainer = Color(0xFF1565C0),
                    onSecondaryContainer = Color(0xFFFFFFFF),
                    tertiary = Color(0xFF42A5F5),
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E)
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFF2196F3),
                    onPrimary = Color(0xFFFFFFFF),
                    primaryContainer = Color(0xFFBBDEFB),
                    onPrimaryContainer = Color(0xFF0D47A1),
                    secondary = Color(0xFF42A5F5),
                    onSecondary = Color(0xFFFFFFFF),
                    secondaryContainer = Color(0xFFE3F2FD),
                    onSecondaryContainer = Color(0xFF0D47A1),
                    tertiary = Color(0xFF64B5F6),
                    background = Color(0xFFFAFAFA),
                    surface = Color(0xFFFFFFFF)
                )
            }
            MaterialTheme(
                colorScheme = blueColorScheme
            ) {
                // 【修复闪退根因】：覆盖 LocalIndication，提供一个空的 IndicationNodeFactory。
                // 项目中 com.google.android.material:material 间接拉入了旧的
                // androidx.compose.material.ripple.PlatformRipple，它不是 IndicationNodeFactory，
                // 导致所有不带 indication 参数的 clickable/combinedClickable 在 attach 时崩溃。
                // 这里用 CompositionLocalProvider 覆盖为安全的 NoIndication，彻底解决闪退。
                CompositionLocalProvider(LocalIndication provides NoRippleIndication) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MainScreen()
                    }
                }
            }
        }
    }
}

/**
 * 空实现的 IndicationNodeFactory，替代不兼容的 PlatformRipple。
 * 不绘制任何水波纹效果，但保证 clickable 不崩溃。
 */
private object NoRippleIndication : IndicationNodeFactory {
    // 【修复闪退根因】：create() 必须每次返回一个全新的节点实例！
    // 之前用 object 单例 NoRippleNode，导致资源页里卡片(combinedClickable)
    // 和封面图(clickable)等多个 clickable 共用同一个被 delegate 的节点，
    // 点击时第二个 clickable 再 delegate 就抛
    // "Cannot delegate to an already delegated node" 而闪退。
    override fun create(interactionSource: InteractionSource): DelegatableNode = NoRippleNode()
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)

    private class NoRippleNode : Modifier.Node(), DrawModifierNode {
        override fun ContentDrawScope.draw() {
            drawContent()
        }
    }
}

@Composable
fun MainScreen() {
    var currentRoute by remember { mutableStateOf(NavRoute.Home) }

    var sniffedResources by remember { mutableStateOf(listOf<SniffedItem>()) }
    val sniffedIds = remember { mutableSetOf<String>() }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var playingVideoUrl by remember { mutableStateOf<String?>(null) }

    val jsInterface = remember {
        SniffJsInterface(
            onAdd = { id, url, thumbUrl, isComplete ->
                if (!sniffedIds.contains(id)) {
                    sniffedIds.add(id)
                    sniffedResources = sniffedResources + SniffedItem(id, url, thumbUrl, isComplete)
                }
            },
            onUpdate = { id, realUrl ->
                sniffedResources = sniffedResources.map {
                    if (it.id == id) it.copy(m3u8Url = realUrl, isComplete = true) else it
                }
            }
        )
    }

    val onWebViewCreated: (WebView) -> Unit = { webView ->
        webViewInstance = webView
    }

    val context = LocalContext.current
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // ======================= 返回键处理 =======================
    var backPressedTime by remember { mutableLongStateOf(0L) }

    BackHandler(enabled = true) {
        if (currentRoute != NavRoute.Home) {
            // 不在首页，切回首页
            currentRoute = NavRoute.Home
        } else {
            // 在首页
            val webView = webViewInstance
            if (webView != null && webView.canGoBack()) {
                // WebView 有历史记录，返回上一个网页
                webView.goBack()
            } else {
                // 没有历史记录，双击退出
                val now = System.currentTimeMillis()
                if (now - backPressedTime < 2000) {
                    (context as? ComponentActivity)?.finish()
                } else {
                    backPressedTime = now
                    Toast.makeText(context, "再按一次退出软件", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    val isFloating = prefs.getBoolean("floating_bottom_bar", false)
    val isLiquidGlass = prefs.getBoolean("liquid_glass", false)
    val isSmoothCorners = prefs.getBoolean("smooth_corners", false)

    var isBottomBarVisible by remember { mutableStateOf(true) }
    var lastScrollTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(currentRoute) {
        isBottomBarVisible = true
    }

    LaunchedEffect(lastScrollTime) {
        if (lastScrollTime > 0L && currentRoute == NavRoute.Home) {
            delay(3000)
            isBottomBarVisible = false
        }
    }

    LaunchedEffect(webViewInstance) {
        webViewInstance?.let { webView ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                webView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                    if (currentRoute == NavRoute.Home) {
                        val deltaY = scrollY - oldScrollY
                        if (deltaY > 15) {
                            isBottomBarVisible = false
                        } else if (deltaY < -15) {
                            isBottomBarVisible = true
                        }
                        lastScrollTime = System.currentTimeMillis()
                    }
                }
            }
        }
    }

    val actualVisibility = if (currentRoute == NavRoute.Home) isBottomBarVisible else true
    val isDarkTheme = isSystemInDarkTheme()

    // ======================= Kyant Backdrop 核心配置 =======================
    val backdrop = rememberLayerBackdrop()

    val bottomBarPadding = if (isFloating || isLiquidGlass) {
        Modifier.padding(horizontal = 16.dp, vertical = 16.dp) 
    } else {
        Modifier
    }
    
    val cornerRadius = if (isSmoothCorners) 28.dp else if (isFloating) 16.dp else 0.dp
    val bottomBarShape = RoundedCornerShape(cornerRadius)

    val bottomBarOffset by animateDpAsState(
        targetValue = if (actualVisibility) 0.dp else 130.dp,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "bottomBarOffset"
    )

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // 底栏容器
            Box(
                modifier = Modifier
                    .navigationBarsPadding()
                    .then(bottomBarPadding)
                    .fillMaxWidth()
                    .height(72.dp)
                    .offset(y = bottomBarOffset),
                contentAlignment = Alignment.Center
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val tabCount = NavRoute.values().size
                    val density = LocalDensity.current
                    val tabWidthPx = with(density) { maxWidth.toPx() } / tabCount
                    val tabWidth = maxWidth / tabCount
                    val scope = rememberCoroutineScope()

                    // ================= 核心参数配置 =================
                    val selectedIndex = NavRoute.values().indexOf(currentRoute)
                    val position = remember { Animatable(selectedIndex.toFloat()) }
                    
                    var isPressed by remember { mutableStateOf(false) }
                    var dragVelocity by remember { mutableStateOf(0f) }

                    // 同步外部路由变化
                    LaunchedEffect(selectedIndex) {
                        if (!isPressed) {
                            position.animateTo(
                                selectedIndex.toFloat(),
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            )
                        }
                    }

                    // 🍎 物理形变动画：加大 jellyScale 让按下时果冻膨胀更明显 🍎
                    val jellyScale by animateFloatAsState(
                        targetValue = if (isPressed) 1.22f else 1f, // 从 1.15 提升到 1.22，充气感更强！
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        label = "jellyScale"
                    )
                    // 拖拽时的形变拉伸
                    val stretchAmount by animateFloatAsState(
                        targetValue = if (isPressed) (abs(dragVelocity) * 0.0015f).coerceIn(0f, 0.25f) else 0f,
                        animationSpec = spring(stiffness = Spring.StiffnessHigh),
                        label = "stretchAmount"
                    )
                    val stretchDirection = if (dragVelocity >= 0f) 1f else -1f
                    
                    // 恢复全宽，不需要再减去圆形大小
                    val sliderOffsetPx = position.value * tabWidthPx

                    // ================= 开始三明治分层绘制 =================

                    // [图层 1]：最底部的整个底栏背景
                    if (isLiquidGlass) {
                        val containerColor = if (isDarkTheme) Color(0xFF121212).copy(alpha = 0.4f) else Color(0xFFFAFAFA).copy(alpha = 0.4f)
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
                                    onDrawSurface = { drawRect(containerColor) }
                                )
                        )
                    } else if (isFloating) {
                        // 悬浮底栏：不透明白色背景 + 圆角 + 阴影
                        val containerColor = if (isDarkTheme) Color(0xFF1E1E1E) else Color(0xFFFFFFFF)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(bottomBarShape)
                                .background(containerColor)
                        )
                    } else {
                        // 普通底栏：不透明白色背景
                        val containerColor = if (isDarkTheme) Color(0xFF1E1E1E) else Color(0xFFFFFFFF)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(containerColor)
                        )
                    }

                    // [图层 2]：液态玻璃滑块的视觉层（藏在图标和文字后面，不会遮挡文字）
                    if (isLiquidGlass) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset { IntOffset(sliderOffsetPx.roundToInt(), 0) }
                                .width(tabWidth)       // 🍎 恢复占据整个 Tab 宽度
                                .fillMaxHeight()
                                .padding(horizontal = 14.dp, vertical = 8.dp) // 🍎 内部留白，形成优雅的胶囊体
                                .graphicsLayer {
                                    scaleX = jellyScale * (1f + stretchAmount * stretchDirection * 0.3f)
                                    scaleY = jellyScale * (1f - stretchAmount * 0.15f)
                                }
                                .drawBackdrop(
                                    backdrop = backdrop,
                                    shape = { Capsule() }, // 胶囊形状
                                    effects = {
                                        val pressProgress = if (isPressed) 1f else 0f
                                        lens(
                                            10.dp.toPx() + 6.dp.toPx() * pressProgress,
                                            14.dp.toPx() + 8.dp.toPx() * pressProgress,
                                            chromaticAberration = true
                                        )
                                    },
                                    highlight = { Highlight.Default },
                                    shadow = { Shadow() },
                                    innerShadow = { InnerShadow(radius = 8.dp) },
                                    onDrawSurface = {
                                        drawRect(
                                            if (isDarkTheme) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f)
                                        )
                                    }
                                )
                        )
                    } else {
                        // 经典样式的胶囊背景
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset { IntOffset(sliderOffsetPx.roundToInt(), 0) }
                                .width(tabWidth)
                                .fillMaxHeight()
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape) // Compose中长方形用CircleShape就是完美胶囊
                        )
                    }

                    // [图层 3]：图标与文字展示（渲染在滑块上方，保证百分百清晰不被模糊）
                    Row(modifier = Modifier.fillMaxSize()) {
                        NavRoute.values().forEach { route ->
                            val isSelected = currentRoute == route
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    // 给图标加上直接点击检测，提升响应速度
                                    .pointerInput(route) {
                                        detectTapGestures { currentRoute = route }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                val contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally, 
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) route.selectedIcon else route.unselectedIcon,
                                        contentDescription = route.title,
                                        tint = contentColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = route.title,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = contentColor
                                    )
                                }
                            }
                        }
                    }

                    // [图层 4]：纯隐形的拖拽事件拦截层（精准盖在当前滑块的上方接收左右拖拽手势）
                    if (isLiquidGlass) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset { IntOffset(sliderOffsetPx.roundToInt(), 0) }
                                .width(tabWidth)       // 🍎 拦截层同样占满整个 Tab 宽
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
                                                        stiffness = Spring.StiffnessLow
                                                    )
                                                )
                                            }
                                            currentRoute = NavRoute.values()[target]
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
                                                    (position.value + delta).coerceIn(0f, (tabCount - 1).toFloat())
                                                )
                                            }
                                        }
                                    )
                                }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        // 外层套一个普通的 Box 作为容器
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {

            // [第 1 层] WebView 独立放在最底层，绝对不参与 layerBackdrop 的实时捕获
            val homeOffset = if (currentRoute == NavRoute.Home) 0.dp else 10000.dp
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(x = homeOffset)
                    .navigationBarsPadding()
                    .padding(bottom = 21.dp)
            ) {
                HomeWebViewScreen(onWebViewCreated = onWebViewCreated, jsInterface = jsInterface)
            }

            // [第 2 层] Resources 和 Settings 页面脱离 layerBackdrop，避免 Scaffold + Insets 闪退
            if (currentRoute == NavRoute.Resources) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    ResourcesScreen(
                        sniffedResources = sniffedResources,
                        onClearClick = {
                            sniffedResources = emptyList()
                            sniffedIds.clear()
                        },
                        onPlayVideo = { playingVideoUrl = it }
                    )
                }
            }

            if (currentRoute == NavRoute.Settings) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    SettingsScreen()
                }
            }
        }
    } // end Scaffold content

    // [顶层] 播放器全屏遮罩：渲染在 Scaffold 之外，盖住含底栏在内的一切元素，
    // 且直接位于 Activity 窗口，因此亮度手势可生效。
    playingVideoUrl?.let { url ->
        Box(modifier = Modifier.fillMaxSize()) {
            VideoPlayerScreen(
                url = url,
                onClose = { playingVideoUrl = null }
            )
        }
    }
    } // end outer Box
}