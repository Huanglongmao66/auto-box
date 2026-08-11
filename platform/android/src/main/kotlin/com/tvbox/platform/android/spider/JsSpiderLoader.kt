package com.tvbox.platform.android.spider

import android.content.Context
import android.util.Log
import com.github.catvod.crawler.Spider
import com.github.catvod.crawler.SpiderNull
import com.github.catvod.net.OkHttp
import com.whl.quickjs.wrapper.JSCallFunction
import com.whl.quickjs.wrapper.JSFunction
import com.whl.quickjs.wrapper.JSObject
import com.whl.quickjs.wrapper.QuickJSContext
import org.jsoup.Jsoup
import java.io.File
import java.security.MessageDigest

/**
 * JS 爬虫加载器
 *
 * 迁移自 TVBoxOS com.github.catvod.crawler.js.JsSpider + JsLoader
 *
 * 通过 [QuickJSContext] 加载和执行 JS 爬虫脚本。
 *
 * 工作流程：
 * 1. 下载或读取 JS 爬虫文件
 * 2. 创建 QuickJS 运行时上下文
 * 3. 注入全局工具函数：req（HTTP 请求）、pdfa/pdfh（HTML 解析）
 * 4. 评估 JS 模块代码，提取 spider 对象
 * 5. 调用 spider 的 init/home/category/detail/search/play 等方法
 *
 * @param context Android 上下文
 */
class JsSpiderLoader(
    private val context: Context
) {
    companion object {
        private const val TAG = "JsSpiderLoader"
    }

    /** JS Spider 实例缓存：siteKey → JsSpider */
    private val spiders = HashMap<String, JsSpider>()

    /**
     * 获取 JS Spider 实例
     *
     * @param key 站点 key
     * @param api JS 文件路径或 URL（.js 结尾）
     * @param ext 扩展数据
     * @param jar Jar URL（JS 爬虫通常不需要，但兼容传入）
     * @return Spider 实例，加载失败返回 [SpiderNull]
     */
    fun getSpider(key: String, api: String, ext: String, jar: String): Spider {
        val safeKey = key.ifEmpty { "" }
        val safeApi = api.ifEmpty { "" }
        val safeExt = ext.ifEmpty { "" }

        if (safeApi.isEmpty()) return SpiderNull()

        // 缓存命中
        val cacheKey = safeKey + "|" + safeApi
        spiders[cacheKey]?.let { return it }

        return try {
            val spider = JsSpider(safeKey, safeApi, safeExt, context)
            spiders[cacheKey] = spider
            Log.i(TAG, "getSpider success key=$cacheKey, api=$safeApi")
            spider
        } catch (e: Throwable) {
            Log.e(TAG, "getSpider error key=$cacheKey, msg=${e.message}")
            SpiderNull()
        }
    }

    /**
     * 清理所有缓存
     */
    fun clear() {
        for (spider in spiders.values) {
            try { spider.destroy() } catch (_: Throwable) {}
        }
        spiders.clear()
    }

    /**
     * 下载 JS 文件内容
     */
    private fun loadJsContent(api: String): String {
        return when {
            api.startsWith("http") -> OkHttp.string(api)
            api.startsWith("assets") -> {
                val path = api.replace("assets://", "").replace("assets/", "")
                context.assets.open(path).bufferedReader().use { it.readText() }
            }
            api.startsWith("file") -> {
                val cleanPath = api.replace("file:/", "")
                File(cleanPath).readText()
            }
            else -> ""
        }
    }
}

/**
 * JS Spider 实现
 *
 * 封装 QuickJS 上下文，提供 JS 爬虫的完整生命周期管理。
 */
