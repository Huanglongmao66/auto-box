plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    linuxX64 {
        binaries {
            executable {
                entryPoint = "com.tvbox.app.linuxtv.main"
            }
        }
    }

    sourceSets {
        linuxX64Main {
            kotlin.srcDir("src/main/kotlin")
            dependencies {
                implementation(project(":common:core"))
                implementation(project(":common:device-api"))
                implementation(project(":common:utils"))
                implementation(project(":platform:linux-tv"))
                implementation(project(":feature"))
            }
        }
    }
}
