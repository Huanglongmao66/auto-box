package com.tvbox.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/**
 * 跨平台 JSON 工具类
 * 基于 kotlinx.serialization 实现全平台兼容的 JSON 序列化/反序列化
 */
object JsonUtils {

    /**
     * 全局 JSON 配置（宽松模式，兼容不规范 JSON）
     */
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
        explicitNulls = false
    }

    /**
     * 对象序列化为 JSON 字符串
     */
    fun <T> toJson(value: T): String {
        return json.encodeToString(value)
    }

    /**
     * JSON 字符串反序列化为对象
     */
    inline fun <reified T> fromJson(jsonString: String): T {
        return json.decodeFromString(jsonString)
    }

    /**
     * JSON 字符串反序列化为对象（安全模式，解析失败返回 null）
     */
    inline fun <reified T> fromJsonSafe(jsonString: String): T? {
        return try {
            json.decodeFromString(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 安全解析 JSON 字符串为 JsonElement
     */
    fun parseToJsonElement(jsonString: String): kotlinx.serialization.json.JsonElement? {
        return try {
            json.parseToJsonElement(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 格式化 JSON 字符串（美化输出）
     */
    fun prettyPrint(jsonString: String): String {
        val element = json.parseToJsonElement(jsonString)
        return Json { prettyPrint = true }.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), element)
    }
}
