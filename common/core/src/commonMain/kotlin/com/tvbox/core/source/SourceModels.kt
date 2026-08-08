package com.tvbox.core.source

import com.tvbox.core.model.VodClass
import com.tvbox.core.model.VodFilter
import com.tvbox.core.model.VodInfo
import kotlinx.serialization.Serializable

/**
 * 首页内容解析结果
 *
 * 对应原版 TVBox 首页接口返回结构，包含推荐列表、分类列表与筛选条件。
 *
 * @property list 推荐影视列表
 * @property categories 站点分类列表
 * @property filters 分类筛选条件
 */
@Serializable
data class HomeContent(
    val list: List<VodInfo> = emptyList(),
    val categories: List<VodClass> = emptyList(),
    val filters: List<VodFilter> = emptyList()
)

/**
 * 分类列表解析结果
 *
 * 对应原版 TVBox 分类接口返回结构，包含分页信息与当前页影视列表。
 *
 * @property list 当前页影视列表
 * @property page 当前页码（从 1 开始）
 * @property pageCount 总页数
 * @property limit 每页条数
 * @property total 记录总数
 */
@Serializable
data class CategoryResult(
    val list: List<VodInfo> = emptyList(),
    val page: Int = 1,
    val pageCount: Int = 1,
    val limit: Int = 20,
    val total: Int = 0
)

/**
 * 站点配置信息
 *
 * 对应原版 TVBox 配置文件中 sites 数组项的站点配置，
 * 可由 [SourceManager.importFromJson] 解析后转换为 MovieSource。
 *
 * @property key 站点 key
 * @property name 站点名称
 * @property api API 基础地址
 * @property type 站点类型（0=XML, 1=JSON, 3=Spider）
 * @property searchable 是否可搜索
 * @property quickSearch 是否可快速搜索
 * @property filterable 是否可过滤分类
 * @property playerType 站点播放器类型
 * @property header 站点头信息（JSON 字符串）
 * @property enabled 是否启用
 * @property ext 扩展字段（jar / spider 配置等）
 */
@Serializable
data class SourceConfig(
    val key: String = "",
    val name: String = "",
    val api: String = "",
    val type: Int = 0,
    val searchable: Boolean = true,
    val quickSearch: Boolean = true,
    val filterable: Boolean = false,
    val playerType: Int = 0,
    val header: String = "",
    val enabled: Boolean = true,
    val ext: String = ""
)