class JsSpider(
    private val siteKey: String,
    private val api: String,
    private val ext: String,
    private val context: Context
) : Spider() {

    companion object {
        /** 常见 HTTP 头字段，用于从 JS options 对象提取 */
        private val COMMON_HEADERS = arrayOf(
            "User-Agent", "Referer", "Cookie", "Accept", "Accept-Language",
            "Content-Type", "Origin", "X-Requested-With"
        )
    }

    private var ctx: QuickJSContext? = null
    private var spiderObj: JSObject? = null
    private var destroyed = false

    init {
        initialize()
    }

    /**
     * 初始化 JS 运行时
     */
    private fun initialize() {
        val ctx = QuickJSContext.create()
        this.ctx = ctx

        // 设置控制台输出
        ctx.setConsole(object : QuickJSContext.Console {
            override fun log(s: String) { Log.d("JsSpider", "[$siteKey] $s") }
            override fun info(s: String) { Log.i("JsSpider", "[$siteKey] $s") }
            override fun warn(s: String) { Log.w("JsSpider", "[$siteKey] $s") }
            override fun error(s: String) { Log.e("JsSpider", "[$siteKey] $s") }
        })

        // 注入全局工具函数
        injectGlobalUtils(ctx)

        // 下载并评估 JS 爬虫代码
        val content = loadJsContent()
        if (content.isBlank()) {
            throw RuntimeException("JS 文件内容为空: $api")
        }

        evaluateSpiderCode(ctx, content)

        // 获取 spider 对象
        val key = "J" + md5(siteKey)
        spiderObj = ctx.getGlobalObject().getProperty(key) as? JSObject
        if (spiderObj == null) {
            throw RuntimeException("无法获取 spider 对象: $key")
        }
        spiderObj!!.hold()
    }

    /**
     * 注入全局工具函数到 QuickJS 上下文
     *
     * 提供 JS 爬虫常用的 HTTP 请求和 HTML 解析工具：
     * - req(url, options): HTTP 请求，返回 {content, headers, url}
     * - pdfa(html, selector): 按选择器查询元素列表
     * - pdfh(html, selector): 按选择器查询单个元素文本
     */
    private fun injectGlobalUtils(ctx: QuickJSContext) {
        val global = ctx.getGlobalObject()

        // HTTP 请求函数
        ctx.setProperty(global, "req", JSCallFunction { args ->
            try {
                val url = args[0]?.toString() ?: return@JSCallFunction ""
                val headerMap = HashMap<String, String>()
                var method = "GET"
                var body: String? = null

                // 解析 options 参数（JSObject）
                if (args.size > 1 && args[1] is JSObject) {
                    val opts = args[1] as JSObject
                    // 读取 method
                    val m = opts.getProperty("method")
                    if (m != null && m.toString().isNotEmpty()) method = m.toString()
                    // 读取 body
                    val b = opts.getProperty("body")
                    if (b != null && b.toString().isNotEmpty()) body = b.toString()
                    // 读取 headers 子对象
                    val headersObj = opts.getProperty("headers")
                    if (headersObj is JSObject) {
                        val headersStr = headersObj.toString()
                        // 尝试解析 JSON 格式的 headers
                        parseHeadersFromJson(headersStr, headerMap)
                    }
                    // 也支持直接在 options 上设置 header 字段
                    for (commonHeader in COMMON_HEADERS) {
                        val v = opts.getProperty(commonHeader)
                        if (v != null && v.toString().isNotEmpty()) {
                            headerMap[commonHeader] = v.toString()
                        }
                    }
                }

                val result = if (method.equals("POST", true) && body != null) {
                    OkHttp.postJson(url, headerMap, body)
                } else {
                    OkHttp.string(url, headerMap)
                }

                // 返回一个 JS 对象 {body: "...", content: "...", url: "..."}
                val resObj = ctx.createNewJSObject()
                ctx.setProperty(resObj, "body", result)
                ctx.setProperty(resObj, "content", result)
                ctx.setProperty(resObj, "url", url)
                resObj
            } catch (e: Throwable) {
                Log.e("JsSpider", "req error: ${e.message}")
                ""
            }
        })

        // HTTP 便捷函数（简单 GET）
        ctx.setProperty(global, "request", JSCallFunction { args ->
            try {
                val url = args[0]?.toString() ?: return@JSCallFunction ""
                OkHttp.string(url)
            } catch (e: Throwable) {
                Log.e("JsSpider", "request error: ${e.message}")
                ""
            }
        })

        // HTML 解析 - pdfa (查询所有匹配元素，返回 HTML 字符串数组)
        ctx.setProperty(global, "pdfa", JSCallFunction { args ->
            try {
                val html = args[0]?.toString() ?: return@JSCallFunction ctx.createNewJSArray()
                val selector = args[1]?.toString() ?: return@JSCallFunction ctx.createNewJSArray()
                val doc = Jsoup.parse(html)
                val elements = doc.select(selector)
                val arr = ctx.createNewJSArray()
                for (i in elements.indices) {
                    ctx.setProperty(arr, i.toString(), elements[i].toString())
                }
                arr
            } catch (e: Throwable) {
                Log.e("JsSpider", "pdfa error: ${e.message}")
                ctx.createNewJSArray()
            }
        })

        // HTML 解析 - pdfh (查询单个元素文本)
        ctx.setProperty(global, "pdfh", JSCallFunction { args ->
            try {
                val html = args[0]?.toString() ?: return@JSCallFunction ""
                val selector = args[1]?.toString() ?: return@JSCallFunction ""
                val doc = Jsoup.parse(html)
                val element = doc.selectFirst(selector)
                element?.text() ?: ""
            } catch (e: Throwable) {
                Log.e("JsSpider", "pdfh error: ${e.message}")
                ""
            }
        })
    }

    /**
     * 从 JSON 字符串解析 headers 到 Map
     */
    private fun parseHeadersFromJson(json: String, map: HashMap<String, String>) {
        try {
            val obj = org.json.JSONObject(json)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = obj.getString(key)
            }
        } catch (_: Throwable) {
            // 不是 JSON 格式，忽略
        }
    }

    /**
     * 评估 JS 爬虫代码
     *
     * 兼容多种 JS 爬虫格式：
     * 1. ES Module export default
     * 2. __JS_SPIDER__ 赋值
     * 3. __jsEvalReturn 函数
     */
    private fun evaluateSpiderCode(ctx: QuickJSContext, content: String) {
        val key = "J" + md5(siteKey)

        var code = content
        // 移除 BOM
        if (code.startsWith("\uFEFF")) code = code.substring(1)

        when {
            // __jsEvalReturn 格式
            code.contains("__jsEvalReturn") -> {
                ctx.evaluate("var req = req;")
                ctx.evaluate(code)
                ctx.evaluate("globalThis.$key = __jsEvalReturn();")
            }
            // __JS_SPIDER__ 格式
            code.contains("__JS_SPIDER__") -> {
                code = code.replace("__JS_SPIDER__", "globalThis.$key")
                ctx.evaluate(code)
            }
            // ES Module export default 格式
            code.contains("export default") -> {
                code = code.replace(Regex("export\\s+default\\s*"), "globalThis.$key = ")
                ctx.evaluate(code)
            }
            // 直接评估
            else -> {
                ctx.evaluate(code)
                ctx.evaluate("globalThis.$key = typeof spider !== 'undefined' ? spider : (typeof __config !== 'undefined' ? __config : {});")
            }
        }
    }

    /**
     * 加载 JS 文件内容
     */
    private fun loadJsContent(): String {
        return when {
            api.startsWith("http") -> OkHttp.string(api)
            api.startsWith("assets") -> {
                val path = api.replace("assets://", "").replace("assets/", "")
                context.assets.open(path).bufferedReader().use { it.readText() }
            }
            api.startsWith("file") -> {
                File(api.replace("file:/", "")).readText()
            }
            else -> ""
        }
    }

    /**
     * 调用 JS spider 方法
     *
     * 使用 QuickJS 的 getJSFunction + call 模式：
     * 1. 从 spider 对象获取指定名称的 JSFunction
     * 2. 调用 function.call(args) 执行 JS 函数
     * 3. 释放 function 引用
     */
    private fun callMethod(name: String, vararg args: Any): String {
        if (destroyed) return ""
        val obj = spiderObj ?: return ""
        return try {
            val function = obj.getJSFunction(name)
            if (function == null) {
                Log.w("JsSpider", "方法不存在: $name")
                return ""
            }
            val result = function.call(args)
            function.release()
            result?.toString() ?: ""
        } catch (e: Throwable) {
            Log.e("JsSpider", "callMethod($name) error: ${e.message}")
            ""
        }
    }

    // ========== Spider 接口实现 ==========

    override fun init(context: Context, extend: String) {
        try {
            val ctx = ctx ?: return
            val obj = spiderObj ?: return
            if (extend.isNotBlank()) {
                callMethod("init", extend)
            } else {
                callMethod("init", "")
            }
        } catch (e: Throwable) {
            Log.e("JsSpider", "init error: ${e.message}")
        }
    }

    override fun homeContent(filter: Boolean): String {
        return callMethod("home", filter)
    }

    override fun homeVideoContent(): String {
        return callMethod("homeVod")
    }

    override fun categoryContent(
        tid: String,
        pg: String,
        filter: Boolean,
        extend: HashMap<String, String>?
    ): String {
        val ctx = ctx ?: return ""
        val obj = spiderObj ?: return ""
        return try {
            // 将 HashMap 转为 JS 对象
            val extendObj = ctx.createNewJSObject()
            extend?.forEach { (k, v) ->
                ctx.setProperty(extendObj, k, v)
            }
            val function = obj.getJSFunction("category")
            if (function == null) return ""
            val result = function.call(tid, pg, filter, extendObj)
            function.release()
            result?.toString() ?: ""
        } catch (e: Throwable) {
            Log.e("JsSpider", "categoryContent error: ${e.message}")
            ""
        }
    }

    override fun detailContent(ids: List<String>): String {
        return callMethod("detail", ids.firstOrNull() ?: "")
    }

    override fun searchContent(key: String, quick: Boolean): String {
        return callMethod("search", key, quick)
    }

    override fun searchContent(key: String, quick: Boolean, pg: String): String {
        return callMethod("search", key, quick, pg)
    }

    override fun playerContent(flag: String, id: String, vipFlags: List<String>): String {
        return callMethod("play", flag, id, vipFlags)
    }

    override fun isVideoFormat(url: String): Boolean {
        val result = callMethod("isVideo", url)
        return result.equals("true", ignoreCase = true)
    }

    override fun manualVideoCheck(): Boolean {
        val result = callMethod("sniffer")
        return result.equals("true", ignoreCase = true)
    }

    override fun liveContent(url: String): String {
        return callMethod("live", url)
    }

    override fun cancelByTag() {
        try { OkHttp.cancel("js_okhttp_tag") } catch (_: Throwable) {}
    }

    override fun destroy() {
        if (destroyed) return
        destroyed = true
        try {
            spiderObj = null
            ctx?.destroy()
        } catch (_: Throwable) {}
    }

    private fun md5(input: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (_: Throwable) {
            input.hashCode().toString()
        }
    }
}
