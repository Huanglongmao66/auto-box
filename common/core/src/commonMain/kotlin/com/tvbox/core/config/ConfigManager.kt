package com.tvbox.core.config

import com.tvbox.core.model.AppConfig
import com.tvbox.core.model.ProxyConfig
import com.tvbox.deviceapi.storage.StorageManager
import com.tvbox.utils.JsonUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 应用配置管理服务
 *
 * 依赖 [StorageManager] 将 [AppConfig] 持久化为 JSON 文件，
 * 提供配置的读取、保存、重置以及播放器 / 代理配置的增量更新。
 *
 * @param storageManager 跨平台存储管理器
 */
class ConfigManager(private val storageManager: StorageManager) {

    /** 配置文件名 */
    private val configFileName = "app_config.json"

    /**
     * 读取应用配置
     *
     * 从用户数据目录的 JSON 文件加载配置；文件不存在或解析失败时返回默认配置。
     *
     * @return 应用配置
     */
    suspend fun getConfig(): AppConfig = withContext(Dispatchers.Default) {
        val path = configFilePath()
        if (!storageManager.exists(path)) {
            return@withContext AppConfig()
        }
        val json = storageManager.readFile(path)
        JsonUtils.fromJsonSafe(json) ?: AppConfig()
    }

    /**
     * 保存应用配置
     *
     * 将配置序列化为 JSON 写入用户数据目录。
     *
     * @param config 应用配置
     */
    suspend fun saveConfig(config: AppConfig) {
        withContext(Dispatchers.Default) {
            storageManager.writeFile(configFilePath(), JsonUtils.toJson(config))
        }
    }

    /**
     * 重置应用配置
     *
     * 删除已持久化的配置文件，后续 [getConfig] 将返回默认配置。
     */
    suspend fun resetConfig() {
        withContext(Dispatchers.Default) {
            val path = configFilePath()
            if (storageManager.exists(path)) {
                storageManager.delete(path)
            }
        }
    }

    /**
     * 更新播放器配置
     *
     * 读取当前配置，仅更新传入的非空字段，其余保持不变，然后持久化。
     *
     * @param playerEngine 播放器内核（null 表示不修改）
     * @param hardwareDecode 是否硬解码（null 表示不修改）
     * @param playSpeed 播放速度（null 表示不修改）
     * @param volume 音量（null 表示不修改）
     * @param autoPlayNext 是否自动播放下一集（null 表示不修改）
     * @return 更新后的应用配置
     */
    suspend fun updatePlayerConfig(
        playerEngine: String? = null,
        hardwareDecode: Boolean? = null,
        playSpeed: Float? = null,
        volume: Float? = null,
        autoPlayNext: Boolean? = null
    ): AppConfig {
        val config = getConfig()
        val updated = config.copy(
            playerEngine = playerEngine ?: config.playerEngine,
            hardwareDecode = hardwareDecode ?: config.hardwareDecode,
            playSpeed = playSpeed ?: config.playSpeed,
            volume = volume ?: config.volume,
            autoPlayNext = autoPlayNext ?: config.autoPlayNext
        )
        saveConfig(updated)
        return updated
    }

    /**
     * 更新代理配置
     *
     * 读取当前配置，替换代理设置后持久化。
     *
     * @param proxy 代理配置
     * @return 更新后的应用配置
     */
    suspend fun updateProxyConfig(proxy: ProxyConfig): AppConfig {
        val config = getConfig()
        val updated = config.copy(proxy = proxy)
        saveConfig(updated)
        return updated
    }

    /**
     * 获取配置文件的完整路径
     */
    private fun configFilePath(): String {
        val dir = storageManager.getDocumentDir().trimEnd('/')
        return "$dir/$configFileName"
    }
}
