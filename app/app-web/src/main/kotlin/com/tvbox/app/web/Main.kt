package com.tvbox.app.web

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.CanvasBasedWindow
import com.tvbox.core.di.ServiceLocator
import com.tvbox.core.ui.theme.AppTheme
import com.tvbox.platform.web.WebDeviceApi

/**
 * Web（Kotlin/JS 浏览器）应用入口
 *
 * 创建平台对应的 [WebDeviceApi] 并初始化全局依赖注入，
 * 随后通过 Compose Canvas 将根内容渲染到浏览器，以 [AppTheme] 包裹。
 * 当前为影视源列表占位框架，后续接入 SourceManager 渲染真实数据。
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ServiceLocator.initialize(WebDeviceApi())
    CanvasBasedWindow(title = "TVBox") {
        AppTheme {
            HomeScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen() {
    Scaffold(
        topBar = { TopAppBar(title = { Text("影视源") }) }
    ) { padding ->
        // 影视源列表占位，后续接入 SourceManager 渲染真实数据
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(placeholderSources()) { source ->
                Text(text = source, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

private fun placeholderSources(): List<String> =
    listOf("影视源 1", "影视源 2", "影视源 3")
