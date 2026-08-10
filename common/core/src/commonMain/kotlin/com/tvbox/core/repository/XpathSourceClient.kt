package com.tvbox.core.repository

import com.tvbox.core.model.MovieSource
import com.tvbox.core.model.SourceType
import com.tvbox.core.model.VodClass
import com.tvbox.core.model.VodEpisode
import com.tvbox.core.model.VodFilter
import com.tvbox.core.model.VodInfo
import com.tvbox.utils.JsonUtils
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * XPath / HTML 爬虫规则解析 (type=2)
 *
 * 兼容 TVBox 生态通用的 XPath 规则 JSON 结构（[MovieSource.ext] 字段内容）：
 * {
 *   "host": "https://xxx.com",                    // 站点基础 URL
 *   "homeUrl": "/index.php",                       // 首页 URL
 *   "homeCatUrl": "/index.php?tid={tid}&pg={pg}",  // 分类 URL 模板
 *   "homeContentNode": ".vodlist li",              // 首页列表节点 CSS selector
 *   "homeContentId": "data-id",                    // 列表项 vod_id 属性
 *   "homeContentName": ".name/text()",             // 列表项 vod_name（支持属性 / text() / html()）
 *   "homeContentPic": ".pic img@src",              // 列表项 vod_pic
 *   "homeContentNote": ".note/text()",             // 列表项 vod_remarks
 *   "detailUrl": "/detail/{id}.html",              // 详情页 URL 模板
 *   "detailNode": ".detail",
 *   "detailName": "h1/text()",
 *   "detailPic": ".poster img@src",
 *   "detailContent": ".intro/text()",
 *   "detailFlags": ".line::attr(flag)",
 *   "detailUrlList": ".playlist li a",             // 剧集列表选择器
 *   "detailUrlListName": "text()",
 *   "detailUrlListUrl": "@href",
 *   "searchUrl": "/search.php?wd={wd}&pg={pg}",
 *   "searchNode": ".search-result li",
 *   "searchId": "data-id",
 *   "searchName": "a/text()",
 *   "searchPic": "img@src",
 *   "playerUrl": "/play/{id}.html?line={flag}",
 *   "playerNode": ".player",
 *   "playerUrlRule": "video@src",
 *   "parseUrl": "https://jx.com/?url={url}"        // 第三方解析接口
 * }
 *
 * 注意：本实现使用自定义轻量级 Html DOM + CSS/XPath 混合子集（不引入 Jsoup/xsoup 等重量级库）。
 * 支持的规则语法：
 *   - CSS 选择器: div.class, #id, tag[attr=val], tag a
 *   - 提取后缀: /text() 文本, /html() 内部 html, @attr 属性, ::attr(name) 属性
 *   - 列表提取: 先 CSS 取节点，再循环按后缀提取字段
 *   - URL 模板变量替换: {tid},{pg},{id},{wd},{flag},{url}
 */
internal class XpathSourceClient(private val http: HttpFetcher) : SourceClient {

    private data class XpathRules(val raw: JsonObject) {
        operator fun get(k: String): String = raw[k]?.jsonPrimitive?.contentOrNull ?: ""
        fun list(k: String): List<String> {
            val el = raw[k] ?: return emptyList()
            return when (el) {
                is JsonArray -> el.mapNotNull { it.jsonPrimitive.contentOrNull }
                else -> listOf(el.jsonPrimitive.contentOrNull).filterNotNull()
            }
        }
    }

    private val rulesCache = HashMap<String, XpathRules>()
    private val initLock = Mutex()

    override suspend fun init(source: MovieSource) {
        if (source.type != SourceType.XPATH) return
        if (rulesCache.containsKey(source.key)) return
        initLock.withLock {
            if (rulesCache.containsKey(source.key)) return@withLock
            runCatching {
                val obj = JsonUtils.parseToJsonElement(source.ext.ifBlank { "{}" }) as? JsonObject
                    ?: JsonObject(emptyMap())
                rulesCache[source.key] = XpathRules(obj)
            }
        }
    }

