package com.tvbox.core.model

import kotlinx.serialization.Serializable

/**
 * 影视源数据模型
 * 兼容原版 TVBox JSON 源格式
 */
@Serializable
data class MovieSource(
    /** 站点 key */
    val key: String = "",
    /** 站点名称 */
    val name: String = "",
    /** API 基础地址 */
    val api: String = "",
    /** 搜索接口地址 */
    val searchUrl: String = "",
    /** 快速搜索接口 */
    val quickSearch: String = "",
    /** 分类列表接口 */
    val categories: String = "",
    /** 播放解析接口（可选）：非直链地址通过此接口二次跳转获取真实 URL */
    val playerUrl: String = "",
    /** 站点类型 (0=XML, 1=JSON, 3=Spider) */
    val type: Int = 0,
    /** 是否启用 */
    val enabled: Boolean = true,
    /** 是否可搜索 */
    val searchable: Boolean = true,
    /** 是否可快速搜索 */
    val quickSearchable: Boolean = true,
    /** 是否可过滤分类 */
    val filterable: Boolean = false,
    /** 站点播放器类型 */
    val playerType: Int = 0,
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
    val lastUpdate: Long = 0
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
