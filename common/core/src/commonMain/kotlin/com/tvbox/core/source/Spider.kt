package com.tvbox.core.source

/**
 * Spider 爬虫接口
 *
 * 迁移自 TVBoxOS `com.github.catvod.crawler.Spider`，用于支持 type=3 的 Spider/CSP 源
 * （Jar/JS/Py 爬虫）。
 *
 * 接口方法与 TVBoxOS 原版保持一致，所有内容方法返回 JSON 字符串，
 * 由 [com.tvbox.core.repository.TvboxJsonResponseParser] 统一解析。
 *
 * 当前版本仅提供空实现 [SpiderNull]，后续可在 androidMain 等平台源集中
 * 通过 DexClassLoader / QuickJS 实现 Jar 与 JS 爬虫的真实加载。
 */
interface Spider {

    /**
     * 站点 key（由调用方在 [init] 后设置）
     */
    var siteKey: String

    /**
     * 初始化爬虫
     *
     * @param extend 扩展数据（来自源配置的 ext 字段，可以是 JSON 字符串或 URL）
     */
    fun init(extend: String)

    /**
     * 首页数据内容
     *
     * @param filter 是否开启筛选
     * @return JSON 字符串，结构与 MacCMS `ac=list` 返回一致（含 class / filters / list）
     */
    fun homeContent(filter: Boolean): String

    /**
     * 首页最近更新数据
     *
     * 如果 [homeContent] 中不包含首页最近更新视频，可使用此接口单独返回。
     *
     * @return JSON 字符串，结构 `{"list": [...]}`
     */
    fun homeVideoContent(): String

    /**
     * 分类数据
     *
     * @param tid 分类 ID
     * @param pg 页码
     * @param filter 是否开启筛选
     * @param extend 筛选条件扩展参数
     * @return JSON 字符串，结构与 MacCMS `ac=list&t=xx&pg=xx` 返回一致
     */
    fun categoryContent(tid: String, pg: String, filter: Boolean, extend: Map<String, String>): String

    /**
     * 详情数据
     *
     * @param ids 影视 ID 列表（通常只取第一个）
     * @return JSON 字符串，结构与 MacCMS `ac=detail&ids=xx` 返回一致
     */
    fun detailContent(ids: List<String>): String

    /**
     * 搜索数据内容
     *
     * @param key 搜索关键词
     * @param quick 是否快速搜索
     * @return JSON 字符串，结构 `{"list": [...]}`
     */
    fun searchContent(key: String, quick: Boolean): String

    /**
     * 搜索数据内容（带分页）
     *
     * @param key 搜索关键词
     * @param quick 是否快速搜索
     * @param pg 页码
     * @return JSON 字符串，结构 `{"list": [...]}`
     */
    fun searchContent(key: String, quick: Boolean, pg: String): String {
        return searchContent(key, quick)
    }

    /**
     * 播放信息
     *
     * @param flag 播放线路标识
     * @param id 播放地址或 ID
     * @param vipFlags 需要 VIP 解析的标识列表
     * @return JSON 字符串，结构 `{"url": "...", "parse": "...", "header": "..."}`
     */
    fun playerContent(flag: String, id: String, vipFlags: List<String>): String

    /**
     * webview 解析时使用：判断当前加载的 url 是否是视频
     *
     * @param url 加载的 URL
     * @return true 表示是视频地址
     */
    fun isVideoFormat(url: String): Boolean

    /**
     * 是否手动检测 webview 中加载的 url
     *
     * @return true 表示需要手动检测
     */
    fun manualVideoCheck(): Boolean

    /**
     * 直播列表
     *
     * @param url 直播源地址
     * @return JSON 字符串
     */
    fun liveContent(url: String): String

    /**
     * 取消请求
     */
    fun cancelByTag()

    /**
     * 销毁爬虫，释放资源
     */
    fun destroy()
}
