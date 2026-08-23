package com.tvbox.core.repository

import com.tvbox.core.model.MovieSource
import com.tvbox.core.model.VodClass
import com.tvbox.core.model.VodFilter
import com.tvbox.core.model.VodInfo
import com.tvbox.core.source.HomeContent
import com.tvbox.core.source.CategoryResult

/**
 * 影视数据仓库接口
 *
 * 统一屏蔽底层影视源差异（JSON / XML / Spider），提供一致的数据获取能力。
 * 实现类负责多源聚合、数据缓存和错误兜底。
 */
interface VodRepository {

    /**
     * 获取首页内容（Banner / 分类 / 推荐）
     */
    suspend fun getHomeContent(): HomeContent

    /**
     * 获取分类列表内容
     * @param tid 分类 ID
     * @param page 页码（从 1 开始）
     * @param filters 筛选条件
     * @param sourceKey 指定源（null 则从所有启用源聚合）
     */
    suspend fun getCategoryContent(
        tid: String,
        page: Int = 1,
        filters: List<VodFilter> = emptyList(),
        sourceKey: String? = null
    ): CategoryResult

    /**
     * 获取影视详情（含选集）
     * @param vodId 影视 ID
     * @param sourceKey 源 key
     */
    suspend fun getDetailContent(vodId: String, sourceKey: String): VodInfo?

    /**
     * 搜索影视
     * @param keyword 搜索关键词
     * @param page 页码
     * @param sourceKey 指定源（null 则多源聚合搜索）
     */
    suspend fun searchContent(
        keyword: String,
        page: Int = 1,
        sourceKey: String? = null
    ): List<VodInfo>

    /**
     * 获取播放地址
     * @param flag 播放线路标志
     * @param input 播放地址或 ID
     * @param sourceKey 源 key
     */
    suspend fun getPlayerContent(flag: String, input: String, sourceKey: String): String

    /**
     * 获取所有可用源
     */
    fun getAvailableSources(): List<MovieSource>

    /**
     * 获取分类列表
     */
    fun getCategories(): List<VodClass>
}
