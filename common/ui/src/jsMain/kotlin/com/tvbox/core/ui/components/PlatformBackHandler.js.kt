package com.tvbox.core.ui.components

import androidx.compose.runtime.Composable

/** JS 平台返回键处理器：当前 no-op（浏览器可扩展为 popstate 监听）。 */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // no-op
}
