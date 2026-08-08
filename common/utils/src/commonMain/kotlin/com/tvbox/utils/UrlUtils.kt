package com.tvbox.utils

/**
 * 跨平台 URL 工具类
 * 提供 URL 解析、编码、参数处理等功能
 */
object UrlUtils {

    /**
     * URL 编码
     */
    fun encode(input: String): String {
        return buildString {
            for (char in input) {
                when {
                    char.isLetterOrDigit() || char in "-_.~" -> append(char)
                    char == ' ' -> append("%20")
                    else -> {
                        val bytes = char.toString().encodeToByteArray()
                        for (byte in bytes) {
                            append('%')
                            append(((byte.toInt() ushr 4) and 0xF).toString(16).uppercase())
                            append((byte.toInt() and 0xF).toString(16).uppercase())
                        }
                    }
                }
            }
        }
    }

    /**
     * URL 解码
     */
    fun decode(input: String): String {
        return buildString {
            var i = 0
            while (i < input.length) {
                when (input[i]) {
                    '%' -> {
                        if (i + 2 < input.length) {
                            val hex = input.substring(i + 1, i + 3)
                            val byte = hex.toInt(16).toByte()
                            append(byte.toInt().toChar())
                            i += 3
                        } else {
                            append(input[i])
                            i++
                        }
                    }
                    '+' -> {
                        append(' ')
                        i++
                    }
                    else -> {
                        append(input[i])
                        i++
                    }
                }
            }
        }
    }

    /**
     * 解析 URL 中的查询参数
     * @return 参数键值对 Map
     */
    fun parseQueryParams(url: String): Map<String, String> {
        val queryStart = url.indexOf('?')
        if (queryStart < 0 || queryStart == url.length - 1) return emptyMap()

        val query = url.substring(queryStart + 1)
        val fragmentIndex = query.indexOf('#')
        val cleanQuery = if (fragmentIndex >= 0) query.substring(0, fragmentIndex) else query

        return cleanQuery.split("&")
            .filter { it.isNotEmpty() }
            .associate { pair ->
                val eqIndex = pair.indexOf('=')
                if (eqIndex >= 0) {
                    decode(pair.substring(0, eqIndex)) to decode(pair.substring(eqIndex + 1))
                } else {
                    decode(pair) to ""
                }
            }
    }

    /**
     * 构建 URL 查询参数字符串
     */
    fun buildQueryParams(params: Map<String, String>): String {
        return params.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
    }

    /**
     * 获取 URL 的域名
     */
    fun getDomain(url: String): String? {
        val regex = Regex("""^(https?://)([^/:]+)""")
        return regex.find(url)?.groups?.get(2)?.value
    }

    /**
     * 获取 URL 的路径（不含域名和查询参数）
     */
    fun getPath(url: String): String {
        val withoutQuery = url.substringBefore('?')
        val protocolIndex = withoutQuery.indexOf("://")
        if (protocolIndex < 0) return withoutQuery
        val pathStart = withoutQuery.indexOf('/', protocolIndex + 3)
        return if (pathStart >= 0) withoutQuery.substring(pathStart) else "/"
    }

    /**
     * 获取 URL 的文件扩展名
     */
    fun getExtension(url: String): String? {
        val path = getPath(url)
        val lastDot = path.lastIndexOf('.')
        val lastSlash = path.lastIndexOf('/')
        return if (lastDot > lastSlash && lastDot < path.length - 1) {
            path.substring(lastDot + 1).lowercase()
        } else {
            null
        }
    }

    /**
     * 在 URL 上追加查询参数
     */
    fun appendQueryParam(url: String, key: String, value: String): String {
        val separator = if (url.contains('?')) '&' else '?'
        return "$url$separator${encode(key)}=${encode(value)}"
    }

    /**
     * 获取 URL 的协议
     */
    fun getProtocol(url: String): String? {
        val index = url.indexOf("://")
        return if (index > 0) url.substring(0, index) else null
    }
}
