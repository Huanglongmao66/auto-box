package com.tvbox.core.source

import com.tvbox.core.di.ServiceLocator
import com.tvbox.core.model.LiveConfig
import com.tvbox.core.model.MovieSource
import com.tvbox.core.model.UserLiveSource
import com.tvbox.core.model.UserVodSource
import com.tvbox.deviceapi.LogLevel
import com.tvbox.utils.EncodingUtils
import com.tvbox.utils.JsonUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * API 订阅解析器
 *
 * 负责从 TVBox 标准订阅地址（API地址）下载并解析：
 * 1. sites 数组 → 点播订阅源列表 (UserVodSource)
 * 2. lives 数组 → 直播订阅源列表 (UserLiveSource)
 *
 * 支持格式：
 * - 标准 JSON: { "sites": [...], "lives": [...] }
 * - 图片伪装配置：JPEG 图片末尾嵌入 Base64 JSON
 * - 多仓格式: { "urls": [{"url": "...", "name": "..."}] } → 解析第一个有效子仓
 */
class ApiSubscriptionParser {

    /**
     * 解析结果
     */
    data class ParseResult(
        val vodSources: List<UserVodSource> = emptyList(),
        val liveSources: List<UserLiveSource> = emptyList(),
        val error: String? = null
    ) {
        val isSuccess: Boolean get() = error == null
    }

    /**
     * 从指定 URL 下载并解析订阅内容
     *
     * @param apiUrl API 订阅地址
     * @return 解析结果（点播源+直播源），失败时 error 字段包含错误信息
     */
    suspend fun parse(apiUrl: String): ParseResult = withContext(Dispatchers.Default) {
        if (apiUrl.isBlank()) {
            return@withContext ParseResult(error = "API 地址为空")
        }

        log(LogLevel.INFO, "开始解析 API 订阅: $apiUrl")

        // 1. 下载原始内容
        val rawBytes = try {
            val network = ServiceLocator.getNetworkService()
            val headers = mapOf(
                "User-Agent" to TVBOX_USER_AGENT,
                "Accept" to "*/*"
            )
            network.download(apiUrl, headers)
        } catch (e: Exception) {
            log(LogLevel.ERROR, "下载 API 订阅失败: ${e.message}")
            return@withContext ParseResult(error = "下载失败: ${e.message ?: "未知错误"}")
        }

        if (rawBytes.isEmpty()) {
            return@withContext ParseResult(error = "API 返回内容为空")
        }

        // 2. 解码内容（支持图片伪装、普通文本）
        val rawText = decodeContent(rawBytes)
        if (rawText.isBlank()) {
            return@withContext ParseResult(error = "解码后内容为空")
        }
        log(LogLevel.DEBUG, "订阅内容前300字符: ${rawText.take(300)}")

        // 3. 解析 JSON
        val element = JsonUtils.parseToJsonElement(rawText)
            ?: return@withContext ParseResult(error = "JSON 解析失败")

        // 4. 尝试标准格式 { sites, lives }
        val standard = parseStandardFormat(element)
        if (standard.vodSources.isNotEmpty() || standard.liveSources.isNotEmpty()) {
            log(LogLevel.INFO, "标准格式解析成功：点播源 ${standard.vodSources.size} 个，直播源 ${standard.liveSources.size} 个")
            return@withContext standard
        }

        // 5. 尝试多仓格式 { urls: [...] } → 取第一个有效子仓
        val multiStore = parseMultiStoreFormat(element)
        if (multiStore != null) {
            log(LogLevel.INFO, "多仓格式解析成功：点播源 ${multiStore.vodSources.size} 个，直播源 ${multiStore.liveSources.size} 个")
            return@withContext multiStore
        }

        // 6. 全部失败
        return@withContext ParseResult(error = "未识别的订阅格式，既无 sites/lives 也无 urls 字段")
    }

    // ============ 内部：内容解码 ============

    private fun decodeContent(bytes: ByteArray): String {
        // JPEG 图片（FF D8 开头）
        if (bytes.size >= 2 &&
            (bytes[0].toInt() and 0xFF) == 0xFF &&
            (bytes[1].toInt() and 0xFF) == 0xD8
        ) {
            log(LogLevel.INFO, "检测到 JPEG 伪装，提取嵌入配置")
            return extractConfigFromJpeg(bytes)
        }
        // 普通文本，自动检测编码
        return EncodingUtils.decodeWithFallback(bytes).trim()
    }

