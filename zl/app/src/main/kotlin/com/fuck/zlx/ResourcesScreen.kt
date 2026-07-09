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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ResourcesScreen(
    sniffedResources: List<SniffedItem>,
    onClearClick: () -> Unit,
    onPlayVideo: (String) -> Unit
) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var showDownloadManager by remember { mutableStateOf(false) }

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
        DownloadScreen(onBack = { showDownloadManager = false })
        return
    }

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
                FilledTonalButton(
                    onClick = { showDownloadManager = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("查看下载", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                if (sniffedResources.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = { onClearClick() },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "清空", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("清空", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- 资源网格列表 ---
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 120.dp), // 调整底部留白，防止被液态底栏挡住
            modifier = Modifier.fillMaxSize()
        ) {
            items(sniffedResources) { item ->
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
                        
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Button(
                                onClick = {
                                    val useLocal = sharedPreferences.getBoolean("use_local_download", false)
                                    if (useLocal) {
                                        val defaultName = "视频_${System.currentTimeMillis()}"
                                        DownloadManager.startDownload(context, item.m3u8Url, item.thumbUrl, defaultName)
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
    }
}
