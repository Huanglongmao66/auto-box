package com.tvbox.core.repository

import java.io.File
import java.net.URL
import java.net.URLClassLoader

/**
 * JVM 平台 JAR 加载 actual 实现
 *
 * 加载约定：
 * - MovieSource.ext：
 *      1. "jar://{fully.qualified.ClassName}"        用当前 classpath 中的类
 *      2. "jar:file:///path/to/spider.jar!ClassName"  本地 jar
 *      3. "jar:https://xxx.com/spider.jar!ClassName"  远程 jar（先下载到临时文件）
 *      4. "https://xxx.com/spider.jar"                远程 jar，自动扫描实现类
 *
 * 被加载的 Spider 类需要满足 CatVod/TVBox 通用签名（不需要依赖 jar 包的接口，采用反射按名调用）：
 *   - void init(String param)                     // 可选
 *   - String homeContent()
 *   - String categoryContent(String tid, String pg, String filter)
 *   - String detailContent(String ids)
 *   - String searchContent(String key, boolean quick)
 *   - String playerContent(String flag, String id, String vipFlags)
 * 未命中的方法返回 ""，调用端 parseList("") 会安全返回空。
 */
internal actual object JarSpiderLoader {
    actual val isSupported: Boolean get() = true

    actual fun load(source: MovieSource, http: HttpFetcher): JarSpiderHandle? {
        val ext = source.ext
        if (ext.isBlank()) return null
        val (urlOrPath, className) = parseJarSpec(ext)
        try {
            val clazz: Class<*> = if (urlOrPath == null) {
                // 直接用当前 classpath
                Class.forName(className)
            } else {
                val jarFile = resolveJar(urlOrPath, http, source)
                val cl = URLClassLoader(arrayOf<URL>(jarFile.toURI().toURL()), this::class.java.classLoader)
                if (className.isNotBlank()) {
                    cl.loadClass(className)
                } else {
                    scanForSpiderClass(cl, jarFile) ?: return null
                }
            }
            val instance = clazz.getDeclaredConstructor().newInstance()
            return ReflectJarSpider(instance, clazz)
        } catch (t: Throwable) {
            return null
        }
    }

    private fun parseJarSpec(ext: String): Pair<String?, String> {
        if (ext.startsWith("jar://")) {
            return null to ext.removePrefix("jar://")
        }
        if (ext.startsWith("jar:")) {
            val rest = ext.removePrefix("jar:")
            val sep = rest.indexOf('!')
            return if (sep > 0) rest.substring(0, sep) to rest.substring(sep + 1)
            else rest to ""
        }
        return ext to ""
    }

    private fun resolveJar(urlOrPath: String, http: HttpFetcher, source: MovieSource): File {
        if (urlOrPath.startsWith("http://") || urlOrPath.startsWith("https://")) {
            // 下载到临时文件（带 key 缓存）
            val tmpDir = File(System.getProperty("java.io.tmpdir"), "tvbox-spider-jars").apply { mkdirs() }
            val target = File(tmpDir, "${safe(source.key)}.jar")
            if (!target.exists() || target.length() == 0L) {
                val bytes = runCatching {
                    // 用 http download：这里用简单 get -> 字节级下载需要 NetworkService 暴露 download；
                    // 退化为调用 NetworkService get 后转 ByteArray；NetworkService 没暴露 readBytes 但 get 是 String，
                    // 二进制 jar 不适合。此处保守地用 Java 原生 URL 下载。
                    val u = URL(urlOrPath)
                    val c = u.openConnection()
                    c.setRequestProperty("User-Agent", HttpFetcher.USER_AGENT)
                    if (source.header.isNotBlank()) {
                        // 简单注入 header 中的 User-Agent：避免解析 JSON 出错
                        val m = Regex("""User-Agent["']?\s*:\s*["']([^"'}]+)""")
                            .find(source.header)
                        if (m != null) c.setRequestProperty("User-Agent", m.groupValues[1])
                    }
                    c.getInputStream().use { inp ->
                        target.outputStream().use { out -> inp.copyTo(out) }
                    }
                }
                if (bytes.isFailure || target.length() == 0L) {
                    target.delete()
                }
            }
            if (target.exists()) return target
        }
        val f = File(urlOrPath)
        if (f.exists()) return f
        // 退化为相对路径：classpath 里如果有以 urlOrPath 为名的资源就 copy 出来
        val res = this::class.java.classLoader.getResource(urlOrPath)
        if (res != null) {
            val tmp = File.createTempFile("spider-", ".jar")
            tmp.deleteOnExit()
            res.openStream().use { inp -> tmp.outputStream().use { out -> inp.copyTo(out) } }
            return tmp
        }
        return f
    }

    private fun scanForSpiderClass(cl: URLClassLoader, jarFile: File): Class<*>? {
        // 简单扫 JAR 清单：如果 jar 有 META-INF/services/com.github.catvod.spider.Spider 就加载
        runCatching {
            val u = cl.findResource("META-INF/services/com.github.catvod.spider.Spider")
            if (u != null) {
                val line = u.openStream().bufferedReader().useLines { it.firstOrNull()?.trim() }
                if (!line.isNullOrBlank()) return cl.loadClass(line)
            }
        }
        // 另一个常见名字
        for (name in listOf("Spider", "MainSpider")) {
            runCatching { return cl.loadClass("com.github.catvod.spider.$name") }
        }
        return null
    }

    private fun safe(s: String): String = s.replace(Regex("""[^\w\-.]"""), "_")

    private class ReflectJarSpider(private val obj: Any, private val cls: Class<*>) : JarSpiderHandle {

        override fun init(param: String) {
            runCatching {
                val m = cls.methods.firstOrNull {
                    it.name == "init" && it.parameterCount == 1
                }
                m?.invoke(obj, param)
            }
        }

        override fun homeContent(): String = invokeStr("homeContent")
        override fun categoryContent(tid: String, pg: String, filter: String): String =
            invokeStr("categoryContent", arrayOf(tid, pg, filter))
        override fun detailContent(ids: String): String = invokeStr("detailContent", arrayOf(ids))
        override fun searchContent(key: String, quick: Boolean): String =
            invokeStr("searchContent", arrayOf(key, quick))
        override fun playerContent(flag: String, id: String, vipFlags: String): String =
            invokeStr("playerContent", arrayOf(flag, id, vipFlags))

        private fun invokeStr(method: String, args: Array<Any>? = null): String {
            return runCatching {
                val m = if (args == null) {
                    cls.methods.firstOrNull { it.name == method && it.parameterCount == 0 }
                } else {
                    cls.methods.firstOrNull {
                        it.name == method && matchParams(it.parameterTypes, args)
                    }
                } ?: return@runCatching ""
                val r = if (args == null) m.invoke(obj) else m.invoke(obj, *args)
                r?.toString() ?: ""
            }.getOrDefault("")
        }

        private fun matchParams(types: Array<Class<*>>, args: Array<Any>): Boolean {
            if (types.size != args.size) return false
            args.forEachIndexed { i, a ->
                val t = types[i]
                val ok = when {
                    t == String::class.java -> a is String
                    t == java.lang.Boolean::class.java || t == Boolean::class.javaPrimitiveType -> a is Boolean
                    t == java.lang.Integer::class.java || t == Int::class.javaPrimitiveType -> a is Int
                    t.isAssignableFrom(a.javaClass) -> true
                    else -> false
                }
                if (!ok) return false
            }
            return true
        }
    }
}