    private fun extractConfigFromJpeg(bytes: ByteArray): String {
        // 找最后一个 JPEG 结束标记 FF D9
        var jpegEnd = -1
        for (i in bytes.size - 2 downTo 0) {
            if ((bytes[i].toInt() and 0xFF) == 0xFF &&
                (bytes[i + 1].toInt() and 0xFF) == 0xD9
            ) {
                jpegEnd = i + 2
                break
            }
        }
        if (jpegEnd < 0 || jpegEnd >= bytes.size) {
            log(LogLevel.WARN, "未找到 JPEG 结束标记")
            return ""
        }
        val embeddedBytes = bytes.copyOfRange(jpegEnd, bytes.size)
        val embeddedText = EncodingUtils.decodeWithFallback(embeddedBytes).trim()
        if (embeddedText.isEmpty()) return ""

        // 格式: 前缀**Base64编码的JSON
        val parts = embeddedText.split("**")
        val base64Part = if (parts.size >= 2) parts.last() else embeddedText
        return decodeBase64(base64Part)
    }

    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    private fun decodeBase64(base64: String): String {
        return try {
            val cleaned = base64.filter { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }
            val decoded = kotlin.io.encoding.Base64.decode(cleaned)
            String(decoded, Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    // ============ 内部：标准格式解析 ============

    private fun parseStandardFormat(element: JsonElement): ParseResult {
        val obj = element as? JsonObject ?: return ParseResult()

        // 全局 jar：TVBoxOS 订阅顶层 spider 字段，作为所有未单独指定 jar 的站点的默认爬虫包
        val globalJar = safeString(obj, "spider", "").ifEmpty { safeString(obj, "jar", "") }
        log(LogLevel.INFO, "全局 jar (spider): ${globalJar.take(80)}")

        val vodSources = (obj["sites"] as? JsonArray)?.mapIndexedNotNull { idx, el ->
            decodeVodSource(el, idx, globalJar)
        }.orEmpty()

        val liveSources = (obj["lives"] as? JsonArray)?.mapIndexedNotNull { idx, el ->
            decodeLiveSource(el, idx)
        }.orEmpty()

        return ParseResult(vodSources = vodSources, liveSources = liveSources)
    }

    private fun decodeVodSource(element: JsonElement, index: Int, globalJar: String = ""): UserVodSource? {
        return try {
            val obj = element as? JsonObject ?: return null
            val key = (obj["key"] as? JsonPrimitive)?.contentOrNull
                ?: "vod_api_${index + 1}"
            val name = (obj["name"] as? JsonPrimitive)?.contentOrNull
                ?: "点播源${index + 1}"
            val api = (obj["api"] as? JsonPrimitive)?.contentOrNull ?: return null
            // 保留所有 type 的源（兼容 TVBoxOS 标准 sites 数组）
            // - type=0: XML 源
            // - type=1: JSON 源（MacCMS 标准接口）
            // - type=3: Spider/CSP 源（Jar/JS/Py 爬虫，api 可能为 csp_xxx / .js / .py）
            // - type=4: 新协议（带 ext 过滤参数的 JSON 源）
            val type = (obj["type"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 1
            val searchable = safeInt(obj, "searchable", 1)
            val quickSearch = safeInt(obj, "quickSearch", 1)
            val filterable = safeInt(obj, "filterable", 1)
            val playerUrl = safeString(obj, "playUrl", "")
            val ext = safeString(obj, "ext", "")
            val siteJar = safeString(obj, "jar", "")
            // 站点未单独指定 jar 时，使用全局 jar（支持 TVBoxOS 订阅顶层 jar 字段）
            val jar = siteJar.ifEmpty { globalJar }
            val playerType = safeInt(obj, "playerType", -1)
            val timeout = safeInt(obj, "timeout", 0)
            val clickSelector = safeString(obj, "click", "")
            val style = safeString(obj, "style", "")
            val categories = safeStringList(obj, "categories")
            log(LogLevel.DEBUG, "解析点播源: key=$key, name=$name, type=$type, api=$api")
            UserVodSource(
                key = key,
                name = name,
                api = api,
                enabled = true,
                type = type,
                searchable = searchable,
                quickSearch = quickSearch,
                filterable = filterable,
                playerUrl = playerUrl,
                ext = ext,
                jar = jar,
                playerType = playerType,
                timeout = timeout,
                clickSelector = clickSelector,
                style = style,
                categories = categories
            )
        } catch (e: Exception) {
            log(LogLevel.WARN, "解析点播源失败: ${e.message}")
            null
        }
    }

    // ============ 内部：JsonObject 安全取值工具 ============

    private fun safeString(obj: JsonObject, key: String, default: String = ""): String {
        val prim = obj[key] as? JsonPrimitive ?: return default
        return prim.contentOrNull?.takeIf { it.isNotBlank() } ?: default
    }

    private fun safeInt(obj: JsonObject, key: String, default: Int): Int {
        val prim = obj[key] as? JsonPrimitive ?: return default
        return prim.contentOrNull?.toIntOrNull() ?: default
    }

    private fun safeStringList(obj: JsonObject, key: String): List<String> {
        val el = obj[key] ?: return emptyList()
        // 字符串：按逗号分隔
        if (el is JsonPrimitive) {
            val s = el.contentOrNull ?: return emptyList()
            return s.split(",", ";", "|").map { it.trim() }.filter { it.isNotBlank() }
        }
        // 数组：逐项取字符串
        if (el is JsonArray) {
            return el.mapNotNull { item ->
                (item as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            }
        }
        return emptyList()
    }

    private fun decodeLiveSource(element: JsonElement, index: Int): UserLiveSource? {
        return try {
            // 先用 LiveConfig 反序列化（标准格式）
            val liveConfig = runCatching {
                JsonUtils.json.decodeFromJsonElement(LiveConfig.serializer(), element)
            }.getOrNull()
            if (liveConfig != null && liveConfig.url.isNotBlank()) {
                return UserLiveSource(
                    key = "live_api_${index + 1}_" + liveConfig.name.filter { it.isLetterOrDigit() }.take(8),
                    name = liveConfig.name.ifBlank { "直播源${index + 1}" },
                    url = liveConfig.url,
                    enabled = true
                )
            }
            // 退而求其次：手动解析 key/name/url
            val obj = element as? JsonObject ?: return null
            val url = (obj["url"] as? JsonPrimitive)?.contentOrNull ?: return null
            val name = (obj["name"] as? JsonPrimitive)?.contentOrNull ?: "直播源${index + 1}"
            UserLiveSource(
                key = "live_api_${index + 1}_" + name.filter { it.isLetterOrDigit() }.take(8),
                name = name,
                url = url,
                enabled = true
            )
        } catch (e: Exception) {
            log(LogLevel.WARN, "解析直播源失败: ${e.message}")
            null
        }
    }

    // ============ 内部：多仓格式解析 ============

    private suspend fun parseMultiStoreFormat(element: JsonElement): ParseResult? {
        val obj = element as? JsonObject ?: return null
        val urlsArray = obj["urls"] as? JsonArray ?: return null

        for ((index, item) in urlsArray.withIndex()) {
            val subObj = item as? JsonObject ?: continue
            val subUrl = (subObj["url"] as? JsonPrimitive)?.contentOrNull ?: continue
            val subName = (subObj["name"] as? JsonPrimitive)?.contentOrNull ?: "仓库${index + 1}"
            log(LogLevel.INFO, "尝试多仓子项[$index]: $subName → $subUrl")

            val subResult = try {
                parse(subUrl)
            } catch (e: Exception) {
                log(LogLevel.WARN, "子仓解析异常: ${e.message}")
                continue
            }
            if (subResult.vodSources.isNotEmpty() || subResult.liveSources.isNotEmpty()) {
                return subResult
            }
        }
        return null
    }

    // ============ 工具 ============

    private fun log(level: LogLevel, message: String) {
        runCatching { ServiceLocator.getDeviceApi().log(level, "ApiSubscriptionParser", message) }
    }

    companion object {
        private const val TVBOX_USER_AGENT = "okhttp/4.9.3"
    }
}
