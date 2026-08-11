package com.tvbox.core.model

import kotlinx.serialization.Serializable

/**
 * 影视源数据模型
 *
 * 兼容原版 TVBox JSON 源格式，支持 type=0/1/3/4 四种源类型。
 * - type=0: XML 源
 * - type=1: JSON 源（MacCMS 标准接口）
 * - type=3: Spider/CSP 源（Jar/JS/Py 爬虫，api 可能为 csp_xxx 或 .js / .py 文件路径）
 * - type=4: 新协议（带 ext 过滤参数的 JSON 源）
 */
@Serializable
data class MovieSource(
    /** 站点 key */
    val key: String = "",
    /** 站点名称 */
    val name: String = "",
    /** API 基础地址（type=3 时可能是 csp_xxx / .js / .py 路径） */
    val api: String = "",
    /** 搜索接口地址 */
    val searchUrl: String = "",
    /** 快速搜索接口 */
    val quickSearch: String = "",
    /** 分类列表接口 */
    val categories: String = "",
    /** 播放解析接口（可选）：非直链地址通过此接口二次跳转获取真实 URL */
    val playerUrl: String = "",
    /** 站点类型 (0=XML, 1=JSON, 3=Spider, 4=带过滤的JSON) */
    val type: Int = 0,
    /** 是否启用 */
    val enabled: Boolean = true,
    /** 是否可搜索 */
    val searchable: Boolean = true,
    /** 是否可快速搜索 */
    val quickSearchable: Boolean = true,
    /** 是否可过滤分类 */
    val filterable: Boolean = false,
    /** 站点播放器类型 (0=system, 1=ijk, 2=exo, 10=mxplayer, -1=跟随设置) */
    val playerType: Int = -1,
    /** 站点 UI 标识 */
    val logo: String = "",
    /** 站点描述 */
    val desc: String = "",
    /** 站点 JS 脚本 */
    val js: String = "",
    /** 站点头信息 */
    val header: String = "",
    /** 站点超时时间（毫秒） */
    val timeout: Int = 15000,
    /** 排序权重 */
    val order: Int = 0,
    /** 最后更新时间 */
    val lastUpdate: Long = 0,
    /** Spider 扩展数据（ext 配置，可以是 JSON 字符串或 URL） */
    val ext: String = "",
    /** 自定义 Jar URL（Spider 源使用，可带 img+ 前缀表示图片伪装） */
    val jar: String = "",
    /** 需要点击播放的嗅探站点 selector */
    val clickSelector: String = "",
    /** 展示风格 */
    val style: String = "",
    /** 自定义分类排序列表 */
    val categoryList: List<String> = emptyList()
)

/**
 * 影视源集合
 */
@Serializable
data class SourceBundle(
    val sources: List<MovieSource> = emptyList()
)

/**
 * 影视源订阅地址
 */
@Serializable
data class SourceSubscription(
    /** 订阅 key */
    val key: String = "",
    /** 订阅名称 */
    val name: String = "",
    /** 订阅地址 */
    val url: String = "",
    /** 是否启用 */
    val enabled: Boolean = true,
    /** 最后更新时间 */
    val lastUpdate: Long = 0
)
