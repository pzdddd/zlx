package com.fuck.zlx

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * 纯数据模型，不持有任何非 Compose 稳定类型的字段（如 Job、FFmpegSession）。
 * 这样 Compose 编译器能正确推断它为 @Stable，避免无限重组导致的 ANR/闪退。
 */
data class DownloadTask(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val thumbUrl: String,
    val fileName: String,
    val progress: String = "准备中...",
    val status: DownloadStatus = DownloadStatus.DOWNLOADING,
    val outputPath: String,
    val completedAt: Long = 0L  // 下载完成时间戳（毫秒），0表示未完成
)

enum class DownloadStatus { DOWNLOADING, SUCCESS, FAILED, CANCELED }

object DownloadManager {
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks

    // job 和 session 从 DownloadTask 中剥离，改为用 id 在外部 Map 中管理
    private val taskJobs = mutableMapOf<String, Job>()
    private val taskSessions = mutableMapOf<String, FFmpegSession>()

    // ============ 核心新增：本地数据持久化 ============
    fun loadTasksFromDisk(context: Context) {
        if (_tasks.value.isNotEmpty()) return // 如果内存中已有数据则不重复加载
        try {
            val json = context.getSharedPreferences("downloads", Context.MODE_PRIVATE).getString("tasks_json", "[]")
            if (json == "[]") return
            val array = JSONArray(json)
            val loadedTasks = mutableListOf<DownloadTask>()
            
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val statusStr = obj.getString("status")
                // 如果 App 被强制关闭时正在下载，那么重新打开时状态应该变为“失败”
                val status = if (statusStr == DownloadStatus.DOWNLOADING.name) DownloadStatus.FAILED else DownloadStatus.valueOf(statusStr)
                val progress = if (status == DownloadStatus.FAILED && statusStr == DownloadStatus.DOWNLOADING.name) "应用被关闭，下载中断" else obj.getString("progress")
                val task = DownloadTask(
                    id = obj.getString("id"),
                    url = obj.getString("url"),
                    thumbUrl = obj.getString("thumbUrl"),
                    fileName = obj.getString("fileName"),
                    progress = progress,
                    status = status,
                    outputPath = obj.getString("outputPath"),
                    completedAt = obj.optLong("completedAt", 0L)
                )
                loadedTasks.add(task)
            }
            
            // 核心修复：过滤掉那些状态是"已完成"，但物理文件已经在相册被删掉的“幽灵记录”
            val validTasks = loadedTasks.filter { task ->
                if (task.status == DownloadStatus.SUCCESS && task.outputPath.isNotEmpty()) {
                    try {
                        if (task.outputPath.startsWith("content://")) {
                            val docFile = DocumentFile.fromSingleUri(context, Uri.parse(task.outputPath))
                            docFile?.exists() == true
                        } else {
                            File(task.outputPath).exists()
                        }
                    } catch (e: Exception) {
                        false
                    }
                } else {
                    true // 正在下载或失败的任务保留
                }
            }
            
            _tasks.value = validTasks
            
            // 如果清理了幽灵文件，顺手更新一下本地 JSON
            if (validTasks.size != loadedTasks.size) {
                saveTasksToDisk(context)
            }
        } catch (e: Exception) { Log.e("DownloadManager", "读取下载记录失败", e) }
    }

    private fun saveTasksToDisk(context: Context) {
        try {
            val array = JSONArray()
            _tasks.value.forEach { task ->
                val obj = JSONObject().apply {
                    put("id", task.id)
                    put("url", task.url)
                    put("thumbUrl", task.thumbUrl)
                    put("fileName", task.fileName)
                    put("progress", task.progress)
                    put("status", task.status.name)
                    put("outputPath", task.outputPath)
                    put("completedAt", task.completedAt)
                }
                array.put(obj)
            }
            context.getSharedPreferences("downloads", Context.MODE_PRIVATE).edit().putString("tasks_json", array.toString()).apply()
        } catch (e: Exception) { Log.e("DownloadManager", "保存下载记录失败", e) }
    }
    // ===============================================

    private fun fetchWithHeaders(urlString: String): ByteArray {
        var retry = 0
        while (retry < 3) {
            try {
                val url = URL(urlString)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                conn.setRequestProperty("Referer", "https://${url.host}/")
                conn.connectTimeout = 10000
                conn.readTimeout = 15000
                if (conn.responseCode !in 200..299) throw Exception("HTTP ${conn.responseCode}")
                return conn.inputStream.readBytes()
            } catch (e: Exception) {
                retry++
                if (retry >= 3) throw e
            }
        }
        return ByteArray(0)
    }

    private fun downloadSegment(urlString: String, destFile: File) {
        val data = fetchWithHeaders(urlString)
        var startIndex = 0
        if (data.size >= 8 && data[0] == 0x89.toByte() && data[1] == 0x50.toByte() && data[2] == 0x4E.toByte() && data[3] == 0x47.toByte()) {
            startIndex = 8
        }
        destFile.writeBytes(data.copyOfRange(startIndex, data.size))
    }

    private fun copyToDestination(context: Context, tempFile: File, fileName: String, dirUriStr: String?): String {
        try {
            if (dirUriStr != null) {
                val treeUri = Uri.parse(dirUriStr)
                val tree = DocumentFile.fromTreeUri(context, treeUri)
                if (tree != null && tree.canWrite()) {
                    val newFile = tree.createFile("video/mp4", fileName)
                    if (newFile != null) {
                        context.contentResolver.openOutputStream(newFile.uri)?.use { out ->
                            tempFile.inputStream().use { input -> input.copyTo(out) }
                        }
                        return newFile.uri.toString()
                    }
                }
            }
        } catch (e: Exception) { Log.e("DownloadManager", "保存自定义目录失败", e) }
        
        try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.mp4")
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ZLX_Video")
                }
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    tempFile.inputStream().use { input -> input.copyTo(out) }
                }
                return uri.toString()
            }
        } catch (e: Exception) { Log.e("DownloadManager", "保存默认 Downloads 失败", e) }
        
        return tempFile.absolutePath
    }

    fun startDownload(context: Context, url: String, thumbUrl: String, defaultName: String) {
        val cleanUrl = url.trim()
        val safeName = defaultName.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
        val outputFile = File(context.cacheDir, "$safeName.mp4")
        
        val task = DownloadTask(url = cleanUrl, thumbUrl = thumbUrl, fileName = safeName, outputPath = outputFile.absolutePath)
        _tasks.update { it + task }
        saveTasksToDisk(context) 

        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val customDirUriStr = prefs.getString("download_dir_uri", null)

        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                val threadCount = prefs.getInt("download_threads", 8)
                val semaphore = Semaphore(threadCount)
                var currentUrl = cleanUrl
                var content = String(fetchWithHeaders(currentUrl))

                if (content.contains("#EXT-X-STREAM-INF")) {
                    val subPath = content.lines().firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                    if (subPath != null) {
                        currentUrl = if (subPath.startsWith("http")) subPath else URL(URL(currentUrl), subPath).toString()
                        content = String(fetchWithHeaders(currentUrl))
                    }
                }

                val baseUrl = URL(currentUrl)
                val taskDir = File(context.cacheDir, "task_${task.id}")
                taskDir.mkdirs()

                val localM3u8Content = StringBuilder()
                data class DownloadItem(val url: String, val localFile: File)
                val downloadQueue = mutableListOf<DownloadItem>()
                var tsIndex = 0

                for (line in content.lines()) {
                    if (line.startsWith("#EXT-X-KEY:")) {
                        val uriRegex = """URI="([^"]+)"""".toRegex()
                        val match = uriRegex.find(line)
                        if (match != null) {
                            val relativeUrl = match.groupValues[1]
                            val absoluteUrl = if (relativeUrl.startsWith("http")) relativeUrl else URL(baseUrl, relativeUrl).toString()
                            val keyFile = File(taskDir, "key_$tsIndex.key")
                            downloadQueue.add(DownloadItem(absoluteUrl, keyFile))
                            localM3u8Content.append(line.replace(relativeUrl, keyFile.name)).append("\n")
                        } else { localM3u8Content.append(line).append("\n") }
                    } else if (line.isNotBlank() && !line.startsWith("#")) {
                        val absoluteUrl = if (line.startsWith("http")) line else URL(baseUrl, line).toString()
                        val tsFile = File(taskDir, "seg_$tsIndex.ts")
                        downloadQueue.add(DownloadItem(absoluteUrl, tsFile))
                        localM3u8Content.append(tsFile.name).append("\n")
                        tsIndex++
                    } else { localM3u8Content.append(line).append("\n") }
                }

                val localM3u8File = File(taskDir, "index.m3u8")
                localM3u8File.writeText(localM3u8Content.toString())

                var downloadedCount = 0
                val totalCount = downloadQueue.size

                downloadQueue.map { item ->
                    async {
                        semaphore.withPermit {
                            ensureActive()
                            downloadSegment(item.url, item.localFile)
                            downloadedCount++
                            _tasks.update { list -> list.map { t -> if (t.id == task.id && t.status == DownloadStatus.DOWNLOADING) t.copy(progress = "正在下载: $downloadedCount / $totalCount") else t } }
                        }
                    }
                }.awaitAll()

                _tasks.update { list -> list.map { t -> if (t.id == task.id) t.copy(progress = "正在封装视频，请稍候...") else t } }

                val command = "-y -allowed_extensions ALL -protocol_whitelist file,http,https,tcp,tls,crypto -i \"${localM3u8File.absolutePath}\" -c copy \"${outputFile.absolutePath}\""
                
                val session = FFmpegKit.executeAsync(command, { session ->
                    if (ReturnCode.isSuccess(session.returnCode)) {
                        val finalUriString = copyToDestination(context, outputFile, safeName, customDirUriStr)
                        outputFile.delete() 
                        taskDir.deleteRecursively()
                        
                        _tasks.update { list -> list.map { t -> if (t.id == task.id) t.copy(status = DownloadStatus.SUCCESS, progress = "下载完成，已存入相册/目录", outputPath = finalUriString, completedAt = System.currentTimeMillis()) else t } }
                        saveTasksToDisk(context) 
                        _tasks.update { list -> list.map { t -> if (t.id == task.id) t.copy(status = DownloadStatus.CANCELED, progress = "已取消") else t } }
                        saveTasksToDisk(context) 
                    } else {
                        _tasks.update { list -> list.map { t -> if (t.id == task.id) t.copy(status = DownloadStatus.FAILED, progress = "合并 MP4 失败") else t } }
                        saveTasksToDisk(context) 
                    }
                }, null, null)

                taskSessions[task.id] = session

            } catch (e: CancellationException) {
                _tasks.update { list -> list.map { t -> if (t.id == task.id) t.copy(status = DownloadStatus.CANCELED, progress = "已取消") else t } }
                saveTasksToDisk(context) 
            } catch (e: Exception) {
                _tasks.update { list -> list.map { t -> if (t.id == task.id) t.copy(status = DownloadStatus.FAILED, progress = "网络中断，下载失败") else t } }
                saveTasksToDisk(context) 
            }
        }
        taskJobs[task.id] = job
    }

    fun stopTask(taskId: String) {
        taskJobs[taskId]?.cancel()
        taskSessions[taskId]?.cancel()
    }

    fun deleteTask(context: Context, taskId: String) {
        taskJobs[taskId]?.cancel()
        taskSessions[taskId]?.cancel()
        taskJobs.remove(taskId)
        taskSessions.remove(taskId)
        val task = _tasks.value.find { it.id == taskId }
        task?.let {
            try {
                if (it.outputPath.isNotEmpty()) {
                    if (it.outputPath.startsWith("content://")) {
                        DocumentFile.fromSingleUri(context, Uri.parse(it.outputPath))?.delete()
                    } else {
                        File(it.outputPath).delete()
                    }
                }
            } catch (e: Exception) {}
        }
        _tasks.update { list -> list.filter { it.id != taskId } }
        saveTasksToDisk(context) 
    }

    fun renameTask(context: Context, taskId: String, newName: String) {
        _tasks.update { list ->
            list.map { t ->
                if (t.id == taskId) {
                    try {
                        if (t.outputPath.startsWith("content://")) {
                            DocumentFile.fromSingleUri(context, Uri.parse(t.outputPath))?.renameTo(newName)
                            t.copy(fileName = newName)
                        } else {
                            val oldFile = File(t.outputPath)
                            val newFile = File(oldFile.parent, "$newName.mp4")
                            if (oldFile.exists()) oldFile.renameTo(newFile)
                            t.copy(fileName = newName, outputPath = newFile.absolutePath)
                        }
                    } catch (e: Exception) { t }
                } else t
            }
        }
        saveTasksToDisk(context) 
    }

    // ============ 新增功能：重新下载 ============
    fun reDownload(context: Context, task: DownloadTask) {
        // 0. 清理旧的 job/session 引用
        taskJobs.remove(task.id)?.cancel()
        taskSessions.remove(task.id)?.cancel()

        // 1. 如果本地还有残留的文件，先删掉它，防止空间堆积
        try {
            if (task.outputPath.isNotEmpty()) {
                if (task.outputPath.startsWith("content://")) {
                    DocumentFile.fromSingleUri(context, Uri.parse(task.outputPath))?.delete()
                } else {
                    File(task.outputPath).delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // 2. 从列表中安全移除旧任务
        _tasks.update { list -> list.filter { it.id != task.id } }
        
        // 3. 重新唤起下载引擎开启新任务
        startDownload(context, task.url, task.thumbUrl, task.fileName)
    }
}
