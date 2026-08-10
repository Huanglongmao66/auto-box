package com.tvbox.core.repository

import com.tvbox.core.model.MovieSource
import com.tvbox.core.model.SourceType
import com.tvbox.core.model.VodFilter
import com.tvbox.core.model.VodInfo

/**
 * XML 源客户端 (type=0)：URL 结构与 JSON 基本一致 (ac=list/detail/...)，响应解析走 XML
 */
internal class XmlSourceClient(private val http: HttpFetcher) : SourceClient {

    override suspend fun home(source: MovieSource): TvboxJsonResponseParser.ParseListResult {
        if (source.type != SourceType.XML) return TvboxJsonResponseParser.ParseListResult.EMPTY
        val raw = http.get(source.api, mapOf("ac" to "list"), source)
        if (raw.isBlank()) return TvboxJsonResponseParser.ParseListResult.EMPTY
        return TvboxXmlResponseParser.parseList(raw, source.key)
    }

    override suspend fun category(
        source: MovieSource,
        tid: String,
        page: Int,
        filters: List<VodFilter>
    ): TvboxJsonResponseParser.ParseListResult {
        if (source.type != SourceType.XML) return TvboxJsonResponseParser.ParseListResult.EMPTY
        val params = mutableMapOf(
            "ac" to "list", "t" to tid, "pg" to page.toString()
        )
        if (filters.isNotEmpty()) {
            params["f"] = filters.joinToString(",") { "${it.key}:${it.selectedValue}" }
        }
        val raw = http.get(source.api, params, source)
        if (raw.isBlank()) return TvboxJsonResponseParser.ParseListResult.EMPTY
        return TvboxXmlResponseParser.parseList(raw, source.key)
    }

    override suspend fun detail(source: MovieSource, vodId: String): VodInfo? {
        if (source.type != SourceType.XML) return null
        val raw = http.get(source.api, mapOf("ac" to "detail", "ids" to vodId), source)
        if (raw.isBlank()) return null
        return TvboxXmlResponseParser.parseDetail(raw, source.key)
    }

    override suspend fun search(source: MovieSource, keyword: String, page: Int): List<VodInfo> {
        if (source.type != SourceType.XML) return emptyList()
        if (!source.searchable) return emptyList()
        val raw = http.get(
            if (source.searchUrl.isNotBlank()) source.searchUrl else source.api,
            mapOf("ac" to "list", "wd" to keyword, "pg" to page.toString()),
            source
        )
        if (raw.isBlank()) return emptyList()
        return TvboxXmlResponseParser.parseSearch(raw, source.key)
    }

    override suspend fun playUrl(
        source: MovieSource,
        flag: String,
        input: String,
        parseUrl: String?
    ): String {
        if (isDirectPlayUrl(input)) return input
        // 1) XML 的 ac=video
        if (source.type == SourceType.XML) {
            runCatching {
                val raw = http.get(
                    source.api,
                    mapOf("ac" to "video", "flag" to flag, "id" to input),
                    source
                )
                val url = TvboxXmlResponseParser.parsePlayUrl(raw)
                if (url.isNotBlank() && isDirectPlayUrl(url)) return url
            }
        }
        // 2) parseUrl
        if (!parseUrl.isNullOrBlank()) {
            runCatching {
                val raw = http.get(parseUrl, mapOf("url" to input), source)
                // parseUrl 返回格式可能是 JSON 或 XML，先尝试 JSON
                var u = TvboxJsonResponseParser.parsePlayUrl(raw)
                if (u.isBlank()) u = TvboxXmlResponseParser.parsePlayUrl(raw)
                if (u.isNotBlank()) return u
            }
        }
        return input
    }

    private fun isDirectPlayUrl(url: String): Boolean {
        val lower = url.lowercase()
        return listOf(".m3u8", ".mp4", ".mkv", ".flv", ".webm", ".ts", ".mov", ".avi").any { lower.contains(it) }
    }
}
