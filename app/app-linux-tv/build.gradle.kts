plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    application
}

kotlin {
    jvm()

    sourceSets {
        jvmMain {
            kotlin.srcDir("src/main/kotlin")
            dependencies {
                implementation(project(":common:core"))
                implementation(project(":common:ui"))
                implementation(project(":common:device-api"))
                implementation(project(":common:utils"))
                implementation(project(":platform:linux-tv"))
                implementation(project(":feature"))
                implementation(compose.desktop.currentOs)
                implementation(compose.foundation)
                implementation(compose.material3)
            }
        }
    }
}

application {
    mainClass.set("com.tvbox.app.linuxtv.MainKt")
}

compose.desktop {
    application {
        mainClass = "com.tvbox.app.linuxtv.MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.AppImage,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Tar
            )
            packageName = "TVBox"
            packageVersion = "1.0.0"
            description = "TVBox Multiplatform for Linux TV"
        }
    }
}
