package com.tvbox.core.repository

import com.tvbox.core.model.MovieSource
import com.tvbox.core.network.NetworkService
import com.tvbox.utils.JsonUtils
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 通用 HTTP 封装：
 * - 自动注入 MovieSource 里的 header / cookie / timeout
 * - 提供 GET/POST + 请求拼接
 * - 为各 SourceClient 共享
 */
internal class HttpFetcher(
    private val networkService: NetworkService
) {

    suspend fun get(
        url: String,
        params: Map<String, String> = emptyMap(),
        source: MovieSource? = null
    ): String {
        val fullUrl = buildUrl(url, params)
        val headers = buildHeaders(source)
        return try {
            networkService.get(fullUrl, headers)
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun post(
        url: String,
        body: String = "",
        params: Map<String, String> = emptyMap(),
        contentType: String = "application/x-www-form-urlencoded",
        source: MovieSource? = null
    ): String {
        val fullUrl = buildUrl(url, params)
        val headers = buildHeaders(source).toMutableMap()
        headers["Content-Type"] = contentType
        return try {
            networkService.post(fullUrl, body, headers)
        } catch (e: Exception) {
            ""
        }
    }

    private fun buildHeaders(source: MovieSource?): Map<String, String> {
        val out = HashMap<String, String>(6)
        // 默认头
        out["User-Agent"] = USER_AGENT
        out["Accept"] = "*/*"
        out["Accept-Language"] = "zh-CN,zh;q=0.9"
        // 从 header JSON 字段解析
        if (source?.header?.isNotBlank() == true) {
            runCatching {
                val obj = JsonUtils.parseToJsonElement(source.header) as? JsonObject
                obj?.forEach { (k, v) ->
                    val s = v.jsonPrimitive.contentOrNull
                    if (s != null) out[k] = s
                }
            }
        }
        // Cookie
        if (source?.cookie?.isNotBlank() == true) {
            out["Cookie"] = source.cookie
        }
        return out
    }

    private fun buildUrl(base: String, params: Map<String, String>): String {
        val cleanBase = base.trimEnd('/', '?', '&')
        if (params.isEmpty()) return cleanBase
        val hasQuery = cleanBase.contains('?')
        val sep = if (hasQuery) "&" else "?"
        val qs = params.entries.joinToString("&") { (k, v) ->
            k.encodeUrl() + "=" + v.encodeUrl()
        }
        return cleanBase + sep + qs
    }

    private fun String.encodeUrl(): String {
        val sb = StringBuilder()
        val hex = "0123456789ABCDEF".toCharArray()
        for (c in this) {
            when (c) {
                in 'A'..'Z', in 'a'..'z', in '0'..'9',
                '-', '_', '.', '~' -> sb.append(c)
                else -> {
                    val v = c.code and 0xFF
                    sb.append('%')
                    sb.append(hex[v ushr 4])
                    sb.append(hex[v and 0x0F])
                }
            }
        }
        return sb.toString()
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    }
}
