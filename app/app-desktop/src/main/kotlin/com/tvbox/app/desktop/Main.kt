package com.tvbox.app.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tvbox.core.di.ServiceLocator
import com.tvbox.core.ui.TVBoxApp
import com.tvbox.platform.desktop.DesktopDeviceApi

/**
 * Desktop（Windows / macOS / Linux）应用入口
 *
 * 创建平台对应的 [DesktopDeviceApi] 并初始化全局依赖注入，
 * 随后启动 Compose Desktop 主窗口，以统一的 [TVBoxApp] 渲染全部页面。
 */
fun main() = application {
    ServiceLocator.initialize(DesktopDeviceApi())
    Window(
        onCloseRequest = { exitApplication() },
        title = "TVBox 多平台版",
        state = rememberWindowState(width = 1440.dp, height = 900.dp)
    ) {
        TVBoxApp(modifier = Modifier.fillMaxSize(), initialDarkMode = true)
    }
}
