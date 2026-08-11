package com.tvbox.core.source

import com.tvbox.core.model.LiveCategory
import com.tvbox.core.model.LiveChannel

/**
 * 直播源解析器
 *
 * 自动识别并兼容两种主流直播源格式：
 *
 * ## 1. TVBox 标准 `.txt` 格式
 *
 * ```
 * 分组名称,#genre#
 * 频道名称,http://example.com/stream.m3u8
 * 频道名称,http://line1.m3u8#http://line2.m3u8
 * ```
 *
 * - 每行一条记录，逗号分隔频道名与地址。
 * - 形如 `xxx,#genre#` 的行作为分组标记，其后的频道归入该分组直至下一分组标记。
 * - 同一频道的多条线路以 `#` 分隔。
 * - 文件起始未声明分组前的频道统一归入「默认分组」。
 *
 * ## 2. M3U / M3U8 格式
 *
 * ```
 * #EXTM3U
 * #EXTINF:-1 tvg-name="CCTV1" tvg-logo="logo.png" group-title="央视频道",CCTV1
 * http://example.com/cctv1.m3u8
 * #EXTINF:-1 group-title="卫视频道",湖南卫视
 * http://example.com/hntv.m3u8
 * ```
 *
 * - `#EXTM3U` 为文件头（可选）。
 * - `#EXTINF` 行携带频道元数据，逗号后为频道名称。
 * - `group-title` 属性指定分组名称。
 * - `#EXTINF` 的下一非注释行为播放地址。
 * - 同名频道的多个 `#EXTINF` 条目合并为多线路。
 */
object LiveSourceParser {

    /** TVBox 分组标记后缀 */
    private const val GENRE_MARKER = "#genre#"

    /** TVBox 多线路分隔符 */
    private const val LINE_SEPARATOR = "#"

    /** 默认分组名 */
    private const val DEFAULT_CATEGORY = "默认分组"

    /** M3U 文件头 */
    private const val M3U_HEADER = "#EXTM3U"

    /** M3U 频道信息行前缀 */
    private const val M3U_INFO = "#EXTINF"

    /** M3U 分组指定行前缀 */
    private const val M3U_GROUP = "#EXTGRP"

    /**
     * 解析直播源文本为分组列表。
     *
     * 自动检测输入格式（M3U 或 TVBox .txt），调用对应解析器。
     *
     * @param text 直播源原始文本
     * @return 分组列表（保留出现顺序，空文本返回空列表）
     */
    fun parse(text: String): List<LiveCategory> {
        if (text.isBlank()) return emptyList()

        // 格式自动检测：以 #EXTM3U 开头或包含 #EXTINF 行即为 M3U 格式
        val trimmed = text.trim()
        if (trimmed.startsWith(M3U_HEADER, ignoreCase = true) ||
            trimmed.contains("\n$M3U_INFO", ignoreCase = true) ||
            trimmed.startsWith(M3U_INFO, ignoreCase = true)
        ) {
            return parseM3u(trimmed)
        }
        return parseTxt(trimmed)
    }

    // ======================== M3U 解析 ========================

