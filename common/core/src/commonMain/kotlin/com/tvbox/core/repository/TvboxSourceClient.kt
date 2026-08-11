package com.tvbox.core.repository

import com.tvbox.core.model.MovieSource
import com.tvbox.core.model.VodClass
import com.tvbox.core.model.VodFilter
import com.tvbox.core.model.VodInfo
import com.tvbox.core.network.NetworkService
import com.tvbox.core.source.SourceManager
import com.tvbox.core.source.SpiderLoader
import com.tvbox.utils.EncodingUtils
import com.tvbox.utils.JsonUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.io.encoding.Base64

/**
 * 影视源 HTTP 客户端
 *
 * 封装 TVBox 原版 5 种核心 API 的 URL 构造与 GET 请求，并支持 Spider 爬虫分发。
 *
 * 兼容规则：
 *  1. JSON 源（type=1）：
 *     ac=list           → 首页 / 分类
 *     ac=detail&ids=xx  → 详情
 *     ac=list&wd=xx     → 搜索
 *     ac=video          → 播放（部分 JSON 源支持）
 *  2. XML 源（type=0）：降级为尝试相同 URL 并调用 XML 解析器
 *  3. Spider/JS/JAR 源（type=3）：通过 [SpiderLoader] 获取 Spider 实例并调用对应方法
 *  4. 带过滤的 JSON 源（type=4）：按 type=1 处理，ext 作为过滤参数
 */
