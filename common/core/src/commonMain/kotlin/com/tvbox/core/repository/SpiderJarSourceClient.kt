package com.tvbox.core.repository

import com.tvbox.core.model.MovieSource
import com.tvbox.core.model.SourceType
import com.tvbox.core.model.VodFilter
import com.tvbox.core.model.VodInfo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Spider-JAR 源客户端 (type=4)
 *
 * commonMain: 暴露统一接口（因为 commonMain 无法直接加载 Jar，真实逻辑委托给 expect/actual 的 [JarSpiderLoader]）
 * - JVM(Desktop/LinuxTV)/Android(Art): 通过 URLClassLoader/DexClassLoader 加载 jar，
 *   通过反射调用 Spider 标准接口 init/homeContent/categoryContent/detailContent/searchContent/playerContent。
 * - JS/Native: 不支持，直接返回空（安全降级）。
 */
internal class SpiderJarSourceClient(private val http: HttpFetcher) : SourceClient {

    private val loaderCache = HashMap<String, JarSpiderHandle>()
    private val lock = Mutex()

    override suspend fun init(source: MovieSource) {
        if (source.type != SourceType.SPIDER_JAR) return
        if (!JarSpiderLoader.isSupported) return
        if (loaderCache.containsKey(source.key)) return
        lock.withLock {
            if (loaderCache.containsKey(source.key)) return@withLock
            runCatching {
                val handle = JarSpiderLoader.load(source, http)
                if (handle != null) {
                    loaderCache[source.key] = handle
                    handle.init(source.ext.ifBlank { "{}" })
                }
            }
        }
    }

    private fun handle(key: String) = loaderCache[key]

    override suspend fun home(source: MovieSource): TvboxJsonResponseParser.ParseListResult {
        val h = handle(source.key) ?: return TvboxJsonResponseParser.ParseListResult.EMPTY
        val json = runCatching { h.homeContent() }.getOrDefault("")
        if (json.isBlank()) return TvboxJsonResponseParser.ParseListResult.EMPTY
        return TvboxJsonResponseParser.parseList(json, source.key)
    }

    override suspend fun category(
        source: MovieSource,
        tid: String,
        page: Int,
        filters: List<VodFilter>
    ): TvboxJsonResponseParser.ParseListResult {
        val h = handle(source.key) ?: return TvboxJsonResponseParser.ParseListResult.EMPTY
        val f = filters.joinToString(",") { "${it.key}:${it.selectedValue}" }
        val json = runCatching { h.categoryContent(tid, page.toString(), f) }.getOrDefault("")
        if (json.isBlank()) return TvboxJsonResponseParser.ParseListResult.EMPTY
        return TvboxJsonResponseParser.parseList(json, source.key)
    }

    override suspend fun detail(source: MovieSource, vodId: String): VodInfo? {
        val h = handle(source.key) ?: return null
        val json = runCatching { h.detailContent(vodId) }.getOrDefault("")
        if (json.isBlank()) return null
        return TvboxJsonResponseParser.parseDetail(json, source.key)
    }

    override suspend fun search(source: MovieSource, keyword: String, page: Int): List<VodInfo> {
        val h = handle(source.key) ?: return emptyList()
        val json = runCatching { h.searchContent(keyword, quick = false) }.getOrDefault("")
        if (json.isBlank()) return emptyList()
        return TvboxJsonResponseParser.parseSearch(json, source.key)
    }

    override suspend fun playUrl(
        source: MovieSource,
        flag: String,
        input: String,
        parseUrl: String?
    ): String {
        if (isDirect(input)) return input
        val h = handle(source.key)
        if (h != null) {
            runCatching {
                val json = h.playerContent(flag, input, vipFlags = "")
                val url = TvboxJsonResponseParser.parsePlayUrl(json)
                if (url.isNotBlank()) return url
            }
        }
        val pu = parseUrl ?: source.playerUrl
        if (pu.isNotBlank()) {
            runCatching {
                val raw = http.get(pu, mapOf("url" to input), source)
                val url = TvboxJsonResponseParser.parsePlayUrl(raw)
                if (url.isNotBlank()) return url
            }
        }
        return input
    }

    private fun isDirect(u: String): Boolean {
        val l = u.lowercase()
        return listOf(".m3u8", ".mp4", ".mkv", ".flv", ".webm", ".ts", ".mov", ".avi").any { l.contains(it) }
    }
}

/**
 * 已加载的 Jar Spider 包装：隐藏反射差异，暴露字符串 JSON 接口
 */
internal interface JarSpiderHandle {
    fun init(param: String)
    fun homeContent(): String
    fun categoryContent(tid: String, pg: String, filter: String): String
    fun detailContent(ids: String): String
    fun searchContent(key: String, quick: Boolean): String
    fun playerContent(flag: String, id: String, vipFlags: String): String
}

/** expect/actual：按平台实现 jar 加载 */
internal expect object JarSpiderLoader {
    val isSupported: Boolean
    fun load(source: MovieSource, http: HttpFetcher): JarSpiderHandle?
}
