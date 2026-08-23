package com.tvbox.utils

/**
 * 跨平台轻量级 XML 解析器
 *
 * 基于 Kotlin 纯实现，不依赖平台 XML 库（DOM / SaxParser 等），
 * 兼容 Android / JVM / JS / LinuxX64 全平台。
 *
 * 支持：
 * - 嵌套标签、自闭合标签
 * - 属性解析（含引号 / 无引号）
 * - CDATA 段
 * - 注释跳过
 * - 文本节点提取
 *
 * 限制：不校验 XML 合法性，假设输入为合法 XML 或 HTML 片段。
 */
object XmlUtils {

    // ============ 公共 API ============

    /**
     * 将 XML 字符串解析为 [XmlDocument]
     */
    fun parse(xml: String): XmlDocument {
        val trimmed = xml.trim()
        if (trimmed.isEmpty()) return XmlDocument(emptyList())
        val parser = XmlParser(trimmed)
        return parser.parseDocument()
    }

    /**
     * 在 XML 字符串中查找所有指定标签名的节点
     */
    fun getElementsByTagName(xml: String, tagName: String): List<XmlNode> {
        return parse(xml).getElementsByTagName(tagName)
    }

    /**
     * 在 XML 字符串中查找第一个指定标签名的节点
     */
    fun getElementsByTagName(xml: String, tagName: String, first: Boolean): XmlNode? {
        return parse(xml).getElementsByTagName(tagName).firstOrNull()
    }

