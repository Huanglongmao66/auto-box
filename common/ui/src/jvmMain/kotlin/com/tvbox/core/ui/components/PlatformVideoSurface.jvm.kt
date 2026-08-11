package com.tvbox.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.tvbox.deviceapi.player.IPlayer

/**
 * 桌面端视频渲染表面占位实现。
 *
 * 桌面端后续可接入 VLCJ / JavaFX Media 真实渲染；当前渲染为黑色占位，
 * 由 LivePlayerScreen 的控制层与状态展示覆盖。
 */
@Composable
actual fun PlatformVideoSurface(player: IPlayer, modifier: Modifier) {
    Box(modifier = modifier.background(Color.Black))
}
