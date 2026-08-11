package com.tvbox.core.ui.components

import androidx.compose.runtime.Composable

/**
 * 跨平台返回键处理器。
 *
 * - Android：基于 [androidx.activity.compose.BackHandler] 拦截系统返回键。
 * - 桌面 / Web：当前为 no-op（后续可扩展为 ESC 键监听）。
 *
 * @param enabled 是否启用返回键拦截
 * @param onBack 返回回调
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
