package com.tvbox.core.model

import kotlinx.serialization.Serializable

/**
 * 影视信息模型
 */
@Serializable
data class VodInfo(
    /** 影视 ID */
    val vodId: String = "",
    /** 影视名称 */
    val vodName: String = "",
    /** 影视类型 */
    val vodPic: String = "",
    /** 影视别名 */
    val vodRemarks: String = "",
    /** 影视年份 */
    val vodYear: String = "",
    /** 影视地区 */
    val vodArea: String = "",
    /** 影视类型分类 */
    val vodClass: String = "",
    /** 影视演员 */
    val vodActor: String = "",
    /** 影视导演 */
    val vodDirector: String = "",
    /** 影视简介 */
    val vodContent: String = "",
    /** 影视评分 */
    val vodScore: String = "",
    /** 影视语言 */
    val vodLang: String = "",
    /** 影视来源站点 key */
    val sourceKey: String = "",
    /** 选集列表 */
    val episodes: List<VodEpisode> = emptyList(),
    /** 播放器标志 */
    val playerFlag: String = ""
)

/**
 * 影视选集
 */
@Serializable
data class VodEpisode(
    /** 选集 ID */
    val episodeId: String = "",
    /** 选集名称 */
    val name: String = "",
    /** 播放地址 */
    val url: String = "",
    /** 请求头 */
    val header: Map<String, String> = emptyMap(),
    /** 解析规则 */
    val parseRules: List<String> = emptyList()
)

/**
 * 分类信息
 */
@Serializable
data class VodClass(
    /** 分类 ID */
    val typeId: String = "",
    /** 分类名称 */
    val typeName: String = "",
    /** 父分类 ID */
    val typeIdParent: String = "0"
)

/**
 * 搜索结果
 */
@Serializable
data class SearchResult(
    val sourceKey: String = "",
    val vodId: String = "",
    val vodName: String = "",
    val vodPic: String = "",
    val vodRemarks: String = ""
)

/**
 * 分类筛选条件
 */
@Serializable
data class VodFilter(
    val key: String = "",
    val name: String = "",
    val values: List<VodFilterValue> = emptyList(),
    var selectedValue: String = ""
)

/**
 * 筛选值
 */
@Serializable
data class VodFilterValue(
    val value: String = "",
    val name: String = ""
)
