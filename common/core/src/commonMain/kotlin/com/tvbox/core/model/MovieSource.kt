package com.tvbox.core.model

import kotlinx.serialization.Serializable

/**
 * 影视源类型枚举（兼容 TVBoxOS/Pluto/CatVod 生态通用定义）
 */
object SourceType {
    /** XML 源 (XML 格式接口返回) */
    const val XML = 0
    /** JSON 源 (JSON 格式接口返回) */
    const val JSON = 1
    /** XPath/爬虫源 (ext 字段内提供站点规则, HTML 爬取) */
    const val XPATH = 2
    /** Spider-JS 源 (js/ext 字段内嵌或远程 JS 脚本) */
    const val SPIDER_JS = 3
    /** Spider-JAR 源 (ext 指向 jar 包, 通过 ClassLoader 加载) */
    const val SPIDER_JAR = 4
    /** 聚合/混合源 (内部按 key 路由到多个源并行聚合) */
    const val MIX = 10
}

/**
 * 影视源数据模型
 *
 * 兼容原版 TVBox JSON 源格式及 CatVod 扩展字段：
 *  - type=0 XML
 *  - type=1 JSON
 *  - type=2 XPath/爬虫 (ext 为规则 JSON)
 *  - type=3 Spider-JS (js 字段为脚本内容/地址，ext 为 init 参数 JSON)
 *  - type=4 Spider-JAR (ext=jar://xxx 或 jar 文件名)
 *  - type=10 混合聚合
 */
@Serializable
data class MovieSource(
    /** 站点 key */
    val key: String = "",
    /** 站点名称 */
    val name: String = "",
    /** API 基础地址 (JSON/XML: 接口 URL; XPath/Spider: 站点首页 URL) */
    val api: String = "",
    /** 搜索接口地址 */
    val searchUrl: String = "",
    /** 快速搜索接口 */
    val quickSearch: String = "",
    /** 分类列表接口 */
    val categories: String = "",
    /** 播放解析接口（可选）：非直链地址通过此接口二次跳转获取真实 URL */
    val playerUrl: String = "",
    /** 站点类型, 参见 [SourceType] (0=XML, 1=JSON, 2=XPath, 3=Spider-JS, 4=Spider-JAR, 10=Mix) */
    val type: Int = SourceType.JSON,
    /** 是否启用 */
    val enabled: Boolean = true,
    /** 是否可搜索 */
    val searchable: Boolean = true,
    /** 是否可快速搜索 */
    val quickSearchable: Boolean = true,
    /** 是否可过滤分类 */
    val filterable: Boolean = false,
    /** 站点播放器类型 (0=系统播放器, 1=内置播放器, 2=IJK) */
    val playerType: Int = 0,
    /** 站点 UI 标识/图标 URL */
    val logo: String = "",
    /** 站点描述 */
    val desc: String = "",
    /** Spider-JS 脚本（可以是 JS 代码、文件路径或 http(s) 远程脚本地址） */
    val js: String = "",
    /** 扩展参数：
     *  - XPath/爬虫: 规则 JSON (包含 homeContent/categoryContent/detailContent/searchContent/playerContent 规则)
     *  - Spider-JS: init 阶段注入的参数 JSON
     *  - Spider-JAR: jar 包路径 或 jar://类名
     *  - MIX: 参与聚合的站点 key 列表 JSON (如 {"sites":["siteA","siteB"]})
     */
    val ext: String = "",
    /** 站点头信息 (JSON 字符串，如 {"User-Agent":"xxx","Referer":"yyy"}) */
    val header: String = "",
    /** 全局 Cookie 字符串 */
    val cookie: String = "",
    /** 站点超时时间（毫秒） */
    val timeout: Int = 15000,
    /** 排序权重（越小越靠前） */
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
