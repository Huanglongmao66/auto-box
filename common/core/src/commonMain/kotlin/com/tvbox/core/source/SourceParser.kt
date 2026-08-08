package com.tvbox.core.source

import com.tvbox.core.model.MovieSource
import com.tvbox.core.model.SearchResult
import com.tvbox.core.model.VodClass
import com.tvbox.core.model.VodEpisode
import com.tvbox.core.model.VodInfo
import com.tvbox.utils.JsonUtils

/**
 * 数据源解析器接口
 *
 * 兼容原版 TVBox JSON 源格式，将站点返回的 JSON 字符串解析为业务模型。
 * 不同站点类型（XML / JSON / Spider）可实现各自的解析逻辑。
 */
interface SourceParser {

    /**
     * 解析首页内容
     * @param json 站点首页接口返回的 JSON 字符串
     * @return 首页内容（推荐列表 + 分类 + 筛选）
     */
    fun parseHomeContent(json: String): HomeContent

    /**
     * 解析分类列表
     * @param json 站点分类接口返回的 JSON 字符串
     * @return 分类列表结果（列表 + 分页信息）
     */
    fun parseCategory(json: String): CategoryResult

    /**
     * 解析影视详情
     * @param json 站点详情接口返回的 JSON 字符串
     * @return 影视信息（含选集列表）
     */
    fun parseDetail(json: String): VodInfo

    /**
     * 解析搜索结果
     * @param json 站点搜索接口返回的 JSON 字符串
     * @return 搜索结果列表
     */
    fun parseSearch(json: String): List<SearchResult>

    /**
     * 解析播放地址
     * @param json 站点播放接口返回的 JSON 字符串
     * @return 实际可播放的视频地址
     */
    fun parsePlayUrl(json: String): String
}

/**
 * 数据源解析器伴生工具
 *
 * 提供基于 [JsonUtils] 的便捷解析入口，供实现类复用。
 */
@Suppress("unused")
object SourceParserSupport {

    /** 关联的模型类型，用于约束解析目标 */
    val supportedModels: List<String> = listOf(
        MovieSource::class.simpleName.orEmpty(),
        VodInfo::class.simpleName.orEmpty(),
        VodEpisode::class.simpleName.orEmpty(),
        SearchResult::class.simpleName.orEmpty(),
        VodClass::class.simpleName.orEmpty()
    )

    /**
     * 安全反序列化为指定类型，解析失败返回 null
     */
    inline fun <reified T> safeParse(json: String): T? = JsonUtils.fromJsonSafe(json)
}
