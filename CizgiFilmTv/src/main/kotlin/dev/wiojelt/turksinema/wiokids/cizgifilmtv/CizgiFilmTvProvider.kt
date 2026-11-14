package dev.wiojelt.turksinema.wiokids.cizgifilmtv

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.vahsikelebek.kids.KidsPublicEmbedProvider
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale

class CizgiFilmTvProvider : KidsPublicEmbedProvider() {
    override var mainUrl = "https://www.cizgifilm.tv"
    override var name = "ÇizgiFilm TV"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Cartoon, TvType.TvSeries, TvType.Movie)

    override val mainPage = mainPageOf(
        "$mainUrl/cizgi-filmler" to "Çizgi Filmler",
        "$mainUrl/yeni" to "Yeni Bölümler",
        "$mainUrl/populer" to "Popüler",
        "$mainUrl/kategori/egitici" to "Eğitici",
        "$mainUrl/kategori/macera" to "Macera",
        "$mainUrl/kategori/hayvan" to "Hayvanlar",
        "$mainUrl/kategori/araba" to "Arabalar",
        "$mainUrl/kategori/yerli" to "Yerli Çizgi Filmler",
        "$mainUrl/masallar" to "Masallar",
        "$mainUrl/ninniler" to "Ninniler",
    )

    @Volatile private var catalogCache: Pair<Long, List<SearchResponse>>? = null

    private fun slugify(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase(Locale.ROOT)
        .replace('ı', 'i')
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')

    private fun cleanCardTitle(text: String): String = text
        .replace(Regex("\\s+\\d+\\s+adet\\s+icerik\\s+var\\.?$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+\\d{1,2}:\\d{2}$"), "")
        .trim()

    private fun posterFromAnchor(anchor: org.jsoup.nodes.Element): String? {
        val raw = anchor.selectFirst("img")?.attr("src")
            ?: anchor.selectFirst("img")?.attr("data-src")
            ?: anchor.parent()?.selectFirst("img")?.attr("src")
            ?: anchor.parent()?.selectFirst("img")?.attr("data-src")
        return absolute(raw)
    }

    private fun seriesItems(doc: Document): List<SearchResponse> {
        return doc.select("a[href]").mapNotNull { a ->
            val text = a.text().trim()
            if (!text.contains("adet içerik var", true) && !text.contains("adet icerik var", true)) return@mapNotNull null
            val href = absolute(a.attr("href")) ?: return@mapNotNull null
            val title = cleanCardTitle(text)
            if (title.isBlank()) return@mapNotNull null
            newTvSeriesSearchResponse(title, href, TvType.Cartoon) {
                posterUrl = posterFromAnchor(a)
            }
        }.distinctBy { it.url }
    }

    private fun episodeItems(doc: Document): List<SearchResponse> {
        return doc.select("a[href]").mapNotNull { a ->
            val href = absolute(a.attr("href")) ?: return@mapNotNull null
            if (!href.startsWith(mainUrl)) return@mapNotNull null
            val path = href.removePrefix(mainUrl).substringBefore('?').trim('/')
            val segments = path.split('/').filter(String::isNotBlank)
            if (segments.size < 2) return@mapNotNull null
            if (segments.first() in setOf("kategori", "kanal", "oyun", "resim", "masal", "ninni", "blog")) return@mapNotNull null
            val title = cleanCardTitle(a.attr("title").ifBlank { a.text() })
            if (title.length < 2) return@mapNotNull null
            newMovieSearchResponse(title, href, TvType.Cartoon) {
                posterUrl = posterFromAnchor(a)
            }
        }.distinctBy { it.url }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val base = request.data.trimEnd('/')
        val url = if (page > 1) "$base/$page" else base
        val doc = app.get(url, headers = wioHeaders, timeout = 30000L).document
        val items = if (request.data.contains("cizgi-filmler")) seriesItems(doc) else episodeItems(doc)
        return newHomePageResponse(request.name, items, items.isNotEmpty())
    }

    private suspend fun catalog(): List<SearchResponse> {
        val now = System.currentTimeMillis()
        catalogCache?.takeIf { now - it.first < 15 * 60 * 1000L }?.let { return it.second }
        val result = mutableListOf<SearchResponse>()
        for (page in 1..12) {
            val url = if (page == 1) "$mainUrl/cizgi-filmler" else "$mainUrl/cizgi-filmler/$page"
            val doc = runCatching { app.get(url, headers = wioHeaders, timeout = 20000L).document }.getOrNull() ?: break
            val chunk = seriesItems(doc)
            if (chunk.isEmpty()) break
            result.addAll(chunk)
        }
        val distinct = result.distinctBy { it.url }
        if (distinct.isNotEmpty()) catalogCache = now to distinct
        return distinct
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()

        val direct = "$mainUrl/" + slugify(q)
        val guessed = runCatching {
            val doc = app.get(direct, headers = wioHeaders, timeout = 16000L).document
            val title = pageTitle(doc)
            if (!title.isNullOrBlank() && !title.contains("404", true)) {
                newTvSeriesSearchResponse(title.substringBefore(" - "), direct, TvType.Cartoon) {
                    posterUrl = pagePoster(doc)
                }
            } else null
        }.getOrNull()

        val matches = catalog().filter { it.name.contains(q, true) }.take(50)
        return listOfNotNull(guessed).plus(matches).distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = runCatching { app.get(url, headers = wioHeaders, timeout = 30000L).document }.getOrNull() ?: return null
        val title = pageTitle(doc)?.substringBefore(" - Çizgifilm.tv")?.trim() ?: return null
        val poster = pagePoster(doc)
        val plot = pagePlot(doc)
        val prefix = url.trimEnd('/') + "/"

        val episodes = doc.select("a[href]").mapNotNull { a ->
            val href = absolute(a.attr("href")) ?: return@mapNotNull null
            if (!href.startsWith(prefix)) return@mapNotNull null
            val epTitle = cleanCardTitle(a.attr("title").ifBlank { a.text() })
            if (epTitle.isBlank()) return@mapNotNull null
            val numbers = seasonEpisode(epTitle)
            newEpisode(payload(href, title, epTitle, numbers.first, numbers.second)) {
                name = epTitle
                season = numbers.first ?: 1
                episode = numbers.second
                posterUrl = posterFromAnchor(a)
            }
        }.distinctBy { it.data }

        return if (episodes.isNotEmpty()) {
            newTvSeriesLoadResponse(title, url, TvType.Cartoon, episodes) {
                posterUrl = poster
                this.plot = plot
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Cartoon, payload(url, title, title)) {
                posterUrl = poster
                this.plot = plot
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean = resolvePublicPage(data, subtitleCallback, callback)
}
