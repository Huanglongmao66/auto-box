package com.tvbox.app.web

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.CanvasBasedWindow
import com.tvbox.core.di.ServiceLocator
import com.tvbox.core.ui.TVBoxApp
import com.tvbox.platform.web.WebDeviceApi

/**
 * Web（Kotlin/JS 浏览器）应用入口
 *
 * 创建平台对应的 [WebDeviceApi] 并初始化全局依赖注入，
 * 随后通过 Compose Canvas 将 [TVBoxApp] 渲染到浏览器。
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ServiceLocator.initialize(WebDeviceApi())
    CanvasBasedWindow(title = "TVBox 多平台版") {
        TVBoxApp(modifier = Modifier.fillMaxSize(), initialDarkMode = true)
    }
}
