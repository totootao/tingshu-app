package com.totootao.tingshu.sources.builtin

import com.github.eprendre.tingshu.extensions.config
import com.github.eprendre.tingshu.extensions.splitQuery
import com.github.eprendre.tingshu.sources.AudioUrlDirectExtractor
import com.github.eprendre.tingshu.sources.AudioUrlExtractor
import com.github.eprendre.tingshu.sources.TingShu
import com.github.eprendre.tingshu.utils.*
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.json.responseJson
import org.jsoup.Jsoup
import java.net.URL
import java.net.URLEncoder

/**
 * LibriVox(移植自原版 LibriVox)
 */
object LibriVox : TingShu() {
    override fun getSourceId(): String {
        return "b722b6d38f1a4a25b453e8f84e5e331a"
    }

    override fun getUrl(): String {
        return "https://librivox.org"
    }

    override fun getName(): String {
        return "LibriVox"
    }

    override fun getDesc(): String {
        return "服务器位于: 美国\n需要科学上网才能正常打开封面和播放。\n当之无愧的全世界最大的自制有声书社区，都是志愿者录制的公共版权的英语有声书。"
    }

    override fun isWebViewNotRequired(): Boolean {
        return true
    }

    override fun search(keywords: String, page: Int): Pair<List<Book>, Int> {
        val encodedKeywords = URLEncoder.encode(keywords, "utf-8")
        val url = "https://librivox.org/advanced_search?title=&author=&reader=&keywords=&genre_id=0&status=all&project_type=either&recorded_language=&sort_order=alpha&search_page=$page&search_form=advanced&q=$encodedKeywords"
        val result = Fuel.get(url)
            .header("X-Requested-With" to "XMLHttpRequest")
            .responseJson()
        val jsonObject = result.third.get().obj()
        val paginationJson = jsonObject.getString("pagination")
        var totalPage = 1
        if (!paginationJson.isNullOrEmpty()) {
            val pagination = Jsoup.parse(paginationJson)
            totalPage = pagination.selectFirst(".last")?.attr("data-page_number")?.toIntOrNull() ?: 1
        }

        val doc = Jsoup.parse(jsonObject.getString("results"))

        val list = ArrayList<Book>()
        val elementList = doc.select(".catalog-result")
        elementList.forEach { element ->
            try {
                val coverUrl = element.selectFirst(".book-cover > img")?.absUrl("src") ?: ""
                val bookUrl = element.selectFirst(".result-data > h3 > a")?.absUrl("href") ?: return@forEach
                val title = element.selectFirst(".result-data > h3 > a")?.text() ?: return@forEach
                val author = element.select(".book-author > a").text()
                val infos = element.select(".book-meta").text().split("|")
                val status = infos.getOrNull(0)?.trim() ?: ""
                val artist = infos.getOrNull(1)?.trim() ?: ""
                val intro = infos.getOrNull(2)?.trim() ?: ""
                if (!bookUrl.startsWith("https://forum.librivox.org")) {
                    list.add(Book(coverUrl, bookUrl, title, author, artist).apply {
                        this.intro = intro
                        this.status = status
                        this.sourceId = getSourceId()
                    })
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return Pair(list, totalPage)
    }

    override fun getBookDetailInfo(bookUrl: String, loadEpisodes: Boolean, loadFullPages: Boolean): BookDetail {
        val doc = Jsoup.connect(bookUrl).config(true).get()

        val episodes = doc.select(".chapter-download > tbody > tr > td > .chapter-name").map {
            Episode(it.text(), it.absUrl("href"))
        }
        val intro = doc.selectFirst(".description")?.text()
        return BookDetail(episodes, intro)
    }

    override fun getAudioUrlExtractor(): AudioUrlExtractor {
        return AudioUrlDirectExtractor
    }

    override fun getCategoryMenus(): List<CategoryMenu> {
        fun genreUrl(key: Int) =
            "https://librivox.org/search/get_results?primary_key=$key&search_category=genre&sub_category=&search_page=1&search_order=catalog_date&project_type=either"
        val menu1 = CategoryMenu(
            "Children", listOf(
                CategoryTab("Children's Fiction", genreUrl(1)),
                CategoryTab("Action & Adventure", genreUrl(37)),
                CategoryTab("Animals & Nature", genreUrl(38)),
                CategoryTab("Myths, Legends & Fairy Tales", genreUrl(39)),
                CategoryTab("Family", genreUrl(40)),
                CategoryTab("General", genreUrl(41)),
                CategoryTab("Historical", genreUrl(42)),
                CategoryTab("Poetry", genreUrl(43)),
                CategoryTab("Religion", genreUrl(44)),
                CategoryTab("School", genreUrl(45)),
                CategoryTab("Short works", genreUrl(46)),
                CategoryTab("Non-fiction", genreUrl(2)),
                CategoryTab("Non-fiction > Arts", genreUrl(47)),
                CategoryTab("Non-fiction > General", genreUrl(48)),
                CategoryTab("Non-fiction > Reference", genreUrl(49)),
                CategoryTab("Non-fiction > Religion", genreUrl(50)),
                CategoryTab("Non-fiction > Science", genreUrl(51))
            )
        )
        val menu2 = CategoryMenu(
            "Fantastic", listOf(
                CategoryTab("Fantastic Fiction", genreUrl(13)),
                CategoryTab("Myths, Legends & Fairy Tales", genreUrl(11)),
                CategoryTab("Horror & Supernatural Fiction", genreUrl(16)),
                CategoryTab("Gothic Fiction", genreUrl(17)),
                CategoryTab("Science Fiction", genreUrl(30)),
                CategoryTab("Fantasy Fiction", genreUrl(55))
            )
        )
        val menu3 = CategoryMenu(
            "Fiction", listOf(
                CategoryTab("General Fiction", genreUrl(15)),
                CategoryTab("Published before 1800", genreUrl(52)),
                CategoryTab("Published 1800-1900", genreUrl(53)),
                CategoryTab("Published 1900 onward", genreUrl(54)),
                CategoryTab("Historical Fiction", genreUrl(18)),
                CategoryTab("Humorous Fiction", genreUrl(19)),
                CategoryTab("Literary Fiction", genreUrl(20)),
                CategoryTab("Nature & Animal Fiction", genreUrl(21)),
                CategoryTab("Nautical & Marine Fiction", genreUrl(23))
            )
        )
        val menu4 = CategoryMenu(
            "Nonfiction", listOf(
                CategoryTab("Non-fiction", genreUrl(36)),
                CategoryTab("War & Military", genreUrl(73)),
                CategoryTab("Animals", genreUrl(77)),
                CategoryTab("Art, Design & Architecture", genreUrl(78)),
                CategoryTab("Bibles", genreUrl(79)),
                CategoryTab("Biography & Autobiography", genreUrl(80)),
                CategoryTab("Memoirs", genreUrl(111)),
                CategoryTab("Business & Economics", genreUrl(81)),
                CategoryTab("Education", genreUrl(83)),
                CategoryTab("Essays & Short Works", genreUrl(84)),
                CategoryTab("History", genreUrl(87)),
                CategoryTab("History > Antiquity", genreUrl(113)),
                CategoryTab("History > Middle Ages", genreUrl(114)),
                CategoryTab("History > Early Modern", genreUrl(115)),
                CategoryTab("History > Modern (19th C)", genreUrl(116)),
                CategoryTab("Modern (20th C)", genreUrl(117)),
                CategoryTab("Nature", genreUrl(96)),
                CategoryTab("Philosophy", genreUrl(98)),
                CategoryTab("Political Science", genreUrl(99)),
                CategoryTab("Psychology", genreUrl(100)),
                CategoryTab("Religion", genreUrl(102)),
                CategoryTab("Science", genreUrl(103)),
                CategoryTab("Self-Help", genreUrl(104)),
                CategoryTab("Social Science", genreUrl(105)),
                CategoryTab("Travel & Geography", genreUrl(108))
            )
        )
        val menu5 = CategoryMenu(
            "Others", listOf(
                CategoryTab("Action & Adventure Fiction", genreUrl(3)),
                CategoryTab("Crime & Mystery Fiction", genreUrl(5)),
                CategoryTab("Detective Fiction", genreUrl(22)),
                CategoryTab("Plays", genreUrl(24)),
                CategoryTab("Poetry", genreUrl(25)),
                CategoryTab("Romance", genreUrl(27)),
                CategoryTab("Satire", genreUrl(29)),
                CategoryTab("Short Stories", genreUrl(31)),
                CategoryTab("Short Stories > Anthologies", genreUrl(75)),
                CategoryTab("Single Author Collections", genreUrl(76)),
                CategoryTab("War & Military Fiction", genreUrl(34)),
                CategoryTab("Westerns", genreUrl(35))
            )
        )
        return listOf(menu1, menu2, menu3, menu4, menu5)
    }

    override fun getCategoryList(url: String): Category {
        val result = Fuel.get(url)
            .header("X-Requested-With" to "XMLHttpRequest")
            .responseJson()
        val jsonObject = result.third.get().obj()
        val currentPage = jsonObject.getJSONObject("input").getInt("search_page")
        val pagination = Jsoup.parse(jsonObject.getString("pagination"))
        val totalPage = pagination.selectFirst(".last")?.attr("data-page_number")?.toIntOrNull() ?: 1
        val queryMap = splitQuery(URL(url))
        queryMap["search_page"] = (currentPage + 1).toString()
        val nextUrl = "https://librivox.org/search/get_results?" + queryMap.map { "${it.key}=${it.value}" }.joinToString("&")

        val doc = Jsoup.parse(jsonObject.getString("results"))

        val list = ArrayList<Book>()
        val elementList = doc.select(".catalog-result")
        elementList.forEach { element ->
            val coverUrl = element.selectFirst(".book-cover > img")?.absUrl("src") ?: ""
            val bookUrl = element.selectFirst(".result-data > h3 > a")?.absUrl("href") ?: return@forEach
            val title = element.selectFirst(".result-data > h3 > a")?.text() ?: return@forEach
            val author = element.select(".book-author > a").text()
            val infos = element.select(".book-meta").text().split("|")
            val status = infos.getOrNull(0)?.trim() ?: ""
            val artist = infos.getOrNull(1)?.trim() ?: ""
            val intro = infos.getOrNull(2)?.trim() ?: ""
            list.add(Book(coverUrl, bookUrl, title, author, artist).apply {
                this.intro = intro
                this.status = status
                this.sourceId = getSourceId()
            })
        }
        return Category(list, currentPage, totalPage, url, nextUrl)
    }
}
