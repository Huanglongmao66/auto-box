package com.tvbox.platform.android.spider

import android.content.Context
import android.util.Log
import com.github.catvod.crawler.SpiderNull
import com.tvbox.core.source.SpiderLoader

/**
 * SpiderAdapter 桥接 Java Spider → Kotlin Spider 接口
 *
 * 将 com.github.catvod.crawler.Spider（Java 抽象类）的实例
 * 适配为 com.tvbox.core.source.Spider（Kotlin 接口）的实现。
 *
 * TVBoxSourceClient 通过 Kotlin Spider 接口调用，
 * SpiderAdapter 内部委托给 Java Spider 实例完成实际工作。
 */
class SpiderAdapter(
    private val javaSpider: com.github.catvod.crawler.Spider
) : com.tvbox.core.source.Spider {

    override var siteKey: String
        get() = javaSpider.siteKey
        set(value) { javaSpider.siteKey = value }

    override fun init(extend: String) {
        try {
            // 调用 Java Spider 的 init(Context, String) 方法
            val method = javaSpider.javaClass.getMethod(
                "init",
                Context::class.java,
                String::class.java
            )
            method.invoke(javaSpider, SpiderContextHolder.context, extend)
        } catch (_: Throwable) {
            // 降级：调用 init(String)
            try {
                val method = javaSpider.javaClass.getMethod("init", String::class.java)
                method.invoke(javaSpider, extend)
            } catch (_: Throwable) {
                // 进一步降级：无参 init
            }
        }
    }

    override fun homeContent(filter: Boolean): String {
        return try { javaSpider.homeContent(filter) } catch (_: Throwable) { "" }
    }

    override fun homeVideoContent(): String {
        return try { javaSpider.homeVideoContent() } catch (_: Throwable) { "" }
    }

    override fun categoryContent(
        tid: String,
        pg: String,
        filter: Boolean,
        extend: Map<String, String>
    ): String {
        return try {
            val hashMap = HashMap<String, String>()
            extend.forEach { (k, v) -> hashMap[k] = v }
            javaSpider.categoryContent(tid, pg, filter, hashMap)
        } catch (_: Throwable) { "" }
    }

    override fun detailContent(ids: List<String>): String {
        return try { javaSpider.detailContent(ids) } catch (_: Throwable) { "" }
    }

    override fun searchContent(key: String, quick: Boolean): String {
        return try { javaSpider.searchContent(key, quick) } catch (_: Throwable) { "" }
    }

    override fun searchContent(key: String, quick: Boolean, pg: String): String {
        return try { javaSpider.searchContent(key, quick, pg) } catch (_: Throwable) { "" }
    }

    override fun playerContent(flag: String, id: String, vipFlags: List<String>): String {
        return try { javaSpider.playerContent(flag, id, vipFlags) } catch (_: Throwable) { "" }
    }

    override fun isVideoFormat(url: String): Boolean {
        return try { javaSpider.isVideoFormat(url) } catch (_: Throwable) { false }
    }

    override fun manualVideoCheck(): Boolean {
        return try { javaSpider.manualVideoCheck() } catch (_: Throwable) { false }
    }

    override fun liveContent(url: String): String {
        return try { javaSpider.liveContent(url) } catch (_: Throwable) { "" }
    }

    override fun cancelByTag() {
        try { javaSpider.cancelByTag() } catch (_: Throwable) {}
    }

    override fun destroy() {
        try { javaSpider.destroy() } catch (_: Throwable) {}
    }
}

/**
 * 全局 Context 持有者
 *
 * 在 AndroidSpiderLoader 初始化时设置，
 * 供 SpiderAdapter 调用 Java Spider 的 init(Context, String) 时使用。
 */
object SpiderContextHolder {
    @Volatile
    var context: Context? = null
}

/**
 * Android 平台统一 SpiderLoader
 *
 * 实现 commonMain 的 [SpiderLoader] 接口，
 * 根据站点 API 类型自动分发到对应的加载器：
 *
 * - api 以 `.js` 结尾 → [JsSpiderLoader]（QuickJS 引擎）
 * - api 以 `csp_` 开头 → [JarSpiderLoader]（DexClassLoader 加载 Jar）
 * - 其他 → [SpiderNull]（不支持）
 *
 * @param context Android 上下文
 */
class AndroidSpiderLoader(
    context: Context
) : SpiderLoader {

    companion object {
        private const val TAG = "AndroidSpiderLoader"
    }

    private val jarLoader: JarSpiderLoader = JarSpiderLoader(context)
    private val jsLoader: JsSpiderLoader = JsSpiderLoader(context)

    init {
        SpiderContextHolder.context = context
    }

    override fun getSpider(
        key: String,
        api: String,
        ext: String,
        jar: String
    ): com.tvbox.core.source.Spider {
        if (api.isEmpty()) return com.tvbox.core.source.SpiderNull()

        Log.d(TAG, "getSpider key=$key, api=$api, jar=${jar.take(50)}")

        // 判断 Spider 类型
        val javaSpider = when {
            // JS 爬虫：api 以 .js 结尾或包含 .js?
            api.endsWith(".js") || api.contains(".js?") -> {
                jsLoader.getSpider(key, api, ext, jar)
            }
            // CSP/Jar 爬虫：api 以 csp_ 开头
            api.startsWith("csp_") -> {
                jarLoader.getSpider(key, api, ext, jar)
            }
            // 其他类型：尝试 Jar 加载（兼容部分特殊格式）
            jar.isNotEmpty() -> {
                jarLoader.getSpider(key, api, ext, jar)
            }
            // 无法识别的类型
            else -> {
                Log.w(TAG, "无法识别的 Spider 类型: api=$api")
                SpiderNull()
            }
        }

        // 如果 Java Spider 是 SpiderNull，直接返回 Kotlin SpiderNull
        if (javaSpider is SpiderNull) {
            return com.tvbox.core.source.SpiderNull()
        }

        // 包装为 Kotlin Spider 接口实现
        return SpiderAdapter(javaSpider)
    }

    override fun clear() {
        jarLoader.clear()
        jsLoader.clear()
    }
}
