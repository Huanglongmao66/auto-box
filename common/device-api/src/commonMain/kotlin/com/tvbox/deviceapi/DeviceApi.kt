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

    // ===== 屏幕方向 =====

    /**
     * 设置当前 Activity 的屏幕方向。
     *
     * 业务层可在进入/退出播放器时调用，实现横竖屏自动或手动切换。
     * 默认空实现，仅 Android 等支持方向控制的平台覆盖。
     */
    fun setScreenOrientation(orientation: ScreenOrientation) {}

    /**
     * 获取当前屏幕方向，无法识别时返回 [ScreenOrientation.UNSPECIFIED]。
     */
    fun getScreenOrientation(): ScreenOrientation = ScreenOrientation.UNSPECIFIED

    /**
     * 是否已锁定屏幕方向（锁定后不再随视频尺寸/重力自动切换）。
     */
    fun isOrientationLocked(): Boolean = false

    /**
     * 锁定/解锁屏幕方向。锁定后 [setScreenOrientation] 的自动调用将被忽略，
     * 但用户手动点击锁定按钮时仍可切换状态。
     */
    fun setOrientationLocked(locked: Boolean) {}

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

/**
 * 屏幕方向
 */
enum class ScreenOrientation {
    /** 未指定 / 跟随系统 */
    UNSPECIFIED,
    /** 竖屏 */
    PORTRAIT,
    /** 横屏 */
    LANDSCAPE,
    /** 跟随重力（横屏，反向横屏） */
    SENSOR,
    /** 跟随重力（横竖屏全部方向） */
    FULL_SENSOR
}
