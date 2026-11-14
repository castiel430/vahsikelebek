package dev.wiojelt.turksinema.wiokids.cizgifilmizleinfo

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.vahsikelebek.kids.KidsPublicEmbedProvider
import org.jsoup.nodes.Document
import java.text.Normalizer
import java.util.Locale

class CizgiFilmIzleInfoProvider : KidsPublicEmbedProvider() {
    override var mainUrl = "https://www.cizgifilmizle.info"
    override var name = "ÇizgiFilmİzle Info"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Cartoon, TvType.TvSeries, TvType.Movie)

    override val mainPage = mainPageOf(
        "$mainUrl/cizgi-film-arsivi" to "Çizgi Film Arşivi",
        "$mainUrl/kategori/cizgi-film-izle" to "Son Bölümler",
        "$mainUrl/kategori/cocuk-sarkilari" to "Çocuk Şarkıları",
        "$mainUrl/kategori/sirinler" to "Şirinler",
        "$mainUrl/kategori/ari-maya" to "Arı Maya",
        "$mainUrl/kategori/itfaiyeci-sam" to "İtfaiyeci Sam",
        "$mainUrl/kategori/istanbul-muhafizlari" to "İstanbul Muhafızları",
    )

    @Volatile private var archiveCache: Pair<Long, List<SearchResponse>>? = null

    private fun slugify(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase(Locale.ROOT)
        .replace('ı', 'i')
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')

    private fun poster(anchor: org.jsoup.nodes.Element): String? {
        val img = anchor.selectFirst("img")
            ?: anchor.parent()?.selectFirst("img")
            ?: anchor.parents().firstOrNull { it.selectFirst("img") != null }?.selectFirst("img")
        return absolute(img?.attr("src") ?: img?.attr("data-src"))
    }

    private fun seriesItems(doc: Document): List<SearchResponse> {
        val blocked = setOf("cizgi-film-izle", "tv-kanallari", "cizgi-film-karakterleri")
        return doc.select("a[href*=/kategori/]").mapNotNull { a ->
            val href = absolute(a.attr("href")) ?: return@mapNotNull null
            val slug = href.substringAfter("/kategori/").substringBefore('/').substringBefore('?')
            if (slug.isBlank() || slug in blocked) return@mapNotNull null
            val title = a.attr("title").ifBlank { a.text() }.trim()
            if (title.length < 2) return@mapNotNull null
            newTvSeriesSearchResponse(title, href, TvType.Cartoon) {
                posterUrl = poster(a)
            }
        }.distinctBy { it.url }
    }

    private fun episodeItems(doc: Document): List<SearchResponse> {
        return doc.select("a[href$=.html], a[href*='.html?']").mapNotNull { a ->
            val href = absolute(a.attr("href")) ?: return@mapNotNull null
            if (!href.startsWith(mainUrl)) return@mapNotNull null
            val title = a.attr("title").ifBlank { a.text() }.trim()
            if (title.length < 3) return@mapNotNull null
            newMovieSearchResponse(title, href, TvType.Cartoon) {
                posterUrl = poster(a)
            }
        }.distinctBy { it.url }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = when {
            request.data.contains("cizgi-film-arsivi") && page > 1 -> "$mainUrl/cizgi-film-arsivi/page/$page"
            request.data.contains("/kategori/") && page > 1 -> request.data.trimEnd('/') + "/page/$page"
            else -> request.data
        }
        val doc = app.get(url, headers = wioHeaders, timeout = 30000L).document
        val items = if (request.data.contains("cizgi-film-arsivi")) seriesItems(doc) else episodeItems(doc)
        return newHomePageResponse(request.name, items, items.isNotEmpty())
    }

    private suspend fun archive(): List<SearchResponse> {
        val now = System.currentTimeMillis()
        archiveCache?.takeIf { now - it.first < 20 * 60 * 1000L }?.let { return it.second }
        val all = mutableListOf<SearchResponse>()
        for (page in 1..6) {
            val url = if (page == 1) "$mainUrl/cizgi-film-arsivi" else "$mainUrl/cizgi-film-arsivi/page/$page"
            val doc = runCatching { app.get(url, headers = wioHeaders, timeout = 20000L).document }.getOrNull() ?: break
            val chunk = seriesItems(doc)
            if (chunk.isEmpty()) break
            all.addAll(chunk)
        }
        val result = all.distinctBy { it.url }
        if (result.isNotEmpty()) archiveCache = now to result
        return result
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()

        val guessedUrl = "$mainUrl/kategori/" + slugify(q)
        val guessed = runCatching {
            val doc = app.get(guessedUrl, headers = wioHeaders, timeout = 16000L).document
            val title = pageTitle(doc)?.substringBefore(" - Çizgi")?.trim()
            if (!title.isNullOrBlank() && episodeItems(doc).isNotEmpty()) {
                newTvSeriesSearchResponse(title, guessedUrl, TvType.Cartoon) {
                    posterUrl = pagePoster(doc)
                }
            } else null
        }.getOrNull()

        val matches = archive().filter { it.name.contains(q, true) }.take(60)
        return listOfNotNull(guessed).plus(matches).distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = runCatching { app.get(url, headers = wioHeaders, timeout = 30000L).document }.getOrNull() ?: return null
        val title = pageTitle(doc)?.substringBefore(" - Çizgi")?.trim() ?: return null
        val image = pagePoster(doc)
        val plot = pagePlot(doc)

        val episodeAnchors = doc.select("a[href$=.html], a[href*='.html?']").mapNotNull { a ->
            val href = absolute(a.attr("href")) ?: return@mapNotNull null
            if (!href.startsWith(mainUrl) || href == url) return@mapNotNull null
            val epTitle = a.attr("title").ifBlank { a.text() }.trim()
            if (epTitle.length < 3) return@mapNotNull null
            Triple(href, epTitle, poster(a))
        }.distinctBy { it.first }

        if (url.contains("/kategori/") && episodeAnchors.isNotEmpty()) {
            val episodes = episodeAnchors.mapIndexed { index, (href, epTitle, epPoster) ->
                val numbers = seasonEpisode(epTitle)
                newEpisode(payload(href, title, epTitle, numbers.first, numbers.second ?: index + 1)) {
                    name = epTitle
                    season = numbers.first ?: 1
                    episode = numbers.second ?: index + 1
                    posterUrl = epPoster
                }
            }
            return newTvSeriesLoadResponse(title, url, TvType.Cartoon, episodes) {
                posterUrl = image
                this.plot = plot
            }
        }

        return newMovieLoadResponse(title, url, TvType.Cartoon, payload(url, null, title)) {
            posterUrl = image
            this.plot = plot
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean = resolvePublicPage(data, subtitleCallback, callback)
}