    private fun rules(source: MovieSource): XpathRules =
        rulesCache[source.key] ?: XpathRules(JsonObject(emptyMap()))

    private fun host(source: MovieSource, r: XpathRules): String {
        val h = r["host"].ifBlank { source.api }
        return h.trimEnd('/')
    }

    private fun String.resolveUrl(host: String): String {
        if (this.startsWith("http://") || this.startsWith("https://")) return this
        if (this.startsWith("//")) return "https:$this"
        val h = host.trimEnd('/')
        return if (this.startsWith("/")) h + this else "$h/$this"
    }

    private fun template(tpl: String, vars: Map<String, String>): String {
        var out = tpl
        for ((k, v) in vars) out = out.replace("{$k}", v)
        return out
    }

    override suspend fun home(source: MovieSource): TvboxJsonResponseParser.ParseListResult {
        if (source.type != SourceType.XPATH) return TvboxJsonResponseParser.ParseListResult.EMPTY
        val r = rules(source)
        val host = host(source, r)
        val urlTpl = r["homeUrl"].ifBlank { "/" }
        val html = http.get(template(urlTpl, emptyMap()).resolveUrl(host), source = source)
        if (html.isBlank()) return TvboxJsonResponseParser.ParseListResult.EMPTY

        val list = extractList(html, r, "homeContent", source.key, host)
        val categories = extractCategories(html, r, "home")

        return TvboxJsonResponseParser.ParseListResult(
            list = list, categories = categories, filters = emptyList(),
            page = 1, pageCount = 1, limit = list.size, total = list.size
        )
    }

    override suspend fun category(
        source: MovieSource,
        tid: String,
        page: Int,
        filters: List<VodFilter>
    ): TvboxJsonResponseParser.ParseListResult {
        if (source.type != SourceType.XPATH) return TvboxJsonResponseParser.ParseListResult.EMPTY
        val r = rules(source)
        val host = host(source, r)
        val tpl = r["homeCatUrl"].ifBlank { r["categoryUrl"] }.ifBlank { "/?tid={tid}&pg={pg}" }
        val vars = buildMap {
            put("tid", tid); put("pg", page.toString())
            filters.forEach { put(it.key, it.selectedValue) }
        }
        val html = http.get(template(tpl, vars).resolveUrl(host), source = source)
        if (html.isBlank()) return TvboxJsonResponseParser.ParseListResult.EMPTY

        val list = extractList(html, r, "category", source.key, host)
            .ifEmpty { extractList(html, r, "homeContent", source.key, host) }
        val categories = extractCategories(html, r, "category")

        return TvboxJsonResponseParser.ParseListResult(
            list = list, categories = categories, filters = emptyList(),
            page = page, pageCount = page, limit = list.size, total = list.size
        )
    }

    override suspend fun detail(source: MovieSource, vodId: String): VodInfo? {
        if (source.type != SourceType.XPATH) return null
        val r = rules(source)
        val host = host(source, r)
        val tpl = r["detailUrl"].ifBlank { "/detail/{id}.html" }
        val html = http.get(template(tpl, mapOf("id" to vodId)).resolveUrl(host), source = source)
        if (html.isBlank()) return null
        return extractDetail(html, r, source.key, vodId, host)
    }

    override suspend fun search(source: MovieSource, keyword: String, page: Int): List<VodInfo> {
        if (source.type != SourceType.XPATH) return emptyList()
        if (!source.searchable) return emptyList()
        val r = rules(source)
        val host = host(source, r)
        val tpl = r["searchUrl"].ifBlank { "/search.php?wd={wd}&pg={pg}" }
        val html = http.get(
            template(tpl, mapOf("wd" to keyword, "pg" to page.toString())).resolveUrl(host),
            source = source
        )
        if (html.isBlank()) return emptyList()
        return extractList(html, r, "search", source.key, host)
    }

