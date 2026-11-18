package com.neowatch

import android.util.Base64
import android.util.Log
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class DiziPal : MainAPI() {
    override var mainUrl = "https://dizipal1596.com"
    override var name = "DiziPal"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    override val mainPage = mainPageOf(
        "$mainUrl/yabanci-dizi-izle" to "Yabancı Diziler",
        "$mainUrl/film-izle" to "Filmler",
        "$mainUrl/trendler" to "Trendler",
        "$mainUrl/kesfet" to "Keşfet",
        "$mainUrl/yeni-eklenen-dizi-bolumler" to "Son Bölümler",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) "${request.data}?page=$page" else request.data
        val document = app.get(url).document

        val items = if (request.name == "Son Bölümler") {
            document.select("a.ecl").mapNotNull { element ->
                val href = element.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val label = element.attr("aria-label")
                    .split(",")
                    .dropLast(1)
                    .joinToString(", ")
                    .trim()
                    .ifBlank { element.text().trim() }
                val poster = element.parent()?.selectFirst("img")?.attr("src")?.takeIf { it.isNotBlank() }
                newTvSeriesSearchResponse(label, fixUrl(href), TvType.TvSeries) {
                    this.posterUrl = poster?.let { fixUrl(it) }
                }
            }
        } else {
            document.select("a.tc").mapNotNull { element ->
                val href = element.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val parts = element.attr("aria-label").split(",").map { it.trim() }
                val title = parts.firstOrNull().orEmpty().ifBlank { element.text().trim() }
                val year = parts.getOrNull(1)?.toIntOrNull()
                val poster = element.selectFirst("img")?.attr("src")?.takeIf { it.isNotBlank() }
                    ?.let { fixUrl(it) }
                val isMovie = href.contains("/movies/")
                if (isMovie) {
                    newMovieSearchResponse(title, fixUrl(href), TvType.Movie) {
                        this.posterUrl = poster
                        this.year = year
                    }
                } else {
                    newTvSeriesSearchResponse(title, fixUrl(href), TvType.TvSeries) {
                        this.posterUrl = poster
                        this.year = year
                    }
                }
            }
        }

        return newHomePageResponse(request.name, items.distinctBy { it.url }, hasNext = items.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val payload = JSONObject()
            .put("action", "searchContent")
            .put("params", JSONArray().put(query.trim()))
            .toString()

        val response = app.post(
            "$mainUrl/api/mj",
            headers = mapOf(
                "Content-Type" to "application/json",
                "Accept" to "application/json, text/plain, */*",
                "Origin" to mainUrl,
                "Referer" to "$mainUrl/",
                "sec-fetch-site" to "same-origin",
                "sec-fetch-mode" to "cors",
                "sec-fetch-dest" to "empty",
            ),
            requestBody = payload.toRequestBody("application/json".toMediaType()),
            referer = "$mainUrl/",
        )

        val result = runCatching { JSONObject(response.text).optJSONArray("result") }.getOrNull()
            ?: return emptyList()

        return buildList {
            for (i in 0 until result.length()) {
                val item = result.optJSONObject(i) ?: continue
                val slug = item.optString("used_slug").trim()
                if (slug.isBlank()) continue
                val title = item.optString("object_name").trim().ifBlank { continue }
                val poster = cleanImage(item.optString("object_poster_url"))
                val year = item.optString("object_release_year").toIntOrNull()
                when (item.optString("used_type")) {
                    "Movie" -> add(newMovieSearchResponse(title, fixUrl("/$slug"), TvType.Movie) {
                        this.posterUrl = poster
                        this.year = year
                    })
                    else -> add(newTvSeriesSearchResponse(title, fixUrl("/$slug"), TvType.TvSeries) {
                        this.posterUrl = poster
                        this.year = year
                    })
                }
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val pageUrl = fixUrl(url)
        val html = app.get(pageUrl).text
        val blob = rscBlob(html)

        if (pageUrl.contains("/bolum/")) {
            val seriesHref = jsonString(blob, "seriesHref")
            if (!seriesHref.isNullOrBlank()) return load(fixUrl(seriesHref))
        }

        val title = jsonString(blob, "mjTitle") ?: return null
        val poster = jsonString(blob, "mjPoster")
        val overview = jsonString(blob, "mjOverview")
        val year = jsonString(blob, "mjYear")?.toIntOrNull()
        val imdb = jsonString(blob, "mjImdb")?.toDoubleOrNull()
        val genres = runCatching {
            val raw = extractValue(blob, "mjGenres") ?: return@runCatching emptyList<String>()
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { array.optJSONObject(it)?.optString("label")?.takeIf { g -> g.isNotBlank() } }
        }.getOrDefault(emptyList())

        val isSeries = pageUrl.contains("/series/") || extractValue(blob, "seasons") != null

        if (!isSeries) {
            return newMovieLoadResponse(title, pageUrl, TvType.Movie, pageUrl) {
                this.posterUrl = poster
                this.plot = overview
                this.year = year
                this.tags = genres
                if (imdb != null && imdb > 0) this.score = Score.from10(imdb)
            }
        }

        val episodes = runCatching {
            val seasonsRaw = extractValue(blob, "seasons") ?: return@runCatching emptyList()
            val seasons = JSONArray(seasonsRaw)
            val list = mutableListOf<com.lagradost.cloudstream3.Episode>()
            for (i in 0 until seasons.length()) {
                val season = seasons.optJSONObject(i) ?: continue
                val seasonNo = season.optInt("no", i + 1)
                val eps = season.optJSONArray("episodes") ?: continue
                for (j in 0 until eps.length()) {
                    val episode = eps.optJSONObject(j) ?: continue
                    val href = episode.optString("href").takeIf { it.isNotBlank() } ?: continue
                    val epName = episode.optString("title").trim()
                        .ifBlank { "Bölüm ${episode.optInt("n")}" }
                    list.add(newEpisode(fixUrl(href)) {
                        this.name = epName
                        this.season = seasonNo
                        this.episode = episode.optInt("n")
                        this.posterUrl = poster
                    })
                }
            }
            list
        }.getOrDefault(emptyList())

        return newTvSeriesLoadResponse(title, pageUrl, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.plot = overview
            this.year = year
            this.tags = genres
            if (imdb != null && imdb > 0) this.score = Score.from10(imdb)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val pageUrl = fixUrl(data)
        val html = app.get(pageUrl).text
        val sources = playerSources(html)

        val targets = if (sources.isEmpty()) listOf("DiziPal" to pageUrl) else sources

        var emitted = false
        targets.distinctBy { it.second }.forEach { (label, href) ->
            val ok = runCatching {
                emitSource(href, label, pageUrl, subtitleCallback, callback)
            }.getOrElse {
                Log.w("DiziPal", "kaynak hatası ($label): ${it.message}")
                false
            }
            if (ok) emitted = true
        }
        return emitted
    }

    private suspend fun playerSources(html: String): List<Pair<String, String>> {
        val blob = rscBlob(html)
        val playerIndex = blob.indexOf("\"player\":{")
        if (playerIndex < 0) return emptyList()
        val segment = blob.substring(playerIndex, minOf(blob.length, playerIndex + 12_000))
        val raw = extractValue(segment, "sources") ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            val href = item.optString("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val label = item.optString("label").ifBlank { "Kaynak ${i + 1}" }
            label to fixUrl(href)
        }
    }

    private suspend fun emitSource(
        href: String,
        label: String,
        fallbackPageUrl: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val pageUrl = fixUrl(href).ifBlank { fallbackPageUrl }
        val html = app.get(pageUrl).text
        val blob = rscBlob(html)
        val playerIndex = blob.indexOf("\"player\":{")
        if (playerIndex < 0) return false
        val segment = blob.substring(playerIndex, minOf(blob.length, playerIndex + 12_000))

        val encRaw = extractValue(segment, "enc")
        val passphrase = jsonString(segment, "k")
        if (encRaw == null || passphrase.isNullOrBlank()) return false

        val enc = runCatching { JSONObject(encRaw) }.getOrNull() ?: return false
        val plain = decrypt(
            passphrase = passphrase,
            ciphertext = enc.optString("ciphertext"),
            ivHex = enc.optString("iv"),
            saltHex = enc.optString("salt"),
        ) ?: return false

        val iframeUrl = when {
            plain.startsWith("//") -> "https:$plain"
            plain.startsWith("http") -> plain
            else -> return false
        }

        val configHtml = app.get(iframeUrl, referer = pageUrl).text
        val config = parseNpConfig(configHtml) ?: return false
        val nonce = solveGate(config)

        var emitted = false
        val sources = config.optJSONArray("sources") ?: JSONArray()
        for (i in 0 until sources.length()) {
            val item = sources.optJSONObject(i) ?: continue
            val master = item.optString("master").takeIf { it.isNotBlank() } ?: continue
            val finalUrl = if (nonce != null) "$master&p=$nonce" else master
            val sourceLabel = item.optString("title").ifBlank { label }
            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = "$name · $label · $sourceLabel",
                    url = finalUrl,
                    type = ExtractorLinkType.M3U8,
                ) {
                    this.referer = iframeUrl
                }
            )
            emitted = true
        }

        if (nonce != null) {
            val subtitles = config.optJSONArray("subtitles")
            if (subtitles != null) {
                for (i in 0 until subtitles.length()) {
                    val item = subtitles.optJSONObject(i) ?: continue
                    val subUrl = item.optString("url").takeIf { it.isNotBlank() } ?: continue
                    val lang = item.optString("lang").ifBlank { "tr" }
                    subtitleCallback.invoke(newSubtitleFile(lang, "$subUrl&p=$nonce"))
                }
            }
        }

        return emitted
    }

    private fun rscBlob(html: String): String {
        val regex = Regex("""self\.__next_f\.push\(\[1,("(?:[^"\\]|\\.)*")\]\)""")
        val builder = StringBuilder()
        regex.findAll(html).forEach { match ->
            val literal = match.groupValues[1]
            val decoded = runCatching { JSONTokener(literal).nextValue() as? String }.getOrNull()
            if (decoded != null) builder.append(decoded)
        }
        return builder.toString()
    }

    private fun jsonString(json: String, key: String): String? {
        val raw = extractValue(json, key) ?: return null
        if (!raw.startsWith("\"")) return raw
        return runCatching { JSONTokener("\"$raw\"").nextValue() as? String }.getOrNull()
    }

    private fun extractValue(json: String, key: String): String? {
        val token = "\"$key\":"
        val start = json.indexOf(token)
        if (start < 0) return null
        var i = start + token.length
        while (i < json.length && json[i].isWhitespace()) i++
        if (i >= json.length) return null

        val first = json[i]
        if (first == '"') {
            var j = i + 1
            var escaped = false
            while (j < json.length) {
                val c = json[j]
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> return json.substring(i + 1, j)
                }
                j++
            }
            return null
        }

        if (first != '{' && first != '[') {
            var j = i
            while (j < json.length && json[j] !in ",}] \n\r\t") j++
            return json.substring(i, j)
        }

        var depth = 0
        var inString = false
        var escaped = false
        var j = i
        while (j < json.length) {
            val c = json[j]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{', '[' -> depth++
                    '}', ']' -> {
                        depth--
                        if (depth == 0) return json.substring(i, j + 1)
                    }
                }
            }
            j++
        }
        return null
    }

    private fun cleanImage(url: String): String? {
        if (url.isBlank()) return null
        val marker = "cdn.ampproject.org/i/s/"
        val index = url.indexOf(marker)
        return if (index >= 0) "https://" + url.substring(index + marker.length) else url
    }

    private fun decrypt(passphrase: String, ciphertext: String, ivHex: String, saltHex: String): String? {
        if (ciphertext.isBlank() || ivHex.isBlank() || saltHex.isBlank()) return null
        return runCatching {
            val salt = hexToBytes(saltHex)
            val iv = hexToBytes(ivHex)
            val data = Base64.decode(ciphertext, Base64.DEFAULT)

            val spec = PBEKeySpec(passphrase.toCharArray(), salt, 999, 256)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
            val key = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

            val cipher = Cipher.getInstance("AES/CBC/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
            val decrypted = cipher.doFinal(data)

            val padding = decrypted.last().toInt() and 0xFF
            if (padding !in 1..16 || padding > decrypted.size) return@runCatching null
            String(decrypted, 0, decrypted.size - padding, Charsets.UTF_8).trim()
        }.getOrNull()
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim()
        require(clean.length % 2 == 0) { "geçersiz hex" }
        return ByteArray(clean.length / 2) { index ->
            ((Character.digit(clean[index * 2], 16) shl 4) or Character.digit(clean[index * 2 + 1], 16)).toByte()
        }
    }

    private fun parseNpConfig(html: String): JSONObject? {
        val keyIndex = html.indexOf("window.NP_CONFIG")
        if (keyIndex < 0) return null
        val start = html.indexOf('{', keyIndex)
        if (start < 0) return null

        var depth = 0
        var inString = false
        var escaped = false
        var i = start
        while (i < html.length) {
            val c = html[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            return runCatching { JSONObject(html.substring(start, i + 1)) }.getOrNull()
                        }
                    }
                }
            }
            i++
        }
        return null
    }

    private fun solveGate(config: JSONObject): String? {
        val gate = config.optJSONObject("gate") ?: return null
        val challenge = gate.optString("n").takeIf { it.isNotBlank() } ?: return null
        val bits = gate.optInt("b", 12).coerceIn(1, 32)
        val magic = "oDesGxmweI"
        val digest = MessageDigest.getInstance("SHA-256")

        for (i in 0 until 20_000_000) {
            val nonce = i.toString(36)
            val hash = digest.digest((challenge + magic + nonce + magic).toByteArray())
            val word = ((hash[24].toInt() and 0xFF) shl 24) or
                ((hash[25].toInt() and 0xFF) shl 16) or
                ((hash[26].toInt() and 0xFF) shl 8) or
                (hash[27].toInt() and 0xFF)
            if ((word ushr (32 - bits)) == 0) return nonce
        }
        return null
    }
}
