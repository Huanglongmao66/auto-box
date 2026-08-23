package com.tvbox.app.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.tvbox.core.di.ServiceLocator
import com.tvbox.core.ui.TVBoxApp
import com.tvbox.platform.android.AndroidDeviceApi

/**
 * Android 主 Activity
 *
 * 使用 Jetpack Compose 承载统一的 [TVBoxApp]，在 onCreate 时
 * 初始化全局依赖注入（AndroidDeviceApi）。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServiceLocator.initialize(AndroidDeviceApi(context = this))
        setContent {
            TVBoxApp(modifier = Modifier.fillMaxSize(), initialDarkMode = true)
        }
    }
}
