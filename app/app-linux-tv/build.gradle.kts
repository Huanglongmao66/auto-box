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
                // Compose Desktop Runtime + Skiko，用于实际运行；
                // 注意：这里不使用 compose.desktop.application{} 块，
                // 因为它会与 application 插件的 installDist/run 任务冲突。
                implementation(compose.desktop.currentOs)
                implementation(compose.foundation)
                implementation(compose.material3)
            }
        }
    }
}

application {
    mainClass.set("com.tvbox.app.linuxtv.MainKt")
    applicationName = "TVBox"
}
