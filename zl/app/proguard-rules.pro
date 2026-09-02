# ===== 体积优化：通用压缩规则 =====

# 移除无用的 Log 调用（生产环境不需要日志）
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
}

# 保留行号信息，方便 crash 排查
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ===== Compose 相关：保留 Compose 运行时所需的反射入口 =====
# Compose 编译器已经做了大部分优化，但需要保留 Composable 函数元数据
-dontwarn androidx.compose.**

# ===== Kotlin 相关 =====
# 保留 Kotlin Metadata（反射库需要）
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata { *; }

# 保留 Kotlin 协程内部实现
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ===== FFmpeg-kit =====
-keep class com.arthenica.ffmpegkit.** { *; }
-dontwarn com.arthenica.ffmpegkit.**

# ===== Coil (图片加载) =====
-dontwarn coil.**

# ===== Media3 / ExoPlayer =====
-dontwarn androidx.media3.**

# ===== 保留 ViewBinding 生成的类 =====
-keep class com.fuck.zlx.databinding.** { *; }

# ===== 保留 Parcelable 序列化 =====
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# ===== 保留枚举 =====
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ===== 保留 R 文件（资源引用） =====
-keep class **.R$* { *; }

# ===== 激进优化：合并接口、内联方法 =====
-allowaccessmodification
-mergeinterfacesaggressively

# ===== 移除注解（减少体积） =====
# 注意：保留 Compose 和 Kotlin 需要的注解
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,RuntimeVisibleTypeAnnotations
# ===== WebView JS 桥：必须保留 @JavascriptInterface 方法（嗅探核心），否则 release 下网页嗅探失效 =====
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
