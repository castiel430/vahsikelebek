package com.hdfilmcehennemi

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.nodes.Element

class HDFilmCehennemiProvider : MainAPI() {
    override var mainUrl = "https://www.hdfilmcehennemi.nl"
    override var name = "HDFilmCehennemi"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var lang = "tr"

    override val hasMainPage = true
    override val mainPage = mainPageOf(
        "$mainUrl/" to "Yeni Eklenenler",
        "$mainUrl/category/film-izle-2/" to "Filmler",
        "$mainUrl/yabancidiziizle-15/" to "Diziler"
    )

    private fun Element.toSearchResult(): SearchResponse? {
        val anchor = this.selectFirst("a[href]") ?: return null
        val href = fixUrlNull(anchor.attr("href")) ?: return null
        val title = this.selectFirst("h3, h2, .title, strong")?.text()?.trim()
            ?: anchor.attr("title").ifBlank { anchor.text() }.trim()
        if (title.isBlank()) return null
        val poster = fixUrlNull(this.selectFirst("img")?.let { img ->
            img.attr("data-src").ifBlank { img.attr("src") }
        })
        val isTv = href.contains("/dizi/") || href.contains("dizi")
        return if (isTv) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = poster }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = poster }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val url = if (page <= 1) request.data else "${request.data.removeSuffix("/")}/page/$page/"
        val doc = app.get(url).document
        val items = doc.select("a[href]").mapNotNull { a ->
            val href = fixUrlNull(a.attr("href")) ?: return@mapNotNull null
            if (!href.startsWith(mainUrl)) return@mapNotNull null
            val title = a.selectFirst("h3, h2, strong")?.text()?.trim()
                ?: a.attr("title").trim()
            if (title.isBlank()) return@mapNotNull null
            val parent = a.parent() ?: a
            val poster = fixUrlNull(parent.selectFirst("img")?.let { img ->
                img.attr("data-src").ifBlank { img.attr("src") }
            })
            val isTv = href.contains("/dizi/")
            if (isTv) newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = poster }
            else newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = poster }
        }.distinctBy { it.url }
        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=$query").document
        val results = doc.select("article, .card, .movie, .film, .item, li").mapNotNull { it.toSearchResult() }
        if (results.isNotEmpty()) return results.distinctBy { it.url }
        return doc.select("a[href]").mapNotNull { a ->
            val href = fixUrlNull(a.attr("href")) ?: return@mapNotNull null
            if (!href.startsWith(mainUrl)) return@mapNotNull null
            val t = a.attr("title").ifBlank { a.text() }.trim()
            if (t.length < 2) return@mapNotNull null
            val poster = fixUrlNull(a.selectFirst("img")?.let { it.attr("data-src").ifBlank { it.attr("src") } })
            if (href.contains("/dizi/")) newTvSeriesSearchResponse(t, href, TvType.TvSeries) { this.posterUrl = poster }
            else newMovieSearchResponse(t, href, TvType.Movie) { this.posterUrl = poster }
        }.distinctBy { it.url }.take(30)
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(doc.selectFirst("meta[property=og:image]")?.attr("content")
            ?: doc.selectFirst(".poster img, .film-poster img, img")?.attr("src"))
        val plot = doc.selectFirst("meta[property=og:description]")?.attr("content")
            ?: doc.selectFirst(".description, .ozet, p")?.text()
        val year = Regex("""(19|20)\d{2}""").find(title + " " + (doc.selectFirst(".year, .yil")?.text() ?: ""))
            ?.value?.toIntOrNull()

        return if (url.contains("/dizi/")) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, emptyList()) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Kaynak/iframe çözümleme mantığı siteye özel olarak burada geliştirilebilir.
        return false
    }
}
