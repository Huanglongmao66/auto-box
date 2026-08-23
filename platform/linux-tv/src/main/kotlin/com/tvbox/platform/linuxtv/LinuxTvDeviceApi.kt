package com.tvbox.platform.linuxtv

import com.tvbox.deviceapi.DeviceApi
import com.tvbox.deviceapi.LogLevel
import com.tvbox.deviceapi.Platform
import com.tvbox.deviceapi.player.IPlayer
import com.tvbox.deviceapi.player.PlayerListener
import com.tvbox.deviceapi.player.TrackInfo
import com.tvbox.deviceapi.player.TrackType
import com.tvbox.deviceapi.storage.StorageManager

/**
 * 嵌入式 Linux TV 平台 DeviceApi 实现
 *
 * 基于 POSIX/Linux 系统调用实现设备能力
 */
class LinuxTvDeviceApi : DeviceApi {

    private val storageManager = LinuxTvStorageManager()

    override fun getStorageManager(): StorageManager = storageManager

    override fun createPlayer(): IPlayer = LinuxTvPlayer()

    override fun createHttpClient(): Any {
        // 使用 Ktor Curl 引擎
        return "ktor-curl"
    }

    override fun getAppVersion(): String = "1.0.0"

    override fun getAppVersionCode(): Int = 1

    override fun getPlatform(): Platform = Platform.LINUX_TV

    override fun getDeviceName(): String {
        return try {
            val process = ProcessBuilder("cat", "/proc/device-tree/model").start()
            val text = process.inputStream.bufferedReader().readText().trim()
            if (text.isNotEmpty()) text else "Linux TV Device"
        } catch (_: Exception) {
            "Linux TV Device"
        }
    }

    override fun showToast(message: String) {
        println("[Toast] $message")
    }

    override fun showConfirmDialog(title: String, message: String): Boolean {
        println("[Confirm] $title: $message")
        print("确认? (y/n): ")
        val input = readlnOrNull()?.lowercase() ?: "n"
        return input == "y" || input == "yes"
    }

    override fun copyToClipboard(text: String) {
        // 嵌入式环境通常无剪贴板，写入临时文件
        try {
            val process = ProcessBuilder("sh", "-c", "echo -n '$text' | xclip -selection clipboard 2>/dev/null || true").start()
            process.waitFor()
        } catch (_: Exception) {
            // 忽略
        }
    }

    override fun getClipboardText(): String {
        return try {
            val process = ProcessBuilder("sh", "-c", "xclip -selection clipboard -o 2>/dev/null || echo ''").start()
            process.inputStream.bufferedReader().readText().trim()
        } catch (_: Exception) {
            ""
        }
    }

    override fun requestStoragePermission(): Boolean = true

    override fun hasStoragePermission(): Boolean = true

    override fun openExternalPlayer(url: String) {
        println("[ExternalPlayer] $url")
        try {
            ProcessBuilder("mpv", url).start()
        } catch (_: Exception) {
            println("无法启动外部播放器，请安装 mpv")
        }
    }

    override fun castVideo(url: String, title: String) {
        println("[Cast] $title: $url (DLNA 投屏待实现)")
    }

    override fun openBrowser(url: String) {
        try {
            ProcessBuilder("xdg-open", url).start()
        } catch (_: Exception) {
            println("无法打开浏览器: $url")
        }
    }

    override fun exitApp() {
        println("TVBox 退出")
        kotlin.system.exitProcess(0)
    }

    override fun log(level: LogLevel, tag: String, message: String) {
        val levelStr = when (level) {
            LogLevel.DEBUG -> "DEBUG"
            LogLevel.INFO -> "INFO"
            LogLevel.WARN -> "WARN"
            LogLevel.ERROR -> "ERROR"
        }
        println("[$levelStr] $tag: $message")
    }

    override fun exportLogs(): String {
        val logPath = "${storageManager.getCacheDir()}/tvbox.log"
        return logPath
    }
}

/**
 * Linux TV 存储管理实现
 */
class LinuxTvStorageManager : StorageManager {

    private val homeDir = System.getProperty("user.home") ?: "/tmp"
    private val cacheDir = "$homeDir/.cache/tvbox"
    private val dataDir = "$homeDir/.local/share/tvbox"
    private val downloadDir = "$homeDir/Downloads"
    private val sourceDir = "$dataDir/sources"

    private val configMap = mutableMapOf<String, String>()

    override fun getCacheDir(): String = cacheDir
    override fun getDocumentDir(): String = dataDir
    override fun getDownloadDir(): String = downloadDir
    override fun getSourceDir(): String = sourceDir

