package com.tvbox.core.source

/**
 * Spider 加载器接口
 *
 * 迁移自 TVBoxOS `JarLoader` / `JsLoader` / `PyLoader` 的统一抽象。
 *
 * 负责根据站点配置（key / api / ext / jar）加载对应的 Spider 爬虫实例。
 *
 * 平台扩展点：
 * - androidMain 可通过 `DexClassLoader` 实现 Jar 爬虫加载（对应 TVBoxOS JarLoader）
 * - androidMain 可通过 `QuickJSContext` 实现 JS 爬虫加载（对应 TVBoxOS JsLoader）
 * - androidMain 可通过 `IPyLoader` 实现 Python 爬虫加载（对应 TVBoxOS PyLoader）
 *
 * commonMain 仅提供 [DefaultSpiderLoader] 空实现，所有 type=3 源请求将降级为空结果。
 */
interface SpiderLoader {

    /**
     * 获取 Spider 实例
     *
     * @param key 站点 key
     * @param api 站点 API（type=3 时可能是 csp_xxx / .js / .py 路径）
     * @param ext 扩展数据（Spider 的 ext 配置，可以是 JSON 字符串或 URL）
     * @param jar 自定义 Jar URL（可带 ;md5; 校验）
     * @return Spider 实例，加载失败时返回 [SpiderNull]
     */
    fun getSpider(key: String, api: String, ext: String, jar: String): Spider

    /**
     * 清理所有已加载的 Spider 实例，释放资源
     */
    fun clear()
}

/**
 * [SpiderLoader] 的默认空实现
 *
 * 始终返回 [SpiderNull]，用于 commonMain 中不具备 Jar / JS / Py 加载能力的场景。
 *
 * 各平台可在 expect/actual 或平台源集中提供真实实现替换此实现。
 */
class DefaultSpiderLoader : SpiderLoader {

    override fun getSpider(key: String, api: String, ext: String, jar: String): Spider {
        return SpiderNull()
    }

    override fun clear() {
        // 空实现：无缓存需要清理
    }
}
