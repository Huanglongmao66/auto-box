package com.tvbox.core.repository

import com.tvbox.core.model.MovieSource
import com.tvbox.core.model.SourceType
import com.tvbox.core.model.VodClass
import com.tvbox.core.model.VodFilter
import com.tvbox.core.model.VodInfo
import com.tvbox.core.source.SourceManager
import com.tvbox.utils.JsonUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 聚合/混合源 (type=10)
 *
 * MovieSource.ext 约定：
 *   {"sites":["siteA","siteB",...]}
 *   或直接 "sites": "siteA,siteB"
 *   sites 为空时 -> 使用所有启用的源（相当于全量聚合）
 *
 * 本 client 会将调用通过 UnifiedSourceClient.owner 路由到每个子源对应类型的 client，
 * 并发执行后按顺序合并去重。
 */
internal class MixSourceClient(
    private val owner: UnifiedSourceClient,
    private val sourceManager: SourceManager
) : SourceClient {

    private fun childSources(mix: MovieSource): List<MovieSource> {
        val keys = parseSites(mix.ext)
        val all = sourceManager.getSources().filter { it.enabled && it.key != mix.key }
        return if (keys.isEmpty()) all else {
            val map = all.associateBy { it.key }
            keys.mapNotNull { map[it] }
        }
    }

    private fun parseSites(ext: String): List<String> {
        if (ext.isBlank()) return emptyList()
        runCatching {
            val obj = JsonUtils.parseToJsonElement(ext).jsonObject
            val s = obj["sites"] ?: return emptyList()
            if (s is JsonArray) return s.mapNotNull { it.jsonPrimitive.contentOrNull }
            val str = s.jsonPrimitive.contentOrNull ?: return emptyList()
            if (str.isNotBlank()) return str.split(Regex(""",|\s+""")).filter { it.isNotBlank() }
        }
        return emptyList()
    }

    override suspend fun home(source: MovieSource): TvboxJsonResponseParser.ParseListResult = coroutineScope {
        val srcs = childSources(source).takeIf { it.isNotEmpty() } ?: return@coroutineScope TvboxJsonResponseParser.ParseListResult.EMPTY
        val results = srcs.map { s ->
            async { runCatching { owner.home(s) }.getOrNull() }
        }.awaitAll().filterNotNull()

        val list = mutableListOf<VodInfo>()
        val categories = mutableListOf<VodClass>()
        val filters = mutableListOf<VodFilter>()
        val seenC = HashSet<String>()
        val seenV = HashSet<String>()
        var pg = 1; var pc = 1; var limit = 20; var total = 0

        for (r in results) {
            if (r.pageCount > pc) pc = r.pageCount
            total += r.total
            if (r.limit > limit) limit = r.limit
            for (c in r.categories) if (seenC.add(c.typeId)) categories.add(c)
            for (f in r.filters) if (filters.none { it.key == f.key }) filters.add(f)
            for (v in r.list) {
                val k = v.sourceKey + "|" + v.vodId
                if (seenV.add(k)) list.add(v)
            }
        }
        TvboxJsonResponseParser.ParseListResult(list, categories, filters, pg, pc, limit, total)
    }

    override suspend fun category(
        source: MovieSource,
        tid: String,
        page: Int,
        filters: List<VodFilter>
    ): TvboxJsonResponseParser.ParseListResult = coroutineScope {
        val srcs = childSources(source).takeIf { it.isNotEmpty() } ?: return@coroutineScope TvboxJsonResponseParser.ParseListResult.EMPTY
        val results = srcs.map { s ->
            async { runCatching { owner.category(s, tid, page, filters) }.getOrNull() }
        }.awaitAll().filterNotNull()

        val list = mutableListOf<VodInfo>()
        val cats = mutableListOf<VodClass>()
        val filts = mutableListOf<VodFilter>()
        val seenV = HashSet<String>()
        val seenC = HashSet<String>()
        var pc = page; var total = 0; var limit = 20

        for (r in results) {
            if (r.pageCount > pc) pc = r.pageCount
            total += r.total
            if (r.limit > limit) limit = r.limit
            for (c in r.categories) if (seenC.add(c.typeId)) cats.add(c)
            for (f in r.filters) if (filts.none { it.key == f.key }) filts.add(f)
            for (v in r.list) {
                val k = v.sourceKey + "|" + v.vodId
                if (seenV.add(k)) list.add(v)
            }
        }
        TvboxJsonResponseParser.ParseListResult(list, cats, filts, page, pc, limit, total)
    }

    override suspend fun detail(source: MovieSource, vodId: String): VodInfo? {
        // MIX 源的详情需要从 vodId 里知道对应子源；这里约定 vodId 前缀形式 "childKey###id"
        val childSources = childSources(source)
        if (childSources.isEmpty()) return null
        val (childKey, realId) = splitVodId(vodId)
        val child = childSources.firstOrNull { it.key == childKey } ?: childSources.first()
        return runCatching { owner.detail(child, realId) }.getOrNull()
    }

    override suspend fun search(source: MovieSource, keyword: String, page: Int): List<VodInfo> = coroutineScope {
        val srcs = childSources(source).takeIf { it.isNotEmpty() } ?: return@coroutineScope emptyList()
        val results = srcs.map { s ->
            async { runCatching { owner.search(s, keyword, page) }.getOrDefault(emptyList()) }
        }.awaitAll()
        val out = mutableListOf<VodInfo>()
        val seen = HashSet<String>()
        for (list in results) for (v in list) {
            val k = v.sourceKey + "|" + v.vodId
            if (seen.add(k)) out.add(v.copy(vodId = combine(v.sourceKey, v.vodId)))
        }
        out
    }

    override suspend fun playUrl(
        source: MovieSource,
        flag: String,
        input: String,
        parseUrl: String?
    ): String {
        val (childKey, realInput) = splitVodId(input)
        val child = childSources(source).firstOrNull { it.key == childKey } ?: return input
        return runCatching { owner.playUrl(child, flag, realInput, parseUrl) }.getOrDefault(input)
    }

    private fun splitVodId(vodId: String): Pair<String, String> {
        val i = vodId.indexOf("###")
        return if (i > 0) vodId.substring(0, i) to vodId.substring(i + 3)
        else "" to vodId
    }

    private fun combine(key: String, id: String): String {
        if (id.startsWith("$key###")) return id
        return "$key###$id"
    }

    companion object {
        /** SourceType=10 时使用的常量名，方便外部引用 */
        const val TYPE = SourceType.MIX
    }
}
