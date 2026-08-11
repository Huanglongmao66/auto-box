plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    androidTarget()
    jvm()
    js { browser() }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":common:core"))
            implementation(project(":common:device-api"))
            implementation(project(":common:utils"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.coil.compose)
        }
        androidMain.dependencies {
            // 直播播放器需要真正的 ExoPlayer + PlayerView 渲染
            implementation(project(":platform:android"))
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.ui)
            implementation(libs.media3.hls)
            // BackHandler 需要 activity-compose
            implementation("androidx.activity:activity-compose:1.9.3")
        }
    }
}

android {
    namespace = "com.tvbox.ui"
    compileSdk = 34
    defaultConfig {
        minSdk = 21
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
