plugins {
    alias(libs.plugins.kotlin.multiplatform)
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
                implementation(libs.ktor.client.js)
            }
        }
    }
}
