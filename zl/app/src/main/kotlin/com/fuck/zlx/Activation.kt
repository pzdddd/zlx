package com.fuck.zlx

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 一机一码激活（自定义时长 + 云端授权）：
 *
 * 1) 离线激活码：XXXX-XXXX-DDD
 *    DDD = 36进制小时数（0-9A-Z），000=永久，最大 ZZZ≈5.3年；
 *    签名 = HMAC-SHA256(私有密钥, 设备码|DDD) 前 8 位。
 *
 * 2) 云端授权（GitHub 授权仓库 auth.json，jsDelivr/GitHub raw 多端点）：
 *    { devices: { 设备码: { expire: 毫秒(0=永久) } }, revoked: [设备码], time: 服务器时间锚 }
 *    联网时优先云端：授权/到期/吊销以云端为准；3 天内取到过云端数据即视为有效来源；
 *    离线或云端无此设备时回退本地激活码。time 字段兼作网络校时。
 *
 * 防时间作弊：本地时钟 + 网络偏移校准；高水位时间戳，回拨>15分钟即失效。
 */
object ActivationManager {
    private const val PREFS_NAME = "activation"
    private const val KEY_CODE = "code"
    private const val KEY_HOURS = "hours"
    private const val KEY_ACTIVATED_AT = "activated_at"
    private const val KEY_LAST_SEEN = "last_seen"
    private const val KEY_CLOUD_JSON = "cloud_json"
    private const val KEY_CLOUD_FETCHED_AT = "cloud_fetched_at"
    private const val KEY_CLOUD_OFFSET = "cloud_offset"
    private const val KEY_NET_OFFSET = "net_offset"
    private const val KEY_NET_AT = "net_at"
    private const val HOUR_MS = 3600_000L
    private const val MAX_HOURS = 46655          // "ZZZ" 36进制上限 ≈ 5.3 年
    private const val CLOUD_FRESH_MS = 3L * 24 * 3600 * 1000 // 3天内取到过云端即视为有效来源

    /** 期望的 APK 签名证书 SHA-256（防二次打包：破解者改代码后必须重签名，指纹改变即拒绝运行） */
    private const val EXPECTED_SIG = "53FE53289AE267EB0E8150602BAC687B687B2A0F9CCAC170E3613E04DEBB61DD"

    // 云端授权多端点（jsDelivr CDN + GitHub raw），内容为 auth.json
    private val CLOUD_ENDPOINTS = listOf(
        "https://cdn.jsdelivr.net/gh/pzdddd/zlx-auth@main/auth.json",
        "https://fastly.jsdelivr.net/gh/pzdddd/zlx-auth@main/auth.json",
        "https://raw.githubusercontent.com/pzdddd/zlx-auth/main/auth.json"
    )

    // 密钥打散混淆存放（运行时还原），防止被一键字符串搜索
    private val secret: String by lazy {
        val p1 = "bbb90e7a90b303d873e6b61131c3daef"
        val p2 = "fead3c13116b6e378d303b09a7e09bbb"
        p1.substring(0, 16) + p2.substring(0, 16)
    }

    /** 本机设备码（16位十六进制，带分隔） */
    fun deviceCode(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        val salted = androidId + ":zlx-salt-v1:" + Build.MODEL
        val bytes = MessageDigest.getInstance("SHA-256").digest(salted.toByteArray())
        val hex = bytes.take(8).joinToString("") { "%02X".format(it) }
        return hex.chunked(4).joinToString("-")
    }

    private fun hmac(message: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        val hash = mac.doFinal(message.toByteArray())
        return hash.take(4).joinToString("") { "%02X".format(it) }
    }

    // ==================== 离线激活码 ====================

    /** 生成指定小时数对应的完整激活码（签名8位 + 时长DDD3位36进制），与生成器算法一致 */
    fun expectedCode(context: Context, hours: Int): String {
        val h = hours.coerceIn(0, MAX_HOURS)
        val ddd = h.toString(36).uppercase().padStart(3, '0').take(3)
        val sig = hmac(deviceCode(context).replace("-", "") + "|" + ddd)
        return sig.chunked(4).joinToString("-") + "-" + ddd
    }

