package com.tvbox.core.migration

import com.tvbox.core.storage.Database
import com.tvbox.deviceapi.storage.StorageManager
import com.tvbox.utils.JsonUtils
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * 数据迁移管理器
 *
 * 统一管理旧版 TVBox 数据的读取、解析、迁移流程，
 * 支持从文件路径或 JSON 字符串导入。
 */
class MigrationManager(
    private val database: Database,
    private val storageManager: StorageManager
) {

    private val migrationTool = DataMigrationTool(database)

    /**
     * 从旧版 TVBox 配置文件迁移全部数据
     *
     * @param configFilePath 旧版 TVBox 配置文件路径
     * @return 迁移结果
     */
    suspend fun migrateFromFile(configFilePath: String): DataMigrationTool.MigrationResult {
        return withContext(Dispatchers.Default) {
            val configJson = storageManager.readFile(configFilePath)
            if (configJson.isBlank()) {
                return@withContext DataMigrationTool.MigrationResult(
                    errors = listOf("配置文件为空或不存在: $configFilePath")
                )
            }
            migrationTool.migrateFromLegacyJson(configJson)
        }
    }

    /**
     * 从 JSON 字符串迁移全部数据
     *
     * @param configJson 旧版 TVBox 配置 JSON 字符串
     * @return 迁移结果
     */
    suspend fun migrateFromJson(configJson: String): DataMigrationTool.MigrationResult {
        return migrationTool.migrateFromLegacyJson(configJson)
    }

    /**
     * 仅迁移影视源
     */
    suspend fun migrateSourcesFromJson(sourcesJson: String): Int {
        return migrationTool.migrateSources(sourcesJson)
    }

    /**
     * 仅迁移播放历史
     */
    suspend fun migrateHistoryFromJson(historyJson: String): Int {
        return migrationTool.migrateHistory(historyJson)
    }

    /**
     * 仅迁移收藏
     */
    suspend fun migrateFavoritesFromJson(favoritesJson: String): Int {
        return migrationTool.migrateFavorites(favoritesJson)
    }

    /**
     * 导出当前数据为旧版 TVBox 兼容 JSON 格式
     *
     * @return JSON 字符串
     */
    suspend fun exportToLegacyJson(): String {
        val sources = database.getSourceDao().getAll()
        val histories = database.getPlayHistoryDao().getAll()
        val favorites = database.getFavoriteDao().getAll()

        val sourceList = sources.map { source ->
            mapOf(
                "key" to source.key,
                "name" to source.name,
                "api" to source.api,
                "searchUrl" to source.searchUrl,
                "quickSearch" to source.quickSearch,
                "type" to source.type.toString(),
                "enabled" to source.enabled.toString(),
                "searchable" to source.searchable.toString(),
                "filterable" to source.filterable.toString(),
                "playerType" to source.playerType.toString(),
                "logo" to source.logo,
                "header" to source.header,
                "timeout" to source.timeout.toString()
            )
        }

        val historyList = histories.map { history ->
            mapOf(
                "vod_id" to history.vodId,
                "vod_name" to history.vodName,
                "vod_pic" to history.vodPic,
                "source_key" to history.sourceKey,
                "episode_name" to history.episodeName,
                "episode_index" to history.episodeIndex.toString(),
                "url" to history.url,
                "position" to history.position.toString(),
                "duration" to history.duration.toString(),
                "progress" to history.progress.toString(),
                "update_time" to history.updateTime.toString()
            )
        }

        val favoriteList = favorites.map { favorite ->
            mapOf(
                "vod_id" to favorite.vodId,
                "vod_name" to favorite.vodName,
                "vod_pic" to favorite.vodPic,
                "source_key" to favorite.sourceKey,
                "vod_class" to favorite.vodClass,
                "vod_content" to favorite.vodContent,
                "create_time" to favorite.createTime.toString()
            )
        }

        val exportData = mapOf(
            "sites" to sourceList,
            "history" to historyList,
            "favorite" to favoriteList
        )

        return JsonUtils.toJson(exportData)
    }

    /**
     * 导出数据到文件
     *
     * @param filePath 目标文件路径
     * @return 是否导出成功
     */
    suspend fun exportToFile(filePath: String): Boolean {
        return withContext(Dispatchers.Default) {
            try {
                val json = exportToLegacyJson()
                storageManager.writeFile(filePath, json)
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
