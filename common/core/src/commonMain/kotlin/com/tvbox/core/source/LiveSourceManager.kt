package com.tvbox.core.source

import com.tvbox.core.di.ServiceLocator
import com.tvbox.core.model.LiveCategory
import com.tvbox.core.model.LiveSource
import com.tvbox.core.model.LiveSubscription
import com.tvbox.deviceapi.LogLevel
import com.tvbox.utils.EncodingUtils
import com.tvbox.utils.JsonUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 直播源管理器
 *
 * 负责直播源的加载、切换与解析：
 *
 * 1. 优先从远端订阅（[subscriptionUrl]）拉取：
 *    - 若返回 JSON（含 `lives` 数组），取第一个 live 项的 `url` 再拉取真正的 .txt 直播源；
 *    - 若返回纯文本（TVBox 标准 .txt 格式），直接解析。
 * 2. 远端任意环节失败时回退到内置示例源（[builtInSource]），保证离线可用。
 *
 * 通过 [LiveSourceParser] 将文本解析为 [LiveCategory] 列表供 UI 使用。
 */
class LiveSourceManager {

    /** 默认远端订阅地址（饭太硬） */
    var subscriptionUrl: String = DEFAULT_SUBSCRIPTION_URL
        private set

    /** 内置示例直播源（使用公开测试流，保证开箱即用） */
    val builtInSource: LiveSource = LiveSource(
        key = "builtin_live",
        name = "内置示例直播源",
        url = "",
        content = BUILTIN_LIVE_TEXT,
        enabled = true,
        order = 0
    )

    /** 当前已加载的分组列表（内存缓存） */
    @Volatile
    private var cachedCategories: List<LiveCategory> = emptyList()

    /** 最近一次加载是否来自远端（用于 UI 显示来源） */
    @Volatile
    var loadedFromRemote: Boolean = false
        private set

    /** 最近一次远端加载失败的错误信息（成功后清空） */
    @Volatile
    var lastRemoteError: String? = null
        private set

    /**
     * 加载直播源并解析为分组列表。
     *
     * 默认流程：先尝试 [subscriptionUrl] 远端加载；任一环节失败则回退 [builtInSource]。
     *
     * @param source 可选，指定使用内置/自定义源；不传则走远端订阅流程
     * @return 解析后的分组列表（永不抛出，失败返回内置源解析结果）
     */
    suspend fun load(source: LiveSource? = null): List<LiveCategory> = withContext(Dispatchers.Default) {
        // 显式指定源：直接走本地解析（不触网）
        if (source != null) {
            val text = source.content.ifBlank { builtInSource.content }
            val parsed = LiveSourceParser.parse(text)
            cachedCategories = parsed
            loadedFromRemote = false
            lastRemoteError = null
            return@withContext parsed
        }

        // 远端订阅加载
        val remote = runCatching { loadFromRemote() }
        if (remote.isSuccess) {
            val cats = remote.getOrNull().orEmpty()
            if (cats.isNotEmpty()) {
                cachedCategories = cats
                loadedFromRemote = true
                lastRemoteError = null
                return@withContext cats
            }
        }
        // 回退内置源
        lastRemoteError = remote.exceptionOrNull()?.message ?: "远端返回为空"
        val fallback = LiveSourceParser.parse(builtInSource.content)
        cachedCategories = fallback
        loadedFromRemote = false
        fallback
    }

    /**
     * 获取最近一次加载的分组列表（未加载时返回空）。
     */
    fun getCached(): List<LiveCategory> = cachedCategories

    /**
     * 切换远端订阅地址（持久化由调用方负责）。
     */
    fun setSubscriptionUrl(url: String) {
        if (url.isNotBlank()) subscriptionUrl = url
    }

