package com.fuck.zlx

import com.fuck.zlx.R
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.documentfile.provider.DocumentFile
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
fun DownloadScreen(onBack: () -> Unit, onPlayVideo: (String) -> Unit = {}) {
    val context = LocalContext.current
    val tasks by DownloadManager.tasks.collectAsState()

    var showMenuFor by remember { mutableStateOf<DownloadTask?>(null) }
    var showRenameDialog by remember { mutableStateOf<DownloadTask?>(null) }
    var showDetailsFor by remember { mutableStateOf<DownloadTask?>(null) }
    var showDeleteConfirmFor by remember { mutableStateOf<DownloadTask?>(null) }
    var newName by remember { mutableStateOf("") }
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    val downloadingTasks by remember(tasks) { derivedStateOf { tasks.filter { it.status != DownloadStatus.SUCCESS } } }
    val completedTasks by remember(tasks) { derivedStateOf { tasks.filter { it.status == DownloadStatus.SUCCESS } } }

    // 已完成列表的排序方式
    var completedSortMode by remember { mutableStateOf(CompletedSortMode.BY_TIME_DESC) }
    val sortedCompletedTasks by remember(completedTasks, completedSortMode) {
        derivedStateOf {
            when (completedSortMode) {
                CompletedSortMode.BY_TIME_DESC -> completedTasks.sortedByDescending { it.completedAt }
                CompletedSortMode.BY_TIME_ASC -> completedTasks.sortedBy { it.completedAt }
                CompletedSortMode.BY_NAME_ASC -> completedTasks.sortedBy { it.fileName }
                CompletedSortMode.BY_NAME_DESC -> completedTasks.sortedByDescending { it.fileName }
            }
        }
    }
    var showSortMenu by remember { mutableStateOf(false) }

    var currentTab by remember { mutableStateOf(0) }
    val tabs = listOf("正在下载", "已完成")
    val tabIcons = listOf(Icons.Default.Refresh, Icons.Default.CheckCircle)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("下载管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") }
                }
            )
        }
    ) { padding ->
        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Info, null, modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    Spacer(Modifier.height(12.dp))
                    Text("暂无下载任务", color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                // ==================== Tab 切换栏 ====================
                TabRow(
                    selectedTabIndex = currentTab,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = currentTab == index,
                            onClick = { currentTab = index },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(tabIcons[index], null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(title)
                                    Spacer(Modifier.width(6.dp))
                                    Surface(
                                        color = if (currentTab == index)
                                            MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            text = (if (index == 0) downloadingTasks.size else completedTasks.size).toString(),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (currentTab == index)
                                                MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        )
                    }
                }

                // ==================== 内容区（带过渡动画）====================
                AnimatedContent(
                    targetState = currentTab,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        val direction = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(tween(300)) { fullWidth -> direction * fullWidth } +
                                fadeIn(tween(300))) togetherWith
                                (slideOutHorizontally(tween(300)) { fullWidth -> -direction * fullWidth } +
                                        fadeOut(tween(300)))
                    },
                    label = "tab_transition"
                ) { tab ->
                    when (tab) {
                        0 -> {
                            if (downloadingTasks.isEmpty()) {
                                EmptyScreenHint("暂无下载中的任务", Icons.Default.Refresh)
                            } else {
                                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                                    items(downloadingTasks, key = { it.id }) { task ->
                                        DownloadTaskCard(task, context, onClick = {}, onLongClick = { showMenuFor = task })
                                    }
                                }
                            }
                        }
                        1 -> {
                            if (completedTasks.isEmpty()) {
                                EmptyScreenHint("暂无已完成的任务", Icons.Default.CheckCircle)
                            } else {
                                Column(Modifier.fillMaxSize()) {
                                    // ==================== 排序按钮栏 ====================
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "共${completedTasks.size}个视频",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                        Spacer(Modifier.weight(1f))
                                        Box {
                                            TextButton(
                                                onClick = { showSortMenu = true },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Sort,
                                                    contentDescription = "排序",
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    text = completedSortMode.label,
                                                    style = MaterialTheme.typography.labelMedium
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = showSortMenu,
                                                onDismissRequest = { showSortMenu = false }
                                            ) {
                                                CompletedSortMode.values().forEach { mode ->
                                                    DropdownMenuItem(
                                                        text = {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Text(mode.label)
                                                                if (mode == completedSortMode) {
                                                                    Spacer(Modifier.width(8.dp))
                                                                    Icon(
                                                                        Icons.Default.Check,
                                                                        contentDescription = null,
                                                                        modifier = Modifier.size(16.dp),
                                                                        tint = MaterialTheme.colorScheme.primary
                                                                    )
                                                                }
                                                            }
                                                        },
                                                        onClick = {
                                                            completedSortMode = mode
                                                            showSortMenu = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                                        items(sortedCompletedTasks, key = { it.id }) { task ->
                                            DownloadTaskCard(task, context,
                                                onClick = {
                                                    var exists = false
                                                    try {
                                                        exists = if (task.outputPath.startsWith("content://")) {
                                                            DocumentFile.fromSingleUri(context, Uri.parse(task.outputPath))?.exists() == true
                                                        } else File(task.outputPath).exists()
                                                    } catch (_: Exception) {}
                                                    if (exists) {
                                                        val playUri = if (task.outputPath.startsWith("content://")) {
                                                            task.outputPath
                                                        } else {
                                                            try {
                                                                androidx.core.content.FileProvider.getUriForFile(
                                                                    context,
                                                                    "${context.packageName}.fileprovider",
                                                                    File(task.outputPath)
                                                                ).toString()
                                                            } catch (_: Exception) {
                                                                "file://${task.outputPath}"
                                                            }
                                                        }
                                                        onPlayVideo(playUri)
                                                    } else {
                                                        Toast.makeText(context, "视频文件已在外部被删除", Toast.LENGTH_SHORT).show()
                                                        DownloadManager.deleteTask(context, task.id)
                                                    }
                                                },
                                                onLongClick = { showMenuFor = task }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ==================== 长按弹出菜单（Dialog 实现，避免 ModalBottomSheet 闪退）====================
    showMenuFor?.let { task ->
        Dialog(
            onDismissRequest = { showMenuFor = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showMenuFor = null },
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { },
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    Column(Modifier.padding(bottom = 24.dp)) {
                        Box(
                            modifier = Modifier
                                .padding(vertical = 12.dp)
                                .align(Alignment.CenterHorizontally)
                                .width(36.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        )
                        Text(
                            text = "${task.fileName}.mp4",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        HorizontalDivider()

                        if (task.status == DownloadStatus.SUCCESS) {
                            MenuItemRow(Icons.Default.PlayArrow, "打开") { playVideo(context, task.outputPath, false); showMenuFor = null }
                            MenuItemRow(Icons.Default.ExitToApp, "用其他应用打开") { playVideo(context, task.outputPath, true); showMenuFor = null }
                            MenuItemRow(Icons.Default.Send, "移动 (导出/分享)") { shareOrMoveVideo(context, task.outputPath); showMenuFor = null }
                        }
                        MenuItemRow(Icons.Default.Edit, "重命名") { showRenameDialog = task; showMenuFor = null }
                        MenuItemRow(Icons.Default.Share, "复制链接") {
                            clipboardManager.setPrimaryClip(ClipData.newPlainText("m3u8_url", task.url))
                            Toast.makeText(context, "链接已复制", Toast.LENGTH_SHORT).show()
                            showMenuFor = null
                        }
                        MenuItemRow(Icons.Default.Refresh, "重新下载") {
                            DownloadManager.reDownload(context, task)
                            Toast.makeText(context, "已加入重新下载队列", Toast.LENGTH_SHORT).show()
                            showMenuFor = null
                        }
                        if (task.status == DownloadStatus.SUCCESS) {
                            MenuItemRow(Icons.Default.Info, "详细信息") { showDetailsFor = task; showMenuFor = null }
                        }
                        if (task.status == DownloadStatus.DOWNLOADING) {
                            MenuItemRow(Icons.Default.Close, "停止下载") { DownloadManager.stopTask(task.id); showMenuFor = null }
                        }
                        HorizontalDivider()
                        MenuItemRow(Icons.Default.Delete, "删除", MaterialTheme.colorScheme.error) {
                            showDeleteConfirmFor = task; showMenuFor = null
                        }
                    }
                }
            }
        }
    }

    // ==================== 删除二次确认 ====================
    showDeleteConfirmFor?.let { task ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmFor = null },
            title = { Text("确认删除视频") },
            text = { Text("确定要删除\"${task.fileName}\"吗？\n删除后本地文件将不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    DownloadManager.deleteTask(context, task.id)
                    Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                    showDeleteConfirmFor = null
                }) { Text("坚决删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirmFor = null }) { Text("取消") } }
        )
    }

    // ==================== 重命名对话框 ====================
    showRenameDialog?.let { task ->
        LaunchedEffect(task) { newName = task.fileName }
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("重命名视频") },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it }) },
            confirmButton = {
                TextButton(onClick = {
                    DownloadManager.renameTask(context, task.id, newName)
                    showRenameDialog = null
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = null }) { Text("取消") } }
        )
    }
    // ==================== 详细信息对话框 ====================
    showDetailsFor?.let { task ->
        var detailText by remember(task.id) { mutableStateOf("加载中...") }
        LaunchedEffect(task) {
            withContext(Dispatchers.IO) {
                detailText = getFileDetails(context, task)
            }
        }
        AlertDialog(
            onDismissRequest = { showDetailsFor = null },
            title = { Text("详细信息") },
            text = {
                Column {
                    val parts = detailText.split("\n\n")
                    parts.forEachIndexed { index, part ->
                        if (part.startsWith("文件路径：")) {
                            Text(
                                text = part,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        } else {
                            Text(
                                text = part,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (index < parts.lastIndex) Spacer(Modifier.height(8.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDetailsFor = null }) { Text("我知道了") } }
        )
    }
}

// ======================= 辅助组件 =======================

// 已完成列表的排序方式
enum class CompletedSortMode(val label: String) {
    BY_TIME_DESC("时间↓"),
    BY_TIME_ASC("时间↑"),
    BY_NAME_ASC("名称A-Z"),
    BY_NAME_DESC("名称Z-A")
}

@Composable
private fun EmptyScreenHint(text: String, icon: ImageVector) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            Spacer(Modifier.height(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun DownloadTaskCard(
    task: DownloadTask,
    context: Context,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // 【修复闪退】：为每个 Card 实例提供独立的 MutableInteractionSource。
    // combinedClickable 在 AnimatedContent 内部切换时，如果不显式提供
    // interactionSource，Compose 的 Node API 会因节点重复委托而抛出
    // "Cannot delegate to an already delegated node" 异常导致闪退。
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        modifier = Modifier
            .combinedClickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material.ripple.rememberRipple(),
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.padding(8.dp).height(84.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.fillMaxHeight().aspectRatio(16f / 9f)) {
                AsyncImage(
                    model = task.thumbUrl.ifEmpty { R.drawable.ic_launcher_foreground },
                    contentDescription = "封面",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp))
                )
                if (task.status == DownloadStatus.DOWNLOADING) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) {
                Text(
                    text = "${task.fileName}.mp4",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (task.status == DownloadStatus.SUCCESS) {
                    var fileInfo by remember(task.outputPath) { mutableStateOf("解析中...") }
                    LaunchedEffect(task.outputPath) {
                        withContext(Dispatchers.IO) {
                            val duration = getVideoDuration(context, task.outputPath)
                            val size = getFileSizeStr(context, task.outputPath)
                            val date = getFileDate(context, task.outputPath)
                            fileInfo = "$duration  |  $size  |  $date"
                        }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = fileInfo,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            maxLines = 1
                        )
                    }
                    // 显示下载完成时间
                    if (task.completedAt > 0L) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.DownloadDone,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "完成于 ${formatCompletedTime(task.completedAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1
                            )
                        }
                    }
                } else {
                    val statusColor = when (task.status) {
                        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                        DownloadStatus.CANCELED -> MaterialTheme.colorScheme.outline
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Text(
                        text = task.progress,
                        color = statusColor,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun MenuItemRow(icon: ImageVector, text: String, color: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        ).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color)
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}
// ======================= 核心业务方法 =======================
private fun getVideoDuration(context: Context, path: String): String {
    return try {
        val retriever = MediaMetadataRetriever()
        if (path.startsWith("content://")) {
            retriever.setDataSource(context, Uri.parse(path))
        } else {
            val file = File(path)
            if (!file.exists() || file.length() == 0L) {
                retriever.release()
                return "未知时长"
            }
            retriever.setDataSource(path)
        }
        val timeMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        retriever.release()

        if (timeMs == 0L) return "未知时长"
        val totalSeconds = timeMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
        else String.format("%02d:%02d", minutes, seconds)
    } catch (_: Exception) {
        "未知时长"
    }
}

// 提取文件大小 (MB转换)
private fun getFileSizeStr(context: Context, path: String): String {
    return try {
        var size = 0L
        if (path.startsWith("content://")) {
            val uri = Uri.parse(path)
            val doc = DocumentFile.fromSingleUri(context, uri)
            size = doc?.length() ?: 0L
        } else {
            size = File(path).length()
        }
        Formatter.formatFileSize(context, size)
    } catch (e: Exception) {
        "0 MB"
    }
}

// 提取文件日期
private fun getFileDate(context: Context, path: String): String {
    return try {
        var lastModified = 0L
        if (path.startsWith("content://")) {
            val uri = Uri.parse(path)
            val doc = DocumentFile.fromSingleUri(context, uri)
            lastModified = doc?.lastModified() ?: 0L
        } else {
            lastModified = File(path).lastModified()
        }
        if (lastModified == 0L) lastModified = System.currentTimeMillis()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sdf.format(Date(lastModified))
    } catch (e: Exception) {
        "未知日期"
    }
}

// 格式化下载完成时间
private fun formatCompletedTime(timestamp: Long): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        sdf.format(Date(timestamp))
    } catch (e: Exception) {
        "未知时间"
    }
}

// 核心播放/外调逻辑
// 核心播放/外调逻辑
private fun playVideo(context: Context, path: String, useChooser: Boolean) {
    try {
        val uri = if (path.startsWith("content://")) {
            Uri.parse(path)
        } else {
            // 修复：Android 7+ 不允许通过 Intent 传递 file:// URI，必须使用 FileProvider
            val file = File(path)
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (useChooser) {
            val chooser = Intent.createChooser(intent, "选择播放器").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(chooser)
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    } catch (e: Exception) {
        Toast.makeText(context, "无法打开，请检查是否安装了视频播放器", Toast.LENGTH_SHORT).show()
    }
}

// 移动/分享逻辑
private fun shareOrMoveVideo(context: Context, path: String) {
    try {
        val uri = if (path.startsWith("content://")) {
            Uri.parse(path)
        } else {
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                File(path)
            )
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "移动或分享到").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "操作失败", Toast.LENGTH_SHORT).show()
    }
}

// 获取文件大小与详情
private fun getFileDetails(context: Context, task: DownloadTask): String {
    val path = task.outputPath
    val sb = StringBuilder()

    // 文件名称
    val displayName = task.fileName.ifBlank { "未知名称" }
    sb.append("文件名称：$displayName.mp4\n\n")

    // 视频时长
    sb.append("视频时长：${getVideoDuration(context, path)}\n\n")

    // 文件大小
    sb.append("文件大小：${getFileSizeStr(context, path)}\n\n")

    // 下载完成时间
    if (task.completedAt > 0L) {
        sb.append("下载时间：${formatCompletedTime(task.completedAt)}\n\n")
    } else {
        sb.append("下载时间：未知\n\n")
    }

    // 文件路径：将 content:// SAF URI 转为人类可读路径
    val displayPath = readablePath(context, path)
    sb.append("文件路径：\n$displayPath")

    return sb.toString()
}

/**
 * 将 content:// SAF URI 或编码路径转为人类可读的存储路径。
 */
private fun readablePath(context: Context, path: String): String {
    if (!path.startsWith("content://")) return path
    return try {
        val uri = Uri.parse(path)
        val fullUri = uri.toString()

        // 先尝试从 DocumentFile 获取真实文件名
        var fileName: String? = null
        try {
            val docFile = DocumentFile.fromSingleUri(context, uri)
            fileName = docFile?.name
        } catch (_: Exception) {}

        // 提取 /tree/ 后面的部分（目录）
        val treePart = fullUri.substringAfter("/tree/", "").substringBefore("/document/")
        val decodedTree = Uri.decode(treePart).removePrefix("primary:")

        // 提取 /document/ 后面的部分
        val docPart = fullUri.substringAfter("/document/", "")
        val decodedDoc = Uri.decode(docPart)

        // 去掉 "primary:" 前缀
        val relativeDoc = decodedDoc.removePrefix("primary:")

        when {
            // 如果 document 部分有完整相对路径（最常见）
            relativeDoc.isNotEmpty() && relativeDoc != decodedDoc -> {
                "/storage/emulated/0/$relativeDoc"
            }
            // document 部分只有文件名，拼接 tree 目录
            decodedTree.isNotEmpty() -> {
                val name = fileName ?: decodedDoc.substringAfterLast(":")
                "/storage/emulated/0/$decodedTree/$name"
            }
            // 有文件名但无目录信息
            !fileName.isNullOrEmpty() -> {
                "/storage/emulated/0/$fileName"
            }
            // 兜底：返回完整解码 URI
            else -> Uri.decode(fullUri)
        }
    } catch (_: Exception) {
        Uri.decode(path)
    }
}