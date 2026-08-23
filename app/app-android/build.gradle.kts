plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    androidTarget()

    sourceSets {
        androidMain {
            kotlin.srcDir("src/main/kotlin")
            dependencies {
                implementation(project(":common:core"))
                implementation(project(":common:device-api"))
                implementation(project(":common:utils"))
                implementation(project(":common:ui"))
                implementation(project(":platform:android"))
                implementation(project(":feature"))
                implementation(compose.preview)
                implementation(compose.ui)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(libs.media3.exoplayer)
                implementation("androidx.activity:activity-compose:1.9.3")
            }
        }
    }
}

android {
    namespace = "com.tvbox.app.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tvbox.app"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
