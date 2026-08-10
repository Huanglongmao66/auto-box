package com.tvbox.core.repository

import com.tvbox.core.model.MovieSource
import com.tvbox.core.model.SearchResult
import com.tvbox.core.model.VodClass
import com.tvbox.core.model.VodFilter
import com.tvbox.core.model.VodInfo
import com.tvbox.core.source.SourceManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * [VodRepository] 的默认实现
 *
 * - 单源调用：直接调用对应 MovieSource
 * - 多源聚合：并行请求所有 enabled=true 的源，合并去重
 * - 所有网络异常被捕获，返回空数据，避免影响 UI 渲染
 */
internal class DefaultVodRepository(
    private val sourceManager: SourceManager,
    private val client: TvboxSourceClient
) : VodRepository {

    // 简单的分类缓存（首次 homeContent 成功后缓存）
    private var cachedCategories: List<VodClass> = emptyList()

    override suspend fun getHomeContent(): HomeContent = coroutineScope {
        val sources = enabledSources()
        if (sources.isEmpty()) return@coroutineScope HomeContent()

        // 并行请求所有源的首页内容
        val results = sources.map { src ->
            async { runCatching { client.home(src) }.getOrNull() }
        }.awaitAll().filterNotNull()

        val banners: MutableList<VodInfo> = mutableListOf()
        val recMap: MutableMap<String, MutableList<VodInfo>> = mutableMapOf()
        val ranking: MutableList<VodInfo> = mutableListOf()
        val categorySet: MutableList<VodClass> = mutableListOf()

        val seenIds = mutableSetOf<String>()

        for (r in results) {
            // 分类聚合
            for (c in r.categories) {
                if (categorySet.none { it.typeId == c.typeId }) categorySet.add(c)
            }

            // 每个源首页的前 12 条作为 Banner/推荐候选
            for (v in r.list) {
                if (v.vodId.isBlank()) continue
                if (seenIds.contains(v.sourceKey + "|" + v.vodId)) continue
                seenIds.add(v.sourceKey + "|" + v.vodId)

                // Banner：取有海报且有评分/热度备注的
                if (banners.size < 6 && v.vodPic.isNotBlank()) {
                    banners.add(v)
                }
                // 推荐：按分类归类
                val className = v.vodClass.takeIf { it.isNotBlank() }?.split(",", "/", "|")
                    ?.firstOrNull() ?: "其他"
                recMap.getOrPut(className) { mutableListOf() }.add(v)

                // 热播榜
                if (ranking.size < 20) ranking.add(v)
            }
        }

        // 补齐分类 Tab，如果从源没拿到，则用通用分类
        if (categorySet.isEmpty()) {
            categorySet.addAll(DEFAULT_CATEGORIES)
        }
        cachedCategories = categorySet.toList()

        HomeContent(
            banners = banners.toList(),
            categories = categorySet.toList(),
            categoryRecommendations = recMap,
            rankingList = ranking.toList()
        )
    }

    override suspend fun getCategoryContent(
        tid: String,
        page: Int,
        filters: List<VodFilter>,
        sourceKey: String?
    ): CategoryResult = coroutineScope {
        val sources = if (sourceKey != null) {
            listOfNotNull(sourceManager.getSource(sourceKey)).filter { it.enabled }
        } else enabledSources()

        if (sources.isEmpty()) return@coroutineScope CategoryResult()

        val results = sources.map { src ->
            async { runCatching { client.category(src, tid, page, filters) }.getOrNull() }
        }.awaitAll().filterNotNull()

        val mergedList = mutableListOf<VodInfo>()
        val seen = mutableSetOf<String>()
        var maxPageCount = 1
        var maxTotal = 0
        val filtersList = results.flatMap { it.filters }.distinctBy { it.key }

        for (r in results) {
            maxPageCount = maxOf(maxPageCount, r.pageCount)
            maxTotal = maxOf(maxTotal, r.total)
            for (v in r.list) {
                val k = v.sourceKey + "|" + v.vodId
                if (k in seen) continue
                seen.add(k)
                mergedList.add(v)
            }
        }

        CategoryResult(
            list = mergedList.toList(),
            page = page,
            pageCount = maxPageCount,
            total = maxTotal,
            filters = filtersList
        )
    }

    override suspend fun getDetailContent(vodId: String, sourceKey: String): VodInfo? {
        val source = sourceManager.getSource(sourceKey) ?: return null
        return runCatching { client.detail(source, vodId) }.getOrNull()
    }

    override suspend fun searchContent(
        keyword: String,
        page: Int,
        sourceKey: String?
    ): List<SearchResult> = coroutineScope {
        val sources = if (sourceKey != null) {
            listOfNotNull(sourceManager.getSource(sourceKey)).filter { it.enabled && it.searchable }
        } else enabledSources().filter { it.searchable }.take(MAX_SEARCH_SOURCES)

        if (sources.isEmpty() || keyword.isBlank()) return@coroutineScope emptyList()

        val results = sources.map { src ->
            async {
                runCatching {
                    client.search(src, keyword, page)
                }.getOrDefault(emptyList())
            }
        }.awaitAll()

        val seen = mutableSetOf<String>()
        val out = mutableListOf<SearchResult>()

        for ((idx, list) in results.withIndex()) {
            val src = sources[idx]
            for (v in list) {
                val k = src.key + "|" + v.vodId
                if (k in seen) continue
                seen.add(k)
                out.add(
                    SearchResult(
                        sourceKey = src.key,
                        vodId = v.vodId,
                        vodName = v.vodName,
                        vodPic = v.vodPic,
                        vodRemarks = v.vodRemarks
                    )
                )
            }
        }
        out.toList()
    }

    override suspend fun getPlayerContent(flag: String, input: String, sourceKey: String): String {
        val source = sourceManager.getSource(sourceKey) ?: return input
        return runCatching { client.playUrl(source, flag, input, source.playerUrl) }.getOrDefault(input)
    }

    override fun getAvailableSources(): List<MovieSource> = enabledSources()

    override fun getCategories(): List<VodClass> {
        return cachedCategories.ifEmpty { DEFAULT_CATEGORIES }
    }

    // ========== 内部工具 ==========

    private fun enabledSources(): List<MovieSource> {
        return sourceManager.getSources().filter { it.enabled }
    }

    companion object {
        private const val MAX_SEARCH_SOURCES = 8
        val DEFAULT_CATEGORIES: List<VodClass> = listOf(
            VodClass("1", "电影"),
            VodClass("2", "电视剧"),
            VodClass("3", "综艺"),
            VodClass("4", "动漫"),
            VodClass("5", "纪录片"),
            VodClass("6", "动画片")
        )
    }
}
