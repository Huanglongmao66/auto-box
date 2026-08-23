package com.tvbox.deviceapi.player

/**
 * 跨平台播放器统一抽象接口
 *
 * 屏蔽底层播放器内核差异（ExoPlayer/MPV/VLC/HTML5），
 * 业务层播放逻辑完全复用，切换播放器内核无需改动业务代码。
 */
interface IPlayer {

    // ===== 播放控制 =====

    /**
     * 设置播放源
     * @param url 视频地址
     * @param headers 请求头（防盗链等）
     * @param subtitleUrl 字幕地址（可选）
     */
    fun setDataSource(url: String, headers: Map<String, String> = emptyMap(), subtitleUrl: String? = null)

    /**
     * 开始播放
     */
    fun play()

    /**
     * 暂停播放
     */
    fun pause()

    /**
     * 停止播放
     */
    fun stop()

    /**
     * 跳转到指定位置（毫秒）
     */
    fun seekTo(positionMs: Long)

    /**
     * 释放播放器资源
     */
    fun release()

    // ===== 播放状态 =====

    /**
     * 获取当前播放位置（毫秒）
     */
    fun getCurrentPosition(): Long

    /**
     * 获取视频总时长（毫秒）
     */
    fun getDuration(): Long

    /**
     * 是否正在播放
     */
    fun isPlaying(): Boolean

    /**
     * 获取缓冲百分比（0-100）
     */
    fun getBufferedPercentage(): Int

    // ===== 播放配置 =====

    /**
     * 设置播放速度（0.25x ~ 4.0x）
     */
    fun setSpeed(speed: Float)

    /**
     * 获取当前播放速度
     */
    fun getSpeed(): Float

    /**
     * 设置音量（0.0 ~ 1.0）
     */
    fun setVolume(volume: Float)

    /**
     * 获取当前音量
     */
    fun getVolume(): Float

    /**
     * 设置视频渲染视图（各平台渲染控件）
     */
    fun setVideoView(renderView: Any)

    // ===== 音轨/字幕 =====

    /**
     * 获取可用音轨列表
     */
    fun getAudioTracks(): List<TrackInfo>

    /**
     * 选择音轨
     */
    fun selectAudioTrack(trackId: Int)

    /**
     * 获取可用字幕列表
     */
    fun getSubtitleTracks(): List<TrackInfo>

    /**
     * 选择字幕
     */
    fun selectSubtitleTrack(trackId: Int)

    /**
     * 加载外部字幕文件
     */
    fun setExternalSubtitle(url: String)

    // ===== 监听 =====

    /**
     * 添加播放器事件监听
     */
    fun addListener(listener: PlayerListener)

    /**
     * 移除播放器事件监听
     */
    fun removeListener(listener: PlayerListener)
}

/**
 * 播放器事件监听回调协议
 */
interface PlayerListener {

    /**
     * 播放器准备就绪
     */
    fun onReady() {}

    /**
     * 开始播放
     */
    fun onPlay() {}

    /**
     * 暂停播放
     */
    fun onPause() {}

    /**
     * 播放完成
     */
    fun onCompletion() {}

    /**
     * 播放进度更新
     * @param positionMs 当前位置（毫秒）
     * @param durationMs 总时长（毫秒）
     */
    fun onProgressChanged(positionMs: Long, durationMs: Long) {}

    /**
     * 缓冲状态变化
     * @param isBuffering 是否正在缓冲
     */
    fun onBufferingStateChanged(isBuffering: Boolean) {}

    /**
     * 播放器错误
     * @param errorCode 错误码
     * @param message 错误信息
     */
    fun onError(errorCode: Int, message: String) {}

    /**
     * 音轨切换
     */
    fun onTrackChanged(trackType: TrackType, trackId: Int) {}

    /**
     * 视频尺寸变化
     */
    fun onVideoSizeChanged(width: Int, height: Int) {}
}

/**
 * 轨道类型
 */
enum class TrackType {
    AUDIO,
    SUBTITLE,
    VIDEO
}

/**
 * 轨道信息
 */
data class TrackInfo(
    val id: Int,
    val type: TrackType,
    val language: String?,
    val label: String?
)
