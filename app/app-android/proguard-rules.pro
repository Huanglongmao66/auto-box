# ===== Kotlin 相关 =====
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }

# ===== Kotlinx Serialization =====
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# 保留所有 @Serializable 标注的类
-keep,allowobfuscation,allowshrinking @kotlinx.serialization.Serializable class *
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# ===== Compose 相关 =====
-dontwarn androidx.compose.**

# ===== Ktor 相关 =====
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }

# ===== Media3 / ExoPlayer =====
-dontwarn androidx.media3.**

# ===== 项目业务类（反射/序列化需要保留） =====
-keep class com.tvbox.core.model.** { *; }
-keep class com.tvbox.deviceapi.** { *; }
-keep class com.tvbox.core.source.** { *; }

# ===== OkHttp =====
-dontwarn okhttp3.**
-dontwarn okio.**
