package com.tvbox.core.source

import com.tvbox.core.model.MovieSource
import com.tvbox.utils.JsonUtils
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * [SourceManager] 的默认实现
 *
 * 使用内存中的 [MutableList] 维护影视源列表。
 * 兼容原版 TVBox 的 JSON 源格式：既支持纯数组 `[{...}]`，也支持 `{"sites": [...]}` 包裹结构。
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
        // 订阅导入依赖网络请求（异步），而接口约定为同步返回。
        // 完整实现需由调用方在协程中拉取远程内容后调用 importFromJson，
        // 此处返回空列表作为占位，待网络层接入后补全。
        return emptyList()
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
}
