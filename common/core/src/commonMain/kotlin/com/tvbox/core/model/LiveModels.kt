package com.tvbox.core.model

import kotlinx.serialization.Serializable

/**
 * 直播源配置
 *
 * 对应 TVBox 直播订阅，可指向一个远程 .txt 直播源地址，也可直接承载内置文本内容。
 *
 * @property key 直播源唯一标识
 * @property name 直播源名称
 * @property url 远端订阅地址（为空时使用 [content] 内置文本）
 * @property content 内置直播源文本（TVBox 标准格式），当 [url] 为空时使用
 * @property enabled 是否启用
 * @property order 排序权重
 */
@Serializable
data class LiveSource(
    val key: String = "",
    val name: String = "",
    val url: String = "",
    val content: String = "",
    val enabled: Boolean = true,
    val order: Int = 0
)

/**
 * 直播频道分组
 *
 * @property name 分组名称（如「央视频道」「卫视频道」）
 * @property channels 该分组下的频道列表
 */
@Serializable
data class LiveCategory(
    val name: String,
    val channels: List<LiveChannel> = emptyList()
)

/**
 * 直播频道
 *
 * @property name 频道名称（如「CCTV-1」）
 * @property urls 播放地址列表（同一频道可能有多条线路，依次回退）
 * @param header 请求头（防盗链等，可选）
 * @property logo 频道图标（可选）
 */
@Serializable
data class LiveChannel(
    val name: String,
    val urls: List<String> = emptyList(),
    val header: Map<String, String> = emptyMap(),
    val logo: String = ""
) {
    /** 当前线路序号（0 起），用于多线路切换 */
    var currentLineIndex: Int = 0
        private set

    /** 当前播放地址 */
    val currentUrl: String
        get() = urls.getOrElse(currentLineIndex.coerceIn(0, urls.lastIndex)) { "" }

    /** 切换到下一条线路，返回切换后的线路序号 */
    fun nextLine(): Int {
        if (urls.isNotEmpty()) {
            currentLineIndex = (currentLineIndex + 1) % urls.size
        }
        return currentLineIndex
    }

    /** 重置为第一条线路 */
    fun resetLine() {
        currentLineIndex = 0
    }
}

/**
 * TVBox 订阅配置中的单个直播项
 *
 * 对应原版 TVBox 配置 JSON 中 `lives` 数组的一项，指向真正的 .txt 直播源地址。
 *
 * @property name 直播源名称
 * @property type 类型（0=文本列表，1=JSON 接口）
 * @property url 直播源地址（.txt 文本或 JSON 接口）
 * @property playerType 播放器类型（0=系统，1=IJK，2=Exo）
 * @property header 请求头（JSON 字符串）
 * @property ep 电视节目单地址（可选）
 */
@Serializable
data class LiveConfig(
    val name: String = "",
    val type: Int = 0,
    val url: String = "",
    val playerType: Int = 0,
    val header: String = "",
    val ep: String = ""
)

/**
 * TVBox 订阅配置顶层结构
 *
 * 仅解析 `lives` 字段，其余字段（sites、wallpaper 等）忽略。
 *
 * @property lives 直播源配置列表
 */
@Serializable
data class LiveSubscription(
    val lives: List<LiveConfig> = emptyList()
)
