package com.tvbox.core.repository

/**
 * 非常轻量的 HTML CSS 选择器实现（纯字符串，不依赖 Jsoup）
 *
 * 仅覆盖 TVBox XPath 源规则里常见的 CSS 子集：
 *   - tag 选择器: div, a, li
 *   - class 选择器: .class-name
 *   - id 选择器: #id
 *   - 属性存在: [attr]
 *   - 属性精确匹配: [attr=value] / [attr="value"]
 *   - 后代组合: div.vodlist li a
 *   - 多节点并列: 如果存在多组匹配，按出现顺序返回
 *   - selectAll: 返回所有匹配节点的 HTML 片段（含自身 tag）
 *   - selectFirst: 返回第一个匹配节点的 HTML 片段
 */
internal object HtmlSelector {

    fun selectAll(html: String, selector: String): List<String> {
        if (html.isBlank() || selector.isBlank()) return emptyList()
        val parts = selector.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return emptyList()
        return findAll(html, parts, 0)
    }

    fun selectFirst(html: String, selector: String): String? = selectAll(html, selector).firstOrNull()

    /**
     * 组合选择器 parts = [".vodlist", "li", "a"]
     * 递归：先匹配第一个选择器得到所有候选节点，从候选的 innerHTML 再匹配剩下部分。
     */
    private fun findAll(html: String, parts: List<String>, depth: Int): List<String> {
        if (depth >= parts.size) return emptyList()
        val part = parts[depth]
        val matches = matchSingle(html, part)
        if (depth == parts.size - 1) {
            return matches
        }
        val out = mutableListOf<String>()
        for (m in matches) {
            val inner = innerHtmlOf(m)
            // 取 inner，找下一层
            val nested = findAll(inner, parts, depth + 1)
            out.addAll(nested)
        }
        return out
    }

    /**
     * 匹配单个选择器（不含后代空格）。返回所有匹配节点的 outer HTML。
     */
    private fun matchSingle(html: String, sel: String): List<String> {
        val (tag, clazz, id, attrs) = parseSel(sel)
        val out = mutableListOf<String>()
        var i = 0
        while (i < html.length) {
            // 找下一个 tag 起点
            val lt = html.findTagOpenStart(i) ?: break
            i = lt + 1
            // 跳过注释、doctype、script/style 头（简单处理）
            if (html.startsWith("!--", i)) {
                val end = html.indexOf("-->", i + 3)
                i = if (end < 0) html.length else end + 3
                continue
            }
            if (html.startsWith("![CDATA[", i)) {
                val end = html.indexOf("]]>", i + 8)
                i = if (end < 0) html.length else end + 3
                continue
            }
            if (html.startsWith("!DOCTYPE", i, true) || html.startsWith("?xml", i, true)) {
                val end = html.indexOf('>', i)
                i = if (end < 0) html.length else end + 1
                continue
            }
            // 解析 tagName
            val (tagName, tagEnd) = readTagName(html, i)
            if (tagName == null) { i = tagEnd; continue }
            val tagNameLc = tagName.lowercase()
            // script/style 自闭合前跳过
            if (tagNameLc == "script" || tagNameLc == "style") {
                val close = findCloseTag(html, tagNameLc, tagEnd)
                i = if (close < 0) html.length else close
                continue
            }
            // 解析 attr 片段（从 tagEnd 到 '>' 或 '/>'）
            val closeInfo = findAttrsEnd(html, tagEnd)
            val attrStr = html.substring(tagEnd, closeInfo.end)
            val selfClose = closeInfo.selfClose
            // class/id/attr 匹配
            var matchesTag = true
            if (tag != null) matchesTag = tagNameLc == tag
            if (matchesTag && clazz != null) {
                val cls = attrGet(attrStr, "class") ?: ""
                matchesTag = cls.split(" ").contains(clazz)
            }
            if (matchesTag && id != null) {
                matchesTag = (attrGet(attrStr, "id") == id)
            }
            if (matchesTag && attrs.isNotEmpty()) {
                for ((k, v) in attrs) {
                    val real = attrGet(attrStr, k)
                    matchesTag = if (v == null) (real != null) else (real == v)
                    if (!matchesTag) break
                }
            }

            // 取 outer html
            if (matchesTag) {
                val outerStart = lt
                val outerEnd = if (selfClose) closeInfo.end + 1 else {
                    val closeTag = findCloseTag(html, tagNameLc, closeInfo.end + 1)
                    if (closeTag < 0) closeInfo.end + 1 else closeTag
                }
                if (outerEnd in (outerStart + 1)..html.length) {
                    out.add(html.substring(outerStart, outerEnd))
                }
            }
            // 跳过整个 tag (含子节点)，继续下一个
            val nextSearch = if (selfClose) closeInfo.end + 1 else {
                val closeTag = findCloseTag(html, tagNameLc, closeInfo.end + 1)
                if (closeTag < 0) closeInfo.end + 1 else closeTag
            }
            i = nextSearch
            if (i <= lt) i = lt + 1
        }
        return out
    }

    private data class AttrEnd(val end: Int, val selfClose: Boolean)

