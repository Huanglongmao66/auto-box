plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":common:core"))
            implementation(project(":common:device-api"))
            implementation(project(":common:utils"))
            implementation(project(":common:ui"))
            implementation(project(":platform:desktop"))
            implementation(project(":feature"))
            implementation(compose.desktop.currentOs)
            implementation(compose.foundation)
            implementation(compose.material3)
        }
    }
}

// Compose Desktop 应用配置
compose.desktop {
    application {
        mainClass = "com.tvbox.app.desktop.MainKt"

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )
            packageName = "TVBox"
            packageVersion = "1.0.0"
        }
    }
}
