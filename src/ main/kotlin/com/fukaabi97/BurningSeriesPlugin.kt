package com.FukaAbi97

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*


class BurningSeriesPlugin : MainAPI() {
    override var mainUrl = "https://www.burning-series.to"
    override var name = "BurningSeries"
    override val hasMainPage = true
    override var lang = "de"

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.Movie,
        TvType.Series
    )

    // Hauptseite laden
    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
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
            ) {
            }
        } ?: throw ErrorLoadingException()
    }

    // Lade Detailseite
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("div.series-title span")?.text() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.seriesCoverBox img")?.attr("
