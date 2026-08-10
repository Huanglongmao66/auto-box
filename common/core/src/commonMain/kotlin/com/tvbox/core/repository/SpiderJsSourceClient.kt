package com.tvbox.core.repository

import com.tvbox.core.model.MovieSource
import com.tvbox.core.model.SourceType
import com.tvbox.core.model.VodFilter
import com.tvbox.core.model.VodInfo
import com.tvbox.utils.JsonUtils
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Spider-JS 源客户端 (type=3)
 *
 * 脚本注入约定（CatVod/TVBox Spider 标准 API）：
 * - init(source) / init(param)：初始化，param 对应 MovieSource.ext（JSON）
 * - homeContent()                            → 返回 JSON list/category/filters/pagecount...
 * - categoryContent(tid:String, pg:String, filter:String)  → 返回 JSON list
 * - detailContent(ids:String)                → 返回 JSON list[0]
 * - searchContent(key:String, quick:Boolean) → 返回 JSON list
 * - playerContent(flag:String, id:String, vipFlags:String?, params:Any?) → 返回 JSON {url,parse?,header?}
 *
 * 兼容：如果脚本只给出部分方法（例如搜索），缺失的方法通过 JSON 的 ac=list/detail 作为 fallback；
 *      或者直接返回空，避免 UI 层崩溃。
 */
internal class SpiderJsSourceClient(private val http: HttpFetcher) : SourceClient {

    private data class EngineState(
        val engine: JsEngine,
        var inited: Boolean = false,
        var hasHome: Boolean = false,
        var hasCategory: Boolean = false,
        var hasDetail: Boolean = false,
        var hasSearch: Boolean = false,
        var hasPlayer: Boolean = false
    )

    private val engines = HashMap<String, EngineState>()
    private val lock = Mutex()

    override suspend fun init(source: MovieSource) {
        if (source.type != SourceType.SPIDER_JS) return
        if (engines.containsKey(source.key)) return
        if (!JsEngineFactory.isSupported) return
        lock.withLock {
            if (engines.containsKey(source.key)) return@withLock
            val engine = JsEngineFactory.create() ?: return@withLock
            val st = EngineState(engine)
            engines[source.key] = st
            runCatching {
                val code = loadJsCode(source)
                if (code.isNotBlank()) engine.eval(code)
                injectBridge(engine, source)
                st.hasHome = hasFn(engine, "homeContent")
                st.hasCategory = hasFn(engine, "categoryContent")
                st.hasDetail = hasFn(engine, "detailContent")
                st.hasSearch = hasFn(engine, "searchContent")
                st.hasPlayer = hasFn(engine, "playerContent")
                val initArg = source.ext.ifBlank { "{}" }
                if (hasFn(engine, "init")) {
                    runCatching { engine.call("init", initArg) }
                }
                st.inited = true
            }
        }
    }

    private fun loadJsCode(source: MovieSource): String {
        val js = source.js
        if (js.isBlank()) return ""
        // 如果是 http(s) URL，下载脚本
        if (js.startsWith("http://") || js.startsWith("https://")) {
            val raw = runCatching { http.get(js, source = source) }.getOrDefault("")
            if (raw.isNotBlank()) return raw
        }
        return js
    }

    private fun injectBridge(engine: JsEngine, source: MovieSource) {
        // 暴露少量 JS 工具：req(reqJson) 同步 GET/POST，返回字符串
        val bridge = SpiderBridge(http, source)
        engine.set("__tvbox_req__", bridge.asJsCallable())
        engine.eval(
            """
            if (typeof req === 'undefined') {
                function req(o){ try { return __tvbox_req__(typeof o==='string'?JSON.stringify({url:o}):JSON.stringify(o)); } catch(e){ return ''; } }
            }
            if (typeof java === 'undefined') var java = {};
            if (typeof android === 'undefined') var android = {};
            """.trimIndent()
        )
    }

    private fun hasFn(e: JsEngine, name: String): Boolean {
        val v = e.eval("(typeof $name === 'function') ? '1' : '';")
        return v == "1"
    }

    private fun state(key: String) = engines[key]

    override suspend fun home(source: MovieSource): TvboxJsonResponseParser.ParseListResult {
        val st = state(source.key) ?: return TvboxJsonResponseParser.ParseListResult.EMPTY
        if (!st.hasHome) return TvboxJsonResponseParser.ParseListResult.EMPTY
        val json = runCatching { st.engine.call("homeContent") }.getOrDefault("")
        if (json.isBlank()) return TvboxJsonResponseParser.ParseListResult.EMPTY
        return TvboxJsonResponseParser.parseList(json, source.key)
    }

    override suspend fun category(
        source: MovieSource,
        tid: String,
        page: Int,
        filters: List<VodFilter>
    ): TvboxJsonResponseParser.ParseListResult {
        val st = state(source.key) ?: return TvboxJsonResponseParser.ParseListResult.EMPTY
        if (!st.hasCategory) return TvboxJsonResponseParser.ParseListResult.EMPTY
        val fStr = if (filters.isEmpty()) "" else JsonUtils.toJson(filters.associate { it.key to it.selectedValue })
        val json = runCatching {
            st.engine.call("categoryContent", tid, page.toString(), fStr)
        }.getOrDefault("")
        if (json.isBlank()) return TvboxJsonResponseParser.ParseListResult.EMPTY
        return TvboxJsonResponseParser.parseList(json, source.key)
    }

    override suspend fun detail(source: MovieSource, vodId: String): VodInfo? {
        val st = state(source.key) ?: return null
        if (!st.hasDetail) return null
        val json = runCatching { st.engine.call("detailContent", vodId) }.getOrDefault("")
        if (json.isBlank()) return null
        return TvboxJsonResponseParser.parseDetail(json, source.key)
    }

    override suspend fun search(source: MovieSource, keyword: String, page: Int): List<VodInfo> {
        val st = state(source.key) ?: return emptyList()
        if (!st.hasSearch) return emptyList()
        val json = runCatching { st.engine.call("searchContent", keyword, false) }.getOrDefault("")
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
        val st = state(source.key)
        if (st != null && st.hasPlayer) {
            runCatching {
                val json = st.engine.call("playerContent", flag, input, "")
                val url = TvboxJsonResponseParser.parsePlayUrl(json)
                if (url.isNotBlank()) return url
            }
        }
        // 降级：parseUrl
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

    companion object {
        /** Spider 对 JS 暴露的同步 req(reqJsonStr) 接口：JS 端通过 JSON 传 {url,method,headers,body} */
        class SpiderBridge(private val http: HttpFetcher, private val source: MovieSource) {
            fun asJsCallable(): (String) -> String = { reqJson ->
                runCatching {
                    val obj = JsonUtils.parseToJsonElement(reqJson) as? JsonObject
                        ?: JsonObject(emptyMap())
                    val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: ""
                    val method = (obj["method"]?.jsonPrimitive?.contentOrNull ?: "GET").uppercase()
                    val body = obj["body"]?.jsonPrimitive?.contentOrNull ?: ""
                    val paramsObj = (obj["params"] as? JsonObject)
                    val params: Map<String, String> = paramsObj?.entries
                        ?.associate { it.key to (it.value.jsonPrimitive.contentOrNull ?: "") }.orEmpty()
                    if (url.isBlank()) return@runCatching ""
                    if (method == "POST") {
                        val ct = obj["contentType"]?.jsonPrimitive?.contentOrNull
                            ?: "application/x-www-form-urlencoded"
                        http.post(url, body, params, ct, source)
                    } else {
                        http.get(url, params, source)
                    }
                }.getOrDefault("")
            }
        }
    }
}
