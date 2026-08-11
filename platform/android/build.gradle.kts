plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    androidTarget()

    sourceSets {
        androidMain {
            kotlin.srcDir("src/main/kotlin")
            dependencies {
                implementation(project(":common:device-api"))
                implementation(project(":common:core"))
                implementation(project(":common:utils"))
                implementation(libs.media3.exoplayer)
                implementation(libs.media3.ui)
                implementation(libs.media3.hls)
                implementation(libs.okhttp)
                // Gson（Jar 爬虫依赖的 JSON 库）
                implementation(libs.gson)
                // QuickJS 引擎（JS 爬虫运行时）
                implementation(libs.quickjs.wrapper.android)
                implementation(libs.quickjs.wrapper.java)
                // JSoup（HTML 解析，JS 爬虫 pdfa/pdfh 辅助）
                implementation(libs.jsoup)
            }
        }
    }
}

android {
    namespace = "com.tvbox.platform.android"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
