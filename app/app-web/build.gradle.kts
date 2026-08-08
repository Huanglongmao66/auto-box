plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    js { browser() }

    sourceSets {
        jsMain.dependencies {
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
