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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.RectangleShape
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
    val isComplete: Boolean,
    val videoSize: String = "",
    val videoDuration: String = "",
    val title: String = ""
)

/**
 * 全屏子页面状态：帮助 / 观看历史 / 下载管理打开时置 true，
 * MainScreen 据此把底部导航栏滑出屏幕，子页获得完整纵向空间。
 */
object SubPageState {
    var covering by mutableStateOf(false)
}
class SniffJsInterface(
    private val onAdd: (String, String, String, Boolean, String) -> Unit,
    private val onUpdate: (String, String) -> Unit
) {
    @JavascriptInterface
    fun onAddItem(id: String, url: String, thumbUrl: String, isComplete: Boolean, title: String) {
        Handler(Looper.getMainLooper()).post { onAdd(id, url, thumbUrl, isComplete, title) }
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
                        // 激活门槛：未激活/已到期先进入激活界面；运行中每30秒复查一次
                        var activated by remember {
                            mutableStateOf(ActivationManager.checkValid(this@MainActivity))
                        }
                        LaunchedEffect(activated) {
                            ActivationManager.refreshNetworkTime(this@MainActivity)
                            while (activated) {
                                kotlinx.coroutines.delay(30_000)
                                if (!ActivationManager.checkValid(this@MainActivity)) {
                                    activated = false
                                }
                            }
                        }
                        if (activated) {
                            MainScreen()
                        } else {
                            ActivationScreen(onActivated = { activated = true })
                        }
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
    var sniffedResources by remember { mutableStateOf(listOf<SniffedItem>()) }
    val sniffedIds = remember { mutableSetOf<String>() }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var playingVideoUrl by remember { mutableStateOf<String?>(null) }

    // 当前页面路由
    var currentRoute by remember { mutableStateOf(NavRoute.Home) }

    // 资源列表的排序与视图状态
    var isDescending by remember { mutableStateOf(false) }
    var isGridView by remember { mutableStateOf(false) }
    // 滚动状态也提升到 MainScreen：切走再切回资源页时保留列表位置
    val resourcesListState = rememberLazyListState()
    val resourcesGridState = rememberLazyGridState()
    val context = LocalContext.current
    val jsInterface = remember {
        SniffJsInterface(
            onAdd = { id, url, thumbUrl, isComplete, title ->
                if (ActivationManager.checkValid(context) && !sniffedIds.contains(id)) {
                    sniffedIds.add(id)
                    sniffedResources = sniffedResources + SniffedItem(id, url, thumbUrl, isComplete, title = title)
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

    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    // 底栏样式开关（设置页可改，切回标签页时生效）
    val isFloating = prefs.getBoolean("floating_bottom_bar", true)
    val isLiquidGlass = prefs.getBoolean("liquid_glass", true)

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

    // ======================= 网页生命周期优化 =======================
    // 网页不在眼前（切到资源/设置页、或正在播放视频）时彻底停下它：
    // INVISIBLE 跳过绘制 + onPause/pauseTimers 停渲染和 JS 动画定时器。
    // 否则网页里的动画广告会带着整个应用满帧率持续重绘（实测约120fps），又卡又耗电。
    val isPlayingVideo = playingVideoUrl != null
    LaunchedEffect(webViewInstance, currentRoute, isPlayingVideo) {
        val webView = webViewInstance ?: return@LaunchedEffect
        if (currentRoute == NavRoute.Home && !isPlayingVideo) {
            webView.visibility = android.view.View.VISIBLE
            webView.onResume()
            webView.resumeTimers()
        } else {
            webView.visibility = android.view.View.INVISIBLE
            webView.onPause()
            webView.pauseTimers()
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

    val bottomBarOffset by animateDpAsState(
        targetValue = if (actualVisibility && !SubPageState.covering) 0.dp else 130.dp,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "bottomBarOffset"
    )

    // 最外层兜底背景：保证任何页面/底栏下方的留白区都显示主题背景色，不露窗口纯白
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // 底栏（复刻 MyDia MiuixNavBar）：悬浮胶囊 / 贴底通栏，毛玻璃可关（纯色省电）
            Box(
                modifier = Modifier
                    .navigationBarsPadding()
                    .then(
                        if (isFloating) Modifier.padding(horizontal = 19.dp).padding(bottom = 12.dp)
                        else Modifier
                    )
                    .offset(y = bottomBarOffset)
            ) {
                MiuixNavBar(
                    items = NavRoute.values().map { it.title to it.selectedIcon },
                    selected = NavRoute.values().indexOf(currentRoute),
                    onSelect = { currentRoute = NavRoute.values()[it] },
                    backdrop = if (isLiquidGlass) backdrop else null,
                    barShape = if (isFloating) Capsule()
                    else RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                )
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
                    .padding(bottom = 11.dp)
            ) {
                HomeWebViewScreen(onWebViewCreated = onWebViewCreated, jsInterface = jsInterface)
            }

            // [第 2 层] 资源/设置页面：整体挂 layerBackdrop 登记为底栏毛玻璃的捕获源
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
            ) {
                if (currentRoute == NavRoute.Resources) {
                    // 背景先于 padding 绘制，铺满整屏（含底栏预留区），子页打开时不留底部空带
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .navigationBarsPadding()
                            .padding(bottom = if (SubPageState.covering) 0.dp else 31.dp)
                    ) {
                        ResourcesScreen(
                            sniffedResources = sniffedResources,
                            onClearClick = {
                                sniffedResources = emptyList()
                                sniffedIds.clear()
                            },
                            onPlayVideo = { playingVideoUrl = it },
                            isDescending = isDescending,
                            isGridView = isGridView,
                            onDescendingChange = { isDescending = it },
                            onGridViewChange = { isGridView = it },
                            listState = resourcesListState,
                            gridState = resourcesGridState
                        )
                    }
                }

                if (currentRoute == NavRoute.Settings) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .navigationBarsPadding()
                            .padding(bottom = if (SubPageState.covering) 0.dp else 31.dp)
                    ) {
                        SettingsScreen()
                    }
                }
            } // end layerBackdrop 捕获层
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