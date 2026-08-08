plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    linuxX64()

    sourceSets {
        linuxX64Main.dependencies {
            implementation(project(":common:device-api"))
            implementation(project(":common:core"))
            implementation(project(":common:utils"))
            implementation(libs.ktor.client.okhttp)
        }
    }
}
