plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    js {
        browser()
        binaries.executable()
    }

    sourceSets {
        jsMain {
            kotlin.srcDir("src/main/kotlin")
            dependencies {
                implementation(project(":common:core"))
                implementation(project(":common:device-api"))
                implementation(project(":common:utils"))
                implementation(project(":common:ui"))
                implementation(project(":platform:web"))
                implementation(project(":feature"))
                implementation(compose.html.core)
                implementation(compose.ui)
                implementation(compose.foundation)
                implementation(compose.material3)
            }
        }
    }
}
