package com.tvbox.core.ui.components

import androidx.compose.runtime.Composable

/** JVM 平台返回键处理器：当前 no-op（桌面可扩展为 ESC 键监听）。 */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // no-op
}
