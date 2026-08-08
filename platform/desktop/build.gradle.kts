plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvm()

    sourceSets {
        jvmMain {
            kotlin.srcDir("src/main/kotlin")
            dependencies {
                implementation(project(":common:device-api"))
                implementation(project(":common:core"))
                implementation(project(":common:utils"))
                implementation(compose.desktop.currentOs)
                implementation(libs.ktor.client.okhttp)
            }
        }
    }
}