    /**
     * 安全转义 XML 特殊字符
     */
    fun escapeXml(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) {
            when (c) {
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '&' -> sb.append("&amp;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /**
     * 反转义 XML 实体
     */
    fun unescapeXml(text: String): String {
        return text
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
    }
}

// ============ XML 文档模型 ============

/**
 * XML 文档根，包含顶层子节点
 */
class XmlDocument(val children: List<XmlNode>) {

    /**
     * 递归查找所有指定标签名的节点
     */
    fun getElementsByTagName(tagName: String): List<XmlNode> {
        val result = mutableListOf<XmlNode>()
        for (child in children) {
            collectByTag(child, tagName, result)
        }
        return result
    }

    private fun collectByTag(node: XmlNode, tagName: String, result: MutableList<XmlNode>) {
        if (node.name.equals(tagName, ignoreCase = true)) {
            result.add(node)
        }
        for (child in node.children) {
            collectByTag(child, tagName, result)
        }
    }

    /**
     * 查找第一个指定标签名的节点
     */
    fun getElementsByTagNameFirst(tagName: String): XmlNode? {
        return getElementsByTagName(tagName).firstOrNull()
    }

    override fun toString(): String {
        return "XmlDocument(${children.size} roots)"
    }
}

/**
 * XML 节点（元素）
 *
 * @property name 标签名
 * @property attributes 属性映射
 * @property text 纯文本内容（已反转义）
 * @property children 子节点
 */
class XmlNode(
    val name: String,
    val attributes: Map<String, String> = emptyMap(),
    val text: String = "",
    val children: List<XmlNode> = emptyList()
) {
    /**
     * 获取属性值（忽略大小写）
     */
    fun getAttribute(key: String): String? {
        val exact = attributes[key]
        if (exact != null) return exact
        for ((k, v) in attributes) {
            if (k.equals(key, ignoreCase = true)) return v
        }
        return null
    }

    /**
     * 获取指定标签名的直接子节点
     */
    fun getElementsByTagName(tagName: String): List<XmlNode> {
        val result = mutableListOf<XmlNode>()
        for (child in children) {
            collectByTag(child, tagName, result)
        }
        return result
    }

    private fun collectByTag(node: XmlNode, tagName: String, result: MutableList<XmlNode>) {
        if (node.name.equals(tagName, ignoreCase = true)) {
            result.add(node)
        }
        for (child in node.children) {
            collectByTag(child, tagName, result)
        }
    }

    /**
     * 获取第一个直接子节点的文本
     */
    fun getChildText(tagName: String): String? {
        return getElementsByTagName(tagName).firstOrNull()?.text?.ifBlank { null }
    }

    override fun toString(): String {
        return "XmlNode(name='$name', attrs=$attributes, textLen=${text.length}, children=${children.size})"
    }
}

// ============ 内部解析器实现 ============

/**
 * 递归下降式 XML 解析器
 */
private class XmlParser(private val source: String) {
    private var pos = 0

    fun parseDocument(): XmlDocument {
        val children = mutableListOf<XmlNode>()
        skipProlog()
        while (pos < source.length) {
            skipWhitespace()
            if (pos >= source.length) break
            if (source[pos] == '<') {
                val node = parseElement()
                if (node != null) children.add(node)
            } else {
                pos++ // 跳过裸文本
            }
        }
        return XmlDocument(children)
    }

    private fun skipProlog() {
        // 跳过 <?xml ... ?>
        skipWhitespace()
        if (pos < source.length - 1 && source[pos] == '<' && source[pos + 1] == '?') {
            val end = source.indexOf("?>", pos)
            if (end >= 0) pos = end + 2
        }
        // 跳过 <!DOCTYPE ... >
        skipWhitespace()
        if (pos < source.length - 1 && source[pos] == '<' && source[pos + 1] == '!') {
            val end = source.indexOf('>', pos)
            if (end >= 0) pos = end + 1
        }
    }

    private fun parseElement(): XmlNode? {
        if (pos >= source.length || source[pos] != '<') return null
        pos++ // 跳过 '<'

        // 注释 <!-- -->
        if (pos < source.length - 1 && source[pos] == '!' && source[pos + 1] == '-') {
            val end = source.indexOf("-->", pos)
            if (end >= 0) pos = end + 3
            return null
        }

        // CDATA <![CDATA[ ... ]]>
        if (pos < source.length - 1 && source[pos] == '!' && source[pos + 1] == '[') {
            val end = source.indexOf("]]>", pos)
            if (end >= 0) pos = end + 3
            return null
        }

        // 闭合标签 </name>
        if (pos < source.length && source[pos] == '/') {
            val end = source.indexOf('>', pos)
            if (end >= 0) pos = end + 1
            return null
        }

        // 解析标签名
        val name = readTagName()
        if (name.isEmpty()) return null

        // 解析属性
        val attrs = mutableMapOf<String, String>()
        while (pos < source.length) {
            skipWhitespace()
            if (pos >= source.length) break
            when {
                source[pos] == '/' -> {
                    pos++
                    if (pos < source.length && source[pos] == '>') pos++
                    return XmlNode(name, attrs)
                }
                source[pos] == '>' -> {
                    pos++
                    break
                }
                else -> {
                    val attr = parseAttribute()
                    if (attr != null) attrs[attr.first] = attr.second
                }
            }
        }

        // 解析子内容
        val children = mutableListOf<XmlNode>()
        val textBuilder = StringBuilder()

        while (pos < source.length) {
            if (pos < source.length - 1 && source[pos] == '<' && source[pos + 1] == '/') {
                // 闭合标签
                val end = source.indexOf('>', pos)
                pos = if (end >= 0) end + 1 else source.length
                break
            }
            if (pos < source.length && source[pos] == '<') {
                if (pos < source.length - 1 && source[pos + 1] == '!') {
                    if (pos < source.length - 8 && source.substring(pos, minOf(pos + 9, source.length)).startsWith("<![CDATA[")) {
                        val cdataEnd = source.indexOf("]]>", pos + 9)
                        if (cdataEnd >= 0) {
                            textBuilder.append(source, pos + 9, cdataEnd)
                            pos = cdataEnd + 3
                        } else {
                            textBuilder.append(source, pos + 9, source.length)
                            pos = source.length
                        }
                        continue
                    }
                }
                val child = parseElement()
                if (child != null) children.add(child)
            } else {
                val nextLt = source.indexOf('<', pos)
                if (nextLt < 0) {
                    textBuilder.append(source, pos, source.length)
                    pos = source.length
                } else {
                    textBuilder.append(source, pos, nextLt)
                    pos = nextLt
                }
            }
        }

        val text = XmlUtils.unescapeXml(textBuilder.toString().trim())
        return XmlNode(name, attrs, text, children)
    }

    private fun readTagName(): String {
        val start = pos
        while (pos < source.length) {
            val c = source[pos]
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '>' || c == '/' || c == '<') break
            pos++
        }
        return source.substring(start, pos)
    }

    private fun parseAttribute(): Pair<String, String>? {
        val keyStart = pos
        while (pos < source.length) {
            val c = source[pos]
            if (c == '=' || c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '>' || c == '/') break
            pos++
        }
        if (pos >= source.length || source[pos] != '=') {
            // 属性无值（HTML 风格），跳过
            if (pos > keyStart) return keyStart.let { Pair(source.substring(it, pos), "") }
            pos++
            return null
        }
        val key = source.substring(keyStart, pos)
        pos++ // 跳过 '='

        skipWhitespace()
        if (pos >= source.length) return Pair(key, "")

        val quote = source[pos]
        if (quote == '"' || quote == '\'') {
            pos++
            val valStart = pos
            while (pos < source.length && source[pos] != quote) pos++
            val value = XmlUtils.unescapeXml(source.substring(valStart, pos))
            if (pos < source.length) pos++
            return Pair(key, value)
        } else {
            // 无引号属性值
            val valStart = pos
            while (pos < source.length) {
                val c = source[pos]
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '>' || c == '/') break
                pos++
            }
            return Pair(key, source.substring(valStart, pos))
        }
    }

    private fun skipWhitespace() {
        while (pos < source.length) {
            val c = source[pos]
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') break
            pos++
        }
    }
}
