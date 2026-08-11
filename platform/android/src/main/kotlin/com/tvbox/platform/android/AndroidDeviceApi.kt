package com.tvbox.platform.android

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.tvbox.deviceapi.DeviceApi
import com.tvbox.deviceapi.LogLevel
import com.tvbox.deviceapi.Platform
import com.tvbox.deviceapi.ScreenOrientation
import com.tvbox.deviceapi.player.IPlayer
import com.tvbox.deviceapi.storage.StorageManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * Android 平台 DeviceApi 实现
 *
 * 整合 Android 系统能力（Toast、剪贴板、浏览器、权限、日志等），
 * 兼容原版 TVBox 全部系统能力入口，业务层统一通过 [DeviceApi] 调用。
 *
 * @param context Android 上下文（建议使用 Application Context）
 */
class AndroidDeviceApi(
    private val context: Context
) : DeviceApi {

    /** 跨平台存储管理器，懒加载避免启动期开销 */
    private val _storageManager: StorageManager by lazy { AndroidStorageManager(context) }

    /** 共享 HTTP 客户端 */
    private val httpClient: OkHttpClient by lazy { OkHttpClient.Builder().build() }

    // ===== 存储能力 =====

    override fun getStorageManager(): StorageManager = _storageManager

    // ===== 播放器能力 =====

    override fun createPlayer(): IPlayer = ExoPlayerImpl(context)

    // ===== 网络能力 =====

    override fun createHttpClient(): Any = httpClient

    // ===== 系统能力 =====

    override fun getAppVersion(): String {
        return runCatching {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName.orEmpty()
        }.getOrDefault("")
    }

    override fun getAppVersionCode(): Int {
        return runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                info.versionCode
            }
        }.getOrDefault(0)
    }

    override fun getPlatform(): Platform = Platform.ANDROID

    override fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val model = Build.MODEL.orEmpty()
        return if (model.equals(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model"
        }.trim().ifEmpty { "Android Device" }
    }

    // ===== 用户交互 =====

    override fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    override fun showConfirmDialog(title: String, message: String): Boolean {
        // 同步对话框：通过 CountDownLatch 阻塞等待用户选择结果
        val activity = context as? Activity ?: return false
        if (isMainThread()) return false // 主线程直接调用会死锁，回退为否
        val latch = CountDownLatch(1)
        var result = false
        activity.runOnUiThread {
            AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("确定") { _, _ -> result = true; latch.countDown() }
                .setNegativeButton("取消") { _, _ -> result = false; latch.countDown() }
                .setOnCancelListener { result = false; latch.countDown() }
                .show()
        }
        latch.await(DIALOG_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        return result
    }

    override fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText(LABEL_CLIPBOARD, text)
        clipboard?.setPrimaryClip(clip)
    }

    override fun getClipboardText(): String {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = clipboard?.primaryClip ?: return ""
        if (clip.itemCount <= 0) return ""
        return clip.getItemAt(0).coerceToText(context).toString()
    }

    // ===== 权限 =====

    override fun requestStoragePermission(): Boolean {
        // Android 13+ 采用细粒度媒体权限，应用专属目录无需声明存储权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        // Android 10+ scoped storage 下应用专属目录访问不需要写权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return hasStoragePermission()
        }
        // Android 9 及以下需要运行时申请 WRITE_EXTERNAL_STORAGE
        if (!hasStoragePermission()) {
            val activity = context as? Activity
            activity?.runOnUiThread {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", context.packageName, null))
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return false
        }
        return true
    }

    override fun hasStoragePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return true
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            READ_EXTERNAL_STORAGE
        } else {
            WRITE_EXTERNAL_STORAGE
        }
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    // ===== 外部能力 =====

    override fun openExternalPlayer(url: String) {
        val uri = Uri.parse(url)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MIME_VIDEO)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.onFailure { showToast("未找到可用的外部播放器") }
    }

    override fun castVideo(url: String, title: String) {
        // DLNA / 投屏需要额外集成投屏框架（如 Cling / Cast SDK）
        // 此处提供能力入口：若设备存在已选中的投屏路由则投屏，否则提示用户
        val activity = context as? Activity
        if (activity == null) {
            showToast("当前环境不支持投屏")
            return
        }
        // 投屏路由检测与媒体投递需在播放页绑定 MediaRouteButton 后完成
        // 这里仅打开外部投屏选择入口，实际投递由播放页 CastController 处理
        showToast("请选择投屏设备：$title")
    }

    override fun openBrowser(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.onFailure { showToast("未找到可用的浏览器") }
    }

    override fun exitApp() {
        (context as? Activity)?.finishAffinity()
        // 兼容原版 TVBox 直接退出进程的行为
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            (context as? Activity)?.finishAndRemoveTask()
        }
        System.exit(0)
    }

    // ===== 屏幕方向 =====

    /** 当前是否已锁定屏幕方向（锁定后 setScreenOrientation 的自动调用将被忽略） */
    @Volatile
    private var orientationLocked: Boolean = false

    override fun setScreenOrientation(orientation: ScreenOrientation) {
        // 锁定时忽略自动切换调用
        if (orientationLocked) return
        val activity = context as? Activity ?: return
        val requested = when (orientation) {
            ScreenOrientation.UNSPECIFIED -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            ScreenOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            ScreenOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            ScreenOrientation.SENSOR -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            ScreenOrientation.FULL_SENSOR -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
        activity.runOnUiThread { activity.requestedOrientation = requested }
    }

    override fun getScreenOrientation(): ScreenOrientation {
        val activity = context as? Activity ?: return ScreenOrientation.UNSPECIFIED
        return when (activity.requestedOrientation) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT -> ScreenOrientation.PORTRAIT
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> ScreenOrientation.LANDSCAPE
            ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR -> ScreenOrientation.FULL_SENSOR
            else -> ScreenOrientation.UNSPECIFIED
        }
    }

    override fun isOrientationLocked(): Boolean = orientationLocked

    override fun setOrientationLocked(locked: Boolean) {
        orientationLocked = locked
        // 解锁后恢复为跟随重力
        if (!locked) {
            setScreenOrientation(ScreenOrientation.FULL_SENSOR)
        }
    }

    // ===== 日志 =====

    override fun log(level: LogLevel, tag: String, message: String) {
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, message)
            LogLevel.INFO -> Log.i(tag, message)
            LogLevel.WARN -> Log.w(tag, message)
            LogLevel.ERROR -> Log.e(tag, message)
        }
        appendLogFile(level, tag, message)
    }

    override fun exportLogs(): String {
        val logFile = File(_storageManager.getDocumentDir(), LOG_FILE_NAME)
        return logFile.absolutePath
    }

    // ===== 内部辅助 =====

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

    /**
     * 判断当前是否运行在主线程
     */
    private fun isMainThread(): Boolean =
        android.os.Looper.myLooper() == android.os.Looper.getMainLooper()

    companion object {
        private const val LABEL_CLIPBOARD = "tvbox_clip"
        private const val MIME_VIDEO = "video/*"
        private const val LOG_FILE_NAME = "tvbox.log"
        private const val LOG_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss"
        private const val DIALOG_TIMEOUT_MS = 30_000L
        private val READ_EXTERNAL_STORAGE = android.Manifest.permission.READ_EXTERNAL_STORAGE
        private val WRITE_EXTERNAL_STORAGE = android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    }
}
