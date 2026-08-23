package com.tvbox.app.linuxtv

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tvbox.core.di.ServiceLocator
import com.tvbox.core.ui.TVBoxApp
import com.tvbox.platform.linuxtv.LinuxTvDeviceApi

/**
 * 嵌入式 Linux TV 应用入口（基于 Compose Desktop JVM）
 *
 * 初始化依赖注入并启动 TVBox 主窗口。
 * - TV / 大屏设备：默认 16:9（1920x1080），深色模式
 * - 焦点导航：TVBoxApp 内部已集成 D-pad 友好的导航框架
 */
fun main() = application {
    ServiceLocator.initialize(LinuxTvDeviceApi())
    Window(
        onCloseRequest = { exitApplication() },
        title = "TVBox for Linux TV",
        undecorated = false,
        resizable = true,
        state = rememberWindowState(width = 1920.dp, height = 1080.dp)
    ) {
        TVBoxApp(modifier = Modifier.fillMaxSize(), initialDarkMode = true)
    }
}