    /**
     * 远端加载流程：
     * 1. GET 订阅地址得到原始文本；
     * 2. 尝试解析为 JSON：
     *    - 含 `lives` 数组时，取首个 live 项的 url 再拉取 .txt；
     *    - 不含 `lives` 时，原文本可能就是 .txt 格式，直接解析。
     * 3. 解析为 [LiveCategory] 列表。
     */
    private suspend fun loadFromRemote(): List<LiveCategory> {
        val network = ServiceLocator.getNetworkService()
        val headers = mapOf(
            "User-Agent" to TVBOX_USER_AGENT,
            "Accept" to "*/*"
        )
        log(LogLevel.INFO, "拉取订阅: $subscriptionUrl")
        val rawBytes = network.download(subscriptionUrl, headers)
        if (rawBytes.isEmpty()) {
            log(LogLevel.WARN, "订阅返回为空")
            return emptyList()
        }

        // 解码订阅内容（支持图片伪装配置 / JSON / 纯文本）
        val raw = decodeSubscriptionContent(rawBytes)
        if (raw.isBlank()) {
            log(LogLevel.WARN, "订阅内容为空")
            return emptyList()
        }
        log(LogLevel.DEBUG, "订阅解码后前200字符: ${raw.take(200)}")

        // 尝试 JSON 解析（TVBox 标准订阅，含 lives 数组）
        val liveTxtUrls = parseLiveTxtUrlsFromJson(raw)
        val txtContent = if (liveTxtUrls.isNotEmpty()) {
            log(LogLevel.INFO, "订阅含 ${liveTxtUrls.size} 个直播源，逐个尝试")
            loadFirstValidLiveTxt(liveTxtUrls, headers, network)
        } else {
            // 非 JSON 或不含 lives，视为直接的 .txt 文本
            log(LogLevel.INFO, "订阅非 lives JSON，按文本格式解析")
            raw
        }

        if (txtContent.isEmpty()) {
            log(LogLevel.WARN, "直播列表文本为空")
            return emptyList()
        }

        val categories = LiveSourceParser.parse(txtContent)
        log(LogLevel.INFO, "解析完成：${categories.size} 个分组，共 ${categories.sumOf { it.channels.size }} 个频道")
        categories.firstOrNull()?.channels?.firstOrNull()?.let { ch ->
            log(LogLevel.DEBUG, "首个频道: name=${ch.name}, url=${ch.urls.firstOrNull()}")
        }
        return categories
    }

    /**
     * 解码订阅内容
     *
     * 支持三种格式：
     * 1. 图片伪装配置（JPEG/BMP，在图片数据后嵌入 Base64 编码的 JSON）
     * 2. 标准 JSON 文本（含 lives 数组）
     * 3. 纯文本（TVBox 标准 .txt 直播源格式）
     *
     * @param bytes 原始响应字节
     * @return 解码后的文本内容
     */
    private fun decodeSubscriptionContent(bytes: ByteArray): String {
        // 检测是否为 JPEG 图片（FF D8 开头）
        if (bytes.size >= 2 && (bytes[0].toInt() and 0xFF) == 0xFF && (bytes[1].toInt() and 0xFF) == 0xD8) {
            log(LogLevel.INFO, "订阅为 JPEG 图片，提取嵌入配置")
            return extractConfigFromJpeg(bytes)
        }
        // 普通文本：自动检测编码
        return EncodingUtils.decodeWithFallback(bytes).trim()
    }

    /**
     * 从 JPEG 图片中提取嵌入的配置文本
     *
     * 饭太硬等订阅源使用图片伪装技术：
     * 1. 返回一个合法的 JPEG 图片
     * 2. 在 JPEG 结束标记（FF D9）之后嵌入文本
     * 3. 文本格式为 `前缀**Base64编码的JSON`
     *
     * @param bytes JPEG 图片字节
     * @return 解码后的 JSON 文本
     */
    private fun extractConfigFromJpeg(bytes: ByteArray): String {
        // 找到最后一个 JPEG 结束标记 FF D9
        var jpegEnd = -1
        for (i in bytes.size - 2 downTo 0) {
            if ((bytes[i].toInt() and 0xFF) == 0xFF && (bytes[i + 1].toInt() and 0xFF) == 0xD9) {
                jpegEnd = i + 2
                break
            }
        }
        if (jpegEnd < 0 || jpegEnd >= bytes.size) {
            log(LogLevel.WARN, "未找到 JPEG 结束标记或无嵌入数据")
            return ""
        }

        // 提取 JPEG 之后的嵌入文本
        val embeddedBytes = bytes.copyOfRange(jpegEnd, bytes.size)
        val embeddedText = EncodingUtils.decodeWithFallback(embeddedBytes).trim()
        if (embeddedText.isEmpty()) {
            log(LogLevel.WARN, "JPEG 后无嵌入文本")
            return ""
        }

        // 格式：前缀**Base64编码的JSON — 用 ** 分割取最后一部分
        val parts = embeddedText.split("**")
        val base64Part = if (parts.size >= 2) parts.last() else embeddedText

        // Base64 解码
        val json = decodeBase64(base64Part)
        if (json.isBlank()) {
            log(LogLevel.WARN, "Base64 解码失败")
            return ""
        }
        log(LogLevel.DEBUG, "从图片提取的 JSON 前200字符: ${json.take(200)}")
        return json
    }

