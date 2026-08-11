package com.tvbox.platform.android.spider

import android.content.Context
import android.util.Log
import com.github.catvod.crawler.Spider
import com.github.catvod.crawler.SpiderApi
import com.github.catvod.crawler.SpiderNull
import com.github.catvod.net.OkHttp
import dalvik.system.DexClassLoader
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Jar 爬虫加载器
 *
 * 迁移自 TVBoxOS com.github.catvod.crawler.JarLoader
 *
 * 通过 [DexClassLoader] 动态加载远程 Jar 文件中的 Spider 爬虫类。
 *
 * 工作流程：
 * 1. 根据 jar URL 下载 Jar 文件到缓存目录
 * 2. 使用 DexClassLoader 加载 Jar
 * 3. 调用 Init.init(Context) 初始化（如果存在）
 * 4. 通过反射加载 com.github.catvod.spider.{ClassName} 并实例化
 * 5. 调用 spider.initApi() 和 spider.init(context, ext) 完成初始化
 *
 * @param context Android 上下文
 */
class JarSpiderLoader(
    private val context: Context
) {
    companion object {
        private const val TAG = "JarSpiderLoader"
        private const val MAIN_KEY = "main"
    }

    /** 后台线程池：用于 Jar 下载等网络操作，避免 NetworkOnMainThreadException */
    private val executor: ExecutorService = Executors.newCachedThreadPool()

    /** Jar URL → DexClassLoader 缓存 */
    private val loaders = HashMap<String, DexClassLoader>()

    /** Spider 实例缓存：jarKey + siteKey → Spider */
    private val spiders = HashMap<String, Spider>()

    /** 并发锁 */
    private val locks = HashMap<String, Any>()

    /**
     * 获取 Spider 实例
     *
     * @param key 站点 key
     * @param api 站点 API（csp_XXX 格式）
     * @param ext 扩展数据
     * @param jar Jar URL（可带 ;md5; 校验）
     * @return Spider 实例，加载失败返回 [SpiderNull]
     */
    fun getSpider(key: String, api: String, ext: String, jar: String): Spider {
        val safeKey = key.ifEmpty { "" }
        val safeApi = api.ifEmpty { "" }
        val safeExt = ext.ifEmpty { "" }
        val safeJar = jar.ifEmpty { "" }

        if (safeApi.isEmpty()) return SpiderNull()

        val jarKey = if (safeJar.isEmpty()) MAIN_KEY else jarKey(safeJar)
        val spKey = jarKey + safeKey

        // 缓存命中
        spiders[spKey]?.let { return it }

        // 在后台线程执行网络操作（Jar 下载），避免 NetworkOnMainThreadException
        val future: Future<Spider> = executor.submit<Spider> {
            try {
                if (MAIN_KEY != jarKey) {
                    parseJar(jarKey, safeJar)
                }
                val loader = loaders[jarKey]
                if (loader == null) {
                    Log.w(TAG, "getSpider: no loader for jarKey=$jarKey")
                    return@submit SpiderNull()
                }

                val className = "com.github.catvod.spider." + className(safeApi)
                val spider = loader.loadClass(className).newInstance() as Spider
                spider.siteKey = safeKey
                spider.initApi(SpiderApi())
                spider.init(context, safeExt)
                synchronized(spiders) { spiders[spKey] = spider }
                Log.i(TAG, "getSpider success key=$spKey, class=$className")
                spider
            } catch (e: Throwable) {
                Log.e(TAG, "getSpider error key=$spKey, ${e.javaClass.name}: ${e.message}")
                SpiderNull()
            }
        }

        return try {
            future.get()
        } catch (e: Throwable) {
            Log.e(TAG, "getSpider future.get() error: ${e.message}")
            SpiderNull()
        }
    }

    /**
     * 解析并加载 Jar 文件
     *
     * @param key Jar 缓存 key
     * @param jar Jar URL（可带 ;md5; 校验）
     */
    fun parseJar(key: String, jar: String) {
        if (key.isEmpty() || jar.isEmpty()) return
        if (loaders.containsKey(key)) return

        val lock = lock(key)
        synchronized(lock) {
            if (loaders.containsKey(key)) return

            // 解析 ;md5; 校验
            var source = jar
            var md5 = ""
            val parts = jar.split(";md5;")
            if (parts.size > 1) {
                source = parts[0]
                md5 = parts[1].trim()
            }
            // md5 是 HTTP URL 时先下载
            if (md5.startsWith("http")) {
                md5 = OkHttp.string(md5).trim()
            }

            val file = fileForJar(source)
            val fileExists = file.exists() && file.length() > 0
            Log.d(TAG, "parseJar source=$source, md5=$md5, fileExists=$fileExists")

            when {
                // MD5 校验通过，直接加载
                md5.isNotEmpty() && fileExists &&
                        fileMd5(file).equals(md5, ignoreCase = true) -> {
                    load(key, file)
                }
                // 无 MD5 但文件存在且一周内下载过
                md5.isEmpty() && fileExists && !isWeekAgo(file) -> {
                    load(key, file)
                }
                // HTTP 下载
                source.startsWith("http") -> {
                    load(key, download(source, file))
                }
                // assets 本地资源
                source.startsWith("assets") -> {
                    load(key, copyAsset(source, file))
                }
                // 本地文件路径
                source.startsWith("file") -> {
                    val localFile = local(source)
                    if (localFile.exists()) load(key, localFile)
                }
            }
        }
    }

    /**
     * 加载 Jar 文件到 DexClassLoader
     */
    private fun load(key: String, file: File?): Boolean {
        if (file == null || !file.exists() || file.length() == 0L) return false
        if (loaders.containsKey(key)) return true
        return try {
            file.setReadOnly()
            val cachePath = jarDir().absolutePath
            val loader = DexClassLoader(
                file.absolutePath,
                cachePath,
                cachePath,
                context.classLoader
            )
            // 调用 Init.init(Context) 如果存在
            invokeInit(loader)
            loaders[key] = loader
            Log.i(TAG, "load success key=$key, file=${file.absolutePath}")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "load error key=$key, msg=${e.message}")
            false
        }
    }

    /**
     * 调用 Jar 中的 Init.init(Context) 方法（如果存在）
     */
    private fun invokeInit(loader: DexClassLoader) {
        try {
            val clz = loader.loadClass("com.github.catvod.spider.Init")
            val method = clz.getMethod("init", Context::class.java)
            method.invoke(null, context)
        } catch (_: Throwable) {
            // Init 类不存在是正常情况，忽略
        }
    }

    /**
     * 清理所有缓存
     */
    fun clear() {
        for (spider in spiders.values) {
            try { spider.destroy() } catch (_: Throwable) {}
        }
        loaders.clear()
        spiders.clear()
        locks.clear()
        executor.shutdownNow()
    }

    // ========== 文件下载与缓存 ==========

    private fun download(url: String, file: File): File {
        var input: InputStream? = null
        var output: FileOutputStream? = null
        try {
            Log.i(TAG, "downloading jar: $url")
            val response: Response = OkHttp.newCall(url).execute()
            if (response.body == null) return file
            input = response.body!!.byteStream()
            output = FileOutputStream(createFile(file))
            val buffer = ByteArray(16384)
            var length: Int
            while (input.read(buffer).also { length = it } != -1) {
                if (Thread.interrupted()) return file
                output.write(buffer, 0, length)
            }
            output.flush()
            Log.i(TAG, "download complete, size=${file.length()}")
        } catch (e: Throwable) {
            Log.e(TAG, "download error: ${e.javaClass.name}: ${e.message}")
        } finally {
            try { input?.close() } catch (_: Throwable) {}
            try { output?.close() } catch (_: Throwable) {}
        }
        return file
    }

    private fun copyAsset(url: String, file: File): File {
        var input: InputStream? = null
        var output: FileOutputStream? = null
        try {
            val path = url.replace("assets://", "").replace("assets/", "")
            input = context.assets.open(path)
            output = FileOutputStream(createFile(file))
            val buffer = ByteArray(16384)
            var length: Int
            while (input.read(buffer).also { length = it } != -1) {
                output.write(buffer, 0, length)
            }
            output.flush()
        } catch (e: Throwable) {
            Log.e(TAG, "copyAsset error: ${e.message}")
        } finally {
            try { input?.close() } catch (_: Throwable) {}
            try { output?.close() } catch (_: Throwable) {}
        }
        return file
    }

    private fun local(path: String): File {
        val cleanPath = path.replace("file:/", "")
        val external = android.os.Environment.getExternalStorageDirectory()
        val file = File(external, cleanPath)
        return if (file.exists()) file else File(cleanPath)
    }

    private fun fileForJar(jar: String): File {
        return File(jarDir(), jarKey(jar) + ".jar")
    }

    private fun jarDir(): File {
        val dir = File(context.cacheDir, "jar")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun createFile(file: File): File {
        val parent = file.parentFile
        if (parent != null && !parent.exists()) parent.mkdirs()
        if (file.exists()) file.delete()
        file.createNewFile()
        file.setReadable(true)
        file.setWritable(true)
        return file
    }

    private fun isWeekAgo(file: File): Boolean {
        return System.currentTimeMillis() - file.lastModified() > 7 * 24 * 60 * 60 * 1000L
    }

    // ========== 工具方法 ==========

    private fun lock(key: String): Any {
        return locks[key] ?: run {
            val created = Any()
            val old = locks.putIfAbsent(key, created)
            old ?: created
        }
    }

    private fun jarKey(jar: String?): String {
        val key = md5(jar ?: "")
        return if (key.isEmpty()) MAIN_KEY else key
    }

    private fun className(api: String): String {
        return if (api.contains("csp_")) api.split("csp_")[1] else api
    }

    private fun md5(input: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (_: Throwable) {
            ""
        }
    }

    private fun fileMd5(file: File): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val input = file.inputStream()
            input.use {
                val buffer = ByteArray(8192)
                var length: Int
                while (it.read(buffer).also { length = it } != -1) {
                    md.update(buffer, 0, length)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Throwable) {
            ""
        }
    }
}
