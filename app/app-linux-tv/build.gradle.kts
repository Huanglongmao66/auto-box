plugins {
    alias(libs.plugins.kotlin.multiplatform)
    application
}

kotlin {
    jvm {
        withJava()
    }

    sourceSets {
        jvmMain {
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

application {
    mainClass.set("com.tvbox.app.linuxtv.MainKt")
}