    /**
     * Base64 解码（跨平台，使用 Kotlin 标准库）
     */
    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeBase64(base64: String): String {
        return try {
            // 清理非 Base64 字符（保留字母、数字、+/=）
            val cleaned = base64.filter { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }
            val decoded = Base64.decode(cleaned)
            String(decoded, Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * 尝试将订阅响应解析为 [LiveSubscription]，返回所有 live 项的 url 列表。
     * 解析失败或无 lives 项时返回空列表。
     */
    private fun parseLiveTxtUrlsFromJson(raw: String): List<String> {
        val element = JsonUtils.parseToJsonElement(raw) ?: return emptyList()
        val sub = runCatching {
            JsonUtils.json.decodeFromJsonElement(LiveSubscription.serializer(), element)
        }.getOrNull() ?: return emptyList()
        return sub.lives.mapNotNull { it.url.takeIf { u -> u.isNotBlank() } }
    }

    /**
     * 逐个尝试 lives URL，返回第一个能解析出有效频道的直播列表文本。
     *
     * @param urls lives URL 列表
     * @param headers 请求头
     * @param network 网络服务
     * @return 有效的直播列表文本；全部失败时返回空字符串
     */
    private suspend fun loadFirstValidLiveTxt(
        urls: List<String>,
        headers: Map<String, String>,
        network: com.tvbox.core.network.NetworkService
    ): String {
        // 直播源 .txt 使用浏览器 UA，避免被 Cloudflare 等防护拦截
        val txtHeaders = headers.toMutableMap().apply {
            put("User-Agent", BROWSER_USER_AGENT)
        }.toMap()
        for ((index, url) in urls.withIndex()) {
            log(LogLevel.INFO, "尝试直播源 ${index + 1}/${urls.size}: $url")
            val txt = try {
                val bytes = network.download(url, txtHeaders)
                EncodingUtils.decodeWithFallback(bytes).trim()
            } catch (e: Exception) {
                log(LogLevel.WARN, "直播源 ${index + 1} 请求失败: ${e.message}")
                continue
            }
            if (txt.isEmpty()) {
                log(LogLevel.WARN, "直播源 ${index + 1} 返回为空")
                continue
            }
            // 检测 HTML 响应（Cloudflare 拦截页、404 页等）
            if (looksLikeHtml(txt)) {
                log(LogLevel.WARN, "直播源 ${index + 1} 返回 HTML 页面，跳过")
                continue
            }
            // 验证是否为有效的直播源文本
            val categories = LiveSourceParser.parse(txt)
            val validChannels = categories.sumOf { it.channels.size }
            if (categories.isNotEmpty() && validChannels > 0) {
                log(LogLevel.INFO, "直播源 ${index + 1} 有效：${categories.size} 个分组，$validChannels 个频道")
                return txt
            } else {
                log(LogLevel.WARN, "直播源 ${index + 1} 无有效频道")
            }
        }
        return ""
    }

    /**
     * 检测文本是否为 HTML 页面（而非直播源文本）。
     *
     * Cloudflare 防护、404/403 错误页等均返回 HTML，需排除。
     */
    private fun looksLikeHtml(text: String): Boolean {
        val head = text.take(500).lowercase()
        return head.contains("<!doctype html") ||
            head.contains("<html") ||
            head.contains("<head>") ||
            head.contains("<body") ||
            head.contains("cloudflare") ||
            head.contains("<meta ")
    }

    private fun log(level: LogLevel, message: String) {
        runCatching { ServiceLocator.getDeviceApi().log(level, "LiveSourceManager", message) }
    }

    companion object {
        /** 默认订阅地址（饭太硬） */
        const val DEFAULT_SUBSCRIPTION_URL = "http://www.饭太硬.net/tv"

        /** TVBox 标准 User-Agent（部分订阅源会根据 UA 返回不同内容） */
        private const val TVBOX_USER_AGENT = "okhttp/4.9.3"

        /** 浏览器 User-Agent，用于拉取直播源 .txt（避免被 Cloudflare 等拦截） */
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        /**
         * 内置示例直播源文本。
         *
         * 采用公开的 HLS 测试流（Apple / Mux / Bitmovin），可在模拟器与真机上直接播放，
         * 用于功能演示与远端订阅失败时的回退。用户可在设置中替换为自有订阅。
         */
        internal const val BUILTIN_LIVE_TEXT = """
央视频道,#genre#
CCTV-1 综合,https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8
CCTV-2 财经,https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8
CCTV-3 综艺,https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8
CCTV-5 体育,https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8
CCTV-6 电影,https://test-streams.mux.dev/test_001/stream.m3u8

卫视频道,#genre#
湖南卫视,https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8
浙江卫视,https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8
东方卫视,https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8
北京卫视,https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8
江苏卫视,https://test-streams.mux.dev/test_001/stream.m3u8

测试频道,#genre#
HLS 测试-1,https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8#https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8
HLS 测试-2,https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8
SINTL 电影,https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8
BigBuckBunny,https://test-streams.mux.dev/test_001/stream.m3u8
"""
    }
}
