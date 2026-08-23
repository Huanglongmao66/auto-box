package com.tvbox.core.repository

import com.tvbox.core.model.MovieSource
import com.tvbox.core.model.VodClass
import com.tvbox.core.model.VodFilter
import com.tvbox.core.model.VodInfo
import com.tvbox.core.network.NetworkService
import com.tvbox.core.source.HomeContent
import com.tvbox.core.source.CategoryResult
import com.tvbox.core.source.SourceManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * [VodRepository] 默认实现
 *
 * 管理多源数据聚合、分类缓存和错误兜底。
 * 对每个源按 [MovieSource.type] 选择对应的 [SourceClient] 实现进行请求。
 */
class DefaultVodRepository(
    private val sourceManager: SourceManager,
    private val networkService: NetworkService
) : VodRepository {

    private var cachedCategories: List<VodClass> = emptyList()

    override suspend fun getHomeContent(): HomeContent = coroutineScope {
        val sources = enabledSources()
        if (sources.isEmpty()) return@coroutineScope HomeContent()

        val results = sources.map { src ->
            async {
                runCatching {
                    val client = SourceClientFactory.create(src, networkService)
                    client.home(src)
                }.getOrNull()
            }
        }.awaitAll().filterNotNull()

        if (results.isEmpty()) return@coroutineScope HomeContent()

        // 合并分类（去重）
        val categorySet = linkedMapOf<String, VodClass>()
        results.forEach { hc ->
            hc.categories.forEach { c ->
                categorySet.putIfAbsent(c.typeId, c)
            }
        }
        cachedCategories = categorySet.values.toList()

        // 合并推荐列表（取第一个有内容的源，其余合并）
        val allList = results.flatMap { it.list }.distinctBy { it.vodId }
        val banners = allList.take(6)
        val filters = results.firstOrNull()?.filters ?: emptyList()

        HomeContent(
            list = allList,
            categories = cachedCategories,
            filters = filters
        )
    }

    override suspend fun getCategoryContent(
        tid: String,
        page: Int,
        filters: List<VodFilter>,
        sourceKey: String?
    ): CategoryResult = coroutineScope {
        val sources = if (sourceKey != null) {
            listOfNotNull(sourceManager.getSource(sourceKey)?.takeIf { it.enabled })
        } else {
            enabledSources()
        }
        if (sources.isEmpty()) return@coroutineScope CategoryResult()

        val results = sources.map { src ->
            async {
                runCatching {
                    val client = SourceClientFactory.create(src, networkService)
                    client.category(src, tid, page, filters)
                }.getOrNull()
            }
        }.awaitAll().filterNotNull()

        if (results.isEmpty()) return@coroutineScope CategoryResult()

        // 合并结果（多源去重）
        val mergedList = results.flatMap { it.list }.distinctBy { it.vodId }
        CategoryResult(
            list = mergedList,
            page = results.first().page,
            pageCount = results.maxOf { it.pageCount },
            limit = results.first().limit,
            total = results.sumOf { it.total }
        )
    }

    override suspend fun getDetailContent(vodId: String, sourceKey: String): VodInfo? {
        val source = sourceManager.getSource(sourceKey) ?: return null
        return runCatching {
            val client = SourceClientFactory.create(source, networkService)
            client.detail(source, vodId)
        }.getOrNull()
    }

    override suspend fun searchContent(
        keyword: String,
        page: Int,
        sourceKey: String?
    ): List<VodInfo> = coroutineScope {
        val sources = if (sourceKey != null) {
            listOfNotNull(sourceManager.getSource(sourceKey)?.takeIf { it.enabled && it.searchable })
        } else {
            enabledSources().filter { it.searchable }
        }
        if (sources.isEmpty()) return@coroutineScope emptyList()

        val results = sources.map { src ->
            async {
                runCatching {
                    val client = SourceClientFactory.create(src, networkService)
                    client.search(src, keyword, page)
                }.getOrNull()
            }
        }.awaitAll().filterNotNull()

        results.flatMap { it.toList() }.distinctBy { it.vodId }
    }

    override suspend fun getPlayerContent(flag: String, input: String, sourceKey: String): String {
        val source = sourceManager.getSource(sourceKey) ?: return input
        return runCatching {
            val client = SourceClientFactory.create(source, networkService)
            client.playUrl(source, flag, input)
        }.getOrDefault(input)
    }

    override fun getAvailableSources(): List<MovieSource> = enabledSources()

    override fun getCategories(): List<VodClass> {
        if (cachedCategories.isNotEmpty()) return cachedCategories
        return enabledSources().flatMap { src ->
            SourceClientFactory.create(src, networkService).let { emptyList<VodClass>() }
        }.also { cachedCategories = it }
    }

    private fun enabledSources(): List<MovieSource> {
        return sourceManager.getSources().filter { it.enabled }
    }
}
