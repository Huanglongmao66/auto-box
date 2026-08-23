package com.tvbox.core.repository

import com.tvbox.core.model.VodClass
import com.tvbox.core.model.VodEpisode
import com.tvbox.core.model.VodFilter
import com.tvbox.core.model.VodFilterValue
import com.tvbox.core.model.VodInfo
import com.tvbox.utils.XmlUtils
import com.tvbox.utils.XmlNode

/**
 * TVBox XML 源响应解析器（type=0）
 *
 * 兼容原版 TVBox XML 源响应格式，字段映射与 JSON 解析器保持一致。
 *
 * XML 源标准结构：
 * ```xml
 * <rss>
 *   <class>
 *     <ty id="1" name="电影"/>
 *     <ty id="2" name="电视剧"/>
 *   </class>
 *   <list page="1" pagecount="10" pagesize="20" recordcount="200">
 *     <video>
 *       <id>123</id>
 *       <name>标题</name>
 *       <pic>图片URL</pic>
 *       <note>备注</note>
 *     </video>
 *   </list>
 * </rss>
 * ```
 *
 * 详情 XML 结构：
 * ```xml
 * <rss>
 *   <list>
 *     <video>
 *       <id>123</id>
 *       <name>标题</name>
 *       <pic>图片</pic>
 *       <actor>演员</actor>
 *       <director>导演</director>
 *       <content>简介</content>
 *       <dl>
 *         <dd flag="线路1">第1集$url1#第2集$url2</dd>
 *         <dd flag="线路2">第1集$url1#第2集$url2</dd>
 *       </dl>
 *     </video>
 *   </list>
 * </rss>
 * ```
 */
internal object TvboxXmlResponseParser {

    // ============ 分类/首页列表 ============

    fun parseList(xml: String, sourceKey: String): ParseListResult {
        val doc = XmlUtils.parse(xml)
        val root = doc.getElementsByTagName("rss").firstOrNull()
            ?: doc.getElementsByTagName("root").firstOrNull()
            ?: return ParseListResult.EMPTY

        val categories = mutableListOf<VodClass>()
        val filters = mutableListOf<VodFilter>()
        val list = mutableListOf<VodInfo>()
        var page = 1
        var pageCount = 1
        var limit = 20
        var total = 0

        // 分类 class
        val classNode = root.getElementsByTagName("class").firstOrNull()
        classNode?.getElementsByTagName("ty")?.forEach { ty ->
            val tid = ty.getAttribute("id") ?: return@forEach
            val tname = ty.getAttribute("name") ?: return@forEach
            categories.add(VodClass(tid, tname))
        }

        // 筛选 filters
        val filtersNode = root.getElementsByTagName("filters").firstOrNull()
        filtersNode?.getElementsByTagName("filter")?.forEach { filterNode ->
            val keyName = filterNode.getAttribute("key") ?: filterNode.getAttribute("id") ?: return@forEach
            val keyShow = filterNode.getAttribute("name") ?: filterNode.getAttribute("show") ?: keyName
            val values = mutableListOf<VodFilterValue>()
            filterNode.getElementsByTagName("item")?.forEach { item ->
                val vv = item.getAttribute("v") ?: item.getAttribute("value") ?: return@forEach
                val vn = item.getAttribute("n") ?: item.getAttribute("name") ?: vv
                values.add(VodFilterValue(vv, vn))
            }
            if (values.isNotEmpty()) {
                filters.add(VodFilter(keyName, keyShow, values.toList()))
            }
        }

        // 影视列表 list
        val listNode = root.getElementsByTagName("list").firstOrNull()
        listNode?.let { ln ->
            ln.getAttribute("page")?.toIntOrNull()?.let { page = it }
            ln.getAttribute("pagecount")?.toIntOrNull()?.let { pageCount = it }
            ln.getAttribute("pagesize")?.toIntOrNull()?.let { limit = it }
            ln.getAttribute("recordcount")?.toIntOrNull()?.let { total = it }

            ln.getElementsByTagName("video").forEach { video ->
                val vod = parseVodInfoBrief(video, sourceKey)
                if (vod != null) list.add(vod)
            }
        }

        return ParseListResult(
            list = list.toList(),
            categories = categories.toList(),
            filters = filters.toList(),
            page = page,
            pageCount = pageCount,
            limit = limit,
            total = total
        )
    }

    data class ParseListResult(
        val list: List<VodInfo>,
        val categories: List<VodClass>,
        val filters: List<VodFilter>,
        val page: Int,
        val pageCount: Int,
        val limit: Int,
        val total: Int
    ) {
        companion object {
            val EMPTY = ParseListResult(emptyList(), emptyList(), emptyList(), 1, 1, 20, 0)
        }
    }

    // ============ 详情 ============

    fun parseDetail(xml: String, sourceKey: String): VodInfo? {
        val doc = XmlUtils.parse(xml)
        val root = doc.getElementsByTagName("rss").firstOrNull()
            ?: doc.getElementsByTagName("root").firstOrNull()
            ?: return null
        val video = root.getElementsByTagName("list").firstOrNull()
            ?.getElementsByTagName("video")?.firstOrNull()
            ?: root.getElementsByTagName("video").firstOrNull()
            ?: return null
        return parseVodInfoDetail(video, sourceKey)
    }

