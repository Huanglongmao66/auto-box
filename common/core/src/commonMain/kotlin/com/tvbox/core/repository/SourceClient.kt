package com.tvbox.core.repository

import com.tvbox.core.model.MovieSource
import com.tvbox.core.model.VodFilter
import com.tvbox.core.model.VodInfo

/**
 * 统一的影视源客户端抽象（对 VodRepository 屏蔽 JSON/XML/XPath/Spider-JS/Spider-JAR/MIX 的差异）
 *
 * 方法签名语义与 TVBoxOS/CatVod Spider 抽象基类一致：
 *   - init(source)  在首次调用业务方法前懒执行，用于加载 JS/JAR/XPath 规则
 *   - home()        首页：Banner / 分类 / 推荐列表 / 热播榜
 *   - category()    分类页
 *   - detail()      详情（含剧集列表）
 *   - search()      搜索
 *   - playUrl()     播放地址解析（三级：直链 > ac=video > playerUrl）
 */
internal interface SourceClient {

    /** 初始化。已初始化后重复调用应快速返回 */
    suspend fun init(source: MovieSource) {}

    suspend fun home(source: MovieSource): TvboxJsonResponseParser.ParseListResult

    suspend fun category(
        source: MovieSource,
        tid: String,
        page: Int,
        filters: List<VodFilter>
    ): TvboxJsonResponseParser.ParseListResult

    suspend fun detail(source: MovieSource, vodId: String): VodInfo?

    suspend fun search(source: MovieSource, keyword: String, page: Int): List<VodInfo>

    suspend fun playUrl(
        source: MovieSource,
        flag: String,
        input: String,
        parseUrl: String? = null
    ): String
}