    private fun findAttrsEnd(html: String, start: Int): AttrEnd {
        var i = start
        var inQuotes: Char = '\u0000'
        while (i < html.length) {
            val c = html[i]
            when {
                inQuotes != '\u0000' -> {
                    if (c == inQuotes && (i == 0 || html[i - 1] != '\\')) inQuotes = '\u0000'
                }
                c == '"' || c == '\'' -> inQuotes = c
                c == '/' && i + 1 < html.length && html[i + 1] == '>' -> return AttrEnd(i, true)
                c == '>' -> return AttrEnd(i, false)
            }
            i++
        }
        return AttrEnd(html.length, false)
    }

    private fun findCloseTag(html: String, tagLower: String, from: Int): Int {
        val needle = "</$tagLower"
        var i = from
        val needleLen = needle.length
        while (i <= html.length - needleLen) {
            // 忽略 script/style 文本里的其他标签；这里简单处理直接找 needle + 空白或 >
            if (html.regionMatches(i, needle, 0, needleLen, true)) {
                var j = i + needleLen
                while (j < html.length && html[j].isWhitespace()) j++
                if (j < html.length && html[j] == '>') {
                    return j + 1
                }
            }
            i++
        }
        return -1
    }

    private fun readTagName(html: String, start: Int): Pair<String?, Int> {
        var i = start
        while (i < html.length && html[i].isWhitespace()) i++
        val sb = StringBuilder()
        while (i < html.length) {
            val c = html[i]
            if (c.isLetterOrDigit() || c == '-' || c == ':') {
                sb.append(c); i++
            } else break
        }
        if (sb.isEmpty()) return null to i
        return sb.toString() to i
    }

    private fun attrGet(attrStr: String, key: String): String? {
        val kLower = key.lowercase()
        val re = """(?:^|\s)($kLower)\s*=\s*("([^"]*)"|'([^']*)'|([^\s'">]+))""".toRegex(RegexOption.IGNORE_CASE)
        val m = re.find(attrStr) ?: return null
        val groups = m.groupValues
        return when {
            groups[3].isNotEmpty() -> groups[3]
            groups[4].isNotEmpty() -> groups[4]
            groups[5].isNotEmpty() -> groups[5]
            else -> null
        }
    }

    private fun innerHtmlOf(outer: String): String {
        val openEnd = outer.indexOfFirstTagClose()
        if (openEnd < 0) return outer
        val closeIdx = outer.indexOfLastClosingTag()
        return if (closeIdx in (openEnd + 1) until outer.length) outer.substring(openEnd + 1, closeIdx)
        else outer.substring(openEnd + 1)
    }

    private fun String.indexOfFirstTagClose(): Int {
        var i = 0
        var q: Char = '\u0000'
        while (i < length) {
            val c = this[i]
            when {
                q != '\u0000' -> { if (c == q) q = '\u0000' }
                c == '"' || c == '\'' -> q = c
                c == '>' -> return i
            }
            i++
        }
        return -1
    }

    private fun String.indexOfLastClosingTag(): Int {
        val m1 = """<\s*/\s*([A-Za-z][\w\-]*)[\s>""".toRegex().find(this)
        val tag = m1?.groupValues?.get(1) ?: return -1
        val close = "</$tag>"
        val idx = this.lastIndexOf(close, ignoreCase = true)
        return if (idx < 0) -1 else idx
    }

    private fun String.findTagOpenStart(from: Int): Int? {
        val i = this.indexOf('<', from)
        return if (i < 0) null else i
    }

    // ===== 选择器词法 =====
    private data class Sel(
        val tag: String?,
        val clazz: String?,
        val id: String?,
        val attrs: List<Pair<String, String?>>
    )

    private fun parseSel(sel: String): Sel {
        var tag: String? = null
        var clazz: String? = null
        var id: String? = null
        val attrs = mutableListOf<Pair<String, String?>>()
        var i = 0
        while (i < sel.length) {
            val c = sel[i]
            when {
                c.isLetterOrDigit() || c == '-' || c == ':' -> {
                    val sb = StringBuilder()
                    while (i < sel.length) {
                        val ch = sel[i]
                        if (ch.isLetterOrDigit() || ch == '-' || ch == ':') { sb.append(ch); i++ } else break
                    }
                    tag = sb.toString().lowercase()
                }
                c == '.' -> {
                    i++
                    val sb = StringBuilder()
                    while (i < sel.length) {
                        val ch = sel[i]
                        if (ch.isLetterOrDigit() || ch == '-' || ch == '_' || ch == ':') { sb.append(ch); i++ } else break
                    }
                    clazz = sb.toString()
                }
                c == '#' -> {
                    i++
                    val sb = StringBuilder()
                    while (i < sel.length) {
                        val ch = sel[i]
                        if (ch.isLetterOrDigit() || ch == '-' || ch == '_') { sb.append(ch); i++ } else break
                    }
                    id = sb.toString()
                }
                c == '[' -> {
                    i++
                    val end = sel.indexOf(']', i)
                    val inside = if (end < 0) sel.substring(i) else sel.substring(i, end)
                    val eq = inside.indexOf('=')
                    if (eq < 0) {
                        attrs.add(inside.trim() to null)
                    } else {
                        val k = inside.substring(0, eq).trim()
                        val v = inside.substring(eq + 1).trim().trim('"', '\'')
                        attrs.add(k to v)
                    }
                    i = if (end < 0) sel.length else end + 1
                }
                else -> {
                    // 无法识别的字符：跳过
                    i++
                }
            }
        }
        return Sel(tag, clazz, id, attrs)
    }
}
