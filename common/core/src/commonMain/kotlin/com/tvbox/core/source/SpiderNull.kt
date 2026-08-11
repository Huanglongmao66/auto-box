package com.tvbox.core.source

/**
 * [Spider] 的空实现
 *
 * 迁移自 TVBoxOS `com.github.catvod.crawler.SpiderNull`。
 *
 * 在以下场景作为兜底返回：
 * 1. type=3 的 Spider 源缺少有效的 Jar / JS / Py 加载器时
 * 2. Jar / JS 加载失败、类未找到时
 * 3. 后台预热爬虫异常时
 *
 * 所有内容方法返回空字符串，由上层解析器自然降级为空列表 / null，
 * 不会影响其他源的请求与 UI 渲染。
 */
class SpiderNull : Spider {

    override var siteKey: String = ""

    override fun init(extend: String) {
        // 空实现：无资源需要初始化
    }

    override fun homeContent(filter: Boolean): String = ""

    override fun homeVideoContent(): String = ""

    override fun categoryContent(
        tid: String,
        pg: String,
        filter: Boolean,
        extend: Map<String, String>
    ): String = ""

    override fun detailContent(ids: List<String>): String = ""

    override fun searchContent(key: String, quick: Boolean): String = ""

    override fun searchContent(key: String, quick: Boolean, pg: String): String = ""

    override fun playerContent(flag: String, id: String, vipFlags: List<String>): String = ""

    override fun isVideoFormat(url: String): Boolean = false

    override fun manualVideoCheck(): Boolean = false

    override fun liveContent(url: String): String = ""

    override fun cancelByTag() {
        // 空实现：无请求需要取消
    }

    override fun destroy() {
        // 空实现：无资源需要释放
    }
}
