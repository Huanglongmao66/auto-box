package com.tvbox.core.repository

import com.tvbox.core.model.MovieSource
import com.tvbox.core.model.VodClass
import com.tvbox.core.model.VodEpisode
import com.tvbox.core.model.VodFilter
import com.tvbox.core.model.VodInfo
import com.tvbox.core.network.NetworkService
import com.tvbox.core.source.HomeContent
import com.tvbox.core.source.CategoryResult
import com.tvbox.core.spider.SpiderEngineFactory
import com.tvbox.utils.XmlUtils
import com.tvbox.utils.XmlNode

/**
 * 数据源客户端接口
 *
 * 封装 TVBox 源的 5 种核心 API 调用：
 *   home（首页）、category（分类）、detail（详情）、search（搜索）、playUrl（播放解析）
 *
 * 按 [MovieSource.type] 分发到具体实现：
 *   0 → [XmlSourceClient]
 *   1 → [JsonSourceClient]
 *   3 → [SpiderSourceClient]
 */
interface SourceClient {

    suspend fun home(source: MovieSource): HomeContent

    suspend fun category(
        source: MovieSource,
        tid: String,
        page: Int,
        filters: List<VodFilter> = emptyList()
    ): CategoryResult

    suspend fun detail(source: MovieSource, vodId: String): VodInfo?

    suspend fun search(source: MovieSource, keyword: String, page: Int = 1): List<VodInfo>

    suspend fun playUrl(source: MovieSource, flag: String, input: String): String
}

/**
 * 根据 [MovieSource.type] 创建对应的 [SourceClient] 实现
 */
object SourceClientFactory {

    fun create(source: MovieSource, networkService: NetworkService): SourceClient {
        return when (source.type) {
            0 -> XmlSourceClient(networkService)
            1 -> JsonSourceClient(networkService)
            3 -> SpiderSourceClient(networkService)
            else -> JsonSourceClient(networkService) // 默认按 JSON 处理
        }
    }
}

// ============ JSON 源客户端 (type=1) ============

/**
 * JSON 源客户端
 *
 * 构造标准 TVBox API URL 并发起 GET 请求，使用 [TvboxJsonResponseParser] 解析响应。
 */