    // ============ 搜索 ============

    fun parseSearch(xml: String, sourceKey: String): List<VodInfo> {
        return parseList(xml, sourceKey).list
    }

    // ============ 播放地址 ============

    fun parsePlayUrl(xml: String): String {
        val doc = XmlUtils.parse(xml)
        val root = doc.getElementsByTagName("rss").firstOrNull()
            ?: doc.getElementsByTagName("root").firstOrNull()
            ?: return ""
        // <url>直链</url>
        root.getElementsByTagName("url").firstOrNull()?.text?.let { return it }
        // <play><url>...</url></play>
        root.getElementsByTagName("play").firstOrNull()?.getElementsByTagName("url")?.firstOrNull()?.text?.let { return it }
        return ""
    }

    // ============ 公共解析方法 ============

    private fun parseVodInfoBrief(node: XmlNode, sourceKey: String): VodInfo? {
        val vodId = node.getChildText("id") ?: node.getChildText("vod_id") ?: return null
        val name = node.getChildText("name") ?: node.getChildText("vod_name") ?: return null
        val pic = node.getChildText("pic") ?: node.getChildText("vod_pic") ?: ""
        val remarks = node.getChildText("note") ?: node.getChildText("vod_remarks") ?: ""
        return VodInfo(
            vodId = vodId,
            vodName = name,
            vodPic = pic,
            vodRemarks = remarks,
            sourceKey = sourceKey
        )
    }

    private fun parseVodInfoDetail(node: XmlNode, sourceKey: String): VodInfo? {
        val vodId = node.getChildText("id") ?: node.getChildText("vod_id") ?: return null
        val name = node.getChildText("name") ?: node.getChildText("vod_name") ?: return null
        val pic = node.getChildText("pic") ?: node.getChildText("vod_pic") ?: ""
        val remarks = node.getChildText("note") ?: node.getChildText("vod_remarks") ?: ""
        val year = node.getChildText("year") ?: node.getChildText("vod_year") ?: ""
        val area = node.getChildText("area") ?: node.getChildText("vod_area") ?: ""
        val vodClass = node.getChildText("class") ?: node.getChildText("type_name") ?: ""
        val actor = node.getChildText("actor") ?: node.getChildText("vod_actor") ?: ""
        val director = node.getChildText("director") ?: node.getChildText("vod_director") ?: ""
        val content = node.getChildText("content") ?: node.getChildText("vod_content") ?: ""
        val score = node.getChildText("score") ?: node.getChildText("vod_score") ?: ""
        val lang = node.getChildText("lang") ?: node.getChildText("vod_lang") ?: ""

        val playFrom = node.getChildText("play_from") ?: ""
        val dlNode = node.getElementsByTagName("dl").firstOrNull()
        val episodes = mutableListOf<VodEpisode>()

        if (dlNode != null) {
            val ddNodes = dlNode.getElementsByTagName("dd")
            val lines = if (playFrom.isNotBlank()) playFrom.split("$$$").map { it.trim() }.filter { it.isNotBlank() } else emptyList()
            ddNodes.forEachIndexed { lineIndex, dd ->
                val lineName = dd.getAttribute("flag") ?: lines.getOrNull(lineIndex) ?: "线路${lineIndex + 1}"
                val playUrl = dd.text
                val pairs = splitEpisodes(playUrl)
                pairs.forEachIndexed { epIndex, (epName, epUrl) ->
                    if (epUrl.isNotBlank()) {
                        episodes.add(
                            VodEpisode(
                                episodeId = "$sourceKey-$vodId-$lineIndex-$epIndex",
                                name = "$lineName · $epName",
                                url = epUrl,
                                parseRules = listOf(lineName)
                            )
                        )
                    }
                }
            }
        }

        return VodInfo(
            vodId = vodId,
            vodName = name,
            vodPic = pic,
            vodRemarks = remarks,
            vodYear = year,
            vodArea = area,
            vodClass = vodClass,
            vodActor = actor,
            vodDirector = director,
            vodContent = content,
            vodScore = score,
            vodLang = lang,
            sourceKey = sourceKey,
            episodes = episodes.toList(),
            playerFlag = playFrom
        )
    }

    // ============ 工具方法 ============

    private val SEP_EP = Regex("""\r\n|\n|#""")

    private fun splitEpisodes(lineStr: String): List<Pair<String, String>> {
        if (lineStr.isBlank()) return emptyList()
        val result = mutableListOf<Pair<String, String>>()
        val parts = lineStr.split(SEP_EP).map { it.trim() }.filter { it.isNotBlank() }
        for ((index, part) in parts.withIndex()) {
            val dollarIdx = part.indexOf('$')
            if (dollarIdx > 0) {
                result.add(part.substring(0, dollarIdx).trim() to part.substring(dollarIdx + 1).trim())
            } else if (dollarIdx == 0) {
                result.add("第${index + 1}集" to part.substring(1).trim())
            } else {
                result.add("第${index + 1}集" to part)
            }
        }
        return result
    }
}
