package com.tvbox.core.model

import kotlinx.serialization.Serializable

/**
 * 播放历史记录
 */
@Serializable
data class PlayHistory(
    /** 记录 ID */
    val id: Long = 0,
    /** 影视 ID */
    val vodId: String = "",
    /** 影视名称 */
    val vodName: String = "",
    /** 影视封面 */
    val vodPic: String = "",
    /** 影视来源站点 key */
    val sourceKey: String = "",
    /** 选集名称 */
    val episodeName: String = "",
    /** 选集索引 */
    val episodeIndex: Int = 0,
    /** 播放地址 */
    val url: String = "",
    /** 播放位置（毫秒） */
    val position: Long = 0,
    /** 总时长（毫秒） */
    val duration: Long = 0,
    /** 播放进度百分比 */
    val progress: Int = 0,
    /** 最后播放时间 */
    val updateTime: Long = 0
)

/**
 * 收藏记录
 */
@Serializable
data class Favorite(
    /** 记录 ID */
    val id: Long = 0,
    /** 影视 ID */
    val vodId: String = "",
    /** 影视名称 */
    val vodName: String = "",
    /** 影视封面 */
    val vodPic: String = "",
    /** 影视来源站点 key */
    val sourceKey: String = "",
    /** 影视类型 */
    val vodClass: String = "",
    /** 影视简介 */
    val vodContent: String = "",
    /** 收藏时间 */
    val createTime: Long = 0
)

/**
 * 热门搜索
 */
@Serializable
data class HotSearch(
    val keyword: String = "",
    val count: Int = 0,
    val updateTime: Long = 0
)

/**
 * 应用配置
 */
@Serializable
data class AppConfig(
    /** 当前选中站点 key */
    val currentSourceKey: String = "",
    /** 默认播放器内核 */
    val playerEngine: String = "exo",
    /** 是否开启硬解码 */
    val hardwareDecode: Boolean = true,
    /** 默认播放速度 */
    val playSpeed: Float = 1.0f,
    /** 默认音量 */
    val volume: Float = 1.0f,
    /** 是否自动播放下一集 */
    val autoPlayNext: Boolean = true,
    /** 搜索超时时间（秒） */
    val searchTimeout: Int = 15,
    /** 是否开启缩略图预览 */
    val thumbnailPreview: Boolean = true,
    /** 代理设置 */
    val proxy: ProxyConfig = ProxyConfig(),
    /** 主题模式 (light/dark/auto) */
    val themeMode: String = "auto",
    /** 首页布局模式 */
    val homeLayout: String = "grid"
)

/**
 * 代理配置
 */
@Serializable
data class ProxyConfig(
    val enabled: Boolean = false,
    val type: String = "http",
    val host: String = "",
    val port: Int = 0,
    val username: String = "",
    val password: String = ""
)
