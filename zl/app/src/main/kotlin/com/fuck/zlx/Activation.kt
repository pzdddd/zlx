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
    private const val KEY_AT = "activatedAt"
    private const val KEY_LAST_SEEN = "lastSeen"
    private const val KEY_NET_OFFSET = "netOffset"
    private const val KEY_INVALID_REASON = "invalidReason"
    private const val KEY_CLOUD_AT = "cloudAt"       // 上次成功拉取云端授权的时间
    private const val KEY_CLOUD_EXPIRE = "cloudExpire" // 云端本机到期毫秒，-1=云端无本机条目
    private const val KEY_CLOUD_REVOKED = "cloudRevoked"

    /** 授权数据端点：jsDelivr 国内CDN优先，GitHub raw 兜底 */
    private val AUTH_ENDPOINTS = listOf(
        "https://cdn.jsdelivr.net/gh/pzdddd/zlx-auth@main/auth.json",
        "https://fastly.jsdelivr.net/gh/pzdddd/zlx-auth@main/auth.json",
        "https://raw.githubusercontent.com/pzdddd/zlx-auth/main/auth.json"
    )

    private const val CLOUD_GRACE_MS = 3L * 24 * 3600 * 1000  // 云端数据3天内视为有效来源

    // 密钥打散混淆存放（运行时还原）
    private val secret: String by lazy {
        val p1 = "bbb90e7a90b303d873e6b61131c3daef"
        val p2 = "fead3c13116b6e378d303b09a7e09bbb"
        p1.substring(0, 16) + p2.substring(0, 16)
    }

    fun deviceCode(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        val salted = androidId + ":zlx-salt-v1:" + Build.MODEL
        val bytes = MessageDigest.getInstance("SHA-256").digest(salted.toByteArray())
        val hex = bytes.take(8).joinToString("") { "%02X".format(it) }
        return hex.chunked(4).joinToString("-")
    }

    private fun deviceId(context: Context) = deviceCode(context).replace("-", "")

    /** 时长(小时) -> 36进制3位token */
    fun hoursToToken(hours: Long): String =
        hours.toString(36).uppercase().padStart(3, '0').takeLast(3)

    /** 36进制token -> 时长(小时)，-1=非法 */
    fun tokenToHours(token: String): Long = try {
        Integer.parseInt(token.lowercase(), 36).toLong()
    } catch (_: Exception) { -1L }

    private fun signatureFor(dev: String, token: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        val hash = mac.doFinal((dev + "|" + token).toByteArray())
        return hash.take(4).joinToString("") { "%02X".format(it) }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun now(context: Context): Long {
        val offset = prefs(context).getLong(KEY_NET_OFFSET, 0L)
        val safe = if (kotlin.math.abs(offset) > 365L * 24 * 3600 * 1000) 0L else offset
        return System.currentTimeMillis() + safe
    }

    /** 后台网络校时（HTTP Date 头） */
    fun refreshNetworkTime(context: Context) {
        Thread {
            try {
                val conn = URL("https://www.baidu.com").openConnection() as HttpURLConnection
                conn.requestMethod = "HEAD"
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                val dateStr = conn.getHeaderField("Date") ?: return@Thread
                conn.disconnect()
                val net = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).parse(dateStr)?.time
                    ?: return@Thread
                val offset = net - System.currentTimeMillis()
                if (kotlin.math.abs(offset) <= 365L * 24 * 3600 * 1000) {
                    prefs(context).edit().putLong(KEY_NET_OFFSET, offset).apply()
                }
            } catch (_: Exception) {
            }
        }.start()
    }

    /**
     * 后台拉取云端授权数据（多端点依次尝试）。
     * 成功后缓存：本机到期时间、吊销名单、服务器时间偏移。
     */
    fun refreshCloudAuth(context: Context, onDone: (() -> Unit)? = null) {
        Thread {
            val dev = deviceId(context)
            for (url in AUTH_ENDPOINTS) {
                try {
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()
                    val obj = org.json.JSONObject(text)
                    val serverTime = obj.optLong("time", 0L)
                    val revoked = mutableSetOf<String>()
                    val arr = obj.optJSONArray("revoked")
                    if (arr != null) for (i in 0 until arr.length()) revoked.add(arr.getString(i).uppercase())
                    val mine = obj.optJSONObject("devices")?.optJSONObject(dev)
                    val expire = if (mine != null) mine.optLong("expire", -1L) else -1L
                    val e = prefs(context).edit()
                        .putLong(KEY_CLOUD_AT, System.currentTimeMillis())
                        .putLong(KEY_CLOUD_EXPIRE, expire)
                        .putStringSet(KEY_CLOUD_REVOKED, revoked)
                    if (serverTime > 0L) {
                        val offset = serverTime - System.currentTimeMillis()
                        if (kotlin.math.abs(offset) <= 365L * 24 * 3600 * 1000) {
                            e.putLong(KEY_NET_OFFSET, offset)
                        }
                    }
                    e.apply()
                    onDone?.invoke()
                    return@Thread
                } catch (_: Exception) {
                }
            }
            onDone?.invoke()
        }.start()
    }

    private fun invalidate(context: Context, reason: String) {
        prefs(context).edit().remove(KEY_CODE).putString(KEY_INVALID_REASON, reason).apply()
    }

    fun invalidReason(context: Context): String? = prefs(context).getString(KEY_INVALID_REASON, null)

    /** 校验并保存离线激活码（XXXX-XXXX-DDD，11位） */
    fun activate(context: Context, input: String): Boolean {
        val code = input.replace("-", "").replace(" ", "").trim().uppercase()
        if (code.length != 11) return false
        val token = code.takeLast(3)
        val signature = code.dropLast(3)
        if (tokenToHours(token) < 0) return false
        if (!signature.equals(signatureFor(deviceId(context), token), ignoreCase = true)) return false
        val t = now(context)
        prefs(context).edit()
            .putString(KEY_CODE, code)
            .putLong(KEY_AT, t)
            .putLong(KEY_LAST_SEEN, t)
            .remove(KEY_INVALID_REASON)
            .apply()
        return true
    }

    /** 云端数据是否在有效缓存期内取到过 */
    private fun cloudFresh(context: Context): Boolean =
        System.currentTimeMillis() - prefs(context).getLong(KEY_CLOUD_AT, 0L) < CLOUD_GRACE_MS

    /** 是否已激活且有效（云端优先，本地兜底）。会更新高水位。 */
    fun checkValid(context: Context): Boolean {
        val p = prefs(context)
        val dev = deviceId(context)

        // ---- 云端授权（3天内拉取成功过才采用）----
        if (cloudFresh(context)) {
            val revoked = p.getStringSet(KEY_CLOUD_REVOKED, emptySet()) ?: emptySet()
            if (revoked.contains(dev)) {
                invalidate(context, "授权已被撤销")
                return false
            }
            val cloudExpire = p.getLong(KEY_CLOUD_EXPIRE, -1L)
            if (cloudExpire >= 0L) {
                val t = now(context)
                val lastSeen = p.getLong(KEY_LAST_SEEN, t)
                if (t + 15 * 60 * 1000 < lastSeen) {
                    invalidate(context, "检测到系统时间被回拨，请重新激活")
                    return false
                }
                p.edit().putLong(KEY_LAST_SEEN, maxOf(t, lastSeen)).apply()
                if (cloudExpire == 0L) return true            // 云端永久授权
                if (t > cloudExpire) {
                    invalidate(context, "授权已到期，请重新获取")
                    return false
                }
                return true                                   // 云端限时授权有效
            }
            // 云端无本机条目 → 回退本地激活码
        }

        // ---- 本地激活码 ----
        val code = p.getString(KEY_CODE, null) ?: return false
        if (code.length != 11) {
            invalidate(context, "激活数据无效，请重新激活")
            return false
        }
        val token = code.takeLast(3)
        val hours = tokenToHours(token)
        if (hours < 0 || !code.dropLast(3).equals(signatureFor(dev, token), ignoreCase = true)) {
            invalidate(context, "激活数据无效，请重新激活")
            return false
        }
        val t = now(context)
        val lastSeen = p.getLong(KEY_LAST_SEEN, t)
        if (t + 15 * 60 * 1000 < lastSeen) {
            invalidate(context, "检测到系统时间被回拨，请重新激活")
            return false
        }
        p.edit().putLong(KEY_LAST_SEEN, maxOf(t, lastSeen)).apply()
        if (hours > 0L) {
            val activatedAt = p.getLong(KEY_AT, t)
            if (t - activatedAt > hours * 3600 * 1000) {
                invalidate(context, "激活已到期，请重新获取激活码")
                return false
            }
        }
        return true
    }

    /** 当前授权来源与剩余时间描述（用于提示） */
    fun durationText(context: Context): String {
        val p = prefs(context)
        if (cloudFresh(context)) {
            val expire = p.getLong(KEY_CLOUD_EXPIRE, -1L)
            if (expire == 0L) return "云端授权·永久"
            if (expire > 0L) {
                val hours = ((expire - now(context)) / 3600000L).coerceAtLeast(0L)
                return "云端授权·剩余" + describeHours(hours)
            }
        }
        val code = p.getString(KEY_CODE, null) ?: return ""
        val hours = tokenToHours(code.takeLast(3))
        return if (hours == 0L) "永久有效" else describeHours(hours)
    }

    private fun describeHours(hours: Long): String = when {
        hours < 48L -> "$hours 小时"
        else -> {
            val d = hours / 24
            val h = hours % 24
            if (h == 0L) "$d 天" else "$d 天 $h 小时"
        }
    }
}

/**
 * 激活界面：展示本机设备码 + 激活码输入框。
 */
@Composable
fun ActivationScreen(onActivated: () -> Unit) {
    val context = LocalContext.current
    val deviceCode = remember { ActivationManager.deviceCode(context) }
    val failReason = remember { ActivationManager.invalidReason(context) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    // 进入激活界面时也拉一次云端（可能已获云端授权）
    LaunchedEffect(Unit) { ActivationManager.refreshCloudAuth(context) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("zlx", fontSize = 42.sp, fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(6.dp))
            Text("视频嗅探浏览器", fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(28.dp))

            if (failReason != null) {
                Text(failReason, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(16.dp))
            }

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
                    Text("把设备码发给发放者获取激活码", fontSize = 12.sp, textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it.take(13)
                    error = false
                },
                label = { Text("激活码（如 A1B2-C3D4-0A9）") },
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

            Spacer(modifier = Modifier.height(32.dp))
            Text(
                "激活码与设备绑定，一码一机，时长由激活码决定。\n到期或更换设备需重新获取。",
                fontSize = 12.sp, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
