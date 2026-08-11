package com.tvbox.platform.android

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.tvbox.deviceapi.player.IPlayer
import com.tvbox.deviceapi.player.PlayerListener
import com.tvbox.deviceapi.player.TrackInfo
import com.tvbox.deviceapi.player.TrackType
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 基于 androidx.media3 ExoPlayer 的播放器实现
 *
 * 兼容原版 TVBox 播放内核能力，支持 HLS / MP4 等主流格式、
 * 播放速度与音量控制、音轨 / 字幕切换以及外部字幕加载。
 *
 * 通过 [DefaultHttpDataSource.Factory] 设置默认请求头与 UA，
 * 保证直播源（防盗链 / Referer 限制）与伪装后的订阅源均可拉取。
 *
 * @param context Android 上下文
 */
class ExoPlayerImpl(
    private val context: Context
) : IPlayer {

    /** 底层 ExoPlayer 实例 */
    private var player: ExoPlayer? = null

    /** 当前媒体项 */
    private var mediaItem: MediaItem? = null

    /** 当前播放速度 */
    private var currentSpeed: Float = SPEED_DEFAULT

    /** 当前音量（0.0 ~ 1.0） */
    private var currentVolume: Float = VOLUME_DEFAULT

    /** 业务层监听器集合（线程安全） */
    private val listeners = CopyOnWriteArraySet<PlayerListener>()

    /** 主线程 Handler，用于轮询播放进度 */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 当前加载的字幕地址 */
    private var externalSubtitleUrl: String? = null

    /** 音轨 ID -> ExoPlayer 轨道引用映射 */
    private val audioTrackMap = mutableMapOf<Int, TrackRef>()

    /** 字幕 ID -> ExoPlayer 轨道引用映射 */
    private val subtitleTrackMap = mutableMapOf<Int, TrackRef>()

    /** HTTP 数据源工厂（设置默认请求头 + UA） */
    private val httpDataSourceFactory: DefaultHttpDataSource.Factory by lazy {
        DefaultHttpDataSource.Factory()
            .setUserAgent(PLAYER_USER_AGENT)
            .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(READ_TIMEOUT_MS)
            .setAllowCrossProtocolRedirects(true)
    }

    /** DataSource 组合工厂：默认（本地 assets/file/content） + HTTP */
    private val dataSourceFactory: DefaultDataSource.Factory by lazy {
        DefaultDataSource.Factory(context, httpDataSourceFactory)
    }

    /** 播放进度轮询任务 */
    private val progressRunnable = object : Runnable {
        override fun run() {
            val p = player ?: return
            if (p.isPlaying) {
                val position = p.currentPosition.coerceAtLeast(0L)
                val duration = p.duration.takeIf { it != C.TIME_UNSET } ?: 0L
                notify { it.onProgressChanged(position, duration) }
                mainHandler.postDelayed(this, PROGRESS_INTERVAL_MS)
            }
        }
    }

    /** ExoPlayer 事件适配器，桥接到业务监听器 */
    private val exoListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> notify { it.onBufferingStateChanged(true) }
                Player.STATE_READY -> {
                    notify { it.onBufferingStateChanged(false) }
                    notify { it.onReady() }
                }
                Player.STATE_ENDED -> notify { it.onCompletion() }
                Player.STATE_IDLE -> notify { it.onBufferingStateChanged(false) }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                notify { it.onPlay() }
                mainHandler.removeCallbacks(progressRunnable)
                mainHandler.post(progressRunnable)
            } else {
                mainHandler.removeCallbacks(progressRunnable)
                notify { it.onPause() }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            notify { it.onError(error.errorCode, error.message.orEmpty()) }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            notify { it.onVideoSizeChanged(videoSize.width, videoSize.height) }
        }
    }

    init {
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { it.addListener(exoListener) }
    }

    // ===== 播放控制 =====

    override fun setDataSource(url: String, headers: Map<String, String>, subtitleUrl: String?) {
        externalSubtitleUrl = subtitleUrl

        // 注入当前请求的自定义请求头（合并到默认 UA）
        if (headers.isNotEmpty()) {
            val merged = buildMap<String, String> {
                // 用户自定义头优先（覆盖默认）
                putAll(headers)
                // 若调用方未指定 UA，则使用播放器内置 UA
                if (!keys.any { it.equals("User-Agent", ignoreCase = true) }) {
                    put("User-Agent", PLAYER_USER_AGENT)
                }
            }
            httpDataSourceFactory.setDefaultRequestProperties(merged)
        } else {
            // 无自定义头，仅保留播放器 UA
            httpDataSourceFactory.setDefaultRequestProperties(
                mapOf("User-Agent" to PLAYER_USER_AGENT)
            )
        }

        val builder = MediaItem.Builder().setUri(url)

        val subtitleConfigs = buildSubtitleConfigurations(subtitleUrl)
        if (subtitleConfigs.isNotEmpty()) {
            builder.setSubtitleConfigurations(subtitleConfigs)
        }

        val item = builder.build()
        mediaItem = item
        player?.run {
            stop()
            clearMediaItems()
            setMediaItem(item)
            prepare()
        }
    }

    override fun play() {
        player?.play()
    }

    override fun pause() {
        player?.pause()
    }

    override fun stop() {
        mainHandler.removeCallbacks(progressRunnable)
        player?.stop()
    }

    override fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }

    override fun release() {
        mainHandler.removeCallbacks(progressRunnable)
        player?.run {
            removeListener(exoListener)
            release()
        }
        player = null
        listeners.clear()
        audioTrackMap.clear()
        subtitleTrackMap.clear()
    }

    // ===== 播放状态 =====

    override fun getCurrentPosition(): Long =
        player?.currentPosition?.coerceAtLeast(0L) ?: 0L

    override fun getDuration(): Long {
        val duration = player?.duration ?: C.TIME_UNSET
        return if (duration == C.TIME_UNSET) 0L else duration
    }

    override fun isPlaying(): Boolean = player?.isPlaying == true

    override fun getBufferedPercentage(): Int = player?.bufferedPercentage ?: 0

    // ===== 播放配置 =====

    override fun setSpeed(speed: Float) {
        currentSpeed = speed.coerceIn(SPEED_MIN, SPEED_MAX)
        player?.playbackParameters = PlaybackParameters(currentSpeed)
    }

    override fun getSpeed(): Float = currentSpeed

    override fun setVolume(volume: Float) {
        currentVolume = volume.coerceIn(VOLUME_MIN, VOLUME_MAX)
        player?.volume = currentVolume
    }

    override fun getVolume(): Float = currentVolume

    override fun setVideoView(renderView: Any) {
        if (renderView is PlayerView) {
            renderView.player = player
        }
    }

    /**
     * 暴露底层 ExoPlayer 实例，供 Compose AndroidView 直接绑定 PlayerView 渲染。
     *
     * 仅用于 UI 层渲染绑定，调用方不得在此实例上执行 release 等破坏生命周期的操作。
     */
    fun getExoPlayer(): ExoPlayer? = player

    // ===== 音轨 / 字幕 =====

    override fun getAudioTracks(): List<TrackInfo> {
        audioTrackMap.clear()
        val result = mutableListOf<TrackInfo>()
        var id = 0
        player?.currentTracks?.groups?.forEach { group ->
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.length) {
                    if (!group.isTrackSupported(i)) continue
                    val format = group.getTrackFormat(i)
                    val trackId = id++
                    audioTrackMap[trackId] = TrackRef(group.mediaTrackGroup, i)
                    result.add(
                        TrackInfo(
                            id = trackId,
                            type = TrackType.AUDIO,
                            language = format.language,
                            label = format.label
                        )
                    )
                }
            }
        }
        return result
    }

    override fun selectAudioTrack(trackId: Int) {
        val ref = audioTrackMap[trackId] ?: return
        val params = player?.trackSelectionParameters?.buildUpon()
            ?.setOverrideForType(TrackSelectionOverride(ref.group, ref.trackIndex))
            ?.build() ?: return
        player?.trackSelectionParameters = params
        notify { it.onTrackChanged(TrackType.AUDIO, trackId) }
    }

    override fun getSubtitleTracks(): List<TrackInfo> {
        subtitleTrackMap.clear()
        val result = mutableListOf<TrackInfo>()
        var id = 0
        player?.currentTracks?.groups?.forEach { group ->
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (i in 0 until group.length) {
                    if (!group.isTrackSupported(i)) continue
                    val format = group.getTrackFormat(i)
                    val trackId = id++
                    subtitleTrackMap[trackId] = TrackRef(group.mediaTrackGroup, i)
                    result.add(
                        TrackInfo(
                            id = trackId,
                            type = TrackType.SUBTITLE,
                            language = format.language,
                            label = format.label
                        )
                    )
                }
            }
        }
        return result
    }

    override fun selectSubtitleTrack(trackId: Int) {
        val ref = subtitleTrackMap[trackId] ?: return
        val params = player?.trackSelectionParameters?.buildUpon()
            ?.setOverrideForType(TrackSelectionOverride(ref.group, ref.trackIndex))
            ?.build() ?: return
        player?.trackSelectionParameters = params
        notify { it.onTrackChanged(TrackType.SUBTITLE, trackId) }
    }

    override fun setExternalSubtitle(url: String) {
        externalSubtitleUrl = url
        val current = mediaItem ?: return
        val newItem = current.buildUpon()
            .setSubtitleConfigurations(buildSubtitleConfigurations(url))
            .build()
        mediaItem = newItem
        player?.run {
            stop()
            clearMediaItems()
            setMediaItem(newItem)
            prepare()
        }
    }

    // ===== 监听 =====

    override fun addListener(listener: PlayerListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: PlayerListener) {
        listeners.remove(listener)
    }

    // ===== 内部辅助 =====

    private inline fun notify(block: (PlayerListener) -> Unit) {
        listeners.forEach { listener -> runCatching { block(listener) } }
    }

    private fun buildSubtitleConfigurations(subtitleUrl: String?): List<MediaItem.SubtitleConfiguration> {
        if (subtitleUrl.isNullOrBlank()) return emptyList()
        return listOf(
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitleUrl))
                .setMimeType(guessSubtitleMimeType(subtitleUrl))
                .build()
        )
    }

    private fun guessSubtitleMimeType(url: String): String {
        val ext = url.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "vtt" -> MimeTypes.TEXT_VTT
            "srt" -> MimeTypes.APPLICATION_SUBRIP
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            "ttml", "xml" -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.TEXT_VTT
        }
    }

    private data class TrackRef(
        val group: androidx.media3.common.TrackGroup,
        val trackIndex: Int
    )

    companion object {
        private const val PROGRESS_INTERVAL_MS = 500L
        private const val SPEED_DEFAULT = 1.0f
        private const val SPEED_MIN = 0.25f
        private const val SPEED_MAX = 4.0f
        private const val VOLUME_DEFAULT = 1.0f
        private const val VOLUME_MIN = 0.0f
        private const val VOLUME_MAX = 1.0f
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000

        /** 播放器专用浏览器 User-Agent（避免被 Cloudflare 等防护拦截） */
        private const val PLAYER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
