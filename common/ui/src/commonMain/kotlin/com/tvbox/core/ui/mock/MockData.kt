package com.tvbox.core.ui.mock

import com.tvbox.core.model.MovieSource
import com.tvbox.core.model.VodClass
import com.tvbox.core.model.VodEpisode
import com.tvbox.core.model.VodInfo

// commonMain 中没有 java.lang.System，Mock 数据使用固定时间戳即可
private object MockClock {
    fun currentTimeMillis(): Long = 1_760_000_000_000L
}

/**
 * UI 展示 Mock 数据
 *
 * 在真实网络请求与本地持久化尚未完全打通前，
 * 为所有页面提供视觉设计所需的模拟数据。
 */
object MockData {

    // ================= 影视源 =================
    val movieSources: List<MovieSource> = listOf(
        MovieSource(
            key = "csp_bestzy",
            name = "最好资源网",
            api = "https://api.bestzy.com/pro.php",
            type = 1,
            enabled = true,
            searchable = true,
            filterable = true,
            logo = "",
            desc = "高清4K资源站，更新速度快",
            order = 1,
            lastUpdate = MockClock.currentTimeMillis() - 3600_000
        ),
        MovieSource(
            key = "csp_yszy",
            name = "影视资源网",
            api = "https://api.yszy.com/pro.php",
            type = 1,
            enabled = true,
            searchable = true,
            filterable = true,
            desc = "华语电影电视剧资源丰富",
            order = 2
        ),
        MovieSource(
            key = "csp_wolong",
            name = "卧龙资源",
            api = "https://wolongzy.net/api.php/provide/vod/",
            type = 1,
            enabled = true,
            searchable = true,
            filterable = false,
            desc = "老牌资源站，资源全而稳",
            order = 3
        ),
        MovieSource(
            key = "csp_douban",
            name = "豆瓣采集",
            api = "https://douban.com/api.php/provide/vod/",
            type = 0,
            enabled = false,
            searchable = false,
            desc = "豆瓣热门榜单元数据",
            order = 4
        ),
        MovieSource(
            key = "csp_anime",
            name = "动漫资源",
            api = "https://animezy.com/api.php",
            type = 1,
            enabled = true,
            searchable = true,
            filterable = true,
            desc = "日漫/国漫/美漫专用",
            order = 5
        ),
        MovieSource(
            key = "csp_jinpin",
            name = "精品蓝光",
            api = "https://jpzy.com/api.php",
            type = 1,
            enabled = true,
            searchable = true,
            filterable = true,
            desc = "高码率蓝光原盘资源",
            order = 6
        )
    )

    val sourceSubscriptions = listOf(
        Pair("官方源合集", "https://tvbox.github.io/sources/official.json"),
        Pair("社区源合集", "https://tvbox.github.io/sources/community.json"),
        Pair("匿名单仓", "https://12586.kstore.space/123.txt"),
        Pair("游魂单仓", "https://www.iyouhun.com/tv/dc"),
        Pair("无名单仓", "https://github.catvod.com/raw.githubusercontent.com/tushen6/Tomorrow/master/lmw.json"),
        Pair("欧歌单仓", "https://双龙.v.nxog.top/nxog/oua.php"),
        Pair("匿名多仓", "https://12586.kstore.space/123.json"),
        Pair("饭太硬", "http://www.饭太硬.net/tv"),
        Pair("饭太硬(备用1)", "http://www.饭太硬.art/tv"),
        Pair("饭太硬(备用2)", "http://fty.xxooo.cf/tv"),
        Pair("饭太硬(备用3)", "http://fty.888484.xyz/tv"),
        Pair("饭太硬(备用4)", "http://fty.333232.xyz/tv"),
        Pair("小米", "https://gh-proxy.org/raw.githubusercontent.com/ggrrttyyiii/CatVodSpider/refs/heads/main/json/demo.json"),
        Pair("肥猫", "http://肥猫.net/tv"),
        Pair("王二小", "https://9280.kstore.vip/newwex.json"),
        Pair("嗷呜", "https://9763.kstore.vip/aowu.json"),
        Pair("摸鱼儿", "http://我不是.摸鱼儿.top"),
        Pair("集多", "http://rihou.cc:88/demo.php"),
        Pair("潇洒", "https://9877.kstore.space/one.json"),
        Pair("欧歌", "https://xn--jory77o.v.nxog.top/m"),
        Pair("小虎斑", "http://hb.小虎斑.site:25252/仅供测试"),
        Pair("南风", "https://gh-proxy.com/raw.githubusercontent.com/yoursmile66/TVBox/refs/heads/main/XC.json"),
        Pair("少儿频道", "https://gh-proxy.com/raw.githubusercontent.com/lubin776/0/refs/heads/main/tvbox/b2.json"),
        Pair("戏曲音乐", "https://z.qiqiv.cn/666")
    )

