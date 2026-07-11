package com.fuck.zlx

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * 从视频 URL 中解析发布日期。
 * 例如: https://img.zl-x.online/file/zhonglao-x/videos/202512/17/69418cbe0c1f1b5ecbe93a23/0025e9/index.m3u8
 * 解析出 2025年12月17日
 */
fun parsePublishDateFromUrl(url: String): String {
    // 匹配 /YYYYMM/DD/ 格式的日期
    val pattern = Pattern.compile("/(\\d{4})(\\d{2})/(\\d{2})/")
    val matcher = pattern.matcher(url)
    if (matcher.find()) {
        val year = matcher.group(1)
        val month = matcher.group(2)
        val day = matcher.group(3)
        return "$year-$month-$day"
    }
    return ""
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ResourcesScreen(
    sniffedResources: List<SniffedItem>,
    onClearClick: () -> Unit,
    onPlayVideo: (String) -> Unit,
    isDescending: Boolean,
    isGridView: Boolean,
    onDescendingChange: (Boolean) -> Unit,
    onGridViewChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var showDownloadManager by remember { mutableStateOf(false) }

    // 排序与视图状态已提升到 MainScreen 层级，通过参数传入
    var showSortMenu by remember { mutableStateOf(false) }

    // 【修复 1】：进入资源页时瞬间加载本地磁盘的下载记录！防止重启 App 后记录被空数据覆盖丢失
    LaunchedEffect(Unit) {
        DownloadManager.loadTasksFromDisk(context)
    }

    // 【修复 2】：精准拦截返回键。如果在下载页按返回，只关闭下载页，完美退回资源页！
    BackHandler(enabled = showDownloadManager) {
        showDownloadManager = false
    }
    // 播放器已提升到 MainActivity 顶层全屏渲染（共享 Activity 窗口，亮度手势才能生效），
    // 这里只需把点击事件回调上去即可。
    if (showDownloadManager) {
        DownloadScreen(
            onBack = { showDownloadManager = false },
            onPlayVideo = onPlayVideo
        )
        return
    }

    // 排序后的资源列表
    val sortedResources = remember(sniffedResources, isDescending) {
        if (isDescending) sniffedResources.reversed() else sniffedResources
    }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // --- 顶部控制栏 ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "嗅探资源 (${sniffedResources.size})",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // --- 排序按钮 ---
                    Box {
                        FilledTonalButton(
                            onClick = { showSortMenu = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.Sort, contentDescription = "排序", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("排序", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            // 正序/倒序切换
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            if (isDescending) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(if (isDescending) "倒序查看（点击切正序）" else "正序查看（点击切倒序）")
                                    }
                                },
                                onClick = { onDescendingChange(!isDescending) }
                            )
                            HorizontalDivider()
                            // 宫格视图
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.GridView,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = if (isGridView) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("宫格视图", color = if (isGridView) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                    }
                                },
                                onClick = {
                                    onGridViewChange(true)
                                    showSortMenu = false
                                }
                            )
                            // 列表视图
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.ViewList,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = if (!isGridView) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("列表视图", color = if (!isGridView) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                    }
                                },
                                onClick = {
                                    onGridViewChange(false)
                                    showSortMenu = false
                                }
                            )
                        }
                    }

                    // --- 查看下载按钮 ---
                    FilledTonalButton(
                        onClick = { showDownloadManager = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("查看下载", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // --- 资源列表区域 ---
            if (isGridView) {
                // ===== 宫格视图 =====
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 120.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    gridItems(sortedResources) { item ->
                        Card(
                            modifier = Modifier
                                .padding(8.dp)
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { onPlayVideo(item.m3u8Url) },
                                    onLongClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("视频链接", item.m3u8Url)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "链接已复制", Toast.LENGTH_SHORT).show()
                                    }
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = androidx.compose.ui.graphics.Color.White
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column {
                                AsyncImage(
                                    model = item.thumbUrl,
                                    contentDescription = "封面",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(3f / 4f)
                                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                )

                                // 标题
                                if (item.title.isNotEmpty()) {
                                    Text(
                                        text = item.title,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }

                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Button(
                                        onClick = {
                                            val useLocal = sharedPreferences.getBoolean("use_local_download", false)
                                            if (useLocal) {
                                                val videoName = item.title.ifBlank { "视频_${System.currentTimeMillis()}" }
                                                DownloadManager.startDownload(context, item.m3u8Url, item.thumbUrl, videoName)
                                                Toast.makeText(context, "已加入内置下载队列", Toast.LENGTH_SHORT).show()
                                            } else {
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                                        data = Uri.parse(item.m3u8Url)
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(Intent.createChooser(intent, "选择下载器"))
                                                } catch (e: Exception) {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    val clip = ClipData.newPlainText("视频链接", item.m3u8Url)
                                                    clipboard.setPrimaryClip(clip)
                                                    Toast.makeText(context, "未找到外部下载器，已复制链接", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("下载", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // ===== 列表视图 =====
                LazyColumn(
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    listItems(sortedResources) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .combinedClickable(
                                    onClick = { onPlayVideo(item.m3u8Url) },
                                    onLongClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("视频链接", item.m3u8Url)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "链接已复制", Toast.LENGTH_SHORT).show()
                                    }
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = androidx.compose.ui.graphics.Color.White
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // 左侧封面图
                                AsyncImage(
                                    model = item.thumbUrl,
                                    contentDescription = "封面",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(100.dp)
                                        .height(130.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )

                                // 右侧信息区
                                Column(
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // 标题
                                    if (item.title.isNotEmpty()) {
                                        Text(
                                            text = item.title,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                    }

                                    // 链接信息
                                    Text(
                                        text = item.m3u8Url,
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                                    // 视频大小
                                    if (item.videoSize.isNotEmpty()) {
                                        Text(
                                            text = "大小: ${item.videoSize}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // 视频时长
                                    if (item.videoDuration.isNotEmpty()) {
                                        Text(
                                            text = "时长: ${item.videoDuration}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // 发布日期（从视频链接中解析）
                                    val publishDate = parsePublishDateFromUrl(item.m3u8Url)
                                    if (publishDate.isNotEmpty()) {
                                        Text(
                                            text = "发布: $publishDate",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Spacer(modifier = Modifier.weight(1f))

                                    // 下载按钮
                                    Button(
                                        onClick = {
                                            val useLocal = sharedPreferences.getBoolean("use_local_download", false)
                                            if (useLocal) {
                                                val videoName = item.title.ifBlank { "视频_${System.currentTimeMillis()}" }
                                                DownloadManager.startDownload(context, item.m3u8Url, item.thumbUrl, videoName)
                                                Toast.makeText(context, "已加入内置下载队列", Toast.LENGTH_SHORT).show()
                                            } else {
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                                        data = Uri.parse(item.m3u8Url)
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(Intent.createChooser(intent, "选择下载器"))
                                                } catch (e: Exception) {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    val clip = ClipData.newPlainText("视频链接", item.m3u8Url)
                                                    clipboard.setPrimaryClip(clip)
                                                    Toast.makeText(context, "未找到外部下载器，已复制链接", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(vertical = 4.dp)
                                    ) {
                                        Text("下载", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 右下角悬浮清空按钮 (FAB) ---
        if (sniffedResources.isNotEmpty()) {
            FloatingActionButton(
                onClick = { onClearClick() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 100.dp),
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ) {
                Icon(Icons.Default.Delete, contentDescription = "清空")
            }
        }
    }
}