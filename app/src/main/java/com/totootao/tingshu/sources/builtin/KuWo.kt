package com.totootao.tingshu.sources.builtin

import com.github.eprendre.tingshu.sources.AudioUrlDirectExtractor
import com.github.eprendre.tingshu.sources.AudioUrlExtractor
import com.github.eprendre.tingshu.sources.TingShu
import com.github.eprendre.tingshu.utils.*
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.json.responseJson
import java.net.URLEncoder
import kotlin.math.ceil

/**
 * 移植自原版 sources_by_eprendre.KuWo
 */
object KuWo : TingShu() {
    override fun getSourceId(): String {
        return "502efedf0613460a9967d9e86ce2b24c"
    }

    override fun getUrl(): String {
        return "http://kuwo.cn/downtingshu"
    }

    override fun getName(): String {
        return "酷我畅听"
    }

    override fun getDesc(): String {
        return "推荐指数:5星 ⭐⭐⭐⭐⭐"
    }

    override fun search(keywords: String, page: Int): Pair<List<Book>, Int> {
        val encodedKeywords = URLEncoder.encode(keywords, "utf-8") //编码
        val url = "http://baby.kuwo.cn/tingshu/api/search/Search?rn=10&type=album&version=8.5.6.1&wd=${encodedKeywords}&pn=${page}&kweexVersion=1.0.2"

        val data = Fuel.get(url)
            .responseJson()
            .third.get().obj().getJSONObject("data")
        val pageCount = ceil(data.getInt("total").toFloat() / 10).toInt()
        val list = ArrayList<Book>()
        val iData = data.getJSONArray("data")
        (0 until iData.length()).forEach { i ->
            val item = iData.getJSONObject(i)
            val albumId = item.getString("albumId")
            val coverUrl = item.getString("coverImg")
            val bookUrl = "http://baby.kuwo.cn/tingshu/api/data/album/songs?albumId=${albumId}&online=0&kweexVersion=1.0.2"
            val title = item.getString("albumName")
            val author = ""
            val artist = "播音: ${item.getString("artistName")}"
            val status = "共 ${item.getInt("songTotal")} 章"
            val intro = item.getString("title")
            list.add(Book(coverUrl, bookUrl, title, author, artist).apply {
                this.status = status
                this.intro = intro
                this.sourceId = getSourceId()
            })
        }

        return Pair(list, pageCount)
    }

    override fun isWebViewNotRequired(): Boolean {
        return true
    }

    override fun getAudioUrlExtractor(): AudioUrlExtractor {
        return AudioUrlDirectExtractor
    }

    override fun getCategoryMenus(): List<CategoryMenu> {
        fun filterUrl(classifyId: Int, categoryId: Int) =
            "http://baby.kuwo.cn/tingshu/api/filter/albums?sortType=tsScore&classifyId=$classifyId&rn=20&categoryId=$categoryId&pn=1&kweexVersion=1.0.2"
        val menu1 = CategoryMenu(
            "小说", listOf(
                CategoryTab("付费排行", "http://baby.kuwo.cn/tingshu/api/page/boutique/getBoutiqueData?pt=1&rn=100&version=8.5.6.1&pn=1&kweexVersion=1.0.2"),
                CategoryTab("免费排行", "http://baby.kuwo.cn/tingshu/api/page/boutique/getBoutiqueData?pt=2&rn=100&version=8.5.6.1&pn=1&kweexVersion=1.0.2"),
                CategoryTab("都市传说", filterUrl(42, 2)),
                CategoryTab("玄幻奇幻", filterUrl(44, 2)),
                CategoryTab("悬疑推理", filterUrl(45, 2)),
                CategoryTab("现代言情", filterUrl(41, 2)),
                CategoryTab("武侠仙侠", filterUrl(48, 2)),
                CategoryTab("穿越架空", filterUrl(52, 2)),
                CategoryTab("经典小说", filterUrl(64, 2)),
                CategoryTab("青春校园", filterUrl(55, 2)),
                CategoryTab("历史军事", filterUrl(56, 2)),
                CategoryTab("科幻竞技", filterUrl(57, 2)),
                CategoryTab("古代言情", filterUrl(207, 2)),
                CategoryTab("能力提升", filterUrl(82, 4)),
                CategoryTab("人文艺术", filterUrl(77, 4)),
                CategoryTab("国学文化", filterUrl(78, 4)),
                CategoryTab("成功法则", filterUrl(79, 4)),
                CategoryTab("外语精通", filterUrl(76, 4)),
                CategoryTab("养生健康", filterUrl(81, 4)),
                CategoryTab("酷我读书", filterUrl(211, 4)),
                CategoryTab("国学经典", filterUrl(117, 9)),
                CategoryTab("历史小说", filterUrl(181, 9)),
                CategoryTab("纪实档案", filterUrl(118, 9)),
                CategoryTab("历史传奇", filterUrl(119, 9)),
                CategoryTab("人物传奇", filterUrl(120, 9)),
                CategoryTab("文化讲堂", filterUrl(121, 9)),
                CategoryTab("百家讲坛", filterUrl(212, 9))
            )
        )
        return listOf(menu1)
    }

    override fun getCategoryList(url: String): Category {
        val currentPage = Regex("pn=(\\d+)").find(url)!!.groupValues[1].toInt()
        val pageCount: Int
        val data = Fuel.get(url).responseJson().third.get().obj().getJSONObject("data")
        if (data.has("pageInfo")) {
            val total = data.getJSONObject("pageInfo").getInt("total")
            pageCount = ceil(total.toFloat() / 100).toInt()
        } else {
            val total = data.getInt("total")
            pageCount = ceil(total.toFloat() / 20).toInt()
        }
        val nextUrl = if (currentPage < pageCount) {
            url.replace(Regex("pn=(\\d+)"), "pn=${currentPage + 1}")
        } else ""

        val list = ArrayList<Book>()
        if (data.has("topDatas")) {
            val topDatas = data.getJSONArray("topDatas")
            (0 until topDatas.length()).forEach { i ->
                val item = topDatas.getJSONObject(i).getJSONObject("albums")
                val albumId = item.getString("albumId")
                val coverUrl = item.getString("img")
                val bookUrl = "http://baby.kuwo.cn/tingshu/api/data/album/songs?albumId=${albumId}&online=0&kweexVersion=1.0.2"
                val title = item.getString("name")
                val author = ""
                val artist = ""
                val status = "共 ${item.getInt("songTotal")} 章"
                val intro = item.getString("title")
                list.add(Book(coverUrl, bookUrl, title, author, artist).apply {
                    this.status = status
                    this.intro = intro
                    this.sourceId = getSourceId()
                })
            }

        } else {
            val iData = data.getJSONArray("data")
            (0 until iData.length()).forEach { i ->
                val item = iData.getJSONObject(i)
                val albumId = item.getString("albumId")
                val coverUrl = item.getString("coverImg")
                val bookUrl = "http://baby.kuwo.cn/tingshu/api/data/album/songs?albumId=${albumId}&online=0&kweexVersion=1.0.2"
                val title = item.getString("albumName")
                val author = ""
                val artist = "播音: ${item.getString("artistName")}"
                val status = "共 ${item.getInt("songTotal")} 章"
                val intro = item.getString("title")
                list.add(Book(coverUrl, bookUrl, title, author, artist).apply {
                    this.status = status
                    this.intro = intro
                    this.sourceId = getSourceId()
                })
            }
        }
        return Category(list, currentPage, pageCount, url, nextUrl)
    }

    override fun getBookDetailInfo(bookUrl: String, loadEpisodes: Boolean, loadFullPages: Boolean): BookDetail {
        val episodes = ArrayList<Episode>()
        if (loadEpisodes) {
            val data = Fuel.get(bookUrl).responseJson().third.get().obj().getJSONArray("data")
            (0 until data.length()).forEach {
                val item = data.getJSONObject(it)
                val name = item.getString("name")
                val musicrid = item.getString("musicrid")
                val url = "http://antiserver.kuwo.cn/anti.s?useless=/resource/&format=mp3&rid=MUSIC_${musicrid}&response=res&type=convert_url"
                episodes.add(Episode(name, url))
            }
        }
        return BookDetail(episodes)
    }
}
