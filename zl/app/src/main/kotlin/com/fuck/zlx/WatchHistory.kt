package com.fuck.zlx

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class WatchHistoryItem(
    val url: String,
    val title: String,
    val thumbUrl: String,
    val watchedAt: Long
)

object WatchHistoryStore {
    private const val PREFS_NAME = "watch_history"
    private const val KEY_ITEMS = "items"
    private const val MAX_ITEMS = 100

    fun load(context: Context): MutableList<WatchHistoryItem> {
        val list = mutableListOf<WatchHistoryItem>()
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ITEMS, null) ?: return list
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    WatchHistoryItem(
                        url = o.getString("url"),
                        title = o.optString("title"),
                        thumbUrl = o.optString("thumb"),
                        watchedAt = o.optLong("time")
                    )
                )
            }
        } catch (_: Exception) {
        }
        return list
    }

    fun save(context: Context, list: List<WatchHistoryItem>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("url", it.url)
                    .put("title", it.title)
                    .put("thumb", it.thumbUrl)
                    .put("time", it.watchedAt)
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_ITEMS, arr.toString()).apply()
    }

    fun add(context: Context, url: String, title: String, thumbUrl: String) {
        if (url.isBlank()) return
        val list = load(context)
        list.removeAll { it.url == url }
        list.add(0, WatchHistoryItem(url, title, thumbUrl, System.currentTimeMillis()))
        while (list.size > MAX_ITEMS) list.removeAt(list.size - 1)
        save(context, list)
    }

    fun remove(context: Context, url: String) {
        val list = load(context)
        list.removeAll { it.url == url }
        save(context, list)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

@Composable
fun WatchHistoryScreen(onBack: () -> Unit, onPlayVideo: (String) -> Unit) {
    val context = LocalContext.current
    var history by remember { mutableStateOf(WatchHistoryStore.load(context)) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize()) {

        // --- 顶部栏 ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "观看历史 (${history.size})",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            FilledTonalButton(
                onClick = {
                    WatchHistoryStore.clear(context)
                    history = mutableListOf()
                },
                enabled = history.isNotEmpty(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("清空", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        // --- 历史列表 ---
        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "暂无观看记录\n在资源页点击视频后会自动记录到这里",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(history, key = { it.url }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = androidx.compose.ui.graphics.Color.White
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // 封面：固定相框 + 完整显示（宽图两侧以浅灰底衬托，不再裁切中间）
                                AsyncImage(
                                    model = item.thumbUrl,
                                    contentDescription = "封面",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .width(160.dp)
                                        .height(150.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(androidx.compose.ui.graphics.Color(0xFFF0F0F0))
                                )

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (item.title.isNotEmpty()) {
                                        Text(
                                            text = item.title,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(end = 44.dp)
                                        )
                                    }
                                    Text(
                                        text = item.url,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "观看于: ${dateFormat.format(Date(item.watchedAt))}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.weight(1f))

                                    // 重新观看按钮：顶到卡片最右侧
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        Spacer(modifier = Modifier.weight(1f))
                                        Button(
                                            onClick = {
                                                WatchHistoryStore.add(context, item.url, item.title, item.thumbUrl)
                                                history = WatchHistoryStore.load(context)
                                                onPlayVideo(item.url)
                                            },
                                            modifier = Modifier.wrapContentWidth(),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 2.dp)
                                        ) {
                                            Text("重新观看", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }

                            // 删除按钮：右上角悬浮，不再挤占按钮右侧空间
                            IconButton(
                                onClick = {
                                    WatchHistoryStore.remove(context, item.url)
                                    history = WatchHistoryStore.load(context)
                                },
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
