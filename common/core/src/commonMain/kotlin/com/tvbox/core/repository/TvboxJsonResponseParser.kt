package com.tvbox.core.repository

import com.tvbox.core.model.VodClass
import com.tvbox.core.model.VodEpisode
import com.tvbox.core.model.VodFilter
import com.tvbox.core.model.VodFilterValue
import com.tvbox.core.model.VodInfo
import com.tvbox.utils.JsonUtils
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * TVBox JSON 源响应解析器（type=1）
 *
 * 兼容原版 TVBox JSON 源的 5 种标准响应格式：
 *   homeContent、categoryContent、detailContent、searchContent、playerContent
 *
 * 字段别名兼容：vod_id / id, vod_name / name, vod_pic / pic 等。
 */
internal object TvboxJsonResponseParser {

    // ============ 分类/首页列表（homeContent & categoryContent 返回） ============

    fun parseList(json: String, sourceKey: String): ParseListResult {
        val root = JsonUtils.parseToJsonElement(json) as? JsonObject ?: return ParseListResult.EMPTY
        val list: MutableList<VodInfo> = mutableListOf()
        val categories: MutableList<VodClass> = mutableListOf()
        val filters: MutableList<VodFilter> = mutableListOf()
        var page = 1
        var pageCount = 1
        var limit = 20
        var total = 0

        root.safeInt("page")?.let { page = it }
        root.safeInt("pagecount")?.let { pageCount = it }
        root.safeInt("pagesize")?.let { limit = it }
        root.safeInt("recordcount")?.let { total = it }
        root.safeInt("total")?.let { total = it }

        // 分类 class
        (root["class"] as? JsonArray)?.let { arr ->
            for (el in arr) {
                val obj = el as? JsonObject ?: continue
                val tid = obj.safeString("type_id") ?: obj.safeString("id") ?: continue
                val tname = obj.safeString("type_name") ?: obj.safeString("name") ?: continue
                categories.add(VodClass(tid, tname))
            }
        }

        // 筛选 filters
        (root["filters"] as? JsonObject)?.let { filtersObj ->
            for ((key, value) in filtersObj) {
                val keyName = key
                val keyShow = (value as? JsonObject)?.safeString("show") ?: key
                val values: MutableList<VodFilterValue> = mutableListOf()
                val vo = value as? JsonObject
                if (vo != null) {
                    val itemsArr = (vo["value"] as? JsonArray) ?: (vo["items"] as? JsonArray)
                    if (itemsArr != null) {
                        for (vi in itemsArr) {
                            val o = vi as? JsonObject ?: continue
                            val vv = o.safeString("v") ?: o.safeString("value") ?: continue
                            val vn = o.safeString("n") ?: o.safeString("name") ?: vv
                            values.add(VodFilterValue(vv, vn))
                        }
                    }
                }
                if (values.isNotEmpty()) {
                    filters.add(VodFilter(keyName, keyShow, values.toList()))
                }
            }
        }

        // 影视列表 list
        val listArr = (root["list"] as? JsonArray) ?: (root["video"] as? JsonArray) ?: JsonArray(emptyList())
        for (el in listArr) {
            val vod = parseVodInfoBrief(el, sourceKey) ?: continue
            list.add(vod)
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

    // ============ 详情（detailContent 返回） ============

    fun parseDetail(json: String, sourceKey: String): VodInfo? {
        val root = JsonUtils.parseToJsonElement(json) as? JsonObject ?: return null
        val listArr = (root["list"] as? JsonArray) ?: return null
        val first = listArr.firstOrNull() ?: return null
        return parseVodInfoDetail(first, sourceKey)
    }

    // ============ 搜索（searchContent 返回） ============

    fun parseSearch(json: String, sourceKey: String): List<VodInfo> {
        return parseList(json, sourceKey).list
    }

    // ============ 播放地址（playerContent 返回） ============

    fun parsePlayUrl(json: String): String {
        val root = JsonUtils.parseToJsonElement(json) as? JsonObject ?: return ""
        root.safeString("url")?.takeIf { it.isNotBlank() }?.let { return it }
        root.safeString("parse_url")?.takeIf { it.isNotBlank() }?.let { return it }
        (root["parse") as? JsonPrimitive)?.let { p ->
            val v = p.safeStringValue()
            if (v.isNotBlank()) return v
        }
        return ""
    }

    // ============ 公共解析方法 ============

    /**
     * 解析影视简要信息（列表项）
     */
    private fun parseVodInfoBrief(element: Any?, sourceKey: String): VodInfo? {
        val obj = element as? JsonObject ?: return null
        val vodId = obj.safeString("vod_id") ?: obj.safeString("id") ?: return null
        val name = obj.safeString("vod_name") ?: obj.safeString("name") ?: return null
        val pic = obj.safeString("vod_pic") ?: obj.safeString("pic") ?: ""
        val remarks = obj.safeString("vod_remarks") ?: obj.safeString("note") ?: ""
        return VodInfo(
            vodId = vodId,
            vodName = name,
            vodPic = pic,
            vodRemarks = remarks,
            sourceKey = sourceKey
        )
    }

    /**
     * 解析影视详情信息（含选集）
     */
    private fun parseVodInfoDetail(element: Any?, sourceKey: String): VodInfo? {
        val obj = element as? JsonObject ?: return null
        val vodId = obj.safeString("vod_id") ?: obj.safeString("id") ?: return null
        val name = obj.safeString("vod_name") ?: obj.safeString("name") ?: return null
        val pic = obj.safeString("vod_pic") ?: obj.safeString("pic") ?: ""
        val remarks = obj.safeString("vod_remarks") ?: obj.safeString("note") ?: ""
        val year = obj.safeString("vod_year") ?: ""
        val area = obj.safeString("vod_area") ?: ""
        val vodClass = obj.safeString("vod_class") ?: obj.safeString("type_name") ?: ""
        val actor = obj.safeString("vod_actor") ?: obj.safeString("actor") ?: ""
        val director = obj.safeString("vod_director") ?: obj.safeString("director") ?: ""
        val content = obj.safeString("vod_content") ?: obj.safeString("content") ?: ""
        val score = obj.safeString("vod_score") ?: ""
        val lang = obj.safeString("vod_lang") ?: ""

        val playFrom = obj.safeString("vod_play_from") ?: obj.safeString("playFrom") ?: ""
        val playUrl = obj.safeString("vod_play_url") ?: obj.safeString("playUrl") ?: ""
        val playNote = obj.safeString("vod_play_note") ?: ""

        val episodes = parseEpisodes(playFrom, playNote, playUrl, sourceKey, vodId)

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
            episodes = episodes,
            playerFlag = playFrom
        )
    }

    /**
     * 解析剧集（多线路 + 多集数）
     *
     * TVBox 格式：
     *   playFrom = "线路1$$$线路2$$$线路3"
     *   playUrl  = "第1集$url1#第2集$url2$$$第1集$url1#第2集$url2"
     */
    private fun parseEpisodes(
        playFrom: String,
        playNote: String,
        playUrl: String,
        sourceKey: String,
        vodId: String
    ): List<VodEpisode> {
        if (playUrl.isBlank()) return emptyList()
        val lines = if (playFrom.isNotBlank()) splitMulti(playFrom) else listOf("默认线路")
        val urlsPerLine = splitMulti(playUrl)
        val result: MutableList<VodEpisode> = mutableListOf()
        urlsPerLine.forEachIndexed { lineIndex, lineStr ->
            val lineName = lines.getOrNull(lineIndex) ?: "线路${lineIndex + 1}"
            val pairs = splitEpisodes(lineStr)
            pairs.forEachIndexed { epIndex, (epName, epUrl) ->
                if (epUrl.isBlank()) return@forEachIndexed
                result.add(
                    VodEpisode(
                        episodeId = "$sourceKey-$vodId-$lineIndex-$epIndex",
                        name = "$lineName · $epName",
                        url = epUrl,
                        parseRules = listOf(lineName)
                    )
                )
            }
        }
        return result.toList()
    }

    private val SEP_MULTI = Regex("""\$\$\$|###|\|\|\||\r\n|\n\n""")
    private val SEP_EP = Regex("""\r\n|\n|#""")

    private fun splitMulti(s: String): List<String> {
        if (s.isBlank()) return emptyList()
        return s.split(SEP_MULTI).map { it.trim() }.filter { it.isNotBlank() }
    }

    /**
     * 解析一行剧集字符串，返回 Pair(name,url) 列表
     * 格式："第1集$url1#第2集$url2" 或 "$url1#$url2"（无名称时自动编号）
     */
    private fun splitEpisodes(lineStr: String): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        val parts = lineStr.split(SEP_EP).map { it.trim() }.filter { it.isNotBlank() }
        for ((index, part) in parts.withIndex()) {
            val dollarIdx = part.indexOf('$')
            if (dollarIdx > 0) {
                val name = part.substring(0, dollarIdx).trim()
                val url = part.substring(dollarIdx + 1).trim()
                result.add(name to url)
            } else if (dollarIdx == 0) {
                result.add("第${index + 1}集" to part.substring(1).trim())
            } else {
                result.add("第${index + 1}集" to part)
            }
        }
        return result
    }