    override suspend fun playUrl(
        source: MovieSource,
        flag: String,
        input: String,
        parseUrl: String?
    ): String {
        if (isDirectPlayUrl(input)) return input
        val r = rules(source)
        val host = host(source, r)
        // 1) playerUrl 模板 + playerUrlRule
        val playerTpl = r["playerUrl"]
        if (playerTpl.isNotBlank()) {
            runCatching {
                val playerHtml = http.get(
                    template(playerTpl, mapOf("id" to input, "flag" to flag)).resolveUrl(host),
                    source = source
                )
                val rule = r["playerUrlRule"].ifBlank { r["playerNode"] }
                val url = extractString(playerHtml, rule, host)
                if (url.isNotBlank() && isDirectPlayUrl(url)) return url
                if (url.isNotBlank() && !parseUrl.isNullOrBlank()) {
                    // 继续走 parse 接口
                }
            }
        }
        // 2) parseUrl
        val pu = parseUrl ?: r["parseUrl"]
        if (pu.isNotBlank()) {
            runCatching {
                val raw = http.get(template(pu, mapOf("url" to input)), source = source)
                var u = TvboxJsonResponseParser.parsePlayUrl(raw)
                if (u.isBlank()) u = TvboxXmlResponseParser.parsePlayUrl(raw)
                if (u.isBlank()) u = extractString(raw, r["playerUrlRule"], host)
                if (u.isNotBlank()) return u
            }
        }
        return input
    }

    // ===== 内部工具：DOM 选择 + 规则抽取 =====

    private fun extractCategories(html: String, r: XpathRules, prefix: String): List<VodClass> {
        val nodeRule = r["${prefix}CatNode"].ifBlank { r["categoryNode"] }
        val idRule = r["${prefix}CatId"].ifBlank { r["categoryId"] }.ifBlank { "@href" }
        val nameRule = r["${prefix}CatName"].ifBlank { r["categoryName"] }.ifBlank { "text()" }
        if (nodeRule.isBlank()) return emptyList()

        val nodes = HtmlSelector.selectAll(html, nodeRule)
        val out = mutableListOf<VodClass>()
        for (n in nodes) {
            val id = extractFromNode(n, idRule).extractTidFromHref()
            if (id.isBlank()) continue
            val name = extractFromNode(n, nameRule)
            if (name.isBlank()) continue
            out.add(VodClass(id, name))
        }
        return out
    }

    private fun extractList(html: String, r: XpathRules, prefix: String, sourceKey: String, host: String): List<VodInfo> {
        val nodeRule = r["${prefix}Node"].ifBlank { r["${prefix}ContentNode"] }
        if (nodeRule.isBlank()) return emptyList()

        val idRule = r["${prefix}Id"].ifBlank { r["${prefix}ContentId"] }.ifBlank { "@href" }
        val nameRule = r["${prefix}Name"].ifBlank { r["${prefix}ContentName"] }.ifBlank { "text()" }
        val picRule = r["${prefix}Pic"].ifBlank { r["${prefix}ContentPic"] }
        val noteRule = r["${prefix}Note"].ifBlank { r["${prefix}ContentNote"] }

        val nodes = HtmlSelector.selectAll(html, nodeRule)
        val out = mutableListOf<VodInfo>()
        for (n in nodes) {
            var id = extractFromNode(n, idRule).extractTidFromHref()
            val name = extractFromNode(n, nameRule).trim()
            if (id.isBlank() && name.isBlank()) continue
            if (id.isBlank()) id = name.md5Id()
            val pic = extractFromNode(n, picRule).resolveUrl(host)
            val note = extractFromNode(n, noteRule).trim()

            // 有些规则会在列表直接给出详情链接字段
            val detailLink = runCatching { extractFromNode(n, r["${prefix}Detail"]).resolveUrl(host) }.getOrDefault("")

            out.add(
                VodInfo(
                    vodId = id, sourceKey = sourceKey, vodName = name,
                    vodPic = pic, vodRemarks = note,
                    vodContent = detailLink,
                    episodes = emptyList()
                )
            )
        }
        return out
    }