    override fun readFile(path: String): String {
        return try {
            java.io.File(path).readText()
        } catch (_: Exception) {
            ""
        }
    }

    override fun writeFile(path: String, content: String, append: Boolean) {
        try {
            val file = java.io.File(path)
            file.parentFile?.mkdirs()
            if (append) file.appendText(content) else file.writeText(content)
        } catch (_: Exception) {
            // 忽略
        }
    }

    override fun readBytes(path: String): ByteArray {
        return try {
            java.io.File(path).readBytes()
        } catch (_: Exception) {
            ByteArray(0)
        }
    }

    override fun writeBytes(path: String, bytes: ByteArray, append: Boolean) {
        try {
            val file = java.io.File(path)
            file.parentFile?.mkdirs()
            if (append) file.appendBytes(bytes) else file.writeBytes(bytes)
        } catch (_: Exception) {
            // 忽略
        }
    }

    override fun exists(path: String): Boolean = java.io.File(path).exists()

    override fun mkdirs(path: String): Boolean = java.io.File(path).mkdirs()

    override fun delete(path: String): Boolean {
        return try {
            val file = java.io.File(path)
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        } catch (_: Exception) {
            false
        }
    }

    override fun listFiles(path: String): List<String> {
        return try {
            java.io.File(path).list()?.toList() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun getFileSize(path: String): Long {
        return try {
            java.io.File(path).length()
        } catch (_: Exception) {
            0L
        }
    }

    override fun move(from: String, to: String): Boolean {
        return try {
            java.io.File(from).renameTo(java.io.File(to))
        } catch (_: Exception) {
            false
        }
    }

    override fun copy(from: String, to: String): Boolean {
        return try {
            val src = java.io.File(from)
            val dst = java.io.File(to)
            dst.parentFile?.mkdirs()
            src.copyTo(dst, overwrite = true)
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun getConfig(key: String, default: String): String {
        return configMap[key] ?: default
    }

    override fun setConfig(key: String, value: String) {
        configMap[key] = value
    }

    override fun removeConfig(key: String) {
        configMap.remove(key)
    }

    override fun clearCache() {
        try {
            java.io.File(cacheDir).deleteRecursively()
            java.io.File(cacheDir).mkdirs()
        } catch (_: Exception) {
            // 忽略
        }
    }

    override fun getAvailableSpace(): Long {
        return try {
            val file = java.io.File(dataDir)
            file.mkdirs()
            file.usableSpace
        } catch (_: Exception) {
            Long.MAX_VALUE
        }
    }
}

/**
 * Linux TV 播放器实现（基于 MPV）
 */
class LinuxTvPlayer : IPlayer {

    private var url: String = ""
    private var position: Long = 0L
    private var duration: Long = 0L
    private var playing: Boolean = false
    private var speed: Float = 1.0f
    private var volume: Float = 1.0f
    private var process: Process? = null
    private val listeners = mutableSetOf<PlayerListener>()

    override fun setDataSource(url: String, headers: Map<String, String>, subtitleUrl: String?) {
        this.url = url
    }

    override fun play() {
        playing = true
        listeners.forEach { it.onPlay() }
        // 使用 mpv 播放
        try {
            process = ProcessBuilder("mpv", "--force-window", url).start()
        } catch (_: Exception) {
            listeners.forEach { it.onError(-1, "无法启动 mpv 播放器") }
        }
    }

    override fun pause() {
        playing = false
        listeners.forEach { it.onPause() }
        // 发送暂停命令到 mpv
    }

    override fun stop() {
        playing = false
        process?.destroy()
        process = null
        listeners.forEach { it.onCompletion() }
    }

    override fun seekTo(positionMs: Long) {
        position = positionMs
    }

    override fun release() {
        stop()
        listeners.clear()
    }

    override fun getCurrentPosition(): Long = position
    override fun getDuration(): Long = duration
    override fun isPlaying(): Boolean = playing
    override fun getBufferedPercentage(): Int = 100

    override fun setSpeed(speed: Float) {
        this.speed = speed
    }

    override fun getSpeed(): Float = speed

    override fun setVolume(volume: Float) {
        this.volume = volume
    }

    override fun getVolume(): Float = volume

    override fun setVideoView(renderView: Any) {
        // MPV 使用自身窗口渲染
    }

    override fun getAudioTracks(): List<TrackInfo> = emptyList()
    override fun selectAudioTrack(trackId: Int) {}
    override fun getSubtitleTracks(): List<TrackInfo> = emptyList()
    override fun selectSubtitleTrack(trackId: Int) {}
    override fun setExternalSubtitle(url: String) {}

    override fun addListener(listener: PlayerListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: PlayerListener) {
        listeners.remove(listener)
    }
}
