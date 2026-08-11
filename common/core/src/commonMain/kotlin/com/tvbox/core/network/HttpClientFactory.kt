package com.tvbox.core.network

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * 跨平台 HTTP 客户端工厂
 * 基于 Ktor 实现全平台兼容的网络请求
 */
class HttpClientFactory(
    private val engineFactory: HttpClientEngineFactory<*>,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }
) {
    /**
     * 创建配置好的 HTTP 客户端
     */
    fun createClient(
        timeout: Int = 15000,
        proxyConfig: ProxyConfig? = null
    ): HttpClient {
        return HttpClient(engineFactory) {
            install(ContentNegotiation) {
                json(this@HttpClientFactory.json)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = timeout.toLong()
                connectTimeoutMillis = timeout.toLong()
                socketTimeoutMillis = timeout.toLong()
            }
            install(UserAgent) {
                agent = USER_AGENT
            }
            followRedirects = true
            expectSuccess = false
            if (proxyConfig != null && proxyConfig.enabled) {
            }
        }
    }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (TVBox-Multiplatform/1.0)"
    }
}

/**
 * 代理配置
 */
data class ProxyConfig(
    val enabled: Boolean = false,
    val type: String = "http",
    val host: String = "",
    val port: Int = 0,
    val username: String = "",
    val password: String = ""
)

/**
 * 网络请求封装
 * 提供统一的 GET/POST/下载 请求方法
 */
class NetworkService(private val httpClient: HttpClient) {

    /**
     * GET 请求
     * @param url 请求地址
     * @param headers 请求头
     * @return 响应文本
     */
    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): String {
        return httpClient.get(url) {
            headers.forEach { (key, value) ->
                header(key, value)
            }
        }.bodyAsText()
    }

    /**
     * POST 请求
     * @param url 请求地址
     * @param body 请求体
     * @param headers 请求头
     * @return 响应文本
     */
    suspend fun post(
        url: String,
        body: String,
        headers: Map<String, String> = emptyMap()
    ): String {
        return httpClient.post(url) {
            setBody(body)
            headers.forEach { (key, value) ->
                header(key, value)
            }
        }.bodyAsText()
    }

    /**
     * POST JSON 请求
     */
    suspend fun postJson(
        url: String,
        jsonBody: String,
        headers: Map<String, String> = emptyMap()
    ): String {
        return httpClient.post(url) {
            contentType(ContentType.Application.Json)
            setBody(jsonBody)
            headers.forEach { (key, value) ->
                header(key, value)
            }
        }.bodyAsText()
    }

    /**
     * 下载文件（返回字节数组）
     */
    suspend fun download(url: String, headers: Map<String, String> = emptyMap()): ByteArray {
        val response = httpClient.get(url) {
            headers.forEach { (key, value) ->
                header(key, value)
            }
        }
        if (!response.status.isSuccess()) {
            throw Exception("HTTP ${response.status.value}: ${response.status.description}")
        }
        return response.readBytes()
    }

    /**
     * 获取响应头
     */
    suspend fun getHeaders(url: String, headers: Map<String, String> = emptyMap()): Map<String, String> {
        val response = httpClient.get(url) {
            headers.forEach { (key, value) ->
                header(key, value)
            }
        }
        return response.headers.entries().associate { it.key to it.value.joinToString(", ") }
    }
}
