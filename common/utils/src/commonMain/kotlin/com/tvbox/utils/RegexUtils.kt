package com.tvbox.utils

/**
 * 跨平台正则表达式工具类
 * 提供通用的正则匹配、提取、替换功能
 */
object RegexUtils {

    /**
     * 判断字符串是否匹配正则
     */
    fun matches(input: String, pattern: String): Boolean {
        return Regex(pattern).matches(input)
    }

    /**
     * 判断字符串是否包含匹配正则的子串
     */
    fun contains(input: String, pattern: String): Boolean {
        return Regex(pattern).containsMatchIn(input)
    }

    /**
     * 查找第一个匹配项
     */
    fun findFirst(input: String, pattern: String): MatchResult? {
        return Regex(pattern).find(input)
    }

    /**
     * 查找所有匹配项
     */
    fun findAll(input: String, pattern: String): List<MatchResult> {
        return Regex(pattern).findAll(input).toList()
    }

    /**
     * 提取第一个匹配的分组
     * @param groupIndex 分组索引（0为整个匹配，1为第一个分组）
     */
    fun extractGroup(input: String, pattern: String, groupIndex: Int = 1): String? {
        val match = Regex(pattern).find(input)
        return match?.groups?.get(groupIndex)?.value
    }

    /**
     * 提取所有匹配的分组
     */
    fun extractAllGroups(input: String, pattern: String, groupIndex: Int = 1): List<String> {
        return Regex(pattern).findAll(input).mapNotNull { it.groups[groupIndex]?.value }.toList()
    }

    /**
     * 替换所有匹配项
     */
    fun replaceAll(input: String, pattern: String, replacement: String): String {
        return input.replace(Regex(pattern), replacement)
    }

    /**
     * 替换第一个匹配项
     */
    fun replaceFirst(input: String, pattern: String, replacement: String): String {
        return input.replaceFirst(Regex(pattern), replacement)
    }

    /**
     * 分割字符串
     */
    fun split(input: String, pattern: String): List<String> {
        return input.split(Regex(pattern))
    }

    // ===== 常用正则校验 =====

    /**
     * 是否为有效URL
     */
    fun isValidUrl(input: String): Boolean {
        return matches(input, """^(https?|ftp|file)://[^\s/$.?#].[^\s]*$""")
    }

    /**
     * 是否为有效IP地址
     */
    fun isValidIp(input: String): Boolean {
        return matches(input, """^(\d{1,3}\.){3}\d{1,3}$""")
    }

    /**
     * 是否为有效邮箱
     */
    fun isValidEmail(input: String): Boolean {
        return matches(input, """^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$""")
    }

    /**
     * 从文本中提取所有URL
     */
    fun extractUrls(input: String): List<String> {
        return extractAllGroups(input, """https?://[^\s<>"']+""", 0)
    }

    /**
     * 从文本中提取所有磁力链接
     */
    fun extractMagnetLinks(input: String): List<String> {
        return extractAllGroups(input, """magnet:\?xt=urn:btih:[a-zA-Z0-9]+""", 0)
    }
}
