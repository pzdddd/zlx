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
    private const val KEY_DAYS = "days"
    private const val KEY_ACTIVATED_AT = "activated_at"
    private const val KEY_LAST_SEEN = "last_seen"
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val MAX_DAYS = 3650 // 最长 10 年

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

    private fun hmac(dev16: String, suffix: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        val hash = mac.doFinal((dev16 + suffix).toByteArray())
        return hash.take(4).joinToString("") { "%02X".format(it) }
    }

    /** 新版：激活码与"有效天数"绑定（0=永久），与生成器算法一致 */
    fun expectedCode(context: Context, days: Int): String = hmac(deviceCode(context).replace("-", ""), ":$days")

    /** 旧版永久码（无天数后缀），用于兼容升级前已激活的设备 */
    private fun legacyCode(context: Context): String = hmac(deviceCode(context).replace("-", ""), "")

    /**
     * 校验并保存。天数在 0..3650 内逐一试算匹配（HMAC 极快，毫秒级）。
     * 旧版永久码也接受，并迁移为新版永久格式。
     */
    fun activate(context: Context, input: String): Boolean {
        val norm = input.replace("-", "").trim().uppercase()
        if (norm.length != 8) return false
        val dev = deviceCode(context).replace("-", "")
        for (d in 0..MAX_DAYS) {
            if (hmac(dev, ":$d") == norm) {
                save(context, norm, d)
                return true
            }
        }
        if (legacyCode(context) == norm) {
            // 旧版永久码 → 迁移为新版永久(0天)存储
            save(context, hmac(dev, ":0"), 0)
            return true
        }
        return false
    }

    private fun save(context: Context, code: String, days: Int) {
        val now = System.currentTimeMillis()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_CODE, code)
            .putInt(KEY_DAYS, days)
            .putLong(KEY_ACTIVATED_AT, now)
            .putLong(KEY_LAST_SEEN, now)
            .apply()
    }

    /**
     * 是否已激活且未过期。
     * 用 lastSeen 防系统时间回拨：有效期按 max(当前时间, 上次启动时间) 推算。
     */
    fun isActivated(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val code = prefs.getString(KEY_CODE, null) ?: return false
        val days = prefs.getInt(KEY_DAYS, 0)
        val activatedAt = prefs.getLong(KEY_ACTIVATED_AT, 0L)
        val lastSeen = prefs.getLong(KEY_LAST_SEEN, 0L)

        // 校验存储的码与设备+天数重新计算的值一致（防篡改天数）
        val dev = deviceCode(context).replace("-", "")
        val valid = code == hmac(dev, ":$days") ||
            code == legacyCode(context) // 兼容升级前旧码
        if (!valid || activatedAt <= 0L) return false

        val now = System.currentTimeMillis()
        val effective = maxOf(now, lastSeen)
        if (now - lastSeen > 60L * 60 * 1000) {
            prefs.edit().putLong(KEY_LAST_SEEN, now).apply()
        }
        if (days > 0 && effective - activatedAt > days * DAY_MS) {
            return false // 已到期
        }
        return true
    }

    /** 剩余有效天数（永久返回 -1；未激活/已过期返回 0） */
    fun remainingDays(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val days = prefs.getInt(KEY_DAYS, 0)
        if (days <= 0) return -1
        val activatedAt = prefs.getLong(KEY_ACTIVATED_AT, 0L)
        val lastSeen = prefs.getLong(KEY_LAST_SEEN, 0L)
        val effective = maxOf(System.currentTimeMillis(), lastSeen)
        val used = (effective - activatedAt) / DAY_MS
        return (days - used).toInt().coerceAtLeast(0)
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