    private fun extractDetail(
        html: String, r: XpathRules,
        sourceKey: String, vodId: String, host: String
    ): VodInfo {
        val nodeRule = r["detailNode"].ifBlank { "body" }
        val idRule = r["detailId"].ifBlank { "data-id" }
        val nameRule = r["detailName"].ifBlank { "text()" }
        val picRule = r["detailPic"]
        val contentRule = r["detailContent"].ifBlank { r["detailBlurb"] }
        val actorRule = r["detailActor"]
        val directorRule = r["detailDirector"]
        val yearRule = r["detailYear"]
        val areaRule = r["detailArea"]
        val langRule = r["detailLang"]
        val classRule = r["detailClass"].ifBlank { r["detailCategory"] }

        val node = HtmlSelector.selectFirst(html, nodeRule) ?: html
        val id = extractFromNode(node, idRule).ifBlank { vodId }
        val name = extractFromNode(node, nameRule).trim()
        val pic = extractFromNode(node, picRule).resolveUrl(host)
        val content = extractFromNode(node, contentRule).trim()

        // 剧集：先线路，再每个线路下的剧集列表
        val episodes = mutableListOf<VodEpisode>()
        val flagsRule = r["detailFlags"].ifBlank { r["detailPlayFrom"] }
        val flags: List<String> = if (flagsRule.isNotBlank()) {
            val fnodes = HtmlSelector.selectAll(node, flagsRule)
            if (fnodes.isNotEmpty()) fnodes.map { extractFromNode(it, "text()").trim() }.filter { it.isNotBlank() }
            else extractFromNode(node, flagsRule).split("\$\$\$").map { it.trim() }.filter { it.isNotBlank() }
        } else listOf("默认线路")

        val urlListRule = r["detailUrlList"].ifBlank { r["detailPlaylist"] }
        val urlListName = r["detailUrlListName"].ifBlank { "text()" }
        val urlListUrl = r["detailUrlListUrl"].ifBlank { "@href" }

        // 两种布局：每个线路下一个 playlist，或一个 playlist 被多个 flag 共用
        val playlists = if (urlListRule.isNotBlank()) HtmlSelector.selectAll(node, urlListRule) else emptyList()
        if (playlists.isNotEmpty()) {
            playlists.forEachIndexed { lineIdx, listEl ->
                val lineName = flags.getOrNull(lineIdx) ?: "线路${lineIdx + 1}"
                val tagName = extractFromNode(listEl, "tagName()")
                val asList: List<String> = if (tagName.equals("a", true)) listOf(listEl)
                else HtmlSelector.selectAll(listEl, "a")
                asList.forEachIndexed { ei, a ->
                    val epName = extractFromNode(a, urlListName).trim().ifBlank { "第${ei + 1}集" }
                    val epUrl = extractFromNode(a, urlListUrl).resolveUrl(host)
                    if (epUrl.isBlank()) return@forEachIndexed
                    episodes.add(
                        VodEpisode(
                            episodeId = "$sourceKey-$id-$lineIdx-$ei",
                            name = "$lineName · $epName",
                            url = epUrl,
                            header = emptyMap(),
                            parseRules = listOf(lineName)
                        )
                    )
                }
            }
        } else {
            // 兼容：detailUrlList 字符串规则（dd 字段）
            val dd = extractFromNode(node, r["detailUrlList"])
            val perLine = dd.split("\$\$\$")
            perLine.forEachIndexed { lineIdx, lineStr ->
                val lineName = flags.getOrNull(lineIdx) ?: "线路${lineIdx + 1}"
                val pairs = lineStr.split(Regex("""\r\n|\n|#""")).mapNotNull { p ->
                    val i = p.indexOf('$')
                    if (i > 0) p.substring(0, i).trim() to p.substring(i + 1).trim().resolveUrl(host)
                    else null
                }
                pairs.forEachIndexed { ei, (n, u) ->
                    if (u.isBlank()) return@forEachIndexed
                    episodes.add(
                        VodEpisode(
                            episodeId = "$sourceKey-$id-$lineIdx-$ei",
                            name = "$lineName · $n",
                            url = u,
                            header = emptyMap(),
                            parseRules = listOf(lineName)
                        )
                    )
                }
            }
        }

        return VodInfo(
            vodId = id, sourceKey = sourceKey, vodName = name,
            vodPic = pic, vodContent = content,
            vodActor = extractFromNode(node, actorRule).trim(),
            vodDirector = extractFromNode(node, directorRule).trim(),
            vodYear = extractFromNode(node, yearRule).trim(),
            vodArea = extractFromNode(node, areaRule).trim(),
            vodLang = extractFromNode(node, langRule).trim(),
            vodClass = extractFromNode(node, classRule).trim(),
            episodes = episodes
        )
    }

