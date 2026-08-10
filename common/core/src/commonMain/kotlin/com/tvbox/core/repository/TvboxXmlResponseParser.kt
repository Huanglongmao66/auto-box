package com.tvbox.core.repository

import com.tvbox.core.model.MovieSource
import com.tvbox.core.model.VodClass
import com.tvbox.core.model.VodEpisode
import com.tvbox.core.model.VodFilter
import com.tvbox.core.model.VodFilterValue
import com.tvbox.core.model.VodInfo

/**
 * 轻量 XML Parser：不依赖 XML DOM 库，纯字符串级解析 TVBox 标准 XML 接口返回
 *
 * TVBox 标准 XML 字段（与 JSON 含义一致）：
 *   <class><ty id="1">电影</ty>...</class>
 *   <video>
 *     <vid>123</vid><name>xxx</name><pic>http://..</pic>
 *     <note>更新至第10集</note><actor>xxx</actor><director>xxx</director>
 *     <year>2024</year><area>中国大陆</area><lang>国语</lang>
 *     <content>简介...</content>
 *     <dl flag="线路1$$$线路2">
 *       <dd>第01集$url1#第02集$url2$$$第01集$url3#第02集$url4</dd>
 *     </dl>
 *   </video>
 *   <page>1</page><pagecount>10</pagecount><pagesize>20</pagesize><recordcount>200</recordcount>
 *   <filters>...</filters>
 *
 * 兼容：很多实际 XML 会混用 camelCase、下划线命名，因此别名匹配。
 */
internal object TvboxXmlResponseParser {

    private fun tagText(xml: String, tag: String, alias: Array<String> = emptyArray()): String {
        for (t in arrayOf(tag, *alias)) {
            val start = xml.indexOf("<$t>")
            val end = xml.indexOf("</$t>")
            if (start >= 0 && end > start) {
                return xml.substring(start + t.length + 2, end).trim().unescapeXml()
            }
            // 兼容 <xxx attr> 形式带属性
            val re = "<$t[^>]*>([\\s\\S]*?)</$t>".toRegex(RegexOption.IGNORE_CASE)
            val m = re.find(xml)
            if (m != null) return m.groupValues[1].trim().unescapeXml()
        }
        return ""
    }

    private fun allBlocks(xml: String, tag: String, alias: Array<String> = emptyArray()): List<String> {
        val tags = arrayOf(tag, *alias)
        val out = mutableListOf<String>()
        for (t in tags) {
            val re = "<$t[^>]*>([\\s\\S]*?)</$t>".toRegex(RegexOption.IGNORE_CASE)
            for (m in re.findAll(xml)) out.add(m.groupValues[1])
            if (out.isNotEmpty()) return out
        }
        return out
    }

    fun parseList(xml: String, sourceKey: String): TvboxJsonResponseParser.ParseListResult {
        if (xml.isBlank()) return TvboxJsonResponseParser.ParseListResult.EMPTY
        val categories = mutableListOf<VodClass>()
        val list = mutableListOf<VodInfo>()
        val filters = mutableListOf<VodFilter>()

        // 分页
        val page = tagText(xml, "page").toIntOrNull() ?: 1
        val pageCount = tagText(xml, "pagecount").toIntOrNull() ?: 1
        val pageSize = tagText(xml, "pagesize").toIntOrNull() ?: 20
        val total = (tagText(xml, "recordcount").toIntOrNull()
            ?: tagText(xml, "total").toIntOrNull() ?: 0)

        // 分类：class -> ty[]
        val classBlock = tagText(xml, "class")
        if (classBlock.isNotBlank()) {
            val ty = allBlocks(classBlock, "ty")
            for (t in ty) {
                // <ty id="1">电影</ty>
                val idRe = """\s+id\s*=\s*["']([^"']+)["']""".toRegex().find(t)
                val id = idRe?.groupValues?.get(1) ?: continue
                val name = """^[^>]*>([\s\S]*)$""".toRegex().find(t)?.groupValues?.get(1)?.trim()?.unescapeXml() ?: continue
                categories.add(VodClass(id, name))
            }
        }

        // 影视列表：video / list
        val videoBlocks = allBlocks(xml, "video") + allBlocks(xml, "list")
        for (vb in videoBlocks) {
            val v = parseVodBrief(vb, sourceKey)
            if (v != null) list.add(v)
        }

        return TvboxJsonResponseParser.ParseListResult(
            list = list, categories = categories, filters = filters,
            page = page, pageCount = pageCount, limit = pageSize, total = total
        )
    }

