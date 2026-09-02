package com.fuck.zlx

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class DragMode { SEEK, BRIGHTNESS, VOLUME }

@Composable
fun VideoPlayerScreen(url: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.getActivity() }
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

    // ============ 播放器 ============
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }

    // ============ 状态 ============
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var bufferedPercentage by remember { mutableStateOf(0) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }

    // 手势瞬时状态
    var speedUp by remember { mutableStateOf(false) }
    var seekFrom by remember { mutableStateOf<Long?>(null) }
    var seekTo by remember { mutableStateOf<Long?>(null) }
    var brightnessBase by remember { mutableFloatStateOf(0.5f) }
    var brightness by remember { mutableFloatStateOf(-1f) }
    var volumeBase by remember { mutableFloatStateOf(0.5f) }
    var volume by remember { mutableFloatStateOf(0.5f) }
    var dragMode by remember { mutableStateOf<DragMode?>(null) }
    var fastHint by remember { mutableStateOf<Pair<String, Alignment>?>(null) }

    // 网速（字节/秒），EWMA 平滑
    var netSpeed by remember { mutableStateOf(0.0) }

    val accent = MaterialTheme.colorScheme.primary

    // ============ 屏幕常亮 ============
    DisposableEffect(player) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // ============ 监听播放器 ============
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(state: Boolean) { isPlaying = state }
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    duration = player.duration.coerceAtLeast(0L)
                    netSpeed = 0.0
                }
            }
        }
        val analytics = object : AnalyticsListener {
            override fun onLoadCompleted(
                eventTime: AnalyticsListener.EventTime,
                loadEventInfo: LoadEventInfo,
                mediaLoadData: MediaLoadData
            ) {
                val ms = loadEventInfo.loadDurationMs.coerceAtLeast(1L)
                val bps = loadEventInfo.bytesLoaded.toDouble() * 1000.0 / ms
                netSpeed = if (netSpeed <= 0.0) bps else netSpeed * 0.6 + bps * 0.4
            }
        }
        player.addListener(listener)
        player.addAnalyticsListener(analytics)
        onDispose {
            player.removeListener(listener)
            player.removeAnalyticsListener(analytics)
        }
    }

    // ============ 进度轮询 ============
    LaunchedEffect(player) {
        while (true) {
            if (seekTo == null) currentPosition = player.currentPosition
            bufferedPercentage = player.bufferedPercentage
            delay(300)
        }
    }

    // ============ 控制栏自动隐藏 ============
    LaunchedEffect(showControls, isPlaying, speedUp) {
        if (showControls && isPlaying && !speedUp) {
            delay(4000)
            showControls = false
        }
    }

    // ============ 快进/后退提示自动消失 ============
    LaunchedEffect(fastHint) {
        if (fastHint != null) {
            delay(700)
            fastHint = null
        }
    }

    // ============ 释放资源 ============
    DisposableEffect(Unit) {
        onDispose {
            player.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            applyBrightness(activity, -1f)
        }
    }

    // ============ 返回键 ============
    BackHandler {
        if (isFullscreen) {
            isFullscreen = false
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            onClose()
        }
    }

    // ============ 双击/单击判定记忆 ============
    val tapState = remember { mutableLongStateOf(0L) }

    fun handleTap(x: Float, w: Float) {
        val now = System.currentTimeMillis()
        if (now - tapState.longValue < 300) {
            // 双击
            tapState.longValue = 0L
            when {
                x < w / 3f -> {
                    val t = (player.currentPosition - 10_000L).coerceAtLeast(0L)
                    player.seekTo(t)
                    currentPosition = t
                    fastHint = "« 10秒" to Alignment.CenterStart
                }
                x > 2f * w / 3f -> {
                    val t = (player.currentPosition + 10_000L).coerceAtMost(duration)
                    player.seekTo(t)
                    currentPosition = t
                    fastHint = "10秒 »" to Alignment.CenterEnd
                }
                else -> {
                    if (isPlaying) player.pause() else player.play()
                }
            }
            showControls = false
        } else {
            tapState.longValue = now
            scope.launch {
                delay(300)
                if (tapState.longValue == now) showControls = !showControls
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // 1) 核心 AndroidView
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2) 全屏手势层
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val slop = viewConfiguration.touchSlop
                    val longPressMs = 450L

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val downX = down.position.x
                        val downY = down.position.y

                        // 阶段一：长按检测窗口（450ms 内是否移动/抬起）
                        val outcome: String? = withTimeoutOrNull(longPressMs) {
                            while (true) {
                                val event = awaitPointerEvent()
                                val c = event.changes.first()
                                val dx = c.position.x - downX
                                val dy = c.position.y - downY
                                if (!c.pressed && c.previousPressed) return@withTimeoutOrNull "UP"
                                if (abs(dx) > slop || abs(dy) > slop) {
                                    dragMode = if (abs(dx) > abs(dy)) DragMode.SEEK
                                    else if (downX < w / 2f) DragMode.BRIGHTNESS
                                    else DragMode.VOLUME
                                    return@withTimeoutOrNull "MOVE"
                                }
                            }
                            @Suppress("UNREACHABLE_CODE")
                            null
                        }

                        when (outcome) {
                            null -> {
                                // 长按 → 2 倍速，直到抬手
                                speedUp = true
                                player.playbackParameters = PlaybackParameters(2.0f)
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val c = event.changes.first()
                                    c.consume()
                                    if (!c.pressed && c.previousPressed) break
                                }
                                speedUp = false
                                player.playbackParameters = PlaybackParameters(1.0f)
                            }
                            "UP" -> {
                                handleTap(downX, w)
                            }
                            else -> {
                                // 拖拽：记录基准
                                when (dragMode) {
                                    DragMode.SEEK -> {
                                        seekFrom = currentPosition
                                        seekTo = currentPosition
                                    }
                                    DragMode.BRIGHTNESS -> {
                                        val cur = activity?.window?.attributes?.screenBrightness ?: -1f
                                        brightnessBase = if (cur < 0f) 0.5f else cur
                                        brightness = brightnessBase
                                    }
                                    DragMode.VOLUME -> {
                                        volumeBase =
                                            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                                .toFloat() / maxVolume
                                        volume = volumeBase
                                    }
                                    null -> {}
                                }
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val c = event.changes.first()
                                    if (!c.pressed && c.previousPressed) {
                                        c.consume()
                                        break
                                    }
                                    val dx = c.position.x - downX
                                    val dy = c.position.y - downY
                                    c.consume()
                                    when (dragMode) {
                                        DragMode.SEEK -> {
                                            val from = seekFrom ?: currentPosition
                                            val span = duration.coerceAtLeast(1L)
                                            val ratio = (dx / w).coerceIn(-1.5f, 1.5f)
                                            seekTo = (from + (ratio * span).toLong())
                                                .coerceIn(0L, duration)
                                        }
                                        DragMode.BRIGHTNESS -> {
                                            val delta = (-dy / h).coerceIn(-1f, 1f)
                                            brightness = (brightnessBase + delta).coerceIn(0f, 1f)
                                            applyBrightness(activity, brightness)
                                        }
                                        DragMode.VOLUME -> {
                                            val delta = (-dy / h).coerceIn(-1f, 1f)
                                            volume = (volumeBase + delta).coerceIn(0f, 1f)
                                            val idx = (volume * maxVolume).roundToInt()
                                                .coerceIn(0, maxVolume)
                                            audioManager.setStreamVolume(
                                                AudioManager.STREAM_MUSIC, idx, 0
                                            )
                                        }
                                        null -> {}
                                    }
                                }
                                // 拖拽结束
                                if (dragMode == DragMode.SEEK) {
                                    seekTo?.let {
                                        player.seekTo(it)
                                        currentPosition = it
                                    }
                                    seekFrom = null
                                    seekTo = null
                                }
                                dragMode = null
                            }
                        }
                    }
                }
        )

        // 3) 缓冲 + 网速提示
        if (isBuffering) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    val label = if (netSpeed <= 0.0) "正在连接…" else "正在缓存"
                    Text(
                        text = "$label ${bufferedPercentage.coerceIn(0, 100)}%",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    if (netSpeed > 0.0) {
                        Text(
                            text = formatSpeed(netSpeed),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // 4) 倍速提示
        if (speedUp) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 40.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("▶▶ 2.0X 倍速播放中", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        // 5) 拖拽进度提示
        seekTo?.let { t ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "${formatTime(t)} / ${formatTime(duration)}",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 6) 亮度/音量手势指示
        dragMode?.let { mode ->
            if (mode == DragMode.BRIGHTNESS || mode == DragMode.VOLUME) {
                val ratio = if (mode == DragMode.BRIGHTNESS) brightness else volume
                val title = if (mode == DragMode.BRIGHTNESS) "亮度" else "音量"
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${(ratio * 100).roundToInt()}%",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(120.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.25f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(ratio.coerceIn(0f, 1f))
                                .align(Alignment.BottomCenter)
                                .background(accent)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = title, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }
            }
        }

        // 7) 双击快进/快退提示
        fastHint?.let { (text, align) ->
            Box(
                modifier = Modifier
                    .align(align)
                    .padding(horizontal = 40.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        // 8) 暂停时中央播放按钮
        if (!isBuffering && !isPlaying) {
            IconButton(
                onClick = { player.play() },
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "播放", tint = Color.White, modifier = Modifier.size(44.dp))
            }
        }

        // 9) 控制栏
        if (showControls) {
            // 顶部渐变
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                        )
                    )
            )
            IconButton(
                onClick = {
                    if (isFullscreen) {
                        isFullscreen = false
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    } else onClose()
                },
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(top = 8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White, modifier = Modifier.size(28.dp))
            }

            // 底部渐变 + 控制条
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp)
                    .padding(top = 28.dp, bottom = 8.dp)
            ) {
                ModernSeekBar(
                    position = currentPosition,
                    duration = duration,
                    bufferedPercent = bufferedPercentage,
                    accent = accent,
                    onSeekStart = { seekFrom = currentPosition },
                    onSeek = {
                        seekTo = it
                        currentPosition = it
                    },
                    onSeekEnd = {
                        player.seekTo(it)
                        seekFrom = null
                        seekTo = null
                    }
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { if (isPlaying) player.pause() else player.play() }) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                    Text(formatTime(currentPosition), color = Color.White, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("/", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(formatTime(duration), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    if (netSpeed > 0.0) {
                        Text(
                            formatSpeed(netSpeed),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    IconButton(onClick = {
                        isFullscreen = !isFullscreen
                        activity?.requestedOrientation =
                            if (isFullscreen) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }) {
                        Text(
                            if (isFullscreen) "退出" else "全屏",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ============ 自定义进度条（含已缓存轨） ============
@Composable
private fun ModernSeekBar(
    position: Long,
    duration: Long,
    bufferedPercent: Int,
    accent: Color,
    onSeekStart: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekEnd: (Long) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(duration) {
                val w = size.width.toFloat()
                val safeDur = duration.coerceAtLeast(1L)
                awaitEachGesture {
                    val d = awaitFirstDown(requireUnconsumed = false)
                    d.consume()
                    var ratio = (d.position.x / w).coerceIn(0f, 1f)
                    onSeekStart()
                    onSeek((ratio * safeDur).toLong())
                    while (true) {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.first()
                        if (!c.pressed && c.previousPressed) {
                            onSeekEnd((ratio * safeDur).toLong())
                            break
                        }
                        ratio = (c.position.x / w).coerceIn(0f, 1f)
                        onSeek((ratio * safeDur).toLong())
                        c.consume()
                    }
                }
            }
    ) {
        val trackW = maxWidth
        val posRatio = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
        val bufRatio = (bufferedPercent / 100f).coerceIn(0f, 1f)

        // 底轨
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .align(Alignment.CenterStart)
                .alpha(0.25f)
                .background(Color.White, RoundedCornerShape(2.dp))
        )
        // 缓存轨
        Box(
            modifier = Modifier
                .fillMaxWidth(bufRatio)
                .height(3.dp)
                .align(Alignment.CenterStart)
                .background(Color.White.copy(alpha = 0.45f), RoundedCornerShape(2.dp))
        )
        // 已播放轨
        Box(
            modifier = Modifier
                .fillMaxWidth(posRatio)
                .height(3.dp)
                .align(Alignment.CenterStart)
                .background(accent, RoundedCornerShape(2.dp))
        )
        // 拖拽手柄
        Box(
            modifier = Modifier
                .size(13.dp)
                .align(Alignment.CenterStart)
                .offset {
                    val x = trackW.toPx() * posRatio - 6.dp.toPx()
                    IntOffset(x.roundToInt(), 0)
                }
                .background(Color.White, CircleShape)
        )
    }
}

// ============ 工具 ============
private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0)
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    else
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

private fun formatSpeed(bytesPerSec: Double): String {
    val bps = bytesPerSec.coerceAtLeast(0.0)
    return if (bps >= 1_048_576.0)
        String.format(Locale.getDefault(), "%.1f MB/s", bps / 1_048_576.0)
    else
        String.format(Locale.getDefault(), "%.0f KB/s", bps / 1024.0)
}

private fun applyBrightness(activity: Activity?, value: Float) {
    val a = activity ?: return
    val attrs = a.window.attributes
    attrs.screenBrightness = value // -1 表示交回系统
    a.window.attributes = attrs
}

private fun Context.getActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.getActivity()
    else -> null
}
