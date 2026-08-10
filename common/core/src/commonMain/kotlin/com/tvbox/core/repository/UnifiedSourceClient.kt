package com.tvbox.core.repository

import com.tvbox.core.model.MovieSource
import com.tvbox.core.model.SourceType
import com.tvbox.core.model.VodFilter
import com.tvbox.core.model.VodInfo
import com.tvbox.core.network.NetworkService
import com.tvbox.core.source.SourceManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 统一的源客户端分发中心
 *
 * 按 [MovieSource.type] 将调用路由到不同的 [SourceClient]：
 * - [SourceType.JSON]       →  JsonSourceClient (包装现有 TvboxSourceClient JSON 逻辑)
 * - [SourceType.XML]        →  XmlSourceClient
 * - [SourceType.XPATH]      →  XpathSourceClient
 * - [SourceType.SPIDER_JS]  →  SpiderJsSourceClient
 * - [SourceType.SPIDER_JAR] →  SpiderJarSourceClient
 * - [SourceType.MIX]        →  MixSourceClient（并发聚合内部站点）
 *
 * 线程/并发：Repository 层负责并发聚合，本类只负责串行单源分发。
 * 初始化按 source.key 懒缓存，避免重复加载 JS/JAR。
 */
internal class UnifiedSourceClient(
    private val networkService: NetworkService,
    private val sourceManager: SourceManager,
    /** 注入各类型 client，方便平台侧替换实际实现 */
    private val jsonClient: SourceClient,
    private val xmlClient: SourceClient,
    private val xpathClient: SourceClient,
    private val spiderJsClient: SourceClient,
    private val spiderJarClient: SourceClient,
    private val mixClientFactory: (UnifiedSourceClient) -> SourceClient
) : SourceClient {

    private var mixDelegate: SourceClient? = null
    private val initLock = Mutex()
    private val initedSources = HashSet<String>()

    private fun pick(source: MovieSource): SourceClient {
        return when (source.type) {
            SourceType.JSON -> jsonClient
            SourceType.XML -> xmlClient
            SourceType.XPATH -> xpathClient
            SourceType.SPIDER_JS -> spiderJsClient
            SourceType.SPIDER_JAR -> spiderJarClient
            SourceType.MIX -> {
                if (mixDelegate == null) mixDelegate = mixClientFactory(this)
                mixDelegate!!
            }
            else -> jsonClient // 未知类型走 JSON 尝试（通常会返回空，安全降级）
        }
    }

    private suspend fun ensureInit(source: MovieSource, delegate: SourceClient) {
        if (initedSources.contains(source.key)) return
        initLock.withLock {
            if (initedSources.contains(source.key)) return@withLock
            runCatching {
                // init 的具体实现在各 client 内部，失败不阻止业务请求
                delegate.init(source)
            }
            initedSources.add(source.key)
        }
    }

    override suspend fun home(source: MovieSource): TvboxJsonResponseParser.ParseListResult {
        val d = pick(source); ensureInit(source, d)
        return runCatching { d.home(source) }.getOrDefault(TvboxJsonResponseParser.ParseListResult.EMPTY)
    }

    override suspend fun category(
        source: MovieSource,
        tid: String,
        page: Int,
        filters: List<VodFilter>
    ): TvboxJsonResponseParser.ParseListResult {
        val d = pick(source); ensureInit(source, d)
        return runCatching { d.category(source, tid, page, filters) }
            .getOrDefault(TvboxJsonResponseParser.ParseListResult.EMPTY)
    }

    override suspend fun detail(source: MovieSource, vodId: String): VodInfo? {
        val d = pick(source); ensureInit(source, d)
        return runCatching { d.detail(source, vodId) }.getOrNull()
    }

    override suspend fun search(source: MovieSource, keyword: String, page: Int): List<VodInfo> {
        val d = pick(source); ensureInit(source, d)
        return runCatching { d.search(source, keyword, page) }.getOrDefault(emptyList())
    }

    override suspend fun playUrl(
        source: MovieSource,
        flag: String,
        input: String,
        parseUrl: String?
    ): String {
        val d = pick(source); ensureInit(source, d)
        return runCatching { d.playUrl(source, flag, input, parseUrl) }.getOrDefault(input)
    }

    companion object {
        /** 工厂方法：使用默认的 client 实现创建 [UnifiedSourceClient] */
        fun createDefault(
            networkService: NetworkService,
            sourceManager: SourceManager
        ): UnifiedSourceClient {
            val http = HttpFetcher(networkService)
            return UnifiedSourceClient(
                networkService = networkService,
                sourceManager = sourceManager,
                jsonClient = JsonSourceClient(http),
                xmlClient = XmlSourceClient(http),
                xpathClient = XpathSourceClient(http),
                spiderJsClient = SpiderJsSourceClient(http),
                spiderJarClient = SpiderJarSourceClient(http),
                mixClientFactory = { owner -> MixSourceClient(owner, sourceManager) }
            )
        }
    }
}
