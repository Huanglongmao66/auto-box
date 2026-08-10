package com.tvbox.core.repository

import com.tvbox.core.model.MovieSource
import com.tvbox.core.model.SearchResult
import com.tvbox.core.model.VodClass
import com.tvbox.core.model.VodFilter
import com.tvbox.core.model.VodInfo

/**
 * 影视数据仓库接口
 *
 * 统一屏蔽底层影视源的差异（JSON/XML/Spider/JAR），
 * 向上层 UI 提供一致的五类数据获取能力。
 *
 * 接口设计参考 TVBoxOS 原版：
 *   homeContent         → 首页内容
 *   categoryContent     → 分类列表
 *   detailContent       → 详情 + 选集
 *   searchContent       → 关键词搜索
 *   playerContent       → 播放地址解析
 */
interface VodRepository {

    /**
     * 获取首页内容
     *
     * 会优先使用所有已启用的源的首页结果合并，
     * 并返回 banner 轮播 + 分类 + 推荐内容的聚合结果。
     */
    suspend fun getHomeContent(): HomeContent

    /**
     * 分类列表查询
     *
     * @param tid       分类 ID（对应分类的 typeId）
     * @param page      页码，从 1 开始
     * @param filters   分类筛选条件（如年代、地区、类型等）
     * @param sourceKey 可选：指定单一源；为空时遍历所有启用源并合并结果
     */
    suspend fun getCategoryContent(
        tid: String,
        page: Int = 1,
        filters: List<VodFilter> = emptyList(),
        sourceKey: String? = null
    ): CategoryResult

    /**
     * 详情查询
     *
     * @param vodId     影视 ID（源内部 ID）
     * @param sourceKey 影视来源站点 key（必填，用于定位具体源）
     */
    suspend fun getDetailContent(
        vodId: String,
        sourceKey: String
    ): VodInfo?

    /**
     * 关键词搜索
     *
     * @param keyword   搜索关键词
     * @param page      页码
     * @param sourceKey 可选：指定单一源；为空时并行搜索所有启用源并合并
     */
    suspend fun searchContent(
        keyword: String,
        page: Int = 1,
        sourceKey: String? = null
    ): List<SearchResult>

    /**
     * 播放地址解析
     *
     * @param flag      线路标识（例如：zuidam3u8、xplay 等）
     * @param input     剧集原始 URL 字符串（可以是直链，也可能是需要 parse 接口二次跳转的 id）
     * @param sourceKey 影视来源站点 key（定位具体源的 playerUrl / parse 配置）
     * @return          可直接播放的真实视频 URL
     */
    suspend fun getPlayerContent(
        flag: String,
        input: String,
        sourceKey: String
    ): String

    /**
     * 返回当前可用的源集合
     */
    fun getAvailableSources(): List<MovieSource>

    /**
     * 获取当前分类列表（用于首页分类 Tab）
     */
    fun getCategories(): List<VodClass>
}

/**
 * 首页聚合结果
 */
data class HomeContent(
    /** Banner 轮播 */
    val banners: List<VodInfo> = emptyList(),
    /** 所有分类 */
    val categories: List<VodClass> = emptyList(),
    /** 分类下的推荐（key = 分类名，value = 该分类的推荐影视） */
    val categoryRecommendations: Map<String, List<VodInfo>> = emptyMap(),
    /** 热门/热播榜单 */
    val rankingList: List<VodInfo> = emptyList()
)

/**
 * 分类查询结果
 */
data class CategoryResult(
    val list: List<VodInfo> = emptyList(),
    val page: Int = 1,
    val pageCount: Int = 1,
    val limit: Int = 20,
    val total: Int = 0,
    /** 该分类可用的筛选条件 */
    val filters: List<VodFilter> = emptyList()
)