    // ================= 分类 =================
    val categories: List<VodClass> = listOf(
        VodClass("1", "电影"),
        VodClass("2", "电视剧"),
        VodClass("3", "综艺"),
        VodClass("4", "动漫"),
        VodClass("5", "纪录片"),
        VodClass("6", "短剧"),
        VodClass("7", "体育"),
    )

    // ================= 影视数据 =================
    private fun ep(name: String, idx: Int, url: String = "") = VodEpisode(
        episodeId = "ep$idx",
        name = name,
        url = "https://example.com/video/$idx.mp4"
    )

    private val episodesForMovie = listOf(ep("正片", 1))
    private val episodesForDrama = (1..40).map { ep("第${it}集", it) }

    val homeBanner: List<VodInfo> = listOf(
        VodInfo(
            vodId = "banner1", sourceKey = "csp_bestzy",
            vodName = "星际穿越：终极回响",
            vodRemarks = "4K HDR", vodYear = "2025", vodArea = "美国",
            vodClass = "科幻 / 冒险",
            vodActor = "马修·麦康纳 / 安妮·海瑟薇",
            vodDirector = "克里斯托弗·诺兰",
            vodContent = "一支探险队穿越虫洞，寻找人类的下一个家园。",
            vodScore = "9.5"
        ),
        VodInfo(
            vodId = "banner2", sourceKey = "csp_yszy",
            vodName = "长安忆", vodRemarks = "全集",
            vodYear = "2025", vodArea = "中国大陆",
            vodClass = "古装 / 剧情", vodScore = "9.2",
            episodes = episodesForDrama
        ),
        VodInfo(
            vodId = "banner3", sourceKey = "csp_bestzy",
            vodName = "深海迷航", vodRemarks = "蓝光原盘",
            vodYear = "2024", vodArea = "美国",
            vodClass = "灾难 / 惊悚", vodScore = "8.8"
        )
    )

    val trending: List<VodInfo> = listOf(
        VodInfo(vodId = "t1", sourceKey = "csp_bestzy", vodName = "银河边界", vodRemarks = "更新到22集", vodYear = "2025", vodClass = "科幻", vodScore = "9.1", episodes = episodesForDrama),
        VodInfo(vodId = "t2", sourceKey = "csp_yszy", vodName = "暗夜法官", vodRemarks = "1080P", vodYear = "2025", vodClass = "悬疑", vodScore = "8.7", episodes = episodesForMovie),
        VodInfo(vodId = "t3", sourceKey = "csp_bestzy", vodName = "山海之恋", vodRemarks = "40集全", vodYear = "2025", vodClass = "爱情", vodScore = "8.4", episodes = episodesForDrama),
        VodInfo(vodId = "t4", sourceKey = "csp_anime", vodName = "剑与魔法之书", vodRemarks = "新番连载", vodYear = "2025", vodClass = "动漫", vodScore = "9.3"),
        VodInfo(vodId = "t5", sourceKey = "csp_yszy", vodName = "江湖风云录", vodRemarks = "更新到18集", vodYear = "2025", vodClass = "武侠", vodScore = "8.6", episodes = episodesForDrama),
        VodInfo(vodId = "t6", sourceKey = "csp_bestzy", vodName = "最后一个夏天", vodRemarks = "BD1080P", vodYear = "2025", vodClass = "剧情", vodScore = "8.9", episodes = episodesForMovie),
    )

    val movieList: List<VodInfo> = listOf(
        VodInfo(vodId = "m1", sourceKey = "csp_bestzy", vodName = "星际救赎", vodRemarks = "蓝光", vodYear = "2025", vodClass = "科幻", vodScore = "8.5", episodes = episodesForMovie),
        VodInfo(vodId = "m2", sourceKey = "csp_bestzy", vodName = "都市迷局", vodRemarks = "高清", vodYear = "2025", vodClass = "犯罪", vodScore = "8.2", episodes = episodesForMovie),
        VodInfo(vodId = "m3", sourceKey = "csp_jinpin", vodName = "雾港往事", vodRemarks = "4K", vodYear = "2024", vodClass = "剧情", vodScore = "9.0", episodes = episodesForMovie),
        VodInfo(vodId = "m4", sourceKey = "csp_jinpin", vodName = "北极救援", vodRemarks = "蓝光", vodYear = "2025", vodClass = "灾难", vodScore = "7.9", episodes = episodesForMovie),
        VodInfo(vodId = "m5", sourceKey = "csp_yszy", vodName = "故乡的云", vodRemarks = "1080P", vodYear = "2024", vodClass = "文艺", vodScore = "8.6", episodes = episodesForMovie),
        VodInfo(vodId = "m6", sourceKey = "csp_bestzy", vodName = "极速狂飙", vodRemarks = "蓝光", vodYear = "2025", vodClass = "动作", vodScore = "8.1", episodes = episodesForMovie),
        VodInfo(vodId = "m7", sourceKey = "csp_yszy", vodName = "山海平妖录", vodRemarks = "高清", vodYear = "2025", vodClass = "奇幻", vodScore = "7.8", episodes = episodesForMovie),
        VodInfo(vodId = "m8", sourceKey = "csp_jinpin", vodName = "黎明之眼", vodRemarks = "4K HDR", vodYear = "2025", vodClass = "战争", vodScore = "8.8", episodes = episodesForMovie),
    )

