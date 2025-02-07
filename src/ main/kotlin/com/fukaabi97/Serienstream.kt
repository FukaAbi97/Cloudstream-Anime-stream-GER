package com.fukaabi97

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Serienstream : MainAPI() {
    override var mainUrl = "https://www.burning-series.to"
    override var name = "BurningSeries"
    override val hasMainPage = true
    override var lang = "de"

    override val supportedTypes = setOf(
        TvType.Series,
        TvType.Movie,
        TvType.Anime
    )

    // Laden der Hauptseite
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document
        val item = arrayListOf<HomePageList>()
        document.select("div.carousel").map { ele ->
            val header = ele.selectFirst("h2")?.text() ?: return@map
            val home = ele.select("div.coverListItem").mapNotNull {
                it.toSearchResult()
            }
            if (home.isNotEmpty()) item.add(HomePageList(header, home))
        }
        return HomePageResponse(item)
    }

    // Suchen
    override suspend fun search(query: String): List<SearchResponse> {
        val json = app.post(
            "$mainUrl/ajax/search",
            data = mapOf("keyword" to query),
            referer = "$mainUrl/search",
            headers = mapOf(
                "x-requested-with" to "XMLHttpRequest"
            )
        )
        return tryParseJson<List<AnimeSearch>>(json.text)?.filter {
            it.link.contains("/serie/") && !it.link.contains("episode-")
        }?.map {
            newAnimeSearchResponse(
                it.title?.replace(Regex("</?em>"), "") ?: "",
                fixUrl(it.link),
                TvType.Series
            ) {}
        } ?: throw ErrorLoadingException()
    }

    // Laden der Detailseite
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("div.series-title span")?.text() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.seriesCoverBox img")?.attr("data-src"))
        val tags = document.select("div.genres li a").map { it.text() }
        val year = document.selectFirst("span[itemprop=startDate] a")?.text()?.toIntOrNull()
        val description = document.select("p.seri_des").text()
        val actor = document.select("li:contains(Schauspieler:) ul li a").map { it.select("span").text() }

        val episodes = mutableListOf<Episode>()
        document.select("div#stream > ul:first-child li").map { ele ->
            val page = ele.selectFirst("a")
            val epsDocument = app.get(fixUrl(page?.attr("href") ?: return@map)).document
            epsDocument.select("div#stream > ul:nth-child(4) li").mapNotNull { eps ->
                episodes.add(
                    Episode(
                        fixUrl(eps.selectFirst("a")?.attr("href") ?: return@mapNotNull null),
                        episode = eps.selectFirst("a")?.text()?.toIntOrNull(),
                        season = page.text().toIntOrNull()
                    )
                )
            }
        }

        return newAnimeLoadResponse(
            title,
            url,
            TvType.Series
        ) {
            engName = title
            posterUrl = poster
            this.year = year
            addEpisodes(DubStatus.Subbed, episodes)
            addActors(actor)
            plot = description
            this.tags = tags
        }
    }

    // Laden der Streaming-Links
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        document.select("div.hosterSiteVideo ul li").map {
            Triple(
                it.attr("data-lang-key"),
                it.attr("data-link-target"),
                it.select("h4").text()
            )
        }.filter {
            it.third != "Vidoza"
        }.apmap {
            val redirectUrl = app.get(fixUrl(it.second)).url
            val lang = it.first.getLanguage(document)
            val name = "${it.third} [${lang}]"
            callback.invoke(
                ExtractorLink(
                    name,
                    name,
                    redirectUrl,
                    redirectUrl,
                    null,
                    null,
                    null,
                    null
                )
            )
        }

        return true
    }

    private fun Element.toSearchResult(): AnimeSearchResponse? {
        val
