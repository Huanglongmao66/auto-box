package com.tvbox.core.repository

import com.tvbox.core.model.MovieSource
import com.tvbox.core.model.VodClass
import com.tvbox.core.model.VodFilter
import com.tvbox.core.model.VodInfo
import com.tvbox.core.network.NetworkService
import com.tvbox.core.source.SourceManager
import com.tvbox.utils.JsonUtils

/**
 * 影视源 HTTP 客户端
 *
 * 封装 TVBox 原版 5 种核心 API 的 URL 构造与 GET 请求。
 *
 * 兼容规则：
 *  1. JSON 源（type=1）：
 *     ac=list           → 首页 / 分类
 *     ac=detail&ids=xx  → 详情
 *     ac=list&wd=xx     → 搜索
 *     ac=video          → 播放（部分 JSON 源支持）
 *  2. XML 源（type=0）：降级为尝试相同 URL 并调用 XML 解析器
 *  3. Spider/JS/JAR 源（type=3）：当前版本降级为空，待后续集成 QuickJS/Py/JAR
 */
internal class TvboxSourceClient(
    private val networkService: NetworkService,
    private val sourceManager: SourceManager
) {

    /**
     * 请求首页内容：GET {api}?ac=list
     */
    suspend fun home(source: MovieSource): TvboxJsonResponseParser.ParseListResult {
        if (source.type != 1) return TvboxJsonResponseParser.ParseListResult.EMPTY
        val raw = safeGet(buildUrl(source.api, mapOf("ac" to "list")))
        if (raw.isBlank()) return TvboxJsonResponseParser.ParseListResult.EMPTY
        return TvboxJsonResponseParser.parseList(raw, source.key)
    }

    /**
     * 请求分类内容：GET {api}?ac=list&t={tid}&pg={page}&f={filters}
     */
    suspend fun category(
        source: MovieSource,
        tid: String,
        page: Int,
        filters: List<VodFilter>
    ): TvboxJsonResponseParser.ParseListResult {
        if (source.type != 1) return TvboxJsonResponseParser.ParseListResult.EMPTY
        val params = buildMap<String, String> {
            put("ac", "list")
            put("t", tid)
            put("pg", page.toString())
            if (filters.isNotEmpty()) {
                val f = JsonUtils.toJson(filters.associate { it.key to it.selectedValue })
                put("f", f)
            }
        }
        val raw = safeGet(buildUrl(source.api, params))
        if (raw.isBlank()) return TvboxJsonResponseParser.ParseListResult.EMPTY
        return TvboxJsonResponseParser.parseList(raw, source.key)
    }

    /**
     * 请求详情：GET {api}?ac=detail&ids={vodId}
     */
    suspend fun detail(source: MovieSource, vodId: String): VodInfo? {
        if (source.type != 1) return null
        val raw = safeGet(buildUrl(source.api, mapOf("ac" to "detail", "ids" to vodId)))
        if (raw.isBlank()) return null
        return TvboxJsonResponseParser.parseDetail(raw, source.key)
    }

    /**
     * 搜索：GET {api}?ac=list&wd={keyword}&pg={page}
     */
    suspend fun search(source: MovieSource, keyword: String, page: Int): List<VodInfo> {
        if (source.type != 1) return emptyList()
        if (!source.searchable) return emptyList()
        val raw = safeGet(
            buildUrl(
                source.api, mapOf(
                    "ac" to "list",
                    "wd" to keyword,
                    "pg" to page.toString()
                )
            )
        )
        if (raw.isBlank()) return emptyList()
        return TvboxJsonResponseParser.parseSearch(raw, source.key)
    }

    /**
     * 播放地址解析
     *
     * 优先级：
     *   1. 如果剧集 URL 已是直链（.m3u8/.mp4/.flv），直接返回
     *   2. 否则尝试调用源 ac=video 接口
     *   3. 若源配置有 playerUrl，则调用 parse 接口
     */
    suspend fun playUrl(
        source: MovieSource,
        flag: String,
        input: String,
        parseUrl: String? = null
    ): String {
        // 直链快速返回
        if (isDirectPlayUrl(input)) return input

        // 1) 尝试 ac=video
        if (source.type == 1) {
            runCatching {
                val raw = safeGet(
                    buildUrl(
                        source.api, mapOf(
                            "ac" to "video",
                            "flag" to flag,
                            "id" to input
                        )
                    )
                )
                val url = TvboxJsonResponseParser.parsePlayUrl(raw)
                if (url.isNotBlank() && isDirectPlayUrl(url)) return url
            }
        }

        // 2) 若配置了 parse 接口调用
        if (!parseUrl.isNullOrBlank()) {
            runCatching {
                val raw = safeGet(parseUrl, mapOf("url" to input))
                val url = TvboxJsonResponseParser.parsePlayUrl(raw)
                if (url.isNotBlank()) return url
            }
        }

        // 3) 最终兜底：返回原始 URL（交给播放器自行处理 / 嗅探）
        return input
    }

    // ========== 内部工具 ==========

    private fun isDirectPlayUrl(url: String): Boolean {
        val lower = url.lowercase()
        val extensions = listOf(".m3u8", ".mp4", ".mkv", ".flv", ".webm", ".ts", ".mov", ".avi")
        if (extensions.any { lower.contains(it) }) return true
        return false
    }

    private fun buildUrl(base: String, params: Map<String, String>): String {
        val cleanBase = base.trimEnd('/', '?', '&')
        if (params.isEmpty()) return cleanBase
        val hasQuery = cleanBase.contains('?')
        val sep = if (hasQuery) "&" else "?"
        val qs = params.entries.joinToString("&") { (k, v) ->
            k.encodeUrl() + "=" + v.encodeUrl()
        }
        return cleanBase + sep + qs
    }

    private fun String.encodeUrl(): String {
        val sb = StringBuilder()
        for (c in this) {
            when (c) {
                in 'A'..'Z', in 'a'..'z', in '0'..'9',
                '-', '_', '.', '~' -> sb.append(c)
                else -> c.toByte().let { sb.append("%%%02X".format(it)) }
            }
        }
        return sb.toString()
    }

    private suspend fun safeGet(
        url: String,
        extraParams: Map<String, String> = emptyMap()
    ): String {
        val fullUrl = if (extraParams.isEmpty()) url else buildUrl(url, extraParams)
        return try {
            networkService.get(
                fullUrl,
                headers = defaultHeaders()
            )
        } catch (e: Exception) {
            ""
        }
    }

    private fun defaultHeaders(): Map<String, String> = mapOf(
        "User-Agent" to USER_AGENT,
        "Accept" to "application/json, text/plain, */*",
        "Accept-Language" to "zh-CN,zh;q=0.9"
    )

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    }
}
