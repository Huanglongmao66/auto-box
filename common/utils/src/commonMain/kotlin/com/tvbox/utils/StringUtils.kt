package com.tvbox.utils

/**
 * 跨平台字符串工具类
 * 提供通用的字符串处理功能
 */
object StringUtils {

    /**
     * 判断字符串是否为空或空白
     */
    fun isBlank(value: String?): Boolean {
        return value.isNullOrBlank()
    }

    /**
     * 判断字符串是否非空且非空白
     */
    fun isNotBlank(value: String?): Boolean {
        return !isBlank(value)
    }

    /**
     * 空字符串安全处理
     */
    fun defaultValue(value: String?, default: String): String {
        return if (isBlank(value)) default else value!!
    }

    /**
     * 截取字符串（安全模式，超出范围不报错）
     */
    fun safeSubstring(value: String, start: Int, end: Int = value.length): String {
        val safeStart = start.coerceIn(0, value.length)
        val safeEnd = end.coerceIn(safeStart, value.length)
        return value.substring(safeStart, safeEnd)
    }

    /**
     * 首字母大写
     */
    fun capitalize(value: String): String {
        if (value.isEmpty()) return value
        return value[0].uppercase() + value.substring(1)
    }

    /**
     * 首字母小写
     */
    fun decapitalize(value: String): String {
        if (value.isEmpty()) return value
        return value[0].lowercase() + value.substring(1)
    }

    /**
     * 驼峰转下划线
     */
    fun camelToSnake(value: String): String {
        return value.replace(Regex("([a-z])([A-Z])")) { "${it.groupValues[1]}_${it.groupValues[2]}" }
            .lowercase()
    }

    /**
     * 下划线转驼峰
     */
    fun snakeToCamel(value: String): String {
        return value.split("_").mapIndexed { index, word ->
            if (index == 0) word.lowercase() else capitalize(word.lowercase())
        }.joinToString("")
    }

    /**
     * 重复字符串
     */
    fun repeat(value: String, count: Int): String {
        if (count <= 0) return ""
        return buildString {
            repeat(count) { append(value) }
        }
    }

    /**
     * 左填充字符串到指定长度
     */
    fun padLeft(value: String, length: Int, padChar: Char = ' '): String {
        return if (value.length >= length) value else repeat(padChar.toString(), length - value.length) + value
    }

    /**
     * 右填充字符串到指定长度
     */
    fun padRight(value: String, length: Int, padChar: Char = ' '): String {
        return if (value.length >= length) value else value + repeat(padChar.toString(), length - value.length)
    }

    /**
     * 移除字符串前缀（如果存在）
     */
    fun removePrefix(value: String, prefix: String): String {
        return if (value.startsWith(prefix)) value.substring(prefix.length) else value
    }

    /**
     * 移除字符串后缀（如果存在）
     */
    fun removeSuffix(value: String, suffix: String): String {
        return if (value.endsWith(suffix)) value.substring(0, value.length - suffix.length) else value
    }

    /**
     * 统计字符串中某个子串出现的次数
     */
    fun countOccurrences(value: String, substring: String): Int {
        if (substring.isEmpty()) return 0
        var count = 0
        var index = 0
        while (true) {
            index = value.indexOf(substring, index)
            if (index < 0) break
            count++
            index += substring.length
        }
        return count
    }

    /**
     * 反转字符串
     */
    fun reverse(value: String): String {
        return value.reversed()
    }

    /**
     * 移除所有空白字符
     */
    fun removeAllWhitespace(value: String): String {
        return value.filter { !it.isWhitespace() }
    }

    /**
     * 压缩连续空白为单个空格
     */
    fun normalizeWhitespace(value: String): String {
        return value.trim().replace(Regex("\\s+"), " ")
    }

    /**
     * 生成随机字符串
     */
    fun randomString(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return buildString {
            repeat(length) {
                append(chars[(kotlin.random.Random.nextDouble() * chars.length).toInt()])
            }
        }
    }
}