internal class TvboxSourceClient(
    private val networkService: NetworkService,
    private val sourceManager: SourceManager,
    private val spiderLoader: SpiderLoader = com.tvbox.core.source.DefaultSpiderLoader()
) {

    /**
     * 请求首页内容
     *
     * - type=1/4: GET {api}?ac=list
     * - type=3: 调用 Spider.homeContent(true)
     * - type=0: 暂未实现 XML 解析，返回空
     */
    suspend fun home(source: MovieSource): TvboxJsonResponseParser.ParseListResult {
        val raw = when (source.type) {
            1, 4 -> {
                val url = buildUrl(source.api, mapOf("ac" to "list"))
                safeGetDecoded(url)
            }
            3 -> {
                withContext(Dispatchers.Default) {
                    val spider = getSpider(source)
                    try { spider.homeContent(true) } catch (_: Throwable) { "" }
                }
            }
            else -> {
                return TvboxJsonResponseParser.ParseListResult.EMPTY
            }
        }
        if (raw.isBlank()) return TvboxJsonResponseParser.ParseListResult.EMPTY
        return TvboxJsonResponseParser.parseList(raw, source.key)
    }

    /**
     * 请求分类内容
     *
     * - type=1/4: GET {api}?ac=list&t={tid}&pg={page}&f={filters}
     * - type=3: 调用 Spider.categoryContent(tid, pg, filter, extend)
     * - type=0: 暂未实现，返回空
     */
    suspend fun category(
        source: MovieSource,
        tid: String,
        page: Int,
        filters: List<VodFilter>
    ): TvboxJsonResponseParser.ParseListResult {
        val raw = when (source.type) {
            1, 4 -> {
                val params = buildMap<String, String> {
                    put("ac", "list")
                    put("t", tid)
                    put("pg", page.toString())
                    if (filters.isNotEmpty()) {
                        val f = JsonUtils.toJson(filters.associate { it.key to it.selectedValue })
                        put("f", f)
                    }
                }
                safeGetDecoded(buildUrl(source.api, params))
            }
            3 -> {
                withContext(Dispatchers.Default) {
                    val spider = getSpider(source)
                    val extend = filters.associate { it.key to it.selectedValue }
                    try { spider.categoryContent(tid, page.toString(), source.filterable, extend) } catch (_: Throwable) { "" }
                }
            }
            else -> return TvboxJsonResponseParser.ParseListResult.EMPTY
        }
        if (raw.isBlank()) return TvboxJsonResponseParser.ParseListResult.EMPTY
        return TvboxJsonResponseParser.parseList(raw, source.key)
    }

    /**
     * 请求详情
     *
     * - type=1/4: GET {api}?ac=detail&ids={vodId}
     * - type=3: 调用 Spider.detailContent([vodId])
     * - type=0: 暂未实现，返回 null
     */
    suspend fun detail(source: MovieSource, vodId: String): VodInfo? {
        val raw = when (source.type) {
            1, 4 -> {
                val url = buildUrl(source.api, mapOf("ac" to "detail", "ids" to vodId))
                safeGetDecoded(url)
            }
            3 -> {
                withContext(Dispatchers.Default) {
                    val spider = getSpider(source)
                    try { spider.detailContent(listOf(vodId)) } catch (_: Throwable) { "" }
                }
            }
            else -> {
                return null
            }
        }
        if (raw.isBlank()) return null
        return TvboxJsonResponseParser.parseDetail(raw, source.key)
    }

    /**
     * 搜索
     *
     * - type=1/4: GET {api}?ac=list&wd={keyword}&pg={page}
     * - type=3: 调用 Spider.searchContent(keyword, false, page)
     * - type=0: 暂未实现，返回空
     */
    suspend fun search(source: MovieSource, keyword: String, page: Int): List<VodInfo> {
        if (!source.searchable) return emptyList()
        val raw = when (source.type) {
            1, 4 -> {
                safeGetDecoded(
                    buildUrl(
                        source.api, mapOf(
                            "ac" to "list",
                            "wd" to keyword,
                            "pg" to page.toString()
                        )
                    )
                )
            }
            3 -> {
                withContext(Dispatchers.Default) {
                    val spider = getSpider(source)
                    try { spider.searchContent(keyword, false, page.toString()) } catch (_: Throwable) { "" }
                }
            }
            else -> return emptyList()
        }
        if (raw.isBlank()) return emptyList()
        return TvboxJsonResponseParser.parseSearch(raw, source.key)
    }

    /**
     * 播放地址解析
     *
     * 优先级：
     *   1. 如果剧集 URL 已是直链（.m3u8/.mp4/.flv），直接返回
     *   2. type=3: 调用 Spider.playerContent(flag, id, vipFlags)
     *   3. type=1/4: 尝试调用源 ac=video 接口
     *   4. 若源配置有 playerUrl，则调用 parse 接口
     *   5. 最终兜底：返回原始 URL（交给播放器自行处理 / 嗅探）
     */
    suspend fun playUrl(
        source: MovieSource,
        flag: String,
        input: String,
        parseUrl: String? = null
    ): String {
        // 直链快速返回
        if (isDirectPlayUrl(input)) return input

        // type=3: Spider 播放解析
        if (source.type == 3) {
            val raw = withContext(Dispatchers.Default) {
                val spider = getSpider(source)
                try { spider.playerContent(flag, input, emptyList()) } catch (_: Throwable) { "" }
            }
            if (raw.isNotBlank()) {
                val url = TvboxJsonResponseParser.parsePlayUrl(raw)
                if (url.isNotBlank()) return url
            }
            return input
        }

        // type=1/4: 尝试 ac=video
        if (source.type == 1 || source.type == 4) {
            runCatching {
                val raw = safeGetDecoded(
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

        // 若配置了 parse 接口调用
        if (!parseUrl.isNullOrBlank()) {
            runCatching {
                val raw = safeGetDecoded(parseUrl, mapOf("url" to input))
                val url = TvboxJsonResponseParser.parsePlayUrl(raw)
                if (url.isNotBlank()) return url
            }
        }

        // 最终兜底：返回原始 URL（交给播放器自行处理 / 嗅探）
        return input
    }

    // ========== Spider 加载 ==========

    /**
     * 获取 Spider 实例（带缓存）
     *
     * 同一站点（key+api+ext+jar）只初始化一次，后续直接复用缓存。
     */
    private val spiderCache = mutableMapOf<String, com.tvbox.core.source.Spider>()

    private fun getSpider(source: MovieSource): com.tvbox.core.source.Spider {
        val cacheKey = source.key + "|" + source.api + "|" + source.ext + "|" + source.jar
        spiderCache[cacheKey]?.let {
            return it
        }
        val spider = spiderLoader.getSpider(
            key = source.key,
            api = source.api,
            ext = source.ext,
            jar = source.jar
        )
        spider.siteKey = source.key
        try { spider.init(source.ext) } catch (_: Throwable) {}
        spiderCache[cacheKey] = spider
        return spider
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
        val hex = "0123456789ABCDEF".toCharArray()
        for (c in this) {
            when (c) {
                in 'A'..'Z', in 'a'..'z', in '0'..'9',
                '-', '_', '.', '~' -> sb.append(c)
                else -> {
                    val v = c.code and 0xFF
                    sb.append('%')
                    sb.append(hex[v ushr 4])
                    sb.append(hex[v and 0x0F])
                }
            }
        }
        return sb.toString()
    }

    /**
     * 安全请求并解码响应：支持普通 JSON 文本、GBK/UTF-8 混合编码、以及 JPEG 图片伪装（FF D8 开头 + 末尾嵌入 Base64 JSON）
     */
    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun safeGetDecoded(
        url: String,
        extraParams: Map<String, String> = emptyMap()
    ): String {
        val fullUrl = if (extraParams.isEmpty()) url else buildUrl(url, extraParams)
        val bytes = try {
            networkService.download(fullUrl, defaultHeaders())
        } catch (e: Exception) {
            return ""
        }
        if (bytes.isEmpty()) return ""

        // JPEG 图片伪装：FF D8 开头 → 在 FF D9 结束标记之后提取 JSON
        if (bytes.size >= 2 &&
            (bytes[0].toInt() and 0xFF) == 0xFF &&
            (bytes[1].toInt() and 0xFF) == 0xD8
        ) {
            var jpegEnd = -1
            for (i in bytes.size - 2 downTo 0) {
                if ((bytes[i].toInt() and 0xFF) == 0xFF &&
                    (bytes[i + 1].toInt() and 0xFF) == 0xD9
                ) {
                    jpegEnd = i + 2
                    break
                }
            }
            if (jpegEnd in 1 until bytes.size) {
                val embeddedBytes = bytes.copyOfRange(jpegEnd, bytes.size)
                val embeddedText = EncodingUtils.decodeWithFallback(embeddedBytes).trim()
                if (embeddedText.isNotEmpty()) {
                    // 格式: 前缀**Base64编码的JSON
                    val parts = embeddedText.split("**")
                    val base64Part = if (parts.size >= 2) parts.last() else embeddedText
                    return try {
                        val cleaned = base64Part.filter {
                            it.isLetterOrDigit() || it == '+' || it == '/' || it == '='
                        }
                        val decoded = Base64.decode(cleaned)
                        String(decoded, Charsets.UTF_8).trim()
                    } catch (_: Throwable) {
                        embeddedText
                    }
                }
            }
        }

        // 普通响应：编码自动检测（UTF-8 → 失败用 GBK 回退）
        return EncodingUtils.decodeWithFallback(bytes).trim()
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
