package com.tvbox.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tvbox.deviceapi.player.IPlayer

/**
 * 跨平台视频渲染表面。
 *
 * - Android：基于 ExoPlayer + PlayerView（AndroidView 包装）真实渲染视频。
 * - 其他平台：占位展示（桌面/Web 后续接入各自播放内核）。
 *
 * 调用方负责 [IPlayer] 的生命周期管理（setDataSource / play / release）。
 *
 * @param player 跨平台播放器实例
 * @param modifier 修饰符
 */
@Composable
expect fun PlatformVideoSurface(player: IPlayer, modifier: Modifier = Modifier)
