package com.tvbox.core.repository

import com.tvbox.core.model.MovieSource
import com.tvbox.core.model.SourceType
import com.tvbox.core.model.VodFilter
import com.tvbox.core.model.VodInfo
import com.tvbox.utils.JsonUtils

/**
 * JSON 源客户端 (type=1)：包装原 TvboxSourceClient 的 URL 构造 + 解析逻辑
 */
internal class JsonSourceClient(private val http: HttpFetcher) : SourceClient {

    override suspend fun home(source: MovieSource): TvboxJsonResponseParser.ParseListResult {
        if (source.type != SourceType.JSON) return TvboxJsonResponseParser.ParseListResult.EMPTY
        val raw = http.get(source.api, mapOf("ac" to "list"), source)
        if (raw.isBlank()) return TvboxJsonResponseParser.ParseListResult.EMPTY
        return TvboxJsonResponseParser.parseList(raw, source.key)
    }

    override suspend fun category(
        source: MovieSource,
        tid: String,
        page: Int,
        filters: List<VodFilter>
    ): TvboxJsonResponseParser.ParseListResult {
        if (source.type != SourceType.JSON) return TvboxJsonResponseParser.ParseListResult.EMPTY
        val params = buildMap<String, String> {
            put("ac", "list")
            put("t", tid)
            put("pg", page.toString())
            if (filters.isNotEmpty()) {
                val f = JsonUtils.toJson(filters.associate { it.key to it.selectedValue })
                put("f", f)
            }
        }
        val raw = http.get(source.api, params, source)
        if (raw.isBlank()) return TvboxJsonResponseParser.ParseListResult.EMPTY
        return TvboxJsonResponseParser.parseList(raw, source.key)
    }

    override suspend fun detail(source: MovieSource, vodId: String): VodInfo? {
        if (source.type != SourceType.JSON) return null
        val raw = http.get(source.api, mapOf("ac" to "detail", "ids" to vodId), source)
        if (raw.isBlank()) return null
        return TvboxJsonResponseParser.parseDetail(raw, source.key)
    }

    override suspend fun search(source: MovieSource, keyword: String, page: Int): List<VodInfo> {
        if (source.type != SourceType.JSON) return emptyList()
        if (!source.searchable) return emptyList()
        val raw = http.get(
            if (source.searchUrl.isNotBlank()) source.searchUrl else source.api,
            mapOf(
                "ac" to "list",
                "wd" to keyword,
                "pg" to page.toString()
            ),
            source
        )
        if (raw.isBlank()) return emptyList()
        return TvboxJsonResponseParser.parseSearch(raw, source.key)
    }

    override suspend fun playUrl(
        source: MovieSource,
        flag: String,
        input: String,
        parseUrl: String?
    ): String {
        if (isDirectPlayUrl(input)) return input
        // 1) ac=video 接口 (仅 JSON 源有定义)
        if (source.type == SourceType.JSON) {
            runCatching {
                val raw = http.get(
                    source.api,
                    mapOf("ac" to "video", "flag" to flag, "id" to input),
                    source
                )
                val url = TvboxJsonResponseParser.parsePlayUrl(raw)
                if (url.isNotBlank() && isDirectPlayUrl(url)) return url
            }
        }
        // 2) playerUrl / parseUrl 解析
        if (!parseUrl.isNullOrBlank()) {
            runCatching {
                val raw = http.get(parseUrl, mapOf("url" to input), source)
                val url = TvboxJsonResponseParser.parsePlayUrl(raw)
                if (url.isNotBlank()) return url
            }
        }
        // 3) 兜底：返回原始
        return input
    }

    private fun isDirectPlayUrl(url: String): Boolean {
        val lower = url.lowercase()
        val exts = listOf(".m3u8", ".mp4", ".mkv", ".flv", ".webm", ".ts", ".mov", ".avi")
        if (exts.any { lower.contains(it) }) return true
        return false
    }
}