    /** 旧版永久码（8位签名，无时长段），用于兼容升级前已激活的设备 */
    private fun legacyCode(context: Context): String = hmac(deviceCode(context).replace("-", ""))

    /**
     * 校验并保存激活码。
     * 新格式 11 位（签名8 + DDD3，DDD=36进制小时数，000=永久）；
     * 旧格式 8 位视为永久，迁移为新格式存储。
     */
    fun activate(context: Context, input: String): Boolean {
        val norm = input.replace("-", "").trim().uppercase()
        val dev = deviceCode(context).replace("-", "")
        when (norm.length) {
            11 -> {
                val sig = norm.substring(0, 8)
                val ddd = norm.substring(8)
                if (!ddd.all { it.isDigit() || it in 'A'..'Z' }) return false
                if (hmac(dev + "|" + ddd) != sig) return false
                save(context, norm, ddd.toInt(36))
                return true
            }
            8 -> {
                if (legacyCode(context) == norm) {
                    // 旧版永久码 → 迁移为新版永久(000)存储
                    save(context, hmac(dev + "|000") + "000", 0)
                    return true
                }
            }
        }
        return false
    }

    private fun save(context: Context, code: String, hours: Int) {
        val now = System.currentTimeMillis()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_CODE, code)
            .putInt(KEY_HOURS, hours)
            .putLong(KEY_ACTIVATED_AT, now)
            .putLong(KEY_LAST_SEEN, now)
            .apply()
    }

    // ==================== 云端授权 ====================

    private class CloudCache(val json: JSONObject?, val fetchedAt: Long)

    private fun cloudCache(context: Context): CloudCache {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CLOUD_JSON, null) ?: return CloudCache(null, 0)
        val fetchedAt = prefs.getLong(KEY_CLOUD_FETCHED_AT, 0)
        return try {
            CloudCache(JSONObject(json), fetchedAt)
        } catch (_: Exception) {
            CloudCache(null, 0)
        }
    }

    private fun cloudFresh(c: CloudCache): Boolean =
        c.json != null && System.currentTimeMillis() - c.fetchedAt < CLOUD_FRESH_MS

    /** APK 签名自校验：证书指纹与发布版不一致（被重打包/重签名）即判定非法 */
    fun signatureOk(context: Context): Boolean = try {
        val pm = context.packageManager
        val sigs = if (Build.VERSION.SDK_INT >= 28) {
            pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNATURES).signatures
        } ?: emptyArray()
        val md = MessageDigest.getInstance("SHA-256")
        sigs.isNotEmpty() && sigs.all {
            val hex = md.digest(it.toByteArray()).joinToString("") { b -> "%02X".format(b) }
            hex == EXPECTED_SIG
        }
    } catch (_: Exception) {
        false
    }

    /** 是否已激活且未过期（对外的校验入口，含签名校验/云端优先/本地兜底/防回拨） */
    fun checkValid(context: Context): Boolean = isActivated(context)

    /**
     * 网络校时：从国内可达站点的 HTTP Date 头取服务器时间，计算本地时钟偏移。
     * 与云端授权的 time 字段互为补充，优先使用 24 小时内最新的校时结果。
     */
    suspend fun refreshNetworkTime(context: Context) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            for (url in listOf("https://www.baidu.com", "https://www.qq.com", "https://www.mi.com")) {
                var conn: HttpURLConnection? = null
                try {
                    conn = (URL(url).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 5000
                        readTimeout = 5000
                        requestMethod = "HEAD"
                        setRequestProperty("User-Agent", "zlx-activation")
                    }
                    val dateStr = conn.getHeaderField("Date") ?: continue
                    val server = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
                        .parse(dateStr)?.time ?: continue
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                        .putLong(KEY_NET_OFFSET, server - System.currentTimeMillis())
                        .putLong(KEY_NET_AT, System.currentTimeMillis())
                        .apply()
                    return@withContext
                } catch (_: Exception) {
                } finally {
                    conn?.disconnect()
                }
            }
        }
    }

    /** 校准后的当前时间：本地时钟 + 最新一次有效校时偏移（24h内网络校时 > 3天内云端锚点） */
    private fun calibratedNow(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val netAt = prefs.getLong(KEY_NET_AT, 0L)
        if (now - netAt < 24L * 3600 * 1000) return now + prefs.getLong(KEY_NET_OFFSET, 0L)
        val c = cloudCache(context)
        if (cloudFresh(c)) return now + prefs.getLong(KEY_CLOUD_OFFSET, 0L)
        return now
    }

    /** 拉取云端授权（进入激活界面时调用；IO 线程执行）。失败静默，本地激活码兜底。
     *  CDN 可能有边缘缓存：URL 带5分钟时间桶破缓存；遍历全部端点取 time 最新的一份。 */
    suspend fun refreshCloudAuth(context: Context) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val bust = System.currentTimeMillis() / (5 * 60 * 1000)
            var bestTime = -1L
            var bestJson: JSONObject? = null
            for (url in CLOUD_ENDPOINTS) {
                var conn: HttpURLConnection? = null
                try {
                    conn = (URL(url + "?t=" + bust).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 8000
                        readTimeout = 8000
                        setRequestProperty("User-Agent", "zlx-activation")
                    }
                    if (conn.responseCode != 200) continue
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body) // 解析失败抛异常 → 试下一个端点
                    val serverTime = json.optLong("time", 0L)
                    if (serverTime > bestTime) {
                        bestTime = serverTime
                        bestJson = json
                    }
                } catch (_: Exception) {
                    // 单个端点失败，继续尝试下一个
                } finally {
                    conn?.disconnect()
                }
            }
            val json = bestJson ?: return@withContext
            val offset = if (bestTime > 0) bestTime - System.currentTimeMillis() else 0L
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_CLOUD_JSON, json.toString())
                .putLong(KEY_CLOUD_FETCHED_AT, System.currentTimeMillis())
                .putLong(KEY_CLOUD_OFFSET, offset)
                .apply()
        }
    }

    /** 校准后的当前时间：本地时钟 + 云端时间偏移（有近期云端数据时），并与高水位对齐 */
    private fun effectiveNow(context: Context, lastSeen: Long): Long {
        return maxOf(calibratedNow(context), lastSeen)
    }

    /**
     * 是否已激活且未过期。
     * 顺序：APK签名自校验 → 防回拨 → 云端优先（吊销/授权/到期，不需要本地激活码）
     * → 本地激活码兜底（含旧版数据迁移）。
     */
    fun isActivated(context: Context): Boolean {
        if (!signatureOk(context)) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dev = deviceCode(context).replace("-", "")

        val nowAdj = calibratedNow(context)
        val lastSeen = prefs.getLong(KEY_LAST_SEEN, 0L)
        val effective = maxOf(nowAdj, lastSeen)
        if (nowAdj - lastSeen > 60L * 60 * 1000) {
            prefs.edit().putLong(KEY_LAST_SEEN, nowAdj).apply()
        }

        // ---- 云端优先（仅云端授权的设备无需本地码）----
        val c = cloudCache(context)
        if (cloudFresh(c) && c.json != null) {
            val revoked = c.json.optJSONArray("revoked")
            if (revoked != null) {
                for (i in 0 until revoked.length()) {
                    if (revoked.optString(i).replace("-", "").uppercase() == dev) return false
                }
            }
            val devObj = c.json.optJSONObject("devices")?.optJSONObject(dev)
            if (devObj != null) {
                val expire = devObj.optLong("expire", -1L)
                return expire == 0L || (expire > 0 && effective <= expire)
            }
            // 云端没有此设备 → 回退本地
        }

        // ---- 本地激活码 ----
        val code = prefs.getString(KEY_CODE, null) ?: return false
        val hours = prefs.getInt(KEY_HOURS, 0)
        val storedValid = when (code.length) {
            11 -> {
                val sig = code.substring(0, 8)
                val ddd = code.substring(8)
                hmac(dev + "|" + ddd) == sig && ddd.toInt(36) == hours
            }
            else -> code == legacyCode(context) // 旧版8位
        }
        if (!storedValid) return false

        var activatedAt = prefs.getLong(KEY_ACTIVATED_AT, 0L)
        if (activatedAt <= 0L) {
            // 兼容旧版存储（键名不同的升级设备）：首次校验通过时迁移回填
            activatedAt = nowAdj
            prefs.edit().putInt(KEY_HOURS, code.takeLast(3).toInt(36)).putLong(KEY_ACTIVATED_AT, activatedAt).apply()
        }
        if (hours > 0 && effective - activatedAt > hours * HOUR_MS) return false // 已到期
        return true
    }

    /** 为什么进入激活界面（红字原因）；首次未激活返回 null */
    fun invalidReason(context: Context): String? {
        if (!signatureOk(context)) return "应用完整性校验失败，请从正规渠道获取安装包"
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val code = prefs.getString(KEY_CODE, null) ?: return null
        val hours = prefs.getInt(KEY_HOURS, 0)
        val activatedAt = prefs.getLong(KEY_ACTIVATED_AT, 0L)
        val lastSeen = prefs.getLong(KEY_LAST_SEEN, 0L)

        val dev = deviceCode(context).replace("-", "")
        val c = cloudCache(context)
        if (cloudFresh(c) && c.json != null) {
            val revoked = c.json.optJSONArray("revoked")
            if (revoked != null) {
                for (i in 0 until revoked.length()) {
                    if (revoked.optString(i).replace("-", "").uppercase() == dev) {
                        return "授权已被发放者收回，请重新获取"
                    }
                }
            }
        }
        val effective = effectiveNow(context, lastSeen)
        if (hours > 0 && effective - activatedAt > hours * HOUR_MS) return "激活已到期，请重新获取激活码"

        val storedValid = when (code.length) {
            11 -> hmac(dev + "|" + code.substring(8)) == code.substring(0, 8)
            else -> code == legacyCode(context)
        }
        if (!storedValid) return "本机激活信息校验失败，请重新激活"
        return null
    }

    /** 激活成功后的有效期描述（用于提示） */
    fun durationText(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hours = prefs.getInt(KEY_HOURS, 0)
        if (hours <= 0) return "永久有效"
        val activatedAt = prefs.getLong(KEY_ACTIVATED_AT, 0L)
        val lastSeen = prefs.getLong(KEY_LAST_SEEN, 0L)
        val remainMs = hours * HOUR_MS - (effectiveNow(context, lastSeen) - activatedAt)
        if (remainMs <= 0) return "已到期"
        val remainHours = (remainMs / HOUR_MS).toInt()
        val d = remainHours / 24
        val h = remainHours % 24
        return if (d > 0) "剩余 " + d + " 天" + (if (h > 0) " " + h + " 小时" else "") else "剩余 " + h + " 小时"
    }
}