    /**
     * 解析 M3U / M3U8 格式直播源。
     *
     * - `#EXTINF` 行提取频道名、分组、logo。
     * - 紧随其后的非注释行为播放地址。
     * - 同名同分组的多个条目合并为多线路频道。
     */
    private fun parseM3u(text: String): List<LiveCategory> {
        val lines = text.lineSequence().map { it.trim() }.toList()

        // 使用 LinkedHashMap 保留分组出现顺序，每组内用 LinkedHashMap 按频道名合并多线路
        val groups = LinkedHashMap<String, LinkedHashMap<String, LiveChannel>>()
        var pendingName: String? = null
        var pendingGroup: String = DEFAULT_CATEGORY
        var pendingLogo: String = ""
        var currentGroup: String = DEFAULT_CATEGORY

        for (line in lines) {
            if (line.isEmpty()) continue

            if (line.startsWith(M3U_HEADER, ignoreCase = true)) {
                continue // 文件头，跳过
            }

            if (line.startsWith(M3U_GROUP, ignoreCase = true)) {
                // #EXTGRP 行直接指定当前分组
                currentGroup = line.substringAfter(':').trim().ifBlank { DEFAULT_CATEGORY }
                continue
            }

            if (line.startsWith(M3U_INFO, ignoreCase = true)) {
                // #EXTINF:-1 attributes,name
                val content = line.substringAfter(':', "").trim()
                // 提取逗号后的频道名（注意：属性部分可能含逗号，但标准格式中名称在最后一个逗号之后）
                val lastComma = content.lastIndexOf(',')
                pendingName = if (lastComma >= 0) content.substring(lastComma + 1).trim() else content.trim()
                // 提取属性
                val attrs = if (lastComma >= 0) content.substring(0, lastComma) else ""
                pendingGroup = extractAttr(attrs, "group-title")?.ifBlank { DEFAULT_CATEGORY } ?: currentGroup
                pendingLogo = extractAttr(attrs, "tvg-logo") ?: ""
                if (pendingName.isBlank()) pendingName = null
                continue
            }

            // 非 # 开头 → 播放地址（可能多线路以 # 分隔）
            if (!line.startsWith("#") && pendingName != null) {
                val urls = line.split(LINE_SEPARATOR)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && isValidStreamUrl(it) }
                if (urls.isNotEmpty()) {
                    val groupMap = groups.getOrPut(pendingGroup) { LinkedHashMap() }
                    val existing = groupMap[pendingName]
                    if (existing != null) {
                        // 同名频道合并多线路
                        groupMap[pendingName] = existing.copy(urls = existing.urls + urls)
                    } else {
                        groupMap[pendingName] = LiveChannel(
                            name = pendingName,
                            urls = urls,
                            logo = pendingLogo
                        )
                    }
                }
                pendingName = null
                pendingLogo = ""
            }
        }

        return groups.map { (groupName, channelMap) ->
            LiveCategory(name = groupName, channels = channelMap.values.toList())
        }
    }

    /**
     * 从 `#EXTINF` 属性串中提取指定属性值。
     *
     * 例如 `tvg-name="CCTV1" group-title="央视频道"` → 提取 `group-title` 返回 `央视频道`。
     */
    private fun extractAttr(attrs: String, key: String): String? {
        val regex = Regex("""$key="([^"]*)"""", RegexOption.IGNORE_CASE)
        return regex.find(attrs)?.groupValues?.getOrNull(1)
    }

    // ======================== TVBox .txt 解析 ========================

    /**
     * 解析 TVBox 标准 .txt 格式直播源。
     */
    private fun parseTxt(text: String): List<LiveCategory> {
        val categories = mutableListOf<LiveCategory>()
        val channelBuffer = mutableListOf<LiveChannel>()
        var currentName = DEFAULT_CATEGORY

        fun flush() {
            if (channelBuffer.isNotEmpty()) {
                categories.add(LiveCategory(name = currentName, channels = channelBuffer.toList()))
                channelBuffer.clear()
            }
        }

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") && !line.endsWith(GENRE_MARKER)) {
                // 跳过空行与普通注释（但分组标记行 #genre# 需放行）
                return@forEach
            }

            val commaIdx = line.indexOf(',')
            if (commaIdx <= 0) return@forEach

            val left = line.substring(0, commaIdx).trim()
            val right = line.substring(commaIdx + 1).trim()

            if (right.endsWith(GENRE_MARKER, ignoreCase = true)) {
                // 新分组：先冲刷上一组
                flush()
                currentName = left.ifBlank { DEFAULT_CATEGORY }
                return@forEach
            }

            // 频道行：right 为地址（可能多线路以 # 分隔）
            // 仅接受以 http/rtmp/rtsp 开头的合法播放地址，过滤 HTML 等无效内容
            val urls = right.split(LINE_SEPARATOR)
                .map { it.trim() }
                .filter { it.isNotEmpty() && isValidStreamUrl(it) }
            if (left.isNotEmpty() && urls.isNotEmpty()) {
                channelBuffer.add(LiveChannel(name = left, urls = urls))
            }
        }
        flush()

        return categories
    }

    // ======================== 通用工具 ========================

    /**
     * 校验是否为合法的播放地址。
     *
     * 仅接受 http(s)://、rtmp://、rtsp://、p3p://、p2p:// 等常见流媒体协议，
     * 用于过滤 HTML 标签、JavaScript 代码等无效内容。
     */
    private fun isValidStreamUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            lower.startsWith("rtmp://") ||
            lower.startsWith("rtsp://") ||
            lower.startsWith("p3p://") ||
            lower.startsWith("p2p://") ||
            lower.startsWith("artp://")
    }
}
