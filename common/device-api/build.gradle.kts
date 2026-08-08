plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    // 目标平台配置
    androidTarget()
    jvm()
    js { browser() }
    linuxX64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlin.stdlib)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