class JsonSourceClient(
    private val networkService: NetworkService
) : SourceClient {

    override suspend fun home(source: MovieSource): HomeContent {
        val url = buildUrl(source.api, mapOf("ac" to "detail", "pg" to "1"))
        val raw = safeGet(url, source.header)
        val result = TvboxJsonResponseParser.parseList(raw, source.key)
        return HomeContent(
            list = result.list,
            categories = result.categories,
            filters = result.filters
        )
    }

    override suspend fun category(
        source: MovieSource,
        tid: String,
        page: Int,
        filters: List<VodFilter>
    ): CategoryResult {
        val params = mutableMapOf("ac" to "detail", "t" to tid, "pg" to page.toString())
        filters.forEach { f -> f.selectedValue.takeIf { it.isNotBlank() }?.let { params[f.key] = it } }
        val url = buildUrl(source.api, params)
        val raw = safeGet(url, source.header)
        val result = TvboxJsonResponseParser.parseList(raw, source.key)
        return CategoryResult(
            list = result.list,
            page = result.page,
            pageCount = result.pageCount,
            limit = result.limit,
            total = result.total
        )
    }

    override suspend fun detail(source: MovieSource, vodId: String): VodInfo? {
        val url = buildUrl(source.api, mapOf("ac" to "detail", "ids" to vodId))
        val raw = safeGet(url, source.header)
        return TvboxJsonResponseParser.parseDetail(raw, source.key)
    }

    override suspend fun search(source: MovieSource, keyword: String, page: Int): List<VodInfo> {
        val searchUrl = if (source.searchUrl.isNotBlank()) source.searchUrl else source.api
        val url = buildUrl(searchUrl, mapOf("wd" to keyword, "pg" to page.toString()))
        val raw = safeGet(url, source.header)
        return TvboxJsonResponseParser.parseSearch(raw, source.key)
    }

    override suspend fun playUrl(source: MovieSource, flag: String, input: String): String {
        if (isDirectPlayUrl(input)) return input
        // 尝试 ac=video 接口解析
        runCatching {
            val url = buildUrl(source.api, mapOf("ac" to "video", "flag" to flag, "id" to input))
            val raw = safeGet(url, source.header)
            val parsed = TvboxJsonResponseParser.parsePlayUrl(raw)
            if (parsed.isNotBlank() && isDirectPlayUrl(parsed)) return parsed
        }
        // 调用 playerUrl 解析接口
        if (source.playerUrl.isNotBlank()) {
            runCatching {
                val raw = networkService.get(source.playerUrl, mapOf("url" to input))
                val parsed = TvboxJsonResponseParser.parsePlayUrl(raw)
                if (parsed.isNotBlank() && isDirectPlayUrl(parsed)) return parsed
            }
        }
        return input
    }

    // ============ 工具方法 ============

    private fun buildUrl(base: String, params: Map<String, String>): String {
        val cleanBase = base.substringBefore("?")
        val sep = if (base.contains("?")) "&" else "?"
        val qs = params.entries.joinToString("&") { (k, v) ->
            k + "=" + v.encodeUrl()
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

    private suspend fun safeGet(url: String, headerJson: String): String {
        val headers = parseHeaders(headerJson)
        return networkService.get(url, headers)
    }

    private fun parseHeaders(headerJson: String): Map<String, String> {
        if (headerJson.isBlank()) return emptyMap()
        return runCatching {
            com.tvbox.utils.JsonUtils.parseToJsonElement(headerJson)
                ?.let { it as? kotlinx.serialization.json.JsonObject }
                ?.mapValues { it.value.toString().trim('"') }
                ?: emptyMap()
        }.getOrDefault(emptyMap())
    }
}

// ============ XML 源客户端 (type=0) ============

/**
 * XML 源客户端
 *
 * 构造 TVBox XML API URL 并发起 GET 请求，使用 [TvboxXmlResponseParser] 解析响应。
 * XML 源的 URL 格式与 JSON 源一致（ac=detail / ac=videoid / wd= 等），
 * 返回体为 XML 而非 JSON。
 */
class XmlSourceClient(
    private val networkService: NetworkService
) : SourceClient {

    override suspend fun home(source: MovieSource): HomeContent {
        val url = buildUrl(source.api, mapOf("ac" to "detail", "pg" to "1"))
        val raw = safeGet(url, source.header)
        val result = TvboxXmlResponseParser.parseList(raw, source.key)
        return HomeContent(
            list = result.list,
            categories = result.categories,
            filters = result.filters
        )
    }

    override suspend fun category(
        source: MovieSource,
        tid: String,
        page: Int,
        filters: List<VodFilter>
    ): CategoryResult {
        val params = mutableMapOf("ac" to "detail", "t" to tid, "pg" to page.toString())
        filters.forEach { f -> f.selectedValue.takeIf { it.isNotBlank() }?.let { params[f.key] = it } }
        val url = buildUrl(source.api, params)
        val raw = safeGet(url, source.header)
        val result = TvboxXmlResponseParser.parseList(raw, source.key)
        return CategoryResult(
            list = result.list,
            page = result.page,
            pageCount = result.pageCount,
            limit = result.limit,
            total = result.total
        )
    }

    override suspend fun detail(source: MovieSource, vodId: String): VodInfo? {
        val url = buildUrl(source.api, mapOf("ac" to "videoid", "id" to vodId))
        val raw = safeGet(url, source.header)
        return TvboxXmlResponseParser.parseDetail(raw, source.key)
    }

    override suspend fun search(source: MovieSource, keyword: String, page: Int): List<VodInfo> {
        val searchUrl = if (source.searchUrl.isNotBlank()) source.searchUrl else source.api
        val url = buildUrl(searchUrl, mapOf("wd" to keyword, "pg" to page.toString()))
        val raw = safeGet(url, source.header)
        return TvboxXmlResponseParser.parseSearch(raw, source.key)
    }

    override suspend fun playUrl(source: MovieSource, flag: String, input: String): String {
        if (isDirectPlayUrl(input)) return input
        // XML 源通常通过 playUrl 接口解析
        if (source.playerUrl.isNotBlank()) {
            runCatching {
                val raw = networkService.get(source.playerUrl, mapOf("url" to input, "flag" to flag))
                val parsed = TvboxXmlResponseParser.parsePlayUrl(raw)
                if (parsed.isNotBlank() && isDirectPlayUrl(parsed)) return parsed
            }
        }
        // 尝试 ac=video
        runCatching {
            val url = buildUrl(source.api, mapOf("ac" to "video", "id" to input))
            val raw = safeGet(url, source.header)
            val parsed = TvboxXmlResponseParser.parsePlayUrl(raw)
            if (parsed.isNotBlank() && isDirectPlayUrl(parsed)) return parsed
        }
        return input
    }

    // ============ 工具方法（与 JsonSourceClient 共享逻辑） ============

    private fun buildUrl(base: String, params: Map<String, String>): String {
        val cleanBase = base.substringBefore("?")
        val sep = if (base.contains("?")) "&" else "?"
        val qs = params.entries.joinToString("&") { (k, v) ->
            k + "=" + v.encodeUrl()
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

    private suspend fun safeGet(url: String, headerJson: String): String {
        val headers = parseHeaders(headerJson)
        return networkService.get(url, headers)
    }

    private fun parseHeaders(headerJson: String): Map<String, String> {
        if (headerJson.isBlank()) return emptyMap()
        return runCatching {
            com.tvbox.utils.JsonUtils.parseToJsonElement(headerJson)
                ?.let { it as? kotlinx.serialization.json.JsonObject }
                ?.mapValues { it.value.toString().trim('"') }
                ?: emptyMap()
        }.getOrDefault(emptyMap())
    }
}

// ============ Spider 源客户端 (type=3) ============

/**
 * Spider（JS）源客户端
 *
 * 通过 [SpiderEngineFactory] 获取 JS 执行引擎，调用 Spider 源脚本中的标准函数。
 * 当平台不支持 JS 执行时（如 LinuxX64），降级为直接 HTTP 请求（尝试 JSON 格式）。
 */
class SpiderSourceClient(
    private val networkService: NetworkService
) : SourceClient {

    private val spiderEngine by lazy { SpiderEngineFactory.get() }

    override suspend fun home(source: MovieSource): HomeContent {
        if (!spiderEngine.available || source.js.isBlank()) {
            // 降级：尝试按 JSON 源处理
            return JsonSourceClient(networkService).home(source)
        }
        val result = runCatching {
            spiderEngine.evalFunction(source.js, "homeContent", "[]")
        }.getOrNull()
        if (result.isNullOrBlank()) return HomeContent()
        return runCatching {
            val parsed = TvboxJsonResponseParser.parseList(result, source.key)
            HomeContent(parsed.list, parsed.categories, parsed.filters)
        }.getOrDefault(HomeContent())
    }

    override suspend fun category(
        source: MovieSource,
        tid: String,
        page: Int,
        filters: List<VodFilter>
    ): CategoryResult {
        if (!spiderEngine.available || source.js.isBlank()) {
            return JsonSourceClient(networkService).category(source, tid, page, filters)
        }
        val argsJson = """["$tid", $page, ${buildFiltersJson(filters)}, "{}"]"""
        val result = runCatching {
            spiderEngine.evalFunction(source.js, "categoryContent", argsJson)
        }.getOrNull()
        if (result.isNullOrBlank()) return CategoryResult()
        return runCatching {
            val parsed = TvboxJsonResponseParser.parseList(result, source.key)
            CategoryResult(parsed.list, parsed.page, parsed.pageCount, parsed.limit, parsed.total)
        }.getOrDefault(CategoryResult())
    }

    override suspend fun detail(source: MovieSource, vodId: String): VodInfo? {
        if (!spiderEngine.available || source.js.isBlank()) {
            return JsonSourceClient(networkService).detail(source, vodId)
        }
        val argsJson = """[["$vodId"]]"""
        val result = runCatching {
            spiderEngine.evalFunction(source.js, "detailContent", argsJson)
        }.getOrNull()
        if (result.isNullOrBlank()) return null
        return runCatching {
            TvboxJsonResponseParser.parseDetail(result, source.key)
        }.getOrNull()
    }

    override suspend fun search(source: MovieSource, keyword: String, page: Int): List<VodInfo> {
        if (!spiderEngine.available || source.js.isBlank()) {
            return JsonSourceClient(networkService).search(source, keyword, page)
        }
        val argsJson = """["$keyword", $page]"""
        val result = runCatching {
            spiderEngine.evalFunction(source.js, "searchContent", argsJson)
        }.getOrNull()
        if (result.isNullOrBlank()) return emptyList()
        return runCatching {
            TvboxJsonResponseParser.parseSearch(result, source.key)
        }.getOrDefault(emptyList())
    }

    override suspend fun playUrl(source: MovieSource, flag: String, input: String): String {
        if (isDirectPlayUrl(input)) return input
        if (spiderEngine.available && source.js.isNotBlank()) {
            val argsJson = """["$flag", "$input", ["$flag"]]"""
            val result = runCatching {
                spiderEngine.evalFunction(source.js, "playUrl", argsJson)
            }.getOrNull()
            if (!result.isNullOrBlank()) {
                val parsed = TvboxJsonResponseParser.parsePlayUrl(result)
                if (parsed.isNotBlank() && isDirectPlayUrl(parsed)) return parsed
            }
        }
        // 降级：playerUrl 解析
        if (source.playerUrl.isNotBlank()) {
            runCatching {
                val raw = networkService.get(source.playerUrl, mapOf("url" to input))
                val parsed = TvboxJsonResponseParser.parsePlayUrl(raw)
                if (parsed.isNotBlank() && isDirectPlayUrl(parsed)) return parsed
            }
        }
        return input
    }

    private fun buildFiltersJson(filters: List<VodFilter>): String {
        if (filters.isEmpty()) return "[]"
        return "[" + filters.joinToString(",") {
            """{"key":"${it.key}","value":"${it.selectedValue}"}"""
        } + "]"
    }
}

// ============ 公共工具 ============

/**
 * 判断 URL 是否为可直接播放的直链
 */
internal fun isDirectPlayUrl(url: String): Boolean {
    if (url.isBlank()) return false
    val lower = url.lowercase()
    return lower.startsWith("http://") || lower.startsWith("https://") ||
        lower.startsWith("rtmp://") || lower.startsWith("rtsp://") ||
        lower.endsWith(".m3u8") || lower.endsWith(".mp4") ||
        lower.endsWith(".flv") || lower.endsWith(".mkv") ||
        lower.endsWith(".avi") || lower.endsWith(".ts") ||
        lower.endsWith(".m4a") || lower.endsWith(".mp3")
}
