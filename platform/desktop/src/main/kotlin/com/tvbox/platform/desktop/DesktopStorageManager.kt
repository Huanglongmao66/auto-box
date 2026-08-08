package com.tvbox.platform.desktop

import com.tvbox.deviceapi.storage.StorageManager
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.prefs.Preferences

/**
 * Desktop（Windows / macOS / Linux）平台存储管理器实现
 *
 * 基于系统临时目录、用户数据目录与 [Preferences] 实现，
 * 兼容原版 TVBox 的本地配置、缓存、源文件存取能力。
 */
class DesktopStorageManager : StorageManager {

    /** 键值对配置存储，基于 JVM Preferences 实现 */
    private val prefs: Preferences =
        Preferences.userNodeForPackage(DesktopStorageManager::class.java)

    /** 用户主目录 */
    private val userHome: String = System.getProperty(
        "user.home", System.getProperty("java.io.tmpdir")
    )

    /** 操作系统名称（小写） */
    private val osName: String = System.getProperty(OS_NAME_PROPERTY, "").lowercase()

    /** 是否为 Windows */
    private val isWindows: Boolean = osName.contains(OS_WINDOWS)

    /** 是否为 macOS */
    private val isMacos: Boolean = osName.contains(OS_MACOS)

    // ===== 目录访问 =====

    override fun getCacheDir(): String {
        val dir = File(System.getProperty("java.io.tmpdir"), DIR_CACHE)
        dir.mkdirs()
        return dir.absolutePath
    }

    override fun getDocumentDir(): String {
        val dir = File(userDataDir())
        dir.mkdirs()
        return dir.absolutePath
    }

    override fun getDownloadDir(): String {
        val dir = File(getDocumentDir(), DIR_DOWNLOAD)
        dir.mkdirs()
        return dir.absolutePath
    }

    override fun getSourceDir(): String {
        val dir = File(getDocumentDir(), DIR_SOURCE)
        dir.mkdirs()
        return dir.absolutePath
    }

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
        return runCatching {
            Files.move(
                src.toPath(),
                dst.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
            true
        }.getOrDefault(false)
    }

    override fun copy(from: String, to: String): Boolean {
        val src = File(from)
        val dst = File(to)
        if (!src.exists()) return false
        dst.parentFile?.mkdirs()
        return runCatching {
            Files.copy(
                src.toPath(),
                dst.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
            true
        }.getOrDefault(false)
    }

    // ===== 配置存储 =====

    override fun getConfig(key: String, default: String): String =
        prefs.get(key, default)

    override fun setConfig(key: String, value: String) {
        prefs.put(key, value)
    }

    override fun removeConfig(key: String) {
        prefs.remove(key)
    }

    override fun clearCache() {
        val cacheDir = File(getCacheDir())
        deleteRecursively(cacheDir)
        cacheDir.mkdirs()
    }

    override fun getAvailableSpace(): Long {
        return runCatching {
            val fileStore = Files.getFileStore(File(getDocumentDir()).toPath())
            fileStore.usableSpace
        }.getOrDefault(0L)
    }

    // ===== 内部辅助 =====

    /**
     * 根据操作系统映射用户数据目录：
     * - Windows: %APPDATA%/TVBox
     * - macOS:   ~/Library/Application Support/TVBox
     * - Linux:   ~/.local/share/tvbox
     */
    private fun userDataDir(): String {
        return when {
            isWindows -> {
                val appData = System.getenv("APPDATA")
                    ?: File(userHome, "AppData/Roaming").absolutePath
                File(appData, APP_DIR_WINDOWS).absolutePath
            }
            isMacos -> File(userHome, "Library/Application Support/$APP_DIR_MACOS").absolutePath
            else -> File(userHome, ".local/share/$APP_DIR_LINUX").absolutePath
        }
    }

    companion object {
        private const val OS_NAME_PROPERTY = "os.name"
        private const val OS_WINDOWS = "win"
        private const val OS_MACOS = "mac"
        private const val DIR_CACHE = "tvbox-cache"
        private const val DIR_DOWNLOAD = "download"
        private const val DIR_SOURCE = "source"
        private const val APP_DIR_WINDOWS = "TVBox"
        private const val APP_DIR_MACOS = "TVBox"
        private const val APP_DIR_LINUX = "tvbox"
    }
}
