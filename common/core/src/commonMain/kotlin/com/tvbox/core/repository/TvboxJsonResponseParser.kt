package com.tvbox.core.repository

import com.tvbox.core.model.MovieSource
import com.tvbox.core.model.VodClass
import com.tvbox.core.model.VodEpisode
import com.tvbox.core.model.VodFilter
import com.tvbox.core.model.VodFilterValue
import com.tvbox.core.model.VodInfo
import com.tvbox.utils.JsonUtils
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * TVBox 原版 JSON 源响应解析器
 *
 * 兼容类型 0=XML（暂未实现，降级返回空）、1=JSON。
 * 严格匹配 TVBoxOS 原版返回字段，字段别名兼容：
 *   vod_id / id, vod_name / name, vod_pic / pic 等。
 */
internal object TvboxJsonResponseParser {

    // ============ 分类列表（homeContent & categoryContent 返回） ============

    fun parseList(json: String, sourceKey: String): ParseListResult {
        val root = JsonUtils.parseToJsonElement(json) as? JsonObject ?: return ParseListResult.EMPTY
        val list: MutableList<VodInfo> = mutableListOf()
        val categories: MutableList<VodClass> = mutableListOf()
        val filters: MutableList<VodFilter> = mutableListOf()
        var page = 1
        var pageCount = 1
        var limit = 20
        var total = 0

        // 分页信息
        root.safeInt("page")?.let { page = it }
        root.safeInt("pagecount")?.let { pageCount = it }
        root.safeInt("pagesize")?.let { limit = it }
        root.safeInt("recordcount")?.let { total = it }
        root.safeInt("total")?.let { total = it }

        // 分类 class / type
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
                val vo = (value as? JsonObject)
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

    // ============ 详情列表（detailContent 返回） ============

    fun parseDetail(json: String, sourceKey: String): VodInfo? {
        val root = JsonUtils.parseToJsonElement(json) as? JsonObject ?: return null
        val listArr = (root["list"] as? JsonArray) ?: return null
        val first = listArr.firstOrNull() ?: return null
        return parseVodInfoDetail(first, sourceKey)
    }

    // ============ 搜索返回（searchContent 返回，可与 list 共享，在此扩展字段） ============

    fun parseSearch(json: String, sourceKey: String): List<VodInfo> {
        return parseList(json, sourceKey).list
    }

    // ============ 播放地址（playerContent 返回） ============

    fun parsePlayUrl(json: String): String {
        val root = JsonUtils.parseToJsonElement(json) as? JsonObject ?: return ""
        // 直链
        root.safeString("url")?.takeIf { it.isNotBlank() }?.let { return it }
        // 兼容字段
        root.safeString("playUrl")?.takeIf { it.isNotBlank() }?.let { return it }
        (root["data"] as? JsonObject)?.safeString("url")?.takeIf { it.isNotBlank() }?.let { return it }
        return ""
    }

    // ============ 内部：解析精简 VodInfo（列表/搜索） ============

    private fun parseVodInfoBrief(el: JsonElement, sourceKey: String): VodInfo? {
        val obj = el as? JsonObject ?: return null
        val vid = obj.safeString("vod_id")
            ?: obj.safeString("id")
            ?: return null
        val vname = obj.safeString("vod_name") ?: obj.safeString("name") ?: ""
        if (vname.isBlank()) return null
        return VodInfo(
            vodId = vid,
            vodName = vname,
            vodPic = obj.safeString("vod_pic") ?: obj.safeString("pic") ?: "",
            vodRemarks = obj.safeString("vod_remarks") ?: obj.safeString("note") ?: obj.safeString("remarks") ?: "",
            vodYear = obj.safeString("vod_year") ?: obj.safeString("year")?.toString() ?: "",
            vodArea = obj.safeString("vod_area") ?: obj.safeString("area") ?: "",
            vodClass = obj.safeString("vod_class") ?: obj.safeString("type") ?: "",
            vodActor = obj.safeString("vod_actor") ?: obj.safeString("actor") ?: "",
            vodDirector = obj.safeString("vod_director") ?: obj.safeString("director") ?: "",
            vodScore = obj.safeString("vod_score") ?: obj.safeString("score") ?: "",
            vodLang = obj.safeString("vod_lang") ?: obj.safeString("lang") ?: "",
            sourceKey = sourceKey
        )
    }

    // ============ 内部：解析详细 VodInfo（含剧集 playFrom/playNote/playUrl） ============

    private fun parseVodInfoDetail(el: JsonElement, sourceKey: String): VodInfo? {
        val obj = el as? JsonObject ?: return null
        val vid = obj.safeString("vod_id")
            ?: obj.safeString("id")
            ?: return null
        val vname = obj.safeString("vod_name") ?: obj.safeString("name") ?: ""
        if (vname.isBlank()) return null

        val brief = parseVodInfoBrief(obj, sourceKey) ?: return null

        // 播放线路 playFrom（分隔符通常是 $$$ || \r\n || ###）
        val playFromRaw = obj.safeString("vod_play_from") ?: obj.safeString("playFrom") ?: ""
        val playNoteRaw = obj.safeString("vod_play_note") ?: obj.safeString("vod_play_note")
            ?: obj.safeString("playNote") ?: obj.safeString("note") ?: ""
        val playUrlRaw = obj.safeString("vod_play_url") ?: obj.safeString("playUrl") ?: ""
        val urlContent = obj.safeString("vod_url") ?: playUrlRaw

        val episodes = parseEpisodes(
            playFrom = playFromRaw,
            playNote = playNoteRaw,
            playUrl = urlContent,
            sourceKey = sourceKey,
            vodId = vid
        )

        // 简介
        val content = obj.safeString("vod_content")
            ?: obj.safeString("vod_blurb")
            ?: obj.safeString("des")
            ?: obj.safeString("description")
            ?: brief.vodContent

        return brief.copy(
            vodContent = content ?: "",
            episodes = episodes
        )
    }

    /**
     * 解析剧集
     *
     * TVBox 原版格式：
     *   playFrom：线路1$$$线路2$$$线路3
     *   playUrl ：第一集$URL1#第二集$URL2$$$第一集$URL1#第二集$URL2
     *
     *   分隔符兼容 $$$ ### # 等不同格式。
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
                        header = emptyMap(),
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
     *
     * 可能的格式：
     *   第一集$https://xxx/1.m3u8#第二集$https://xxx/2.m3u8
     *   第一集$xxx#第二集$xxx
     *   第一集https://xxx#第二集https://xxx（较少见，兼容）
     */
    private fun splitEpisodes(line: String): List<Pair<String, String>> {
        val items = line.split(SEP_EP).map { it.trim() }.filter { it.isNotBlank() }
        val pairs: MutableList<Pair<String, String>> = mutableListOf()
        for (item in items) {
            val idx = item.lastIndexOf('$')
            val pair: Pair<String, String> = if (idx >= 0) {
                item.substring(0, idx).trim() to item.substring(idx + 1).trim()
            } else {
                // 尝试分离：URL 以 http / https / ftp / rtsp / 等等开头
                val urlIdx = item.indexOf("http")
                if (urlIdx > 0) {
                    item.substring(0, urlIdx).trim() to item.substring(urlIdx).trim()
                } else {
                    item to item
                }
            }
            pairs.add(pair)
        }
        return pairs.toList()
    }

    // ============ JsonObject / JsonPrimitive 扩展工具 ============

    private fun JsonObject.safeString(key: String): String? {
        val prim = this[key] as? JsonPrimitive ?: return null
        if (prim.isString) return prim.contentOrNull?.ifBlank { null }
        // 数字/boolean：以其 string 表示返回（score, year, page 等场景）
        val asInt = prim.intOrNull
        if (asInt != null) return asInt.toString()
        val asLong = prim.longOrNull
        if (asLong != null) return asLong.toString()
        val asFloat = prim.floatOrNull
        if (asFloat != null) {
            return formatFloat(asFloat)
        }
        return prim.contentOrNull?.ifBlank { null }
    }

    private fun formatFloat(f: Float): String {
        if (f == f.toInt().toFloat()) return f.toInt().toString()
        // 保留最多 2 位小数，去除尾部 0 和多余小数点
        val multi = (f * 100).toInt()
        val str = multi.toString()
        val decimalPos = str.length - 2
        val whole = str.substring(0, decimalPos).ifEmpty { "0" }
        val dec = str.substring(decimalPos)
        val trimmed = dec.trimEnd('0')
        return if (trimmed.isEmpty()) whole else "$whole.$trimmed"
    }

    private fun JsonObject.safeInt(key: String): Int? {
        val prim = this[key] as? JsonPrimitive ?: return null
        return prim.intOrNull ?: prim.contentOrNull?.toIntOrNull()
    }

    private fun JsonObject.safeLong(key: String): Long? {
        val prim = this[key] as? JsonPrimitive ?: return null
        return prim.longOrNull ?: prim.contentOrNull?.toLongOrNull()
    }

    private fun JsonObject.safeFloat(key: String): Float? {
        val prim = this[key] as? JsonPrimitive ?: return null
        return prim.floatOrNull ?: prim.contentOrNull?.toFloatOrNull()
    }
}
