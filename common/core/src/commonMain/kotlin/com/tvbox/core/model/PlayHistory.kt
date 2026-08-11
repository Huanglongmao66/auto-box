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
 * 用户自定义影视源
 *
 * 兼容 TVBoxOS 标准 sites 数组格式，扩展字段保留 Spider/CSP/JS 源所需信息。
 * - type=0: XML 源
 * - type=1: JSON 源（MacCMS 标准接口）
 * - type=3: Spider/CSP 源（Jar/JS/Py 爬虫）
 * - type=4: 新协议（带 ext 过滤参数的 JSON 源）
 */
@Serializable
data class UserVodSource(
    val key: String,
    val name: String,
    val api: String,
    val enabled: Boolean = true,
    /** 站点类型 (0=XML, 1=JSON, 3=Spider, 4=带过滤的JSON) */
    val type: Int = 1,
    /** 是否可搜索 (1=可搜索) */
    val searchable: Int = 1,
    /** 是否可快速搜索 */
    val quickSearch: Int = 1,
    /** 是否可过滤分类 */
    val filterable: Int = 1,
    /** 站点解析 URL */
    val playerUrl: String = "",
    /** 扩展数据（Spider 的 ext 配置，可以是 JSON 字符串或 URL） */
    val ext: String = "",
    /** 自定义 Jar URL（Spider 源使用） */
    val jar: String = "",
    /** 站点播放器类型 (0=system, 1=ijk, 2=exo, 10=mxplayer, -1=跟随设置) */
    val playerType: Int = -1,
    /** 站点超时时间（秒） */
    val timeout: Int = 0,
    /** 需要点击播放的嗅探站点 selector */
    val clickSelector: String = "",
    /** 展示风格 */
    val style: String = "",
    /** 自定义分类排序 */
    val categories: List<String> = emptyList()
)

/**
 * 用户自定义直播源
 */
@Serializable
data class UserLiveSource(
    val key: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true
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
    val homeLayout: String = "grid",
    /** API 地址列表 */
    val apiUrls: List<String> = emptyList(),
    /** 当前选中的 API 索引 */
    val currentApiIndex: Int = 0,
    /** 用户自定义影视源列表 */
    val vodSources: List<UserVodSource> = emptyList(),
    /** 当前选中的影视源 key */
    val currentVodKey: String = "",
    /** 用户自定义直播源列表 */
    val liveSources: List<UserLiveSource> = emptyList(),
    /** 当前选中的直播源 key */
    val currentLiveKey: String = "",
    /** 壁纸索引 */
    val wallpaperIndex: Int = 0,
    /** 搜索历史 */
    val searchHistory: List<String> = emptyList(),
    /** 是否深色模式 */
    val isDarkMode: Boolean = true,
    /** 是否跳过片头 */
    val skipTitle: Boolean = true,
    /** 是否保持画面比例 */
    val keepRatio: Boolean = true,
    /** 是否开启 HDR 增强 */
    val hdrEnhance: Boolean = false,
    /** 主题色种子（用于主题预设） */
    val themeColorSeed: Int = 0,
    /** 首页卡片样式（0=标准卡片, 1=紧凑卡片, 2=大号卡片） */
    val cardStyle: Int = 0,
    /** 是否显示评分角标 */
    val showScoreBadge: Boolean = true
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