    // ============ JSON 扩展工具 ============

    private fun JsonObject.safeString(key: String): String? {
        val prim = this[key] as? JsonPrimitive ?: return null
        return prim.safeStringValue()
    }

    private fun JsonObject.safeInt(key: String): Int? {
        val prim = this[key] as? JsonPrimitive ?: return null
        return prim.intOrNull ?: prim.contentOrNull?.toIntOrNull()
    }

    private fun JsonPrimitive.safeStringValue(): String {
        if (this.isString) return this.contentOrNull?.ifBlank { null } ?: ""
        val asInt = this.intOrNull
        if (asInt != null) return asInt.toString()
        val asLong = this.longOrNull
        if (asLong != null) return asLong.toString()
        val asFloat = this.floatOrNull
        if (asFloat != null) return formatFloat(asFloat)
        return this.contentOrNull?.ifBlank { null } ?: ""
    }

    private fun formatFloat(f: Float): String {
        if (f == f.toInt().toFloat()) return f.toInt().toString()
        val multi = (f * 100).toInt()
        val str = multi.toString()
        val decimalPos = str.length - 2
        val whole = if (decimalPos > 0) str.substring(0, decimalPos) else "0"
        val dec = if (decimalPos > 0) str.substring(decimalPos) else str
        val trimmed = dec.trimEnd('0')
        return if (trimmed.isEmpty()) whole else "$whole.$trimmed"
    }
}
