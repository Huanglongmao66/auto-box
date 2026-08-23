plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.sqldelight) apply false
}

allprojects {
    group = "com.tvbox"
    version = "1.0.0"

    repositories {
        google()
        mavenCentral()
    }
}

// 全局编译参数配置：支持平台切换、播放器内核切换、功能裁剪
ext {
    // 编译目标平台：android / desktop / web / linux
    set("targetPlatform", project.findProperty("platform") ?: "all")
    // 播放器内核：exo / mpv / vlc
    set("playerEngine", project.findProperty("player") ?: "exo")
    // 功能开关
    set("featureCast", project.findProperty("featureCast")?.toString()?.toBoolean() ?: true)
    set("featureUpdate", project.findProperty("featureUpdate")?.toString()?.toBoolean() ?: true)
    set("minify", project.findProperty("minify")?.toString()?.toBoolean() ?: false)
}
