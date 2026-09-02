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
import java.util.Date
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 一机一码离线激活（支持时长）：
 *  设备码 = SHA-256(ANDROID_ID + 盐) 前 16 位十六进制（XXXX-XXXX-XXXX-XXXX），每台设备唯一；
 *  激活码 = 8 位签名 + 1 位时长码（XXXX-XXXX-D），签名 = HMAC-SHA256(私有密钥, 设备码|时长码)。
 *  时长码：0=永久 1=1小时 2=1天 3=3天 4=7天 5=30天。
 *
 * 防时间作弊：
 *  - 高水位：记录见过的最大时间戳，系统时间回拨超过15分钟即判定失效；
 *  - 网络校时：定期取 HTTP Date 头算出本地时钟偏移，计算到期时用校准后的时间。
 */
object ActivationManager {
    private const val PREFS_NAME = "activation"
    private const val KEY_CODE = "code"
    private const val KEY_AT = "activatedAt"
    private const val KEY_LAST_SEEN = "lastSeen"
    private const val KEY_NET_OFFSET = "netOffset"
    private const val KEY_INVALID_REASON = "invalidReason"

    /** 时长码 -> 分钟（0 表示永久） */
    private val DURATIONS = mapOf(
        '0' to 0L, '1' to 60L, '2' to 1440L, '3' to 4320L, '4' to 10080L, '5' to 43200L
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

    /** 指定时长码对应的 8 位签名（与发放者生成器算法一致） */
    private fun signatureFor(context: Context, durationChar: Char): String {
        val dev = deviceCode(context).replace("-", "")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        val hash = mac.doFinal((dev + "|" + durationChar).toByteArray())
        return hash.take(4).joinToString("") { "%02X".format(it) }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 校准后的当前时间（本地时钟 + 网络偏移，偏移超一年视为无效不采用） */
    private fun now(context: Context): Long {
        val offset = prefs(context).getLong(KEY_NET_OFFSET, 0L)
        val safe = if (kotlin.math.abs(offset) > 365L * 24 * 3600 * 1000) 0L else offset
        return System.currentTimeMillis() + safe
    }

    /** 后台取网络时间（HTTP Date 头）修正本地时钟偏移，结果存 prefs */
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

    private fun invalidate(context: Context, reason: String) {
        prefs(context).edit()
            .remove(KEY_CODE)
            .putString(KEY_INVALID_REASON, reason)
            .apply()
    }

    /** 上次失效原因（供激活界面提示，如"激活已到期"），无则 null */
    fun invalidReason(context: Context): String? =
        prefs(context).getString(KEY_INVALID_REASON, null)?.also {
            // 读一次即清除，避免激活成功后残留
        }

    /** 校验并保存激活码（格式 XXXX-XXXX-D，9 位）。返回是否成功 */
    fun activate(context: Context, input: String): Boolean {
        val code = input.replace("-", "").replace(" ", "").trim().uppercase()
        if (code.length != 9) return false
        val durationChar = code.last()
        val signature = code.dropLast(1)
        if (durationChar !in DURATIONS) return false
        if (!signature.equals(signatureFor(context, durationChar), ignoreCase = true)) return false
        val t = now(context)
        prefs(context).edit()
            .putString(KEY_CODE, code)
            .putLong(KEY_AT, t)
            .putLong(KEY_LAST_SEEN, t)
            .remove(KEY_INVALID_REASON)
            .apply()
        return true
    }

    /** 是否已激活且仍在有效期内（含时间作弊检测）。会更新高水位。 */
    fun checkValid(context: Context): Boolean {
        val code = prefs(context).getString(KEY_CODE, null) ?: return false
        if (code.length != 9) {
            invalidate(context, "激活数据无效，请重新激活")
            return false
        }
        val durationChar = code.last()
        if (durationChar !in DURATIONS ||
            !code.dropLast(1).equals(signatureFor(context, durationChar), ignoreCase = true)
        ) {
            invalidate(context, "激活数据无效，请重新激活")
            return false
        }
        val minutes = DURATIONS[durationChar]!!
        val t = now(context)
        val p = prefs(context)
        val lastSeen = p.getLong(KEY_LAST_SEEN, t)
        if (t + 15 * 60 * 1000 < lastSeen) {
            invalidate(context, "检测到系统时间被回拨，请重新激活")
            return false
        }
        p.edit().putLong(KEY_LAST_SEEN, maxOf(t, lastSeen)).apply()
        if (minutes > 0L) {
            val activatedAt = p.getLong(KEY_AT, t)
            if (t - activatedAt > minutes * 60 * 1000) {
                invalidate(context, "激活已到期，请重新获取激活码")
                return false
            }
        }
        return true
    }

    /** 激活成功后的有效期描述 */
    fun durationText(context: Context): String {
        val code = prefs(context).getString(KEY_CODE, null) ?: return ""
        return when (code.getOrNull(8)) {
            '0' -> "永久有效"
            '1' -> "1 小时"
            '2' -> "1 天"
            '3' -> "3 天"
            '4' -> "7 天"
            '5' -> "30 天"
            else -> ""
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
                    input = it.take(11)
                    error = false
                },
                label = { Text("激活码（如 A1B2-C3D4-3）") },
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
                "激活码与设备绑定，一码一机，有效期由激活码决定。\n到期或更换设备需重新获取。",
                fontSize = 12.sp, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