/**
 * 激活界面：展示本机设备码 + 激活码输入框。
 */
@Composable
fun ActivationScreen(onActivated: () -> Unit) {
    val context = LocalContext.current
    val deviceCode = remember { ActivationManager.deviceCode(context) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    // 进入激活界面时拉取网络时间与云端授权；若发放者已远程授权则直接进入应用
    LaunchedEffect(Unit) {
        ActivationManager.refreshNetworkTime(context)
        ActivationManager.refreshCloudAuth(context)
        if (ActivationManager.checkValid(context)) onActivated()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {


            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("本机设备码", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(deviceCode, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp)
                        IconButton(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("设备码", deviceCode))
                            Toast.makeText(context, "设备码已复制", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "复制",
                                modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it.take(13)
                    error = false
                },
                label = { Text("激活码") },
                isError = error,
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrect = false,
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii),
                modifier = Modifier.fillMaxWidth()
            )
            if (error) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("激活码不正确，请核对后重试", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (ActivationManager.activate(context, input)) {
                        Toast.makeText(context, "激活成功（${ActivationManager.durationText(context)}）",
                            Toast.LENGTH_SHORT).show()
                        onActivated()
                    } else {
                        error = true
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = input.isNotBlank()
            ) {
                Text("激活", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

        }
    }
}
