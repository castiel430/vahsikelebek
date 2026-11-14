package dev.wiojelt.turksinema.vendor.cizgivedizi

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import android.util.JsonReader
import android.util.JsonToken
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URLEncoder
import com.vahsikelebek.kids.KidsYoutubeFallback

private data class CizgiItem(
    val id: String = "",
    val title: String,
    val url: String,
    val poster: String?,
    val type: String,
    val filmKind: String = "",
    val genres: String = "",
    val channel: String = "",
    val order: Int = 0,
    val isAdult: Boolean = false,
)

class CizgiVeDiziProvider : MainAPI() {
    override var mainUrl = "https://cizgivedizi.net"
    override var name = "ÇizgiVeDizi"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "new" to "Yeni Eklenenler",
        "cizgi" to "Çizgi Diziler",
        "animasyon" to "Çizgi Filmler",
        "komedi" to "Komedi",
        "macera" to "Macera",
        "egitici_ogretici" to "Eğitici Çizgi Filmler",
        "doga_ve_hayvanlar" to "Hayvan Dostları",
        "fantastik" to "Fantastik Maceralar",
        "muzik_muzikal" to "Müzik ve Şarkılar",
        "channel:Cartoon Network" to "Cartoon Network",
        "channel:TRT Çocuk" to "TRT Çocuk",
        "channel:Disney Jr." to "Disney Jr.",
        "channel:Nick Jr." to "Nick Jr.",
    )

    private val requestHeaders = mapOf(
        "User-Agent" to CLIENT_USER_AGENT,
        "Accept" to "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8",
        "Accept-Language" to "tr-TR,tr;q=0.9,en;q=0.7",
    )

    @Volatile private var poolCache: Pair<Long, List<CizgiItem>>? = null

    private fun cleanTitle(value: String): String = value
        .replace(Regex("\\s+"), " ")
        .replace(Regex("\\s*(?:türkçe\\s+)?(?:dublaj|altyazılı)?\\s*(?:full\\s*hd|hd)?\\s*(?:film|dizi)?\\s*izle(?:\\s*[|–—-].*)?$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s*[|–—-]\\s*Çizgi\\s+ve\\s+Dizi.*$", RegexOption.IGNORE_CASE), "")
        .trim()

    private fun fixPosterUrl(url: String): String {
        val path = url.substringBefore('?')
        return if (path.endsWith(".avif", ignoreCase = true)) {
            "https://wsrv.nl/?url=${urlEncode(url)}&output=webp"
        } else {
            url
        }
    }

    private fun absolute(value: String?): String? = value
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.replace("https://cizgivedizi.com", mainUrl)
        ?.replace("https://video.cizgivedizi.com", VIDEO_ORIGIN)
        ?.let(::fixUrl)
        ?.let(::fixPosterUrl)

    private fun parsePoolStream(reader: JsonReader): List<CizgiItem> {
        val result = mutableListOf<CizgiItem>()
        runCatching {
            reader.beginArray()
            while (reader.hasNext()) {
                reader.beginObject()
                var href: String? = null
                var poster: String? = null
                var labelTitle: String? = null
                var datasetName: String? = null
                var datasetType: String? = null
                var filmKind: String? = null
                var genres: String? = null
                var channel: String? = null
                var order = 0
                var isAdult = false

                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "href" -> href = if (reader.peek() == JsonToken.STRING) reader.nextString() else { reader.skipValue(); null }
                        "poster" -> poster = if (reader.peek() == JsonToken.STRING) reader.nextString() else { reader.skipValue(); null }
                        "label" -> labelTitle = if (reader.peek() == JsonToken.STRING) reader.nextString() else { reader.skipValue(); null }
                        "dataset" -> {
                            if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    when (reader.nextName()) {
                                        "name" -> datasetName = if (reader.peek() == JsonToken.STRING) reader.nextString() else { reader.skipValue(); null }
                                        "type" -> datasetType = if (reader.peek() == JsonToken.STRING) reader.nextString() else { reader.skipValue(); null }
                                        "filmkind" -> filmKind = if (reader.peek() == JsonToken.STRING) reader.nextString() else { reader.skipValue(); null }
                                        "genres" -> genres = if (reader.peek() == JsonToken.STRING) reader.nextString() else { reader.skipValue(); null }
                                        "kanaltext" -> channel = if (reader.peek() == JsonToken.STRING) reader.nextString() else { reader.skipValue(); null }
                                        "order" -> order = if (reader.peek() == JsonToken.STRING) reader.nextString().toIntOrNull() ?: 0 else { reader.skipValue(); 0 }
                                        "hay" -> isAdult = if (reader.peek() == JsonToken.STRING) reader.nextString().contains("18+") else { reader.skipValue(); false }
                                        else -> reader.skipValue()
                                    }
                                }
                                reader.endObject()
                            } else {
                                reader.skipValue()
                            }
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()

                val resolvedHref = absolute(href)
                val rawTitle = labelTitle?.takeIf { it.isNotBlank() } ?: datasetName
                val cleaned = if (!rawTitle.isNullOrBlank()) cleanTitle(rawTitle) else ""
                if (resolvedHref != null && cleaned.isNotBlank()) {
                    result.add(
                        CizgiItem(
                            title = cleaned,
                            url = resolvedHref,
                            poster = absolute(poster),
                            type = datasetType?.takeIf { it.isNotBlank() } ?: "other",
                            filmKind = filmKind.orEmpty(),
                            genres = genres.orEmpty(),
                            channel = channel.orEmpty(),
                            order = order,
                            isAdult = isAdult,
                        )
                    )
                }
            }
            reader.endArray()
        }
        return result.distinctBy { it.url }
    }

    private suspend fun getPool(): List<CizgiItem> {
        val now = System.currentTimeMillis()
        poolCache?.takeIf { now - it.first < POOL_CACHE_MS }?.let { return it.second }

        // The live archive includes the animation flag used by WioKids.
        val items = runCatching {
            app.get(mainUrl, headers = requestHeaders, timeout = 45000L).body.byteStream().use { stream ->
                val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8), 32768)
                val marker = "id=\"poolData\""
                val sb = java.lang.StringBuilder()
                var ch: Int
                var markerFound = false
                while (reader.read().also { ch = it } != -1) {
                    sb.append(ch.toChar())
                    if (sb.length > marker.length) sb.deleteCharAt(0)
                    if (sb.toString() == marker) {
                        markerFound = true
                        break
                    }
                }
                if (!markerFound) return@use emptyList()

                // Skip until closing '>' of <script ...>
                while (reader.read().also { ch = it } != -1) {
                    if (ch.toChar() == '>') break
                }

                val jsonReader = JsonReader(reader)
                jsonReader.isLenient = true
                parsePoolStream(jsonReader)
            }
        }.getOrDefault(emptyList())

        if (items.isNotEmpty()) {
            poolCache = now to items
            return items
        }

        return items
    }

    private fun CizgiItem.toSearch(): SearchResponse {
        val tvType = when (type.lowercase()) {
            "film" -> TvType.Movie
            "anime" -> TvType.Anime
            else -> TvType.TvSeries
        }
        return if (tvType == TvType.Movie) newMovieSearchResponse(title, url, tvType) { posterUrl = poster }
        else newTvSeriesSearchResponse(title, url, tvType) { posterUrl = poster }
    }

    private fun CizgiItem.isForKids(): Boolean = !isAdult &&
        (type.equals("cizgi", true) || (type.equals("film", true) && filmKind.equals("animasyon", true)))

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val filtered = getPool().asSequence().filter { it.isForKids() }.filter {
            when (request.data) {
                "new" -> true
                "cizgi" -> it.type.equals("cizgi", true)
                "animasyon" -> it.filmKind.equals("animasyon", true)
                "channel:Cartoon Network", "channel:TRT Çocuk", "channel:Disney Jr.", "channel:Nick Jr." ->
                    it.channel.equals(request.data.removePrefix("channel:"), true)
                else -> it.genres.split('|').any { genre -> genre.equals(request.data, true) }
            }
        }.toList().let { if (request.data == "new") it.sortedByDescending { item -> item.order } else it }
        val start = (page - 1).coerceAtLeast(0) * PAGE_SIZE
        val pageItems = filtered.drop(start).take(PAGE_SIZE)
        return newHomePageResponse(request.name, pageItems.map { it.toSearch() }, start + pageItems.size < filtered.size)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val normalized = query.trim()
        if (normalized.isBlank()) return emptyList()
        return getPool().asSequence().filter { it.isForKids() }
            .filter { it.title.contains(normalized, true) }.take(100).map { it.toSearch() }.toList()
    }

    private fun Element.attrOrNull(name: String): String? = attr(name).trim().takeIf(String::isNotEmpty)

    private fun parseEpisodeRoute(url: String): Pair<String, String>? {
        val path = url.substringBefore('?').substringBefore('#')
        val segments = path.split('/').filter(String::isNotBlank)
        val diziIndex = segments.indexOfFirst { it.equals("dizi", ignoreCase = true) }
        if (diziIndex < 0) return null

        val diziid = segments.getOrNull(diziIndex + 1)?.trim().orEmpty()
        // /dizi/{diziid}/{slug}/{bolum}/{episodeSlug} -> bolum is at diziIndex + 3 ("1", "14", etc.)
        // /dizi/{diziid}/{slug}/{bolum}               -> bolum is at diziIndex + 3
        // /dizi/{diziid}/{bolum}                      -> bolum is at diziIndex + 2
        val bolum = when {
            segments.size > diziIndex + 3 -> segments[diziIndex + 3].trim()
            segments.size > diziIndex + 2 -> segments[diziIndex + 2].trim()
            else -> ""
        }

        return if (diziid.isNotBlank() && bolum.isNotBlank()) diziid to bolum else null
    }

    private fun createEpisodeFromEntry(entry: Element, seriesUrl: String, seasonNum: Int, defaultEpIndex: Int): Episode? {
        val href = absolute(entry.attrOrNull("data-href") ?: entry.selectFirst("a.cvd-episode-title, a[href]")?.attr("href")) ?: return null
        val epSeasonNo = entry.selectFirst("td.cvd-season-no")?.text()?.trim()?.toIntOrNull() ?: defaultEpIndex
        val titleTr = entry.selectFirst("a.cvd-episode-title-tr")?.text()?.trim()
        val titleEng = entry.selectFirst("a.cvd-episode-title-eng")?.text()?.trim()
        val title = listOfNotNull(titleTr, titleEng).firstOrNull { it.isNotBlank() } ?: "Bölüm $epSeasonNo"
        val desc = entry.selectFirst("tr.cvd-summary-row td")?.text()?.trim()

        val (diziid, bolum) = parseEpisodeRoute(href) ?: ("" to "")

        val dataPayload = JSONObject()
            .put("page", href)
            .put("show", seriesUrl)
            .put("diziid", diziid)
            .put("bolum", bolum)
            .put("episode", bolum)
            .toString()

        return newEpisode(dataPayload) {
            this.name = title
            this.season = seasonNum
            this.episode = epSeasonNo
            this.description = desc
        }
    }

    private fun createEpisode(element: Element, seriesUrl: String, season: Int, seasonEpisode: Int): Episode? {
        val href = absolute(element.attrOrNull("href")) ?: return null
        val key = element.attrOrNull("data-episode-id") ?: element.attrOrNull("data-episode-safe-id").orEmpty()
        val (diziid, bolum) = parseEpisodeRoute(href) ?: ("" to "")
        val resolvedBolum = key.ifBlank { bolum }
        val title = element.selectFirst(".name, .card-title")?.text()?.trim().orEmpty()
        return newEpisode(
            JSONObject()
                .put("page", href)
                .put("show", seriesUrl)
                .put("diziid", diziid)
                .put("bolum", resolvedBolum)
                .put("episode", resolvedBolum)
                .toString()
        ) {
            name = title.ifBlank { "Bölüm $seasonEpisode" }
            this.season = season
            episode = seasonEpisode
            description = element.selectFirst(".sub")?.text()?.trim()
        }
    }

    private fun seriesEpisodes(document: Document, seriesUrl: String): List<Episode> {
        val seasonBlocks = document.select("section.cvd-season-block")
        if (seasonBlocks.isNotEmpty()) {
            return seasonBlocks.flatMapIndexed { sIndex, section ->
                val seasonHeader = section.selectFirst(".cvd-sh-text, .cvd-season-header")?.text().orEmpty()
                val seasonNum = Regex("(\\d+)").find(seasonHeader)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: (sIndex + 1)
                section.select("tbody.cvd-episode-entry").mapIndexedNotNull { epIndex, entry ->
                    createEpisodeFromEntry(entry, seriesUrl, seasonNum, epIndex + 1)
                }
            }.distinctBy { it.data }
        }

        val directEntries = document.select("tbody.cvd-episode-entry")
        if (directEntries.isNotEmpty()) {
            return directEntries.mapIndexedNotNull { epIndex, entry ->
                val seasonNum = entry.selectFirst("td.cvd-season-no")?.text()?.trim()?.toIntOrNull() ?: 1
                createEpisodeFromEntry(entry, seriesUrl, seasonNum, epIndex + 1)
            }.distinctBy { it.data }
        }

        val seasonal = document.select("div.list-box[id^=s]")
            .filter { it.id().removePrefix("s").toIntOrNull() != null }
            .flatMap { box ->
                val season = box.id().removePrefix("s").toIntOrNull() ?: 1
                val templateContent = box.selectFirst("template")?.let { Jsoup.parseBodyFragment(it.html()) } ?: box
                templateContent.select("a.row[data-episode-id], a[data-episode-id][href], a.row[href]").mapIndexedNotNull { index, element ->
                    createEpisode(element, seriesUrl, season, index + 1)
                }
            }
        if (seasonal.isNotEmpty()) return seasonal.distinctBy { it.data }

        return document.select("a.row[data-episode-id], a[data-episode-id][href]")
            .mapIndexedNotNull { index, element -> createEpisode(element, seriesUrl, 1, index + 1) }
            .distinctBy { it.data }
    }

    override suspend fun load(url: String): LoadResponse? {
        val pageUrl = absolute(runCatching { JSONObject(url).optString("page") }.getOrNull()?.takeIf(String::isNotBlank) ?: url) ?: return null
        // Load details from the live site. The catalog database contains old, short-lived
        // player tokens, so its show JSON must never determine current playback URLs.
        val document = app.get(pageUrl, headers = requestHeaders).document
        val title = cleanTitle(document.selectFirst("meta[property=og:title]")?.attr("content") ?: document.selectFirst("h1")?.text().orEmpty())
        if (title.isBlank()) return null
        val poster = absolute(document.selectFirst("meta[property=og:image]")?.attr("content"))
        val plot = document.selectFirst("meta[name=description], meta[property=og:description]")?.attr("content")?.trim()
        val episodes = seriesEpisodes(document, pageUrl)
        return if ((pageUrl.contains("/dizi/", true) || episodes.isNotEmpty()) && episodes.isNotEmpty()) {
            newTvSeriesLoadResponse(title, pageUrl, TvType.TvSeries, episodes) { posterUrl = poster; this.plot = plot }
        } else {
            newMovieLoadResponse(title, pageUrl, TvType.Movie, pageUrl) { posterUrl = poster; this.plot = plot }
        }
    }

    private fun decodeEmbedList(encoded: String): List<String> = runCatching {
        val array = JSONArray(String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8))
        buildList { for (index in 0 until array.length()) array.optString(index).trim().takeIf(String::isNotEmpty)?.let(::add) }
    }.getOrDefault(emptyList())

    private suspend fun movieSources(page: String, payloadEmbeds: JSONArray? = null): List<Pair<String, String>> {
        if (payloadEmbeds != null && payloadEmbeds.length() > 0) {
            return buildList {
                for (i in 0 until payloadEmbeds.length()) {
                    val embed = payloadEmbeds.optString(i).trim()
                    if (embed.isNotBlank()) add("Kaynak ${i + 1}" to embed)
                }
            }
        }
        val container = runCatching { app.get(page, headers = requestHeaders).document.selectFirst("#videoDataContainer[data-embeds]") }.getOrNull() ?: return emptyList()
        return decodeEmbedList(container.attr("data-embeds")).mapIndexed { index, embed -> "Kaynak ${index + 1}" to embed }
    }

    private suspend fun episodeSources(
        page: String,
        showUrl: String,
        episode: String,
        payloadDiziid: String? = null,
        payloadBolum: String? = null,
    ): List<Pair<String, String>> {
        val routeIds = parseEpisodeRoute(page)
        var diziid = payloadDiziid?.takeIf(String::isNotBlank)
            ?: routeIds?.first?.takeIf(String::isNotBlank)
            ?: showUrl.substringAfter("/dizi/").substringBefore('/').trim()

        var bolum = payloadBolum?.takeIf(String::isNotBlank)
            ?: episode.takeIf(String::isNotBlank)
            ?: routeIds?.second.orEmpty()

        val pageResponse = runCatching { app.get(page, headers = requestHeaders, timeout = 20000L) }.getOrNull()
        val pageCookies = pageResponse?.cookies.orEmpty()
        val pageDoc = pageResponse?.text.orEmpty()

        if (diziid.isBlank() || bolum.isBlank()) {
            val scriptDiziid = Regex("(?:__)?diziid\\s*=\\s*['\"]([^'\"]+)").find(pageDoc)?.groupValues?.getOrNull(1).orEmpty()
            val scriptBolum = Regex("(?:__)?bolum(?:GecerliId)?\\s*=\\s*['\"]([^'\"]+)").find(pageDoc)?.groupValues?.getOrNull(1).orEmpty()
            if (scriptDiziid.isNotBlank()) diziid = scriptDiziid
            if (scriptBolum.isNotBlank()) bolum = scriptBolum

            if (diziid.isBlank() || bolum.isBlank()) {
                val show = showUrl.substringAfter("/dizi/").substringBefore('/').trim()
                if (show.isNotBlank() && episode.isNotBlank()) {
                    diziid = show
                    bolum = episode
                }
            }
        }

        suspend fun requestSources(requestDiziId: String, requestBolum: String): List<Pair<String, String>> {
            if (requestDiziId.isBlank() || requestBolum.isBlank()) return emptyList()
            val response = app.get(
                "$mainUrl/diziyeni/video_sources.php?s=${urlEncode(requestDiziId)}&b=${urlEncode(requestBolum)}&refresh=1",
                referer = page,
                cookies = pageCookies,
                headers = requestHeaders + mapOf(
                    "X-Requested-With" to "XMLHttpRequest",
                    "Accept" to "application/json",
                    "Cache-Control" to "no-cache",
                ),
            )
            val root = runCatching { JSONObject(response.text) }.getOrNull() ?: return emptyList()
            val sources = root.takeIf { it.optInt("ok") == 1 }?.optJSONArray("sources") ?: return emptyList()
            return buildList {
                for (index in 0 until sources.length()) {
                    val source = sources.optJSONObject(index) ?: continue
                    val embed = source.optString("embed").trim().takeIf(String::isNotEmpty) ?: continue
                    val sourceName = source.optString("ad").ifBlank { source.optString("kanal") }
                        .ifBlank { "Kaynak ${index + 1}" }
                    val part = source.optInt("part").takeIf { it > 0 }?.let { "Kısım $it" } ?: "Tam Bölüm"
                    val label = "$sourceName • $part"
                    add(label to embed)
                }
            }
        }

        val directSources = requestSources(diziid, bolum)
        if (directSources.isNotEmpty()) return directSources

        // If the route shape changes again, trust the page's own runtime identifiers and retry once.
        val fallbackDiziid = Regex("(?:__)?diziid\\s*=\\s*['\"]([^'\"]+)").find(pageDoc)?.groupValues?.getOrNull(1).orEmpty()
        val fallbackBolum = Regex("(?:__)?bolum(?:GecerliId)?\\s*=\\s*['\"]([^'\"]+)").find(pageDoc)?.groupValues?.getOrNull(1).orEmpty()

        if (fallbackDiziid.isNotBlank() && fallbackBolum.isNotBlank() &&
            (fallbackDiziid != diziid || fallbackBolum != bolum)
        ) {
            diziid = fallbackDiziid
            bolum = fallbackBolum
            return requestSources(diziid, bolum)
        }

        return emptyList()
    }

    private val videoProxy by lazy { CvdVideoProxy { playerUrl, page, label ->
        authorizeInternalPlayer(playerUrl, page) ?: run {
            val refreshed = if (page.contains("/film/", true)) {
                movieSources(page)
            } else {
                episodeSources(page, "", "")
            }.firstOrNull { it.first == label }?.second
            refreshed?.let { absolute(it) }?.let { authorizeInternalPlayer(it, page) }
        }
    } }

    private suspend fun authorizeInternalPlayer(playerUrl: String, page: String): CvdVideoAuth? {
        var key = Regex("[?&]k=([^&#]+)").find(playerUrl)?.groupValues?.getOrNull(1).orEmpty()
        var videoOrigin = VIDEO_ORIGIN

        // Keep the complete browser session across the page -> player -> video
        // authorization chain. CizgiVeDizi can issue a session cookie on the
        // detail page and a second cache cookie on player.php; dropping the first
        // one makes yetki.php return HTTP 403 even though the endpoint itself is
        // reachable (CloudStream then reports error 2004).
        val sessionCookies = linkedMapOf<String, String>()
        runCatching {
            val pageResponse = app.get(page, headers = requestHeaders, timeout = 45000L)
            sessionCookies.putAll(pageResponse.cookies)
        }

        val playerRequestHeaders = requestHeaders + mapOf(
            "Origin" to mainUrl,
            "Sec-Fetch-Site" to "same-origin",
            "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-Dest" to "iframe",
            "Upgrade-Insecure-Requests" to "1",
        )
        val playerResponse = app.get(
            playerUrl,
            referer = page,
            cookies = sessionCookies,
            headers = playerRequestHeaders,
            timeout = 45000L,
        )
        val playerCookies = linkedMapOf<String, String>().apply {
            putAll(sessionCookies)
            putAll(playerResponse.cookies)
        }
        if (playerResponse.isSuccessful) {
            val foundKey = Regex("cvdBridgeKey\\s*=\\s*[\"']([^\"']+)").find(playerResponse.text)
                ?.groupValues?.getOrNull(1)?.trim().orEmpty()
            if (foundKey.isNotBlank()) key = foundKey

            val foundOrigin = Regex("cvdVideoOrigin\\s*=\\s*[\"']([^\"']+)").find(playerResponse.text)
                ?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.startsWith("https://") }
            if (foundOrigin != null) videoOrigin = foundOrigin
        }
        if (key.isBlank()) return null

        // yetki.php requires root Referer and Origin for authorization
        val auth = app.get(
            "$videoOrigin/yetki.php?k=$key",
            referer = "$mainUrl/",
            cookies = playerCookies,
            headers = requestHeaders + mapOf(
                "Accept" to "*/*",
                "Origin" to mainUrl,
                "Referer" to "$mainUrl/",
                "Sec-Fetch-Site" to "same-site",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Dest" to "empty",
                "Cache-Control" to "no-cache",
                "Pragma" to "no-cache",
            ),
        )
        if (!auth.isSuccessful) return null

        val opaquePath = auth.headers["X-CVD-Video-URL"] ?: auth.headers["x-cvd-video-url"] ?: return null
        if (!Regex("^/[A-Za-z0-9_-]{20,128}$").matches(opaquePath)) return null

        // Only video-host cookies go to the video server. The site session
        // cookies belong to cizgivedizi.net and can make the final gate reject.
        val videoCookies = linkedMapOf<String, String>().apply {
            putAll(auth.cookies.filterKeys { it.startsWith("cvd_video_") })
            runCatching {
                for (i in 0 until auth.headers.size) {
                    if (auth.headers.name(i).equals("Set-Cookie", ignoreCase = true)) {
                        val raw = auth.headers.value(i).substringBefore(';').trim()
                        val split = raw.indexOf('=')
                        if (split > 0 && raw.substring(0, split).startsWith("cvd_video_")) {
                            put(raw.substring(0, split).trim(), raw.substring(split + 1).trim())
                        }
                    }
                }
            }
        }
        if (videoCookies.isEmpty()) return null
        val cookies = videoCookies.entries.joinToString("; ") { (name, value) -> "$name=$value" }

        // Keep the opaque ticket for the loopback proxy. The proxy opens the
        // gate and its redirected media URL on one TLS connection, and carries
        // the gate's second cookie. Returning a resolved remote URL produces
        // HTTP 410 in CloudStream's separate player connection.
        return CvdVideoAuth(videoOrigin.trimEnd('/'), opaquePath, cookies)
    }

    private suspend fun resolveInternalPlayer(playerUrl: String, page: String, label: String, callback: (ExtractorLink) -> Unit): Boolean {
        val auth = authorizeInternalPlayer(playerUrl, page) ?: return false
        val localUrl = videoProxy.register(playerUrl, page, label, auth)
        callback(newExtractorLink(name, "$name • $label", localUrl, ExtractorLinkType.VIDEO) {
            this.quality = Qualities.Unknown.value
        })
        return true
    }

    private suspend fun resolveSource(
        rawUrl: String,
        page: String,
        label: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val url = absolute(rawUrl) ?: return false
        if (url.contains("/diziyeni/player.php", true)) return resolveInternalPlayer(url, page, label, callback)
        if (url.contains(".m3u8", true) || url.contains(".mpd", true) || url.contains(".mp4", true)) {
            callback(newExtractorLink(name, "$name • $label", url, when {
                url.contains(".m3u8", true) -> ExtractorLinkType.M3U8
                url.contains(".mpd", true) -> ExtractorLinkType.DASH
                else -> ExtractorLinkType.VIDEO
            }) {
                referer = page; headers = mapOf("User-Agent" to CLIENT_USER_AGENT, "Referer" to page); quality = Qualities.Unknown.value
            })
            return true
        }
        return loadExtractor(url, page, subtitleCallback, callback)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val payload = runCatching { JSONObject(data) }.getOrNull()
        val page = payload?.optString("page")?.takeIf(String::isNotBlank) ?: data
        val sources = if (payload != null && !payload.has("embeds")) {
            episodeSources(
                page = page,
                showUrl = payload.optString("show"),
                episode = payload.optString("episode"),
                payloadDiziid = payload.optString("diziid").takeIf(String::isNotBlank),
                payloadBolum = payload.optString("bolum").takeIf(String::isNotBlank),
            )
        } else {
            movieSources(page, payload?.optJSONArray("embeds"))
        }
        val fallbackSeriesTitle = payload?.optString("seriesTitle")?.takeIf(String::isNotBlank)
        val fallbackEpisodeTitle = payload?.optString("episodeTitle")?.takeIf(String::isNotBlank)
        val fallbackSeason = payload?.optInt("seasonNumber")?.takeIf { it > 0 }
        val fallbackEpisode = payload?.optInt("episodeNumber")?.takeIf { it > 0 }
            ?: payload?.optString("episode")?.toIntOrNull()

        var found = false
        sources.forEach { (label, source) ->
            found = runCatching { resolveSource(source, page, label, subtitleCallback, callback) }.getOrDefault(false) || found
        }

        val pageTitle = if (!found) runCatching {
            val doc = app.get(page, headers = requestHeaders, timeout = 25000L).document
            cleanTitle(
                doc.selectFirst("meta[property=og:title]")?.attr("content")
                    ?: doc.selectFirst("h1")?.text().orEmpty()
            )
        }.getOrNull()?.takeIf(String::isNotBlank) else null

        val youtubeFound = if (!found) KidsYoutubeFallback.append(
            query = KidsYoutubeFallback.buildQuery(
                seriesTitle = fallbackSeriesTitle ?: pageTitle,
                episodeTitle = fallbackEpisodeTitle ?: pageTitle,
                season = fallbackSeason,
                episode = fallbackEpisode,
            ),
            subtitleCallback = subtitleCallback,
            callback = callback,
        ) else false
        return found || youtubeFound
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val CLIENT_USER_AGENT = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
        const val PAGE_SIZE = 40
        const val POOL_CACHE_MS = 10 * 60 * 1000L
        const val VIDEO_ORIGIN = "https://video.cizgivedizi.net"
    }
}
