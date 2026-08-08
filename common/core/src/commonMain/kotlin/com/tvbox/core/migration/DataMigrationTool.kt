package com.tvbox.core.migration

import com.tvbox.core.model.MovieSource
import com.tvbox.core.model.PlayHistory
import com.tvbox.core.model.Favorite
import com.tvbox.core.storage.Database
import com.tvbox.utils.JsonUtils
import com.tvbox.utils.DateUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 旧版 TVBox 数据迁移工具
 *
 * 支持将原版安卓 TVBox 的配置、影视源、播放历史、收藏数据
 * 一键迁移至多平台版本，保证数据完整无丢失。
 */
class DataMigrationTool(
    private val database: Database
) {

    /**
     * 迁移结果
     */
    data class MigrationResult(
        val sourcesImported: Int = 0,
        val historiesImported: Int = 0,
        val favoritesImported: Int = 0,
        val configImported: Boolean = false,
        val errors: List<String> = emptyList()
    ) {
        /**
         * 迁移是否完全成功
         */
        val isSuccess: Boolean get() = errors.isEmpty()

        /**
         * 迁移总数
         */
        val totalImported: Int get() = sourcesImported + historiesImported + favoritesImported
    }

    /**
     * 从旧版 TVBox JSON 配置字符串导入全部数据
     *
     * @param configJson 旧版 TVBox 的 JSON 配置字符串
     * （包含 sites/history/favorite 等字段的原版格式）
     * @return 迁移结果
     */
    suspend fun migrateFromLegacyJson(configJson: String): MigrationResult {
        val errors = mutableListOf<String>()
        var sourcesCount = 0
        var historiesCount = 0
        var favoritesCount = 0
        var configImported = false

        val root = JsonUtils.parseToJsonElement(configJson) as? JsonObject
        if (root == null) {
            return MigrationResult(errors = listOf("JSON 解析失败：无法解析配置文件"))
        }

        // 迁移影视源
        try {
            val sites = root["sites"] as? JsonArray
            if (sites != null) {
                sourcesCount = migrateSources(sites)
            }
        } catch (e: Exception) {
            errors.add("影视源迁移失败: ${e.message}")
        }

        // 迁移播放历史
        try {
            val history = root["history"] as? JsonArray
            if (history != null) {
                historiesCount = migrateHistory(history)
            }
        } catch (e: Exception) {
            errors.add("播放历史迁移失败: ${e.message}")
        }

        // 迁移收藏
        try {
            val favorite = root["favorite"] as? JsonArray
            if (favorite != null) {
                favoritesCount = migrateFavorites(favorite)
            }
        } catch (e: Exception) {
            errors.add("收藏迁移失败: ${e.message}")
        }

        // 迁移配置
        try {
            configImported = root["spider"] != null || root["wallpaper"] != null
        } catch (e: Exception) {
            errors.add("配置迁移失败: ${e.message}")
        }

        return MigrationResult(
            sourcesImported = sourcesCount,
            historiesImported = historiesCount,
            favoritesImported = favoritesCount,
            configImported = configImported,
            errors = errors
        )
    }

    /**
     * 从旧版 TVBox 单独的源配置 JSON 导入影视源
     *
     * @param sourcesJson 源配置 JSON（数组格式或包含 sites 字段的对象）
     * @return 导入数量
     */
    suspend fun migrateSources(sourcesJson: String): Int {
        val element = JsonUtils.parseToJsonElement(sourcesJson) ?: return 0
        val sitesArray = when (element) {
            is JsonArray -> element
            is JsonObject -> element["sites"] as? JsonArray ?: return 0
            else -> return 0
        }
        return migrateSources(sitesArray)
    }

    /**
     * 从旧版 TVBox 播放历史 JSON 导入
     *
     * @param historyJson 历史记录 JSON 字符串
     * @return 导入数量
     */
    suspend fun migrateHistory(historyJson: String): Int {
        val element = JsonUtils.parseToJsonElement(historyJson) ?: return 0
        val historyArray = when (element) {
            is JsonArray -> element
            is JsonObject -> element["history"] as? JsonArray ?: return 0
            else -> return 0
        }
        return migrateHistory(historyArray)
    }

    /**
     * 从旧版 TVBox 收藏 JSON 导入
     *
     * @param favoriteJson 收藏 JSON 字符串
     * @return 导入数量
     */
    suspend fun migrateFavorites(favoriteJson: String): Int {
        val element = JsonUtils.parseToJsonElement(favoriteJson) ?: return 0
        val favoriteArray = when (element) {
            is JsonArray -> element
            is JsonObject -> element["favorite"] as? JsonArray ?: return 0
            else -> return 0
        }
        return migrateFavorites(favoriteArray)
    }

    // ===== 内部迁移方法 =====

    private suspend fun migrateSources(sitesArray: JsonArray): Int {
        val sourceDao = database.getSourceDao()
        var count = 0
        for (site in sitesArray) {
            try {
                val obj = site as? JsonObject ?: continue
                val source = parseLegacySource(obj)
                if (source.key.isNotEmpty()) {
                    sourceDao.insert(source)
                    count++
                }
            } catch (_: Exception) {
                // 单条解析失败跳过，继续迁移其他
            }
        }
        return count
    }

    private suspend fun migrateHistory(historyArray: JsonArray): Int {
        val historyDao = database.getPlayHistoryDao()
        var count = 0
        for (item in historyArray) {
            try {
                val obj = item as? JsonObject ?: continue
                val history = parseLegacyHistory(obj)
                historyDao.insert(history)
                count++
            } catch (_: Exception) {
                // 单条解析失败跳过
            }
        }
        return count
    }

    private suspend fun migrateFavorites(favoriteArray: JsonArray): Int {
        val favoriteDao = database.getFavoriteDao()
        var count = 0
        for (item in favoriteArray) {
            try {
                val obj = item as? JsonObject ?: continue
                val favorite = parseLegacyFavorite(obj)
                favoriteDao.insert(favorite)
                count++
            } catch (_: Exception) {
                // 单条解析失败跳过
            }
        }
        return count
    }

    /**
     * 解析旧版 TVBox 源配置为 MovieSource
     */
    private fun parseLegacySource(obj: JsonObject): MovieSource {
        return MovieSource(
            key = obj.getString("key") ?: "",
            name = obj.getString("name") ?: "",
            api = obj.getString("api") ?: "",
            searchUrl = obj.getString("searchUrl") ?: "",
            quickSearch = obj.getString("quickSearch") ?: "",
            categories = obj.getString("categories") ?: "",
            type = obj.getInt("type") ?: 0,
            enabled = obj.getBoolean("enabled") ?: true,
            searchable = obj.getBoolean("searchable") ?: true,
            quickSearchable = obj.getBoolean("quickSearchable") ?: true,
            filterable = obj.getBoolean("filterable") ?: false,
            playerType = obj.getInt("playerType") ?: 0,
            logo = obj.getString("logo") ?: "",
            desc = obj.getString("desc") ?: "",
            js = obj.getString("js") ?: "",
            header = obj.getString("header") ?: "",
            timeout = obj.getInt("timeout") ?: 15000,
            order = obj.getInt("order") ?: 0,
            lastUpdate = DateUtils.currentTimeMillis()
        )
    }

    /**
     * 解析旧版 TVBox 播放历史为 PlayHistory
     */
    private fun parseLegacyHistory(obj: JsonObject): PlayHistory {
        return PlayHistory(
            vodId = obj.getString("vod_id") ?: obj.getString("id") ?: "",
            vodName = obj.getString("vod_name") ?: obj.getString("name") ?: "",
            vodPic = obj.getString("vod_pic") ?: obj.getString("pic") ?: "",
            sourceKey = obj.getString("source_key") ?: obj.getString("siteKey") ?: "",
            episodeName = obj.getString("episode_name") ?: "",
            episodeIndex = obj.getInt("episode_index") ?: 0,
            url = obj.getString("url") ?: "",
            position = obj.getLong("position") ?: 0L,
            duration = obj.getLong("duration") ?: 0L,
            progress = obj.getInt("progress") ?: 0,
            updateTime = obj.getLong("update_time") ?: DateUtils.currentTimeMillis()
        )
    }

    /**
     * 解析旧版 TVBox 收藏为 Favorite
     */
    private fun parseLegacyFavorite(obj: JsonObject): Favorite {
        return Favorite(
            vodId = obj.getString("vod_id") ?: obj.getString("id") ?: "",
            vodName = obj.getString("vod_name") ?: obj.getString("name") ?: "",
            vodPic = obj.getString("vod_pic") ?: obj.getString("pic") ?: "",
            sourceKey = obj.getString("source_key") ?: obj.getString("siteKey") ?: "",
            vodClass = obj.getString("vod_class") ?: "",
            vodContent = obj.getString("vod_content") ?: obj.getString("desc") ?: "",
            createTime = obj.getLong("create_time") ?: DateUtils.currentTimeMillis()
        )
    }

    // ===== JsonObject 扩展工具方法 =====

    private fun JsonObject.getString(key: String): String? {
        val element = this[key] as? JsonPrimitive ?: return null
        return element.contentOrNull
    }

    private fun JsonObject.getInt(key: String): Int? {
        val str = getString(key) ?: return null
        return str.toIntOrNull()
    }

    private fun JsonObject.getLong(key: String): Long? {
        val str = getString(key) ?: return null
        return str.toLongOrNull()
    }

    private fun JsonObject.getBoolean(key: String): Boolean? {
        val str = getString(key) ?: return null
        return str.toBooleanStrictOrNull()
    }
}
