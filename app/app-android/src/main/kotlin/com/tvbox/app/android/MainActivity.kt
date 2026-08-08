package com.tvbox.app.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tvbox.core.ui.theme.AppTheme

/**
 * Android 主 Activity
 *
 * 使用 Jetpack Compose 承载 UI，以 [AppTheme] 包裹根内容，
 * 当前为影视源列表占位框架，后续接入 [com.tvbox.core.source.SourceManager] 渲染真实数据。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                HomeScreen()
            }
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