    /** 规则语法总入口：针对整段 HTML 上下文 */
    private fun extractString(html: String, rule: String, host: String): String {
        if (rule.isBlank()) return ""
        // 规则可能是 "selector@suffix" 或 "selector/suffix"，交给节点层解析
        val node = HtmlSelector.selectFirst(html, cssOnlyPart(rule)) ?: html
        val r = extractFromNode(node, rule)
        return r.resolveUrl(host)
    }

    private fun cssOnlyPart(rule: String): String {
        val idx = indexOfExtractorStart(rule)
        return if (idx > 0) rule.substring(0, idx) else rule
    }

    private fun indexOfExtractorStart(rule: String): Int {
        val i1 = rule.indexOfLast { it == ' ' }.let { if (it < 0) rule.length else it }
        val a = rule.indexOf("@")
        val b = rule.indexOf("/text()")
        val c = rule.indexOf("/html()")
        val d = rule.indexOf("::attr")
        val cands = listOf(a, b, c, d).filter { it >= 0 }
        return cands.minOrNull() ?: -1
    }

    /** 从单个节点（HTML 片段字符串，含自身 tag）按提取后缀拿数据 */
    private fun extractFromNode(node: String, rule: String): String {
        if (rule.isBlank()) return ""
        // 几种后缀形式: @attr, /text(), /html(), ::attr(name)
        val attrMatch1 = """@([A-Za-z_:][\w\-.:]*)""".toRegex().find(rule)
        val attrMatch2 = """::attr\(\s*([^)]+?)\s*\)""".toRegex().find(rule)
        when {
            rule.contains("/text()", true) || rule.lowercase() == "text()" -> {
                val css = cssOnlyPart(rule)
                val target = if (css.isNotBlank()) HtmlSelector.selectFirst(node, css) else node
                return (target ?: "").stripTags().trim()
            }
            rule.contains("/html()", true) || rule.lowercase() == "html()" -> {
                val css = cssOnlyPart(rule)
                val target = if (css.isNotBlank()) HtmlSelector.selectFirst(node, css) else node
                return (target ?: "").innerHtmlOrText()
            }
            attrMatch2 != null -> {
                val name = attrMatch2.groupValues[1]
                val css = rule.substring(0, attrMatch2.range.first).trim()
                val target = if (css.isNotBlank()) HtmlSelector.selectFirst(node, css) else node
                return attr(target ?: "", name)
            }
            attrMatch1 != null -> {
                val name = attrMatch1.groupValues[1]
                val css = rule.substring(0, attrMatch1.range.first).trim()
                val target = if (css.isNotBlank()) HtmlSelector.selectFirst(node, css) else node
                return attr(target ?: "", name)
            }
            rule.lowercase() == "tagname()" -> {
                val m = """^\s*<\s*([A-Za-z][\w\-]*)""".toRegex().find(node)
                return m?.groupValues?.get(1).orEmpty()
            }
            else -> {
                // 直接当 CSS 选择子节点，然后取文本
                val target = HtmlSelector.selectFirst(node, rule) ?: node
                return target.stripTags().trim()
            }
        }
    }

