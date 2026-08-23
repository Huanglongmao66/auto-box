package com.tvbox.platform.desktop

import com.tvbox.deviceapi.DeviceApi
import com.tvbox.deviceapi.LogLevel
import com.tvbox.deviceapi.Platform
import com.tvbox.deviceapi.player.IPlayer
import com.tvbox.deviceapi.storage.StorageManager
import okhttp3.OkHttpClient
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.swing.JOptionPane

/**
 * Desktop（Windows / macOS / Linux）平台 DeviceApi 实现
 *
 * 整合 JVM 桌面端系统能力（剪贴板、浏览器、日志等），
 * 业务层统一通过 [DeviceApi] 调用，不感知底层操作系统。
 */
class DesktopDeviceApi : DeviceApi {

    /** 跨平台存储管理器，懒加载避免启动期开销 */
    private val _storageManager: StorageManager by lazy { DesktopStorageManager() }

    /** 共享 HTTP 客户端 */
    private val httpClient: OkHttpClient by lazy { OkHttpClient.Builder().build() }

    // ===== 存储能力 =====

    override fun getStorageManager(): StorageManager = _storageManager

    // ===== 播放器能力 =====

    override fun createPlayer(): IPlayer = DesktopPlayerImpl()

    // ===== 网络能力 =====

    override fun createHttpClient(): Any = httpClient

    // ===== 系统能力 =====

    override fun getAppVersion(): String {
        // 优先从 JAR Manifest 的 Implementation-Version 读取
        val manifestVersion = this.javaClass.`package`?.implementationVersion
        if (!manifestVersion.isNullOrBlank()) return manifestVersion
        // 回退到系统属性或默认版本号
        return System.getProperty(APP_VERSION_PROPERTY, DEFAULT_VERSION)
    }

    override fun getAppVersionCode(): Int {
        // 从版本号解析数字部分，无法解析时回退到默认版本码
        val code = getAppVersion().substringBefore("-").filter { it.isDigit() }
        return code.toIntOrNull() ?: DEFAULT_VERSION_CODE
    }

    override fun getPlatform(): Platform {
        val osName = System.getProperty(OS_NAME_PROPERTY, "").lowercase()
        return when {
            osName.contains(OS_WINDOWS) -> Platform.DESKTOP_WINDOWS
            osName.contains(OS_MACOS) -> Platform.DESKTOP_MACOS
            osName.contains(OS_LINUX) || osName.contains(OS_UNIX) -> Platform.DESKTOP_LINUX
            else -> Platform.DESKTOP_LINUX
        }
    }

    override fun getDeviceName(): String {
        val osName = System.getProperty(OS_NAME_PROPERTY, "").ifEmpty { "Desktop" }
        val osArch = System.getProperty("os.arch", "")
        return "$osName $osArch".trim()
    }

    // ===== 用户交互 =====

    override fun showToast(message: String) {
        // 桌面端无原生 Toast，使用标准输出替代
        println("[Toast] $message")
    }

    override fun showConfirmDialog(title: String, message: String): Boolean {
        return runCatching {
            val result = JOptionPane.showConfirmDialog(
                null,
                message,
                title,
                JOptionPane.YES_NO_OPTION
            )
            result == JOptionPane.YES_OPTION
        }.getOrDefault(false)
    }

    override fun copyToClipboard(text: String) {
        runCatching {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(text), null)
        }
    }

    override fun getClipboardText(): String {
        return runCatching {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val contents = clipboard.getContents(null) ?: return ""
            contents.getTransferData(DataFlavor.stringFlavor) as? String ?: ""
        }.getOrDefault("")
    }

    // ===== 权限 =====

    override fun requestStoragePermission(): Boolean {
        // 桌面端无运行时存储权限限制，默认已授权
        return true
    }

    override fun hasStoragePermission(): Boolean {
        // 桌面端无运行时存储权限限制，默认已授权
        return true
    }

    // ===== 外部能力 =====

    override fun openExternalPlayer(url: String) {
        // 桌面端通过系统默认关联程序打开视频地址
        val command = openCommand(url) ?: run {
            showToast("未找到可用的外部播放器")
            return
        }
        runCatching { Runtime.getRuntime().exec(command) }
            .onFailure { showToast("未找到可用的外部播放器") }
    }

    override fun castVideo(url: String, title: String) {
        // DLNA / 投屏需要额外集成投屏框架，桌面端仅提供能力入口
        showToast("当前环境不支持投屏：$title")
    }

    override fun openBrowser(url: String) {
        if (Desktop.isDesktopSupported() &&
            Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)
        ) {
            runCatching { Desktop.getDesktop().browse(URI(url)) }
                .onFailure { showToast("未找到可用的浏览器") }
        } else {
            showToast("当前平台不支持打开浏览器：$url")
        }
    }

    override fun exitApp() {
        // 桌面端直接退出 JVM 进程
        System.exit(0)
    }

    // ===== 日志 =====

    override fun log(level: LogLevel, tag: String, message: String) {
        // 桌面端无 android.util.Log，统一使用标准输出
        println("${level.name}/$tag: $message")
        appendLogFile(level, tag, message)
    }

    override fun exportLogs(): String {
        val logFile = File(_storageManager.getDocumentDir(), LOG_FILE_NAME)
        return logFile.absolutePath
    }

    // ===== 内部辅助 =====

    /**
     * 根据操作系统构建打开视频地址的命令，无法识别平台时返回 null
     */
    private fun openCommand(url: String): Array<String>? {
        val osName = System.getProperty(OS_NAME_PROPERTY, "").lowercase()
        return when {
            osName.contains(OS_MACOS) -> arrayOf("open", url)
            osName.contains(OS_WINDOWS) -> arrayOf(
                "rundll32", "url.dll,FileProtocolHandler", url
            )
            else -> arrayOf("xdg-open", url)
        }
    }

    /**
     * 将日志追加写入日志文件，便于导出排查
     */
    private fun appendLogFile(level: LogLevel, tag: String, message: String) {
        runCatching {
            val timestamp = SimpleDateFormat(LOG_DATE_FORMAT, Locale.getDefault()).format(Date())
            val line = "$timestamp ${level.name}/$tag: $message\n"
            _storageManager.writeFile(
                path = File(_storageManager.getDocumentDir(), LOG_FILE_NAME).absolutePath,
                content = line,
                append = true
            )
        }
    }

    companion object {
        private const val OS_NAME_PROPERTY = "os.name"
        private const val APP_VERSION_PROPERTY = "app.version"
        private const val OS_WINDOWS = "win"
        private const val OS_MACOS = "mac"
        private const val OS_LINUX = "nux"
        private const val OS_UNIX = "nix"
        private const val DEFAULT_VERSION = "1.0.0"
        private const val DEFAULT_VERSION_CODE = 1
        private const val LOG_FILE_NAME = "tvbox.log"
        private const val LOG_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss"
    }
}
