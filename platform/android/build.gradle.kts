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
