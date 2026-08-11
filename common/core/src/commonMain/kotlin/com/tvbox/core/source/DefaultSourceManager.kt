package com.tvbox.core.source

import com.tvbox.core.model.MovieSource
import com.tvbox.core.model.UserVodSource
import com.tvbox.utils.JsonUtils
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * [SourceManager] 的默认实现
 *
 * 使用内存中的 [MutableList] 维护影视源列表。
 * 兼容原版 TVBox 的 JSON 源格式：既支持纯数组 `[{...}]`，也支持 `{"sites": [...]}` 包裹结构。
 * 支持多仓订阅格式：`{"urls": [{"url": "...", "name": "..."}]}`。
 */
class DefaultSourceManager : SourceManager {

    private val sources = mutableListOf<MovieSource>()

    override fun getSources(): List<MovieSource> = sources.toList()

    override fun getSource(key: String): MovieSource? = sources.firstOrNull { it.key == key }

    override fun addSource(source: MovieSource) {
        upsert(source)
    }

    override fun removeSource(key: String) {
        sources.removeAll { it.key == key }
    }

    override fun enableSource(key: String, enabled: Boolean) {
        val index = sources.indexOfFirst { it.key == key }
        if (index >= 0) {
            sources[index] = sources[index].copy(enabled = enabled)
        }
    }

    override fun importFromJson(json: String): List<MovieSource> {
        val imported = parseSources(json)
        if (imported.isEmpty()) return emptyList()
        imported.forEach { upsert(it) }
        return imported
    }

    override fun exportToJson(): String = JsonUtils.toJson(sources.toList())

    override fun importSubscription(url: String): List<MovieSource> {
        return emptyList()
    }

    override suspend fun importSubscriptionAsync(
        url: String,
        fetcher: suspend (String) -> String
    ): List<MovieSource> {
        val raw = try {
            fetcher(url)
        } catch (e: Exception) {
            return emptyList()
        }
        val rawTrimmed = raw.trim()

        // 尝试直接解析为影视源列表
        val direct = parseSources(rawTrimmed)
        if (direct.isNotEmpty()) {
            direct.forEach { upsert(it) }
            return direct
        }

        // 尝试解析为多仓格式 {"urls": [{"url": "...", "name": "..."}]}
        val multiStoreSources = parseMultiStore(rawTrimmed, fetcher)
        if (multiStoreSources.isNotEmpty()) {
            multiStoreSources.forEach { upsert(it) }
            return multiStoreSources
        }

        return emptyList()
    }

    override fun syncFromUserSources(userVodSources: List<UserVodSource>) {
        sources.clear()
        userVodSources.forEach { userSource ->
            // 保留所有源（含 CSP/JS/Spider），完整映射 TVBoxOS 字段
            // - type=0: XML 源
            // - type=1: JSON 源（MacCMS 标准接口）
            // - type=3: Spider/CSP 源（Jar/JS/Py 爬虫）
            // - type=4: 新协议（带 ext 过滤参数的 JSON 源）
            val movieSource = MovieSource(
                key = userSource.key,
                name = userSource.name,
                api = userSource.api,
                enabled = userSource.enabled,
                type = userSource.type,
                searchable = userSource.searchable != 0,
                quickSearchable = userSource.quickSearch != 0,
                filterable = userSource.filterable != 0,
                playerUrl = userSource.playerUrl,
                playerType = userSource.playerType,
                timeout = if (userSource.timeout > 0) userSource.timeout * 1000 else 15000,
                ext = userSource.ext,
                jar = userSource.jar,
                clickSelector = userSource.clickSelector,
                style = userSource.style,
                categoryList = userSource.categories
            )
            upsert(movieSource)
        }
    }

    /**
     * 按 key 新增或覆盖影视源
     */
    private fun upsert(source: MovieSource) {
        val index = sources.indexOfFirst { it.key == source.key }
        if (index >= 0) sources[index] = source else sources.add(source)
    }

    /**
     * 解析原版 TVBox 格式的 JSON 影视源列表
     *
     * 兼容两种结构：
     * - 纯数组：`[{ "key": ..., "name": ... }]`
     * - 对象包裹：`{ "sites": [ ... ] }`
     *
     * 单条解析失败的源会被跳过，不影响其余源导入。
     */
    private fun parseSources(json: String): List<MovieSource> {
        val element = JsonUtils.parseToJsonElement(json) ?: return emptyList()
        val array = when (element) {
            is JsonArray -> element
            is JsonObject -> element["sites"] as? JsonArray ?: return emptyList()
            else -> return emptyList()
        }
        return array.mapNotNull { decodeSource(it) }
    }

    /**
     * 将单个 JSON 元素安全反序列化为 [MovieSource]，失败返回 null
     */
    private fun decodeSource(element: JsonElement): MovieSource? {
        return try {
            JsonUtils.json.decodeFromJsonElement(MovieSource.serializer(), element)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 解析多仓订阅格式
     *
     * 多仓格式示例：
     * ```json
     * {"urls": [{"url": "https://...", "name": "仓库名"}]}
     * ```
     *
     * 对每个子仓库 URL 发起请求，递归解析为影视源列表。
     */
    private suspend fun parseMultiStore(
        json: String,
        fetcher: suspend (String) -> String
    ): List<MovieSource> {
        val element = JsonUtils.parseToJsonElement(json) as? JsonObject ?: return emptyList()
        val urlsArray = element["urls"] as? JsonArray ?: return emptyList()

        val results = mutableListOf<MovieSource>()
        for (item in urlsArray) {
            val obj = item as? JsonObject ?: continue
            val subUrl = (obj["url"] as? JsonPrimitive)?.contentOrNull ?: continue
            val subName = (obj["name"] as? JsonPrimitive)?.contentOrNull ?: subUrl

            try {
                val subRaw = fetcher(subUrl).trim()
                val subSources = parseSources(subRaw)
                if (subSources.isNotEmpty()) {
                    results.addAll(subSources)
                } else {
                    // 子仓库也可能是多仓格式，递归尝试
                    val nested = parseMultiStoreRecursive(subRaw, fetcher, depth = 0)
                    results.addAll(nested)
                }
            } catch (_: Exception) {
                // 单个仓库拉取失败跳过
            }
        }
        return results
    }

    /**
     * 递归解析多仓（限制深度防止无限循环）
     */
    private suspend fun parseMultiStoreRecursive(
        json: String,
        fetcher: suspend (String) -> String,
        depth: Int
    ): List<MovieSource> {
        if (depth >= 2) return emptyList()

        val direct = parseSources(json)
        if (direct.isNotEmpty()) return direct

        return parseMultiStore(json, fetcher)
    }
}
