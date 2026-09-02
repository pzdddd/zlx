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
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 一机一码离线激活：
 *  设备码 = SHA-256(ANDROID_ID + 盐) 前 16 位十六进制（XXXX-XXXX-XXXX-XXXX），每台设备唯一；
 *  激活码 = HMAC-SHA256(仅持有者知道的密钥, 设备码) 前 8 位（XXXX-XXXX）。
 * 激活码只有拿到密钥的发放者才能计算，且只对对应设备有效，应用本地校验、无需服务器。
 */
object ActivationManager {
    private const val PREFS_NAME = "activation"
    private const val KEY_CODE = "code"

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

    /** 由设备码计算对应激活码（与发放者生成器算法一致） */
    fun expectedCode(context: Context): String {
        val dev = deviceCode(context).replace("-", "")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        val hash = mac.doFinal(dev.toByteArray())
        val hex = hash.take(4).joinToString("") { "%02X".format(it) }
        return hex.chunked(4).joinToString("-")
    }

    fun isActivated(context: Context): Boolean {
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CODE, null) ?: return false
        return saved.replace("-", "").equals(expectedCode(context).replace("-", ""), ignoreCase = true)
    }

    /** 校验并保存。返回是否激活成功 */
    fun activate(context: Context, input: String): Boolean {
        val ok = input.replace("-", "").trim()
            .equals(expectedCode(context).replace("-", ""), ignoreCase = true)
        if (ok) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_CODE, input.trim()).apply()
        }
        return ok
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
            Spacer(modifier = Modifier.height(36.dp))

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
                    input = it.take(9)
                    error = false
                },
                label = { Text("激活码（如 A1B2-C3D4）") },
                isError = error,
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrect = false, keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii),
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
                        Toast.makeText(context, "激活成功！", Toast.LENGTH_SHORT).show()
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
                "激活码与设备绑定，一码一机。\n更换设备需重新获取。",
                fontSize = 12.sp, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
