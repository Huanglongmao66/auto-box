package com.tvbox.core.source

import com.tvbox.core.model.MovieSource
import com.tvbox.core.model.UserVodSource

/**
 * 数据源管理器接口
 *
 * 负责影视源的增删改查、启用 / 禁用、JSON 导入导出与订阅更新，
 * 屏蔽底层存储实现差异（各平台可基于 SharedPreferences / Preferences / IndexedDB 等实现）。
 */
interface SourceManager {

    /**
     * 获取全部影视源
     * @return 影视源列表
     */
    fun getSources(): List<MovieSource>

    /**
     * 根据 key 获取影视源
     * @param key 站点 key
     * @return 影视源，不存在返回 null
     */
    fun getSource(key: String): MovieSource?

    /**
     * 添加影视源
     * @param source 影视源
     */
    fun addSource(source: MovieSource)

    /**
     * 根据 key 移除影视源
     * @param key 站点 key
     */
    fun removeSource(key: String)

    /**
     * 启用 / 禁用影视源
     * @param key 站点 key
     * @param enabled 是否启用
     */
    fun enableSource(key: String, enabled: Boolean)

    /**
     * 从 JSON 字符串导入影视源
     * @param json 原版 TVBox 格式的 JSON 字符串
     * @return 导入成功的影视源列表
     */
    fun importFromJson(json: String): List<MovieSource>

    /**
     * 导出全部影视源为 JSON 字符串
     * @return JSON 字符串
     */
    fun exportToJson(): String

    /**
     * 从订阅地址导入影视源（同步占位，保留兼容）
     * @param url 订阅地址
     * @return 导入成功的影视源列表
     */
    fun importSubscription(url: String): List<MovieSource>

    /**
     * 从订阅地址异步导入影视源
     * 拉取远程内容后解析为 MovieSource 列表并导入
     * @param url 订阅地址
     * @param fetcher 网络拉取函数（由调用方注入，避免 commonMain 直接依赖 Ktor 引擎）
     * @return 导入成功的影视源列表
     */
    suspend fun importSubscriptionAsync(url: String, fetcher: suspend (String) -> String): List<MovieSource>

    /**
     * 从用户自定义影视源列表同步
     * 清空现有源后将 UserVodSource 转换为 MovieSource 并全部插入
     * @param userVodSources 用户自定义影视源列表
     */
    fun syncFromUserSources(userVodSources: List<UserVodSource>)
}
