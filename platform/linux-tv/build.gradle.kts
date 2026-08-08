plugins {
    alias(libs.plugins.kotlin.multiplatform)
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
                implementation(libs.ktor.client.okhttp)
            }
        }
    }
}