    val dramaList: List<VodInfo> = listOf(
        VodInfo(vodId = "d1", sourceKey = "csp_yszy", vodName = "浮生若梦", vodRemarks = "更新到30集", vodYear = "2025", vodClass = "古装", vodScore = "9.2", episodes = episodesForDrama),
        VodInfo(vodId = "d2", sourceKey = "csp_bestzy", vodName = "都市追梦人", vodRemarks = "更新到14集", vodYear = "2025", vodClass = "都市", vodScore = "8.4", episodes = episodesForDrama),
        VodInfo(vodId = "d3", sourceKey = "csp_yszy", vodName = "紫禁风云", vodRemarks = "60集全", vodYear = "2025", vodClass = "历史", vodScore = "8.9", episodes = episodesForDrama),
        VodInfo(vodId = "d4", sourceKey = "csp_bestzy", vodName = "海边的我们", vodRemarks = "更新到22集", vodYear = "2025", vodClass = "青春", vodScore = "8.1", episodes = episodesForDrama),
        VodInfo(vodId = "d5", sourceKey = "csp_yszy", vodName = "悬案调查组", vodRemarks = "更新到16集", vodYear = "2025", vodClass = "刑侦", vodScore = "8.8", episodes = episodesForDrama),
        VodInfo(vodId = "d6", sourceKey = "csp_bestzy", vodName = "春风十里", vodRemarks = "40集全", vodYear = "2025", vodClass = "爱情", vodScore = "8.5", episodes = episodesForDrama),
    )

    val animeList: List<VodInfo> = listOf(
        VodInfo(vodId = "a1", sourceKey = "csp_anime", vodName = "星海守望者", vodRemarks = "连载中", vodYear = "2025", vodClass = "日漫", vodScore = "9.4"),
        VodInfo(vodId = "a2", sourceKey = "csp_anime", vodName = "山海奇谈", vodRemarks = "连载中", vodYear = "2025", vodClass = "国漫", vodScore = "9.1"),
        VodInfo(vodId = "a3", sourceKey = "csp_anime", vodName = "机械战神", vodRemarks = "完结", vodYear = "2024", vodClass = "日漫", vodScore = "8.9"),
        VodInfo(vodId = "a4", sourceKey = "csp_anime", vodName = "魔法少女物语", vodRemarks = "连载中", vodYear = "2025", vodClass = "日漫", vodScore = "8.6"),
        VodInfo(vodId = "a5", sourceKey = "csp_anime", vodName = "凡人修仙传 第3季", vodRemarks = "连载中", vodYear = "2025", vodClass = "国漫", vodScore = "9.2"),
        VodInfo(vodId = "a6", sourceKey = "csp_anime", vodName = "刺客伍六七", vodRemarks = "完结", vodYear = "2024", vodClass = "国漫", vodScore = "9.0"),
    )

    // ================= 搜索热词 =================
    val hotKeywords: List<String> = listOf(
        "长安忆", "星际穿越", "暗夜法官", "山海之恋",
        "剑与魔法之书", "浮生若梦", "银河边界", "最后一个夏天"
    )

    // ================= 详情页示例 =================
    fun sampleVodDetail(): VodInfo = VodInfo(
        vodId = "sample_vod",
        sourceKey = "csp_bestzy",
        vodName = "长安忆",
        vodRemarks = "更新到30集 / 共40集",
        vodYear = "2025",
        vodArea = "中国大陆",
        vodClass = "古装 / 剧情 / 权谋",
        vodActor = "张三 / 李四 / 王五 / 赵六 / 陈七",
        vodDirector = "大导演A",
        vodContent = "唐朝玄宗年间，寒门少年李青云凭借一腔热血与智慧，在长安城中历经艰险，最终从布衣成长为一代名臣，辅佐君王开创盛世繁华。剧情融合朝堂权谋、江湖恩义与儿女情长，展现大唐气象下的家国情怀。全剧制作精良，服化道考究，被观众誉为近年古装剧新标杆。",
        vodScore = "9.2",
        vodLang = "国语中字",
        episodes = episodesForDrama
    )

    // ================= 历史记录示例 =================
    val historySamples: List<VodInfo> = listOf(
        dramaList[0], dramaList[1], movieList[0]
    )

    // ================= 收藏示例 =================
    val favoriteSamples: List<VodInfo> = listOf(
        dramaList[0], dramaList[2], animeList[0], movieList[2], trending[0]
    )
}