    private fun attr(node: String, name: String): String {
        val lower = node.lowercase()
        val key = """$name\s*=\s*""".toRegex(RegexOption.IGNORE_CASE).find(lower) ?: return ""
        var idx = key.range.last + 1
        while (idx < node.length && node[idx].isWhitespace()) idx++
        if (idx >= node.length) return ""
        val quote = when (node[idx]) {
            '"', '\'' -> { val q = node[idx]; idx++; q }
            else -> '\u0000'
        }
        val sb = StringBuilder()
        while (idx < node.length) {
            val c = node[idx]
            if (quote == '\u0000') {
                if (c.isWhitespace() || c == '>' || c == '/') break
                sb.append(c)
            } else {
                if (c == quote) break
                sb.append(c)
            }
            idx++
        }
        return sb.toString().unescapeHtml().trim()
    }

    private fun String.extractTidFromHref(): String {
        if (this.isBlank()) return this
        // 从路径或参数中提取数字或最后一段
        val noQ = this.substringBefore('?')
        val m1 = "/(\\d+)\\.html".toRegex().find(noQ)
        if (m1 != null) return m1.groupValues[1]
        val m2 = """[?&](?:id|tid|vid|ids)=([^&]+)""".toRegex().find(this)
        if (m2 != null) return m2.groupValues[1]
        val last = noQ.trimEnd('/').substringAfterLast('/')
        if (last.isNotBlank()) return last
        return this
    }

    private fun String.md5Id(): String {
        // 使用轻量 hashCode 代替 md5；commonMain 避免引入 crypto
        var h = 1779033703
        for (c in this) h = (h xor c.code) * 16777619
        return (h.toLong() and 0xFFFFFFFFL).toString(16)
    }

    private fun String.stripTags(): String =
        this.replace("""<script[\s\S]*?</script>""".toRegex(RegexOption.IGNORE_CASE), " ")
            .replace("""<style[\s\S]*?</style>""".toRegex(RegexOption.IGNORE_CASE), " ")
            .replace("""<[^>]+>""".toRegex(), " ")
            .unescapeHtml()
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun String.innerHtmlOrText(): String {
        val m = """<\s*([A-Za-z][\w\-]*)(?=\s|>)""".toRegex().find(this)
        val tag = m?.groupValues?.get(1)?.lowercase() ?: return this
        val openEnd = this.indexOf('>', m.range.last + 1)
        if (openEnd < 0) return this
        val close = this.indexOf("</$tag", openEnd, true)
        return if (close < 0) this.substring(openEnd + 1)
        else this.substring(openEnd + 1, close).unescapeHtml()
    }

    private fun String.unescapeHtml(): String {
        var s = this
        s = s.replace("&amp;", "&")
        s = s.replace("&lt;", "<")
        s = s.replace("&gt;", ">")
        s = s.replace("&quot;", "\"")
        s = s.replace("&apos;", "'")
        s = s.replace("&#39;", "'")
        s = s.replace("&#x([0-9a-fA-F]+);".toRegex()) {
            it.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: it.value
        }
        s = s.replace("&#([0-9]+);".toRegex()) {
            it.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: it.value
        }
        return s
    }

    private fun isDirectPlayUrl(url: String): Boolean {
        val lower = url.lowercase()
        return listOf(".m3u8", ".mp4", ".mkv", ".flv", ".webm", ".ts", ".mov", ".avi").any { lower.contains(it) }
    }
}
