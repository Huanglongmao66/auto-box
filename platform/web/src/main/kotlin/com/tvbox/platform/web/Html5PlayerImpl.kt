package com.tvbox.platform.web

import com.tvbox.deviceapi.player.IPlayer
import com.tvbox.deviceapi.player.PlayerListener
import com.tvbox.deviceapi.player.TrackInfo
import com.tvbox.deviceapi.player.TrackType
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLVideoElement
import org.w3c.dom.events.Event

/**
 * Web 平台播放器实现
 *
 * 基于 HTML5 `<video>` 元素与 hls.js 实现，支持 MP4 / WebM / HLS 等主流格式，
 * 兼容原版 TVBox 播放内核能力。HLS 流通过 hls.js 处理（待对接），原生支持
 * HLS 的浏览器（Safari）直接走 `video.src`。
 *
 * 业务层统一通过 [IPlayer] 调用，与 Android ExoPlayer / Desktop MPV 实现对齐。
 */
class Html5PlayerImpl : IPlayer {

    /** HTML5 video 元素，懒加载并绑定媒体事件 */
    private val video: HTMLVideoElement by lazy {
        document.createElement("video").unsafeCast<HTMLVideoElement>().also { bindMediaEvents(it) }
    }

    /** hls.js 实例引用（待对接，通过 js 动态加载） */
    private var hlsInstance: dynamic = null

    /** 当前媒体地址 */
    private var currentUrl: String = ""

    /** 当前播放速度 */
    private var currentSpeed: Float = SPEED_DEFAULT

    /** 当前音量（0.0 ~ 1.0） */
    private var currentVolume: Float = VOLUME_DEFAULT

    /** 进度轮询定时器 ID */
    private var progressTimerId: Int = 0

    /** 业务层监听器集合（浏览器单线程，无需并发保护） */
    private val listeners = mutableSetOf<PlayerListener>()

    // ===== 播放控制 =====

    override fun setDataSource(url: String, headers: Map<String, String>, subtitleUrl: String?) {
        currentUrl = url
        // TODO: 通过 hls.js 加载 HLS 流（.m3u8），普通格式直接赋值 src
        //       headers 在浏览器侧受 CORS 限制，需通过 hls.js xhrSetup 传递
        destroyHls()
        if (url.endsWith(HLS_EXT) && !canPlayHlsNatively()) {
            // TODO: 初始化 hls.js 并绑定到 video 元素
            //       hlsInstance = js("new Hls()")
            //       hlsInstance.loadSource(url); hlsInstance.attachMedia(video)
            video.src = url
        } else {
            video.src = url
        }
        video.load()
    }

    override fun play() {
        // play() 返回 Promise，浏览器自动播放策略可能拦截
        val result = video.asDynamic().play()
        result?.catch { /* 忽略自动播放策略拦截 */ }
    }

    override fun pause() {
        video.pause()
    }

    override fun stop() {
        video.pause()
        video.currentTime = 0.0
        stopProgressTimer()
    }

    override fun seekTo(positionMs: Long) {
        video.currentTime = positionMs / MS_TO_SECONDS
    }

    override fun release() {
        stopProgressTimer()
        video.pause()
        video.removeAttribute("src")
        video.load()
        destroyHls()
        listeners.clear()
    }

    // ===== 播放状态 =====

    override fun getCurrentPosition(): Long = (video.currentTime * MS_TO_SECONDS).toLong()

    override fun getDuration(): Long {
        val duration = video.duration
        return if (duration.isNaN() || duration.isInfinite()) 0L else (duration * MS_TO_SECONDS).toLong()
    }

    override fun isPlaying(): Boolean = !video.paused && !video.ended

    override fun getBufferedPercentage(): Int {
        val duration = video.duration
        if (duration.isNaN() || duration.isInfinite() || duration <= 0.0) return 0
        val buffered = video.buffered
        if (buffered.length == 0) return 0
        val end = buffered.end(buffered.length - 1)
        return ((end / duration) * 100).toInt().coerceIn(0, 100)
    }

    // ===== 播放配置 =====

    override fun setSpeed(speed: Float) {
        currentSpeed = speed.coerceIn(SPEED_MIN, SPEED_MAX)
        video.playbackRate = currentSpeed.toDouble()
    }

    override fun getSpeed(): Float = currentSpeed

