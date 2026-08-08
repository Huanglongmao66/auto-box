plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    js { browser() }

    sourceSets {
        jsMain {
            kotlin.srcDir("src/main/kotlin")
            dependencies {
                implementation(project(":common:device-api"))
                implementation(project(":common:core"))
                implementation(project(":common:utils"))
                implementation(compose.html.core)
                implementation(libs.ktor.client.js)
            }
        }
    }
}
