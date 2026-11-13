package com.sezonlukdizi

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink

class SezonlukDiziProvider : MainAPI() {
    override var mainUrl = "https://sezonlukdizi6.com"
    override var name = "SezonlukDizi"
    override val supportedTypes = setOf(TvType.TvSeries)
    override var lang = "tr"

    override val hasMainPage = true
    override val mainPage = mainPageOf(
        "$mainUrl/" to "Son Bölümler",
        "$mainUrl/diziler.asp" to "Diziler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val url = if (page <= 1) request.data else request.data
        val doc = runCatching { app.get(url).document }.getOrNull() ?: return newHomePageResponse(request.name, emptyList())
        val items = doc.select("a[href]").mapNotNull { a ->
            val href = fixUrlNull(a.attr("href")) ?: return@mapNotNull null
            val title = a.attr("title").ifBlank { a.text() }.trim()
            if (title.length < 2) return@mapNotNull null
            val poster = fixUrlNull(a.selectFirst("img")?.let { it.attr("data-src").ifBlank { it.attr("src") } })
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = poster }
        }.distinctBy { it.url }.take(30)
        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = runCatching { app.get("$mainUrl/ara.asp?arama=$query").document }.getOrNull() ?: return emptyList()
        return doc.select("a[href]").mapNotNull { a ->
            val href = fixUrlNull(a.attr("href")) ?: return@mapNotNull null
            val title = a.attr("title").ifBlank { a.text() }.trim()
            if (title.length < 2) return@mapNotNull null
            val poster = fixUrlNull(a.selectFirst("img")?.let { it.attr("data-src").ifBlank { it.attr("src") } })
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = poster }
        }.distinctBy { it.url }.take(30)
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = runCatching { app.get(url).document }.getOrNull() ?: return null
        val title = doc.selectFirst("h1, .title")?.text()?.trim() ?: return null
        val poster = fixUrlNull(doc.selectFirst("meta[property=og:image]")?.attr("content"))
        val plot = doc.selectFirst("meta[property=og:description]")?.attr("content")
        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, emptyList()) {
            this.posterUrl = poster
            this.plot = plot
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Bölüm/oynatıcı kaynak çözümleme mantığı siteye özel olarak burada geliştirilebilir.
        return false
    }
}
