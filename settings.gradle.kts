pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "auto-box"

// ===== 公共模块（全平台复用） =====
include(":common:core")
include(":common:device-api")
include(":common:utils")
include(":common:ui")

// ===== 平台实现模块 =====
include(":platform:android")
include(":platform:desktop")
include(":platform:web")
include(":platform:linux-tv")

// ===== 各平台打包入口 =====
include(":app:app-android")
include(":app:app-desktop")
include(":app:app-web")
include(":app:app-linux-tv")

// ===== 可选扩展模块 =====
include(":feature")
