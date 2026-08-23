package com.tvbox.platform.android

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.os.StatFs
import com.tvbox.deviceapi.storage.StorageManager
import java.io.File

/**
 * Android 平台存储管理器实现
 *
 * 基于应用沙箱目录（filesDir / cacheDir）与 SharedPreferences 实现，
 * 兼容原版 TVBox 的本地配置、缓存、源文件存取能力。
 *
 * @param context Android 上下文
 */
class AndroidStorageManager(
    private val context: Context
) : StorageManager {

    /** 键值对配置存储 */
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ===== 目录访问 =====

    override fun getCacheDir(): String = context.cacheDir.absolutePath

    override fun getDocumentDir(): String = context.filesDir.absolutePath

    override fun getDownloadDir(): String {
        // 优先使用应用专属外部下载目录，无外部存储时回退到内部目录
        val external = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        return external?.absolutePath ?: File(context.filesDir, DIR_DOWNLOAD).absolutePath
    }

    override fun getSourceDir(): String =
        File(context.filesDir, DIR_SOURCE).apply { mkdirs() }.absolutePath

    // ===== 文件操作 =====

    override fun readFile(path: String): String {
        val file = File(path)
        if (!file.exists() || !file.isFile) return ""
        return runCatching { file.readText() }.getOrDefault("")
    }

    override fun writeFile(path: String, content: String, append: Boolean) {
        val file = File(path)
        file.parentFile?.mkdirs()
        runCatching {
            if (append) file.appendText(content) else file.writeText(content)
        }
    }

    override fun readBytes(path: String): ByteArray {
        val file = File(path)
        if (!file.exists() || !file.isFile) return ByteArray(0)
        return runCatching { file.readBytes() }.getOrDefault(ByteArray(0))
    }

    override fun writeBytes(path: String, bytes: ByteArray, append: Boolean) {
        val file = File(path)
        file.parentFile?.mkdirs()
        runCatching {
            if (append) {
                file.outputStream().use { it.write(bytes) }
            } else {
                file.writeBytes(bytes)
            }
        }
    }

    // ===== 文件管理 =====

    override fun exists(path: String): Boolean = File(path).exists()

    override fun mkdirs(path: String): Boolean {
        val file = File(path)
        if (file.exists()) return file.isDirectory
        return file.mkdirs()
    }

    override fun delete(path: String): Boolean {
        val file = File(path)
        if (!file.exists()) return false
        return deleteRecursively(file)
    }

    /**
     * 递归删除文件或目录
     */
    private fun deleteRecursively(file: File): Boolean {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child -> deleteRecursively(child) }
        }
        return file.delete()
    }

    override fun listFiles(path: String): List<String> {
        val file = File(path)
        if (!file.exists() || !file.isDirectory) return emptyList()
        return file.listFiles()?.map { it.name } ?: emptyList()
    }

    override fun getFileSize(path: String): Long {
        val file = File(path)
        return if (file.exists()) file.length() else 0L
    }

    override fun move(from: String, to: String): Boolean {
        val src = File(from)
        val dst = File(to)
        if (!src.exists()) return false
        dst.parentFile?.mkdirs()
        return runCatching { src.renameTo(dst) }.getOrDefault(false)
    }

    override fun copy(from: String, to: String): Boolean {
        val src = File(from)
        val dst = File(to)
        if (!src.exists()) return false
        dst.parentFile?.mkdirs()
        return runCatching {
            src.inputStream().use { input ->
                dst.outputStream().use { output -> input.copyTo(output) }
            }
            true
        }.getOrDefault(false)
    }

    // ===== 配置存储 =====

    override fun getConfig(key: String, default: String): String =
        prefs.getString(key, default) ?: default

    override fun setConfig(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun removeConfig(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun clearCache() {
        deleteRecursively(context.cacheDir)
        context.cacheDir.mkdirs()
    }

    override fun getAvailableSpace(): Long {
        return runCatching {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong
        }.getOrDefault(0L)
    }

    companion object {
        private const val PREFS_NAME = "tvbox_config"
        private const val DIR_DOWNLOAD = "download"
        private const val DIR_SOURCE = "source"
    }
}
