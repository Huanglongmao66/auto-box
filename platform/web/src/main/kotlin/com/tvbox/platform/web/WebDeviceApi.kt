package com.tvbox.platform.web

import com.tvbox.deviceapi.DeviceApi
import com.tvbox.deviceapi.LogLevel
import com.tvbox.deviceapi.Platform
import com.tvbox.deviceapi.player.IPlayer
import com.tvbox.deviceapi.storage.StorageManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLTextAreaElement

/**
 * Web 平台 DeviceApi 实现
 *
 * 基于 Kotlin/JS 浏览器环境，整合 DOM / BOM 能力（弹窗、剪贴板、浏览器、
 * 日志等），业务层统一通过 [DeviceApi] 调用，与 Android / Desktop 平台对齐。
 */
class WebDeviceApi : DeviceApi {

    /** 跨平台存储管理器，懒加载避免启动期开销 */
    private val storageManager: StorageManager by lazy { WebStorageManager() }

    /** 共享 HTTP 客户端（Ktor JS 引擎） */
    private val httpClient: HttpClient by lazy { buildHttpClient() }

    /** 最近一次复制到剪贴板的文本（浏览器安全策略限制同步读取） */
    private var lastCopiedText: String = ""

    // ===== 存储能力 =====

    override fun getStorageManager(): StorageManager = storageManager

    // ===== 播放器能力 =====

    override fun createPlayer(): IPlayer = Html5PlayerImpl()

    // ===== 网络能力 =====

    override fun createHttpClient(): Any = httpClient

    // ===== 系统能力 =====

    override fun getAppVersion(): String = APP_VERSION

    override fun getAppVersionCode(): Int = APP_VERSION_CODE

    override fun getPlatform(): Platform = Platform.WEB

    override fun getDeviceName(): String =
        window.navigator.userAgent.takeIf { it.isNotEmpty() } ?: "Web Browser"

    // ===== 用户交互 =====

    override fun showToast(message: String) {
        showDomToast(message)
    }

    override fun showConfirmDialog(title: String, message: String): Boolean {
        val fullMessage = if (title.isEmpty()) message else "$title\n\n$message"
        return window.confirm(fullMessage)
    }

    override fun copyToClipboard(text: String) {
        lastCopiedText = text
        val clipboard = window.navigator.asDynamic().clipboard
        if (clipboard != null && clipboard.writeText != null) {
            try {
                // 优先使用 Clipboard API，失败时回退到 execCommand
                clipboard.writeText(text).catch { execCommandCopy(text) }
            } catch (e: Throwable) {
                execCommandCopy(text)
            }
        } else {
            execCommandCopy(text)
        }
    }

    override fun getClipboardText(): String {
        // 浏览器安全策略禁止同步读取剪贴板，返回最近一次写入的文本
        return lastCopiedText
    }

    // ===== 权限 =====

    override fun requestStoragePermission(): Boolean = true

    override fun hasStoragePermission(): Boolean = true

    // ===== 外部能力 =====

    override fun openExternalPlayer(url: String) {
        // Web 端无外部播放器，在新标签页打开以借助浏览器原生播放能力
        window.open(url, "_blank") ?: showDomToast("无法打开外部播放器")
    }

    override fun castVideo(url: String, title: String) {
        // Web 端不支持 DLNA / 投屏，提示用户
        showDomToast("当前环境不支持投屏：$title")
    }

    override fun openBrowser(url: String) {
        window.open(url, "_blank") ?: showDomToast("无法打开浏览器")
    }

    override fun exitApp() {
        // 仅对脚本打开的窗口有效，否则浏览器会忽略
        window.close()
    }

    // ===== 日志 =====

    override fun log(level: LogLevel, tag: String, message: String) {
        consoleLog(level, "$tag: $message")
        appendLogFile(level, tag, message)
    }

    override fun exportLogs(): String =
        "${storageManager.getDocumentDir()}/$LOG_FILE_NAME"

    // ===== 内部辅助 =====

    /**
     * 构建 Ktor JS HttpClient，统一超时与 UserAgent 配置
     */
    private fun buildHttpClient(): HttpClient = HttpClient(Js) {
        install(HttpTimeout) {
            requestTimeoutMillis = HTTP_TIMEOUT_MS
            connectTimeoutMillis = HTTP_TIMEOUT_MS
            socketTimeoutMillis = HTTP_TIMEOUT_MS
        }
        install(UserAgent) {
            agent = USER_AGENT
        }
    }

    /**
     * 通过 DOM 创建轻量级 Toast 提示，自动延时移除
     */
    private fun showDomToast(message: String) {
        val container = document.body ?: run {
            window.alert(message)
            return
        }
        val toast = document.createElement("div").unsafeCast<HTMLElement>()
        toast.textContent = message
        toast.style.apply {
            position = "fixed"
            left = "50%"
            bottom = "20%"
            transform = "translateX(-50%)"
            padding = "10px 18px"
            backgroundColor = "rgba(0,0,0,0.78)"
            color = "#ffffff"
            fontSize = "14px"
            borderRadius = "6px"
            zIndex = "99999"
            maxWidth = "80%"
            whiteSpace = "pre-wrap"
        }
        container.appendChild(toast)
        window.setTimeout({ container.removeChild(toast) }, TOAST_DURATION_MS)
    }

    /**
     * 兼容旧浏览器 / 非安全上下文的剪贴板复制（textarea + execCommand）
     */
    private fun execCommandCopy(text: String) {
        try {
            val ta = document.createElement("textarea").unsafeCast<HTMLTextAreaElement>()
            ta.value = text
            ta.style.position = "absolute"
            ta.style.left = "-9999px"
            document.body?.appendChild(ta)
            ta.select()
            document.execCommand("copy")
            document.body?.removeChild(ta)
        } catch (e: Throwable) {
            // 忽略：浏览器安全策略限制
        }
    }

    /**
     * 将日志输出到浏览器控制台
     */
    private fun consoleLog(level: LogLevel, line: String) {
        val console = js("console")
        when (level) {
            LogLevel.DEBUG -> console.log(line)
            LogLevel.INFO -> console.info(line)
            LogLevel.WARN -> console.warn(line)
            LogLevel.ERROR -> console.error(line)
        }
    }

    /**
     * 将日志追加写入日志文件，便于导出排查
     */
    private fun appendLogFile(level: LogLevel, tag: String, message: String) {
        try {
            val timestamp = js("new Date().toISOString()") as String
            val line = "$timestamp ${level.name}/$tag: $message\n"
            storageManager.writeFile(
                path = "${storageManager.getDocumentDir()}/$LOG_FILE_NAME",
                content = line,
                append = true
            )
        } catch (e: Throwable) {
            // 忽略日志写入失败
        }
    }

    companion object {
        private const val APP_VERSION = "1.0.0"
        private const val APP_VERSION_CODE = 1
        private const val LOG_FILE_NAME = "tvbox.log"
        private const val TOAST_DURATION_MS = 2000
        private const val HTTP_TIMEOUT_MS = 15000L
        private const val USER_AGENT = "Mozilla/5.0 (TVBox-Multiplatform-Web/1.0)"
    }
}
