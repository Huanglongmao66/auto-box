package com.tvbox.platform.web

import com.tvbox.deviceapi.storage.StorageManager
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.browser.window
import org.w3c.dom.Storage

/**
 * Web 平台存储管理器实现
 *
 * 基于 localStorage 实现配置与文件存取能力：
 * - 配置以键值对形式存储在 localStorage；
 * - 文本 / 二进制文件以虚拟路径映射到 localStorage 键（二进制经 Base64 编码）。
 *
 * 目录以虚拟 URI（localStorage://<dir>）形式对外暴露，
 * 业务层无需感知底层为浏览器本地存储。
 */
@OptIn(ExperimentalEncodingApi::class)
class WebStorageManager : StorageManager {

    // ===== 目录访问 =====

    override fun getCacheDir(): String = CACHE_DIR

    override fun getDocumentDir(): String = DATA_DIR

    override fun getDownloadDir(): String = DOWNLOAD_DIR

    override fun getSourceDir(): String = SOURCE_DIR

    // ===== 文件操作 =====

    override fun readFile(path: String): String {
        return storage.getItem(fileKey(path)) ?: ""
    }

    override fun writeFile(path: String, content: String, append: Boolean) {
        val key = fileKey(path)
        val existing = if (append) storage.getItem(key).orEmpty() else ""
        storage.setItem(key, existing + content)
    }

    override fun readBytes(path: String): ByteArray {
        val base64 = storage.getItem(bytesKey(path)) ?: return ByteArray(0)
        return runCatching { Base64.decode(base64) }.getOrDefault(ByteArray(0))
    }

    override fun writeBytes(path: String, bytes: ByteArray, append: Boolean) {
        val key = bytesKey(path)
        val merged = if (append) readBytes(path) + bytes else bytes
        storage.setItem(key, Base64.encode(merged))
    }

    // ===== 文件管理 =====

    override fun exists(path: String): Boolean {
        if (isKnownDir(path)) return true
        if (storage.getItem(fileKey(path)) != null) return true
        if (storage.getItem(bytesKey(path)) != null) return true
        return listFiles(path).isNotEmpty()
    }

    override fun mkdirs(path: String): Boolean {
        // 浏览器本地存储无真实目录概念，目录为虚拟路径，始终视为创建成功
        return true
    }

    override fun delete(path: String): Boolean {
        var removed = false
        val fKey = fileKey(path)
        val bKey = bytesKey(path)
        if (storage.getItem(fKey) != null) {
            storage.removeItem(fKey)
            removed = true
        }
        if (storage.getItem(bKey) != null) {
            storage.removeItem(bKey)
            removed = true
        }
        // 递归删除该目录下所有子项
        val childPrefix = pathWithSeparator(path)
        val len = storage.length
        for (i in 0 until len) {
            val key = storage.key(i) ?: continue
            val p = storagePathOf(key) ?: continue
            if (p.startsWith(childPrefix)) {
                storage.removeItem(key)
                removed = true
            }
        }
        return removed
    }

    override fun listFiles(path: String): List<String> {
        val prefix = pathWithSeparator(path)
        val names = mutableSetOf<String>()
        val len = storage.length
        for (i in 0 until len) {
            val key = storage.key(i) ?: continue
            val p = storagePathOf(key) ?: continue
            if (!p.startsWith(prefix)) continue
            val relative = p.removePrefix(prefix)
            val name = relative.substringBefore('/')
            if (name.isNotEmpty()) names.add(name)
        }
        return names.toList()
    }

    override fun getFileSize(path: String): Long {
        val text = storage.getItem(fileKey(path))
        if (text != null) return text.length.toLong()
        val base64 = storage.getItem(bytesKey(path))
        if (base64 != null) return (base64.length * 3 / 4).toLong()
        return 0L
    }

    override fun move(from: String, to: String): Boolean {
        if (!copy(from, to)) return false
        delete(from)
        return true
    }

    override fun copy(from: String, to: String): Boolean {
        val fromText = storage.getItem(fileKey(from))
        if (fromText != null) {
            storage.setItem(fileKey(to), fromText)
            return true
        }
        val fromBytes = storage.getItem(bytesKey(from))
        if (fromBytes != null) {
            storage.setItem(bytesKey(to), fromBytes)
            return true
        }
        return false
    }

    // ===== 配置存储 =====

    override fun getConfig(key: String, default: String): String {
        return storage.getItem(configKey(key)) ?: default
    }

    override fun setConfig(key: String, value: String) {
        storage.setItem(configKey(key), value)
    }

    override fun removeConfig(key: String) {
        storage.removeItem(configKey(key))
    }

    override fun clearCache() {
        val prefix = pathWithSeparator(CACHE_DIR)
        val len = storage.length
        for (i in 0 until len) {
            val key = storage.key(i) ?: continue
            val p = storagePathOf(key) ?: continue
            if (p.startsWith(prefix)) storage.removeItem(key)
        }
    }

    override fun getAvailableSpace(): Long {
        val used = estimateUsedSpace()
        return (LOCAL_STORAGE_QUOTA - used).coerceAtLeast(0L)
    }

    // ===== 内部辅助 =====

    /** 浏览器本地存储访问入口 */
    private val storage: Storage
        get() = window.localStorage

    /** 文本文件 localStorage 键 */
    private fun fileKey(path: String): String = FILE_PREFIX + path

    /** 二进制文件 localStorage 键 */
    private fun bytesKey(path: String): String = BYTES_PREFIX + path

    /** 配置项 localStorage 键 */
    private fun configKey(key: String): String = CONFIG_PREFIX + key

    /** 是否为已知的虚拟目录 */
    private fun isKnownDir(path: String): Boolean = path in KNOWN_DIRS

    /** 规范化路径并以分隔符结尾，用于前缀匹配 */
    private fun pathWithSeparator(path: String): String =
        if (path.endsWith("/")) path else "$path/"

    /** 从 localStorage 键还原虚拟路径，非文件键返回 null */
    private fun storagePathOf(key: String): String? {
        if (key.startsWith(FILE_PREFIX)) return key.removePrefix(FILE_PREFIX)
        if (key.startsWith(BYTES_PREFIX)) return key.removePrefix(BYTES_PREFIX)
        return null
    }

    /** 估算 localStorage 已用空间（字节，UTF-16 每字符约 2 字节） */
    private fun estimateUsedSpace(): Long {
        var total = 0L
        val len = storage.length
        for (i in 0 until len) {
            val key = storage.key(i) ?: continue
            val value = storage.getItem(key).orEmpty()
            total += key.length + value.length
        }
        return total * 2L
    }

    companion object {
        private const val FILE_PREFIX = "tvbox:file:"
        private const val BYTES_PREFIX = "tvbox:bytes:"
        private const val CONFIG_PREFIX = "tvbox:config:"
        private const val CACHE_DIR = "localStorage://cache"
        private const val DATA_DIR = "localStorage://data"
        private const val DOWNLOAD_DIR = "localStorage://download"
        private const val SOURCE_DIR = "localStorage://source"
        private const val LOCAL_STORAGE_QUOTA = 5_000_000L
        private val KNOWN_DIRS = setOf(CACHE_DIR, DATA_DIR, DOWNLOAD_DIR, SOURCE_DIR)
    }
}
