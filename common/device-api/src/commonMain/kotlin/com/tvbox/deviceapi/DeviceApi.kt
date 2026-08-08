package com.tvbox.deviceapi

import com.tvbox.deviceapi.player.IPlayer
import com.tvbox.deviceapi.storage.StorageManager

/**
 * 设备通用抽象接口 - 跨平台统一系统能力入口
 *
 * 所有平台系统操作（存储、网络、播放器、权限、弹窗等）统一抽象，
 * 各平台单独实现，业务层仅依赖此接口，不感知底层操作系统。
 */
interface DeviceApi {

    // ===== 存储能力 =====

    /**
     * 获取跨平台存储管理器
     */
    fun getStorageManager(): StorageManager

    // ===== 播放器能力 =====

    /**
     * 创建跨平台播放器实例
     */
    fun createPlayer(): IPlayer

    // ===== 网络能力 =====

    /**
     * 创建HTTP客户端（返回平台对应的HttpClient实例）
     */
    fun createHttpClient(): Any

    // ===== 系统能力 =====

    /**
     * 获取应用版本名称
     */
    fun getAppVersion(): String

    /**
     * 获取应用版本号
     */
    fun getAppVersionCode(): Int

    /**
     * 获取设备平台标识
     */
    fun getPlatform(): Platform

    /**
     * 获取设备型号/名称
     */
    fun getDeviceName(): String

    // ===== 用户交互 =====

    /**
     * 显示Toast提示
     */
    fun showToast(message: String)

    /**
     * 显示确认对话框，返回用户选择结果
     */
    fun showConfirmDialog(title: String, message: String): Boolean

    /**
     * 复制文本到剪贴板
     */
    fun copyToClipboard(text: String)

    /**
     * 从剪贴板读取文本
     */
    fun getClipboardText(): String

    // ===== 权限 =====

    /**
     * 请求存储权限
     */
    fun requestStoragePermission(): Boolean

    /**
     * 检查存储权限是否已授予
     */
    fun hasStoragePermission(): Boolean

    // ===== 外部能力 =====

    /**
     * 唤起外部播放器播放视频
     */
    fun openExternalPlayer(url: String)

    /**
     * 投屏/DLNA
     */
    fun castVideo(url: String, title: String)

    /**
     * 打开系统浏览器
     */
    fun openBrowser(url: String)

    /**
     * 退出应用
     */
    fun exitApp()

    // ===== 日志 =====

    /**
     * 写入日志
     */
    fun log(level: LogLevel, tag: String, message: String)

    /**
     * 导出日志文件路径
     */
    fun exportLogs(): String
}

/**
 * 支持的平台枚举
 */
enum class Platform {
    ANDROID,
    ANDROID_TV,
    DESKTOP_WINDOWS,
    DESKTOP_MACOS,
    DESKTOP_LINUX,
    WEB,
    LINUX_TV
}

/**
 * 日志级别
 */
enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}