    fun parseDetail(xml: String, sourceKey: String): VodInfo? {
        if (xml.isBlank()) return null
        val vb = allBlocks(xml, "video").firstOrNull()
            ?: allBlocks(xml, "list").firstOrNull() ?: return null
        val brief = parseVodBrief(vb, sourceKey) ?: return null
        val content = tagText(vb, "content", arrayOf("desc", "description", "vod_content", "blurb"))
            .ifBlank { brief.vodContent }

        // 剧集 <dl flag="线路1$$$线路2"><dd>...</dd></dl>
        val episodes = mutableListOf<VodEpisode>()
        val dls = allBlocks(vb, "dl")
        for (dl in dls) {
            val flagAttr = """\s+flag\s*=\s*["']([^"']+)["']""".toRegex().find(dl)?.groupValues?.get(1) ?: "默认线路"
            val lines = if (flagAttr.contains("\$\$\$")) flagAttr.split("\$\$\$") else listOf(flagAttr)
            val dd = tagText(dl, "dd")
            val urlsPerLine = if (dd.contains("\$\$\$")) dd.split("\$\$\$") else listOf(dd)
            urlsPerLine.forEachIndexed { idx, lineStr ->
                val lineName = lines.getOrNull(idx) ?: "线路${idx + 1}"
                val pairs = splitEpisodes(lineStr)
                pairs.forEachIndexed { ei, (epName, epUrl) ->
                    if (epUrl.isBlank()) return@forEachIndexed
                    episodes.add(
                        VodEpisode(
                            episodeId = "$sourceKey-${brief.vodId}-$idx-$ei",
                            name = "$lineName · $epName",
                            url = epUrl,
                            header = emptyMap(),
                            parseRules = listOf(lineName)
                        )
                    )
                }
            }
        }

        return brief.copy(vodContent = content, episodes = episodes)
    }

    fun parseSearch(xml: String, sourceKey: String): List<VodInfo> = parseList(xml, sourceKey).list

    fun parsePlayUrl(xml: String): String {
        if (xml.isBlank()) return ""
        return tagText(xml, "url", arrayOf("playUrl", "link", "src"))
    }

    // ===== 内部工具 =====

    private fun parseVodBrief(vb: String, sourceKey: String): VodInfo? {
        val id = tagText(vb, "vid", arrayOf("id", "vod_id"))
        val name = tagText(vb, "name", arrayOf("title", "vod_name"))
        if (id.isBlank() && name.isBlank()) return null
        return VodInfo(
            vodId = id,
            sourceKey = sourceKey,
            vodName = name,
            vodPic = tagText(vb, "pic", arrayOf("img", "vod_pic", "cover", "poster")),
            vodRemarks = tagText(vb, "note", arrayOf("remarks", "vod_remarks", "status")),
            vodActor = tagText(vb, "actor", arrayOf("actors", "vod_actor", "starring")),
            vodDirector = tagText(vb, "director", arrayOf("directors", "vod_director")),
            vodYear = tagText(vb, "year", arrayOf("vod_year")),
            vodArea = tagText(vb, "area", arrayOf("country", "vod_area", "region")),
            vodLang = tagText(vb, "lang", arrayOf("language", "vod_lang")),
            vodClass = tagText(vb, "type", arrayOf("category", "vod_class", "cid")),
            vodContent = tagText(vb, "content", arrayOf("desc", "vod_content", "description", "blurb")),
            vodScore = tagText(vb, "score", arrayOf("rating", "vod_score")),
            episodes = emptyList()
        )
    }

    private fun String.unescapeXml(): String {
        return this
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&#x([0-9a-fA-F]+);".toRegex()) { m ->
                m.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: m.value
            }
            .replace("&#([0-9]+);".toRegex()) { m ->
                m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: m.value
            }
    }

    private val SEP_EP = Regex("""\r\n|\n|#""")
    private fun splitEpisodes(s: String): List<Pair<String, String>> {
        if (s.isBlank()) return emptyList()
        val parts = s.split(SEP_EP).map { it.trim() }.filter { it.isNotBlank() }
        val out = mutableListOf<Pair<String, String>>()
        for (p in parts) {
            val idx = p.indexOf('$')
            if (idx > 0) {
                val name = p.substring(0, idx).trim().ifBlank { "剧集" }
                val url = p.substring(idx + 1).trim()
                if (url.isNotBlank()) out.add(name to url)
            } else {
                out.add("剧集" to p)
            }
        }
        return out
    }
}
