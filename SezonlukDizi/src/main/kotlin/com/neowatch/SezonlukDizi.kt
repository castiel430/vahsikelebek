package com.neowatch

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.extractors.ByseSX
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.delay
import org.jsoup.nodes.Document

class SezonlukDizi : MainAPI() {
    override var mainUrl = "https://sezonlukdizi.cc"
    override var name = "SezonlukDizi"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Anime)

    private val ajaxHeaders = mapOf(
        "X-Requested-With" to "XMLHttpRequest",
        "Content-Type" to "application/x-www-form-urlencoded"
    )

    override val mainPage = mainPageOf(
        "$mainUrl/diziler.asp?siralama_tipi=id&s=" to "Son Eklenenler",
        "$mainUrl/diziler.asp?siralama_tipi=id&tur=mini&s=" to "Mini Diziler",
        "$mainUrl/diziler.asp?siralama_tipi=id&kat=2&s=" to "Yerli Diziler",
        "$mainUrl/diziler.asp?siralama_tipi=id&kat=1&s=" to "Yabancı Diziler",
        "$mainUrl/diziler.asp?siralama_tipi=id&kat=3&s=" to "Asya Dizileri",
        "$mainUrl/diziler.asp?siralama_tipi=id&kat=4&s=" to "Animasyonlar",
        "$mainUrl/diziler.asp?siralama_tipi=id&kat=5&s=" to "Animeler",
        "$mainUrl/diziler.asp?siralama_tipi=id&kat=6&s=" to "Belgeseller"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = runCatching { app.get("${request.data}$page").document }.getOrNull()
            ?: return newHomePageResponse(request.name, emptyList())

        return newHomePageResponse(request.name, parseShowCards(doc))
    }

    private fun parseShowCards(doc: Document): List<SearchResponse> {
        return doc.select("a.column[title]").mapNotNull { link ->
            val card = link.selectFirst("div.ui.card") ?: return@mapNotNull null
            val href = link.attr("href")
            if (href.isBlank()) return@mapNotNull null

            val title = card.selectFirst(".content .description")?.text()?.trim()
                ?: link.attr("title").removeSuffix(" izle").trim()

            val img = card.selectFirst("img[data-src], img[src]")
            val posterUrl = img?.let {
                val src = it.attr("data-src").ifEmpty { it.attr("src") }
                when {
                    src.isBlank() || src.startsWith("data:") -> null
                    else -> fixUrl(src)
                }
            }

            val scoreText = card.selectFirst("span.imdbp")?.text()
                ?.replace("IMDb", "")?.trim()
                ?.replace(",", ".")
                ?.replace(Regex("[^0-9.]"), "")
                ?.trim()

            newTvSeriesSearchResponse(title, fixUrl(href), TvType.TvSeries) {
                this.posterUrl = posterUrl
                if (!scoreText.isNullOrBlank()) this.score = runCatching { Score.from10(scoreText) }.getOrNull()
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val fromApi = runCatching {
            val response = app.post(
                "$mainUrl/ajax/arama.asp",
                data = mapOf("q" to query),
                headers = ajaxHeaders
            ).parsedSafe<SearchApiResponse>()

            response?.results?.diziler?.results?.mapNotNull { item ->
                val href = item.url ?: return@mapNotNull null
                val title = item.title ?: return@mapNotNull null
                newTvSeriesSearchResponse(title, fixUrl(href), TvType.TvSeries) {
                    this.posterUrl = item.image?.let { fixUrl(it) }
                }
            } ?: emptyList()
        }.getOrNull() ?: emptyList()

        if (fromApi.isNotEmpty()) return fromApi

        // Yedek arama
        return runCatching {
            val doc = app.get(
                "$mainUrl/diziler.asp",
                params = mapOf("siralama_tipi" to "id", "adi" to query, "s" to "1"),
                referer = "$mainUrl/"
            ).document
            parseShowCards(doc)
        }.getOrNull() ?: emptyList()
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val doc = runCatching { app.get(url).document }.getOrNull() ?: return null

        val title = doc.selectFirst(".content .header")?.text()?.trim()
            ?: doc.selectFirst("div.header")?.text()?.trim()
            ?: doc.selectFirst("h1")?.text()?.trim()
            ?: return null

        val img = doc.selectFirst("img[data-src]") ?: doc.selectFirst("div.image img")
        val posterUrl = img?.let {
            val src = it.attr("data-src").ifEmpty { it.attr("src") }
            if (src.isBlank() || src.startsWith("data:")) null else fixUrl(src)
        }

        val plot = doc.selectFirst("#tartismayorum-konu blockquote")?.text()
            ?: doc.selectFirst("span#tartismayorum-konu")?.text()

        val yearText = doc.selectFirst(".extra.content .right.floated")?.text()
            ?: doc.selectFirst("div.extra span")?.text()
        val year = yearText?.split("-")?.firstOrNull()?.trim()?.toIntOrNull()

        val genres = doc.select("a.ui.blue.label.golge").map { it.text() }.filter { it.isNotBlank() }
            .ifEmpty { doc.select("div.labels a[href*='tur']").mapNotNull { it.text().trim() } }

        val imdbText = doc.selectFirst(".ui.label.imdb .detail")?.text()
            ?: doc.selectFirst("div.dizipuani a div")?.text()
        val score = imdbText?.replace(",", ".")?.replace(Regex("[^0-9.]"), "")?.trim()

        val duration = doc.selectXpath("//span[contains(text(), 'Dk.')]").text().trim()
            .substringBefore(" Dk.").trim().toIntOrNull()

        val slug = doc.selectFirst("#dizidetay")?.attr("data-dizi")
            ?: doc.selectFirst("[data-dizi]")?.attr("data-dizi")
            ?: url.trimEnd('/').substringAfterLast('/').removeSuffix(".html")

        val actors = runCatching {
            app.get("$mainUrl/oyuncular/$slug.html").document
                .select("div.ui.card.golgever, div.doubling div.ui")
                .mapNotNull { card ->
                    val personName = card.selectFirst("div.header")?.text()?.trim()
                        ?: return@mapNotNull null
                    val photo = card.selectFirst("img")?.let { i ->
                        val src = i.attr("src").ifEmpty { i.attr("data-src") }
                        if (src.isBlank() || src.startsWith("data:")) null else fixUrl(src)
                    }
                    Actor(personName, photo)
                }
                .filter { it.name.isNotBlank() }
        }.getOrDefault(emptyList())

        val episodes = mutableListOf<Episode>()
        runCatching {
            val episodesDoc = app.get("$mainUrl/bolumler/$slug.html").document

            for (table in episodesDoc.select("table[sid], table.unstackable")) {
                for (row in table.select("tr")) {
                    val rowLink = row.selectFirst("a[href*='-sezon-']")
                        ?: row.selectFirst("td:nth-of-type(3) a[href]")
                        ?: continue
                    val href = rowLink.attr("href")
                    if (href.isBlank()) continue

                    val match = Regex("""(\d+)-sezon-(\d+)-bolum""").find(href)
                    val tds = row.select("td")

                    val season = match?.groupValues?.get(1)?.toIntOrNull()
                        ?: tds.getOrNull(1)?.text()?.substringBefore(".Sezon")?.trim()?.toIntOrNull() ?: 1
                    val episodeNo = match?.groupValues?.get(2)?.toIntOrNull()
                        ?: Regex("""(\d+)\.""").find(tds.getOrNull(2)?.text() ?: "")?.groupValues?.get(1)?.toIntOrNull()

                    val epTitle = tds.getOrNull(3)?.text()?.trim()?.takeIf { it.isNotBlank() }
                        ?: "$season. Sezon ${episodeNo ?: "?"}. Bölüm"

                    episodes.add(newEpisode(fixUrl(href)) {
                        this.name = epTitle
                        this.season = season
                        this.episode = episodeNo
                        this.posterUrl = posterUrl
                    })
                }
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = posterUrl
            this.plot = plot
            this.year = year
            this.tags = genres
            this.duration = duration
            if (!score.isNullOrBlank()) this.score = runCatching { Score.from10(score) }.getOrNull()
            addActors(actors)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = runCatching { app.get(data, referer = "$mainUrl/").document }.getOrNull() ?: return false
        val episodeId = doc.selectFirst("#dilsec")?.attr("data-id") ?: return false

        val aspData = runCatching { getAspData() }.getOrElse { AspData("22", "22") }

        // Site çoğu kaynağı Cloudflare Turnstile arkasına aldı; captcha istemeyen
        // kaynakları (byse/filemoon) önce deniyoruz ki en az bir bağlantı çıksın.
        val skipNames = listOf("pixel", "netu")
        var found = false

        for ((dil, prefix) in listOf("1" to "Altyazı", "0" to "Dublaj")) {
            val response = runCatching {
                app.post(
                    "$mainUrl/ajax/dataAlternatif${aspData.alternatif}.asp",
                    data = mapOf("bid" to episodeId, "dil" to dil),
                    headers = ajaxHeaders,
                    referer = data
                ).parsedSafe<Kaynak>()
            }.getOrNull() ?: continue

            if (response.status != "success") continue

            val sources = response.data.sortedBy {
                if (it.baslik.contains("byse", true) || it.baslik.contains("filemoon", true)) 0 else 1
            }

            for (veri in sources) {
                if (veri.baslik.lowercase() in skipNames) continue

                val embedHtml = runCatching {
                    app.post(
                        "$mainUrl/ajax/dataEmbed${aspData.embed}.asp",
                        data = mapOf("id" to veri.id.toString()),
                        headers = ajaxHeaders,
                        referer = data
                    ).text
                }.getOrNull() ?: continue

                // Turnstile korumalı kaynaklar atlanır
                if (embedHtml.contains("reCAPTCHA", ignoreCase = true)) continue

                var src = runCatching {
                    org.jsoup.Jsoup.parse(embedHtml).selectFirst("iframe")?.attr("src")
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: continue

                if (src.startsWith("//")) src = "https:$src"
                src = fixUrl(src)

                // vidmoly.net -> vidmoly.biz (site .net döndürüyor, oynatma .biz istiyor)
                if (src.contains("vidmoly.net")) src = src.replace("vidmoly.net", "vidmoly.biz")

                val label = "$prefix - ${veri.baslik}"

                if (src.contains("ruby", true)) {
                    if (extractRuby(src, label, callback)) {
                        found = true
                        continue
                    }
                }

                if (src.contains("byse", true)) {
                    runCatching {
                        loadExtractor(src, "$mainUrl/", subtitleCallback) { link ->
                            runCatching { callback(renameLink(link, label)) }
                            found = true
                        }
                    }
                    continue
                }

                runCatching {
                    loadExtractor(src, "$mainUrl/", subtitleCallback) { link ->
                        runCatching { callback(renameLink(link, label)) }
                        found = true
                    }
                }

                delay(200) // site çok hızlı isteklerde 429 döndürüyor
            }
        }

        return found
    }

    private fun renameLink(link: ExtractorLink, newName: String): ExtractorLink = ExtractorLink(
        source = link.source ?: newName,
        name = newName,
        url = link.url ?: "",
        referer = link.referer ?: "$mainUrl/",
        quality = link.quality,
        headers = link.headers ?: emptyMap(),
        extractorData = link.extractorData,
        type = link.type,
        audioTracks = link.audioTracks ?: emptyList()
    )

    private suspend fun extractRuby(
        iframe: String,
        label: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return runCatching {
            val script = app.get(
                iframe,
                referer = "$mainUrl/",
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0",
                    "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                )
            ).document.select("script")
                .find { it.data().contains("function(p,a,c,k,e") }?.data()
                ?: return false

            val unpacked = JsUnpacker(script).unpack() ?: return false
            val file = Regex("""["']?file["']?\s*:\s*["']([^"']+)["']""")
                .find(unpacked)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
                ?: return false

            callback(
                newExtractorLink(label, label, file, ExtractorLinkType.M3U8) {
                    this.referer = "$mainUrl/"
                    this.quality = Qualities.Unknown.value
                }
            )
            true
        }.getOrDefault(false)
    }

    private suspend fun getAspData(): AspData {
        val js = app.get("$mainUrl/js/site.min.js", referer = "$mainUrl/").text
        val alternatif = Regex("""dataAlternatif(.*?)\.asp""").find(js)?.groupValues?.get(1)
        val embed = Regex("""dataEmbed(.*?)\.asp""").find(js)?.groupValues?.get(1)
        return AspData(
            alternatif = alternatif?.takeIf { it.isNotBlank() } ?: "22",
            embed = embed?.takeIf { it.isNotBlank() } ?: "22"
        )
    }
}
