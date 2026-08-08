package com.tvbox.core.di

import com.tvbox.core.config.ConfigManager
import com.tvbox.core.favorite.FavoriteManager
import com.tvbox.core.history.HistoryManager
import com.tvbox.core.network.NetworkService
import com.tvbox.core.source.DefaultSourceManager
import com.tvbox.core.source.SourceManager
import com.tvbox.core.storage.Database
import com.tvbox.core.storage.InMemoryDatabase
import com.tvbox.deviceapi.DeviceApi
import com.tvbox.utils.JsonUtils
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

/**
 * 全局依赖注入容器
 *
 * 以单例对象形式统一管理跨平台服务的创建与获取，业务层通过对应的 getXxx() 方法
 * 取得服务实例，避免在各处重复构造，也屏蔽底层平台差异。
 *
 * 调用方需在应用启动时先调用 [initialize]，传入平台对应的 [DeviceApi]，
 * 随后即可通过各 getter 获取数据库、历史、收藏、配置、网络、数据源等服务。
 */
object ServiceLocator {

    /** 默认网络超时时间（毫秒） */
    private const val DEFAULT_TIMEOUT_MS = 15_000L

    private var deviceApi: DeviceApi? = null

    private var database: Database? = null

    private var historyManager: HistoryManager? = null

    private var favoriteManager: FavoriteManager? = null

    private var configManager: ConfigManager? = null

    private var sourceManager: SourceManager? = null

    /**
     * 跨平台 HTTP 客户端，懒加载以延迟引擎查找。
     *
     * 注：commonMain 中无法直接引用平台专属 Ktor 引擎（OkHttp / Js），
     * 此处使用默认引擎重载；各平台运行时会通过 SPI 自动选择对应引擎。
     * 若平台未注册默认引擎（如纯 JS），需后续以 expect/actual 注入引擎工厂。
     */
    private val httpClient: HttpClient by lazy { buildHttpClient() }

    /**
     * 网络服务，懒加载以避免在应用启动期触发引擎初始化。
     */
    private val _networkService: NetworkService by lazy { NetworkService(httpClient) }

    /**
     * 初始化全部全局服务
     *
     * 基于 [DeviceApi] 创建数据库、历史、收藏、配置、数据源等服务实例。
     * 网络服务为懒加载，首次访问 [getNetworkService] 时构建。
     *
     * @param deviceApi 平台对应的设备能力入口
     */
    fun initialize(deviceApi: DeviceApi) {
        this.deviceApi = deviceApi
        val db = InMemoryDatabase()
        database = db
        historyManager = HistoryManager(db.getPlayHistoryDao())
        favoriteManager = FavoriteManager(db.getFavoriteDao())
        configManager = ConfigManager(deviceApi.getStorageManager())
        sourceManager = DefaultSourceManager()
    }

    fun getDeviceApi(): DeviceApi = deviceApi ?: notInitialized()

    fun getDatabase(): Database = database ?: notInitialized()

    fun getHistoryManager(): HistoryManager = historyManager ?: notInitialized()

    fun getFavoriteManager(): FavoriteManager = favoriteManager ?: notInitialized()

    fun getConfigManager(): ConfigManager = configManager ?: notInitialized()

    fun getNetworkService(): NetworkService = _networkService

    fun getSourceManager(): SourceManager = sourceManager ?: notInitialized()

    /**
     * 构建配置好的 Ktor HTTP 客户端
     */
    private fun buildHttpClient(): HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(JsonUtils.json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = DEFAULT_TIMEOUT_MS
            connectTimeoutMillis = DEFAULT_TIMEOUT_MS
            socketTimeoutMillis = DEFAULT_TIMEOUT_MS
        }
    }

    private fun notInitialized(): Nothing =
        error("ServiceLocator 尚未初始化，请先调用 initialize(DeviceApi)")
}
