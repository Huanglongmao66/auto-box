package com.tvbox.core.ui.components

import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.tvbox.deviceapi.player.IPlayer
import com.tvbox.platform.android.ExoPlayerImpl

/**
 * Android 平台视频渲染表面。
 *
 * 通过 [AndroidView] 承载 media3 的 [PlayerView]，并从 [ExoPlayerImpl] 取出底层
 * [ExoPlayer] 实例绑定到视图。直播 / 点播均由此渲染真实画面。
 */
@UnstableApi
@Composable
actual fun PlatformVideoSurface(player: IPlayer, modifier: Modifier) {
    // 仅 ExoPlayerImpl 能提供真实渲染；其他实现退化为占位
    val exoPlayer: ExoPlayer? = remember(player) {
        (player as? ExoPlayerImpl)?.getExoPlayer()
    }

    if (exoPlayer == null) {
        Box(modifier = modifier)
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                this.player = exoPlayer
            }
        },
        update = { view ->
            view.player = exoPlayer
        }
    )

    DisposableEffect(exoPlayer) {
        onDispose {
            // 视图销毁时不释放 player（player 由调用方管理生命周期）
        }
    }
}
