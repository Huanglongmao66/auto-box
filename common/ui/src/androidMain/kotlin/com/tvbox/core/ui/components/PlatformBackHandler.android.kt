package com.tvbox.core.ui.components

import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler

/**
 * Android 平台返回键处理器：基于 androidx.activity.compose.BackHandler。
 */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}