    override fun setVolume(volume: Float) {
        currentVolume = volume.coerceIn(VOLUME_MIN, VOLUME_MAX)
        video.volume = currentVolume.toDouble()
    }

    override fun getVolume(): Float = currentVolume

    override fun setVideoView(renderView: Any) {
        // TODO: 将外部传入的 video 元素替换内部默认实例
        //       （用于 Compose Web / 自定义播放容器场景）
    }

    // ===== 音轨 / 字幕 =====

    override fun getAudioTracks(): List<TrackInfo> {
        // TODO: HTML5 video 原生不暴露音轨列表，需通过 hls.js / MSE 读取
        return emptyList()
    }

    override fun selectAudioTrack(trackId: Int) {
        // TODO: 通过 hls.js / audioTracks API 切换音轨
        notify { it.onTrackChanged(TrackType.AUDIO, trackId) }
    }

    override fun getSubtitleTracks(): List<TrackInfo> {
        // TODO: 读取 video.textTracks 列表
        return emptyList()
    }

    override fun selectSubtitleTrack(trackId: Int) {
        // TODO: 通过 TextTrack API 切换字幕
        notify { it.onTrackChanged(TrackType.SUBTITLE, trackId) }
    }

    override fun setExternalSubtitle(url: String) {
        // TODO: 通过 <track> 元素加载外部字幕（VTT / SRT）
    }

    // ===== 监听 =====

    override fun addListener(listener: PlayerListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: PlayerListener) {
        listeners.remove(listener)
    }

    // ===== 内部辅助 =====

    /**
     * 绑定 HTML5 media 事件，桥接到业务监听器
     */
    private fun bindMediaEvents(video: HTMLVideoElement) {
        video.addEventListener("loadedmetadata") { _: Event ->
            notify { it.onReady() }
            notify { it.onBufferingStateChanged(false) }
        }
        video.addEventListener("play") { _: Event -> notify { it.onPlay() }; startProgressTimer() }
        video.addEventListener("pause") { _: Event -> notify { it.onPause() }; stopProgressTimer() }
        video.addEventListener("ended") { _: Event -> notify { it.onCompletion() }; stopProgressTimer() }
        video.addEventListener("waiting") { _: Event -> notify { it.onBufferingStateChanged(true) } }
        video.addEventListener("playing") { _: Event -> notify { it.onBufferingStateChanged(false) } }
        video.addEventListener("error") { _: Event ->
            notify { it.onError(ERROR_CODE_GENERIC, ERROR_MSG_PLAYBACK) }
        }
    }

    /**
     * 启动播放进度轮询定时器
     */
    private fun startProgressTimer() {
        stopProgressTimer()
        progressTimerId = window.setInterval({
            notify { it.onProgressChanged(getCurrentPosition(), getDuration()) }
        }, PROGRESS_INTERVAL_MS)
    }

    /**
     * 停止播放进度轮询定时器
     */
    private fun stopProgressTimer() {
        if (progressTimerId != 0) {
            window.clearInterval(progressTimerId)
            progressTimerId = 0
        }
    }

    /**
     * 销毁 hls.js 实例（若存在）
     */
    private fun destroyHls() {
        hlsInstance?.destroy?.invoke()
        hlsInstance = null
    }

    /**
     * 检测浏览器是否原生支持 HLS 播放（Safari）
     */
    private fun canPlayHlsNatively(): Boolean {
        return video.canPlayType(HLS_MIME_TYPE) != ""
    }

    /**
     * 向所有业务监听器分发事件
     */
    private inline fun notify(block: (PlayerListener) -> Unit) {
        listeners.forEach { listener -> runCatching { block(listener) } }
    }

    companion object {
        private const val HLS_EXT = ".m3u8"
        private const val HLS_MIME_TYPE = "application/vnd.apple.mpegurl"
        private const val MS_TO_SECONDS = 1000.0
        private const val PROGRESS_INTERVAL_MS = 500
        private const val ERROR_CODE_GENERIC = -1
        private const val ERROR_MSG_PLAYBACK = "HTML5 video playback error"
        private const val SPEED_DEFAULT = 1.0f
        private const val SPEED_MIN = 0.25f
        private const val SPEED_MAX = 4.0f
        private const val VOLUME_DEFAULT = 1.0f
        private const val VOLUME_MIN = 0.0f
        private const val VOLUME_MAX = 1.0f
    }
}
