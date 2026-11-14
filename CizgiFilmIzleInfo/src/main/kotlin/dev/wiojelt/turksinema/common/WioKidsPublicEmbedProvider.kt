package dev.wiojelt.turksinema.common

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import java.net.URLDecoder
import java.util.Base64

abstract class WioKidsPublicEmbedProvider : MainAPI() {
    protected val wioHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36",
        "Accept-Language" to "tr-TR,tr;q=0.9,en;q=0.7",
    )

    protected data class PlayPayload(
        val page: String,
        val series: String?,
        val episodeTitle: String?,
        val season: Int?,
        val episode: Int?,
    )

    protected fun absolute(raw: String?, base: String = mainUrl): String? {
        val value = raw?.trim()
            ?.replace("&amp;", "&")
            ?.replace("&#038;", "&")
            ?.replace("\\/", "/")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return when {
            value.startsWith("//") -> "https:$value"
            value.startsWith("http://") || value.startsWith("https://") -> value
            value.startsWith("#") || value.startsWith("javascript:", true) ||
                value.startsWith("about:", true) || value.startsWith("data:", true) -> null
            else -> runCatching {
                URI(base).resolve(value).toString()
            }.getOrElse {
                mainUrl.trimEnd('/') + "/" + value.trimStart('/')
            }
        }
    }

    protected fun payload(
        page: String,
        series: String? = null,
        episodeTitle: String? = null,
        season: Int? = null,
        episode: Int? = null,
    ): String = JSONObject()
        .put("page", page)
        .put("series", series)
        .put("episodeTitle", episodeTitle)
        .put("season", season)
        .put("episode", episode)
        .toString()

    protected fun readPayload(data: String): PlayPayload {
        val json = runCatching { JSONObject(data) }.getOrNull()
        return if (json == null || !json.has("page")) {
            PlayPayload(data, null, null, null, null)
        } else {
            PlayPayload(
                page = json.optString("page").ifBlank { data },
                series = json.optString("series").takeIf(String::isNotBlank),
                episodeTitle = json.optString("episodeTitle").takeIf(String::isNotBlank),
                season = json.optInt("season").takeIf { it > 0 },
                episode = json.optInt("episode").takeIf { it > 0 },
            )
        }
    }

    protected fun pageTitle(doc: Document): String? {
        return doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()?.takeIf(String::isNotBlank)
            ?: doc.selectFirst("h1, h2")?.text()?.trim()?.takeIf(String::isNotBlank)
            ?: doc.title().trim().takeIf(String::isNotBlank)
    }

    protected fun pagePoster(doc: Document): String? {
        return absolute(
            doc.selectFirst("meta[property=og:image]")?.attr("content")
                ?: doc.selectFirst("meta[name=twitter:image]")?.attr("content")
                ?: doc.selectFirst("main img, article img, .poster img, .cover img")?.attr("src")
        )
    }

    protected fun pagePlot(doc: Document): String? {
        return doc.selectFirst("meta[property=og:description]")?.attr("content")?.trim()?.takeIf(String::isNotBlank)
            ?: doc.selectFirst("meta[name=description]")?.attr("content")?.trim()?.takeIf(String::isNotBlank)
            ?: doc.selectFirst("article p, .description, .desc, .summary")?.text()?.trim()?.takeIf(String::isNotBlank)
    }

    protected fun seasonEpisode(text: String): Pair<Int?, Int?> {
        val season = Regex("(?:sezon|s)\\s*[-.:]?\\s*(\\d+)", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val episode = Regex("(?:bölüm|bolum|e)\\s*[-.:]?\\s*(\\d+)", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return season to episode
    }

    private fun host(url: String): String? = runCatching { URI(url).host?.lowercase() }.getOrNull()

    private fun youtubeId(url: String): String? {
        return Regex(
            """(?:youtu\.be/|youtube(?:-nocookie)?\.com/(?:.*[?&]v=|embed/|shorts/|live/))([A-Za-z0-9_-]{11})""",
            RegexOption.IGNORE_CASE,
        ).find(url)?.groupValues?.getOrNull(1)
    }

    private fun normalizeYoutube(url: String): String {
        val id = youtubeId(url) ?: return url
        return "https://www.youtube.com/watch?v=$id"
    }

    private fun decodeBase64Url(raw: String): String? {
        val value = raw.trim()
        if (value.length < 24 || !value.matches(Regex("[A-Za-z0-9_+/=-]+"))) return null
        val padded = value + "=".repeat((4 - value.length % 4) % 4)
        val variants = listOf(
            runCatching { Base64.getDecoder().decode(padded) }.getOrNull(),
            runCatching { Base64.getUrlDecoder().decode(padded) }.getOrNull(),
        )
        return variants.asSequence()
            .filterNotNull()
            .map { it.toString(Charsets.UTF_8).trim() }
            .firstOrNull { it.startsWith("https://") || it.startsWith("http://") }
    }

    private fun candidate(raw: String?, base: String = mainUrl): String? {
        val clean = raw?.trim()?.takeIf(String::isNotBlank) ?: return null
        val decoded = decodeBase64Url(clean) ?: clean
        return absolute(decoded, base)?.let(::normalizeYoutube)
    }

    private fun isDirect(url: String): ExtractorLinkType? {
        val path = url.substringBefore('?').substringBefore('#')
        return when {
            path.endsWith(".m3u8", true) -> ExtractorLinkType.M3U8
            path.endsWith(".mpd", true) -> ExtractorLinkType.DASH
            path.endsWith(".mp4", true) || path.endsWith(".webm", true) -> ExtractorLinkType.VIDEO
            else -> null
        }
    }

    private fun isMediaHost(url: String): Boolean {
        val h = host(url).orEmpty()
        if (h.contains("youtube.com") || h.contains("youtu.be")) return youtubeId(url) != null
        return listOf(
            "dailymotion.com", "dai.ly", "rumble.com", "sibnet.ru", "ok.ru", "vk.com", "mail.ru",
            "vimeo.com", "streamtape", "vidmoly", "filelions", "voe.sx", "mixdrop",
            "upstream", "sendvid", "web.tv", "drive.google.com", "tau-video",
            "cizgiduo", "cizgipass", "streamwish", "filemoon", "luluvdo"
        ).any { h.contains(it) }
    }

    private fun collect(doc: Document, html: String, base: String = mainUrl): List<String> {
        val urls = linkedSetOf<String>()
        val attrs = listOf(
            "src", "data-src", "data-lazy-src", "data-litespeed-src", "data-original",
            "data-url", "data-video", "data-video-url", "data-file", "data-source",
            "data-embed", "data-embed-url", "data-iframe", "data-player",
        )

        doc.select("iframe, video, source, embed, [data-src], [data-lazy-src], [data-litespeed-src], [data-url], [data-video], [data-file], [data-source], [data-embed], [data-player], meta[property=og:video], meta[property=og:video:url]")
            .forEach { node ->
                attrs.forEach { key -> candidate(node.attr(key), base)?.let(urls::add) }
                candidate(node.attr("content"), base)?.let(urls::add)
                node.attr("srcdoc").takeIf(String::isNotBlank)?.let { srcdoc ->
                    val nested = Jsoup.parseBodyFragment(srcdoc)
                    collect(nested, srcdoc, base).forEach(urls::add)
                }
            }

        doc.select("a[href]").forEach { node ->
            val url = candidate(node.attr("href"), base) ?: return@forEach
            if (
                isDirect(url) != null || isMediaHost(url) ||
                url.contains("player", true) || url.contains("embed", true) ||
                url.contains("source", true) || url.contains("trembed=", true)
            ) urls.add(url)
        }

        val normalized = html
            .replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("&amp;", "&")

        Regex("""https?://[^\s'"<>\\]+""", RegexOption.IGNORE_CASE).findAll(normalized).forEach { match ->
            val url = candidate(match.value.trimEnd(')', ']', '}', ',', ';'), base) ?: return@forEach
            if (
                isDirect(url) != null || isMediaHost(url) ||
                url.contains("player", true) || url.contains("embed", true) ||
                url.contains("trembed=", true)
            ) urls.add(url)
        }

        Regex("""atob\(\s*["']([^"']+)["']\s*\)""", RegexOption.IGNORE_CASE)
            .findAll(normalized)
            .forEach { match -> candidate(match.groupValues[1], base)?.let(urls::add) }

        return urls.toList()
    }

    private suspend fun resolveDrive(
        url: String,
        referer: String,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val docId = Regex("""drive\.google\.com/file/d/([^/]+)/""", RegexOption.IGNORE_CASE)
            .find(url)?.groupValues?.getOrNull(1)
            ?: return false

        val info = runCatching {
            app.get(
                "https://drive.google.com/get_video_info?docid=$docId&drive_originator_app=303",
                referer = referer,
                headers = wioHeaders,
                timeout = 20000L,
            ).text
        }.getOrNull() ?: return false

        val encoded = Regex("""(?:^|&)fmt_stream_map=([^&]+)""")
            .find(info)?.groupValues?.getOrNull(1)
            ?: return false

        val decoded = runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrDefault(encoded)
        val streams = decoded.split(',').mapNotNull { entry ->
            entry.substringAfter('|', "").takeIf(String::isNotBlank)
        }
        if (streams.isEmpty()) return false

        streams.distinct().forEachIndexed { index, stream ->
            callback(
                newExtractorLink(
                    source = "Google Drive",
                    name = if (streams.size > 1) "Google Drive ${index + 1}" else "Google Drive",
                    url = stream,
                    type = INFER_TYPE,
                ) {
                    this.referer = url
                    headers = mapOf("Referer" to url, "User-Agent" to wioHeaders.getValue("User-Agent"))
                    quality = Qualities.Unknown.value
                }
            )
        }
        return true
    }

    private suspend fun resolveCandidate(
        rawUrl: String,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        depth: Int,
    ): Boolean {
        val url = normalizeYoutube(rawUrl)
        val directType = isDirect(url)
        if (directType != null) {
            callback(
                newExtractorLink(name, name, url, directType) {
                    this.referer = referer
                    headers = mapOf("Referer" to referer, "User-Agent" to wioHeaders.getValue("User-Agent"))
                    quality = Qualities.Unknown.value
                }
            )
            return true
        }

        if (host(url).orEmpty().contains("drive.google.com") && resolveDrive(url, referer, callback)) {
            return true
        }

        if (youtubeId(url) != null) {
            val youtubeLinks = mutableListOf<ExtractorLink>()
            val extracted = runCatching {
                loadExtractor(url, referer, subtitleCallback) { youtubeLinks += it }
            }.getOrDefault(false)
            youtubeLinks.forEach { link ->
                val renditions = if (link.type == ExtractorLinkType.M3U8) runCatching {
                    M3u8Helper.generateM3u8(link.name, link.url, link.referer.ifBlank { referer }, headers = link.headers)
                }.getOrDefault(emptyList()) else emptyList()
                (renditions.takeIf { it.isNotEmpty() } ?: listOf(link)).forEach(callback)
            }
            if (extracted || youtubeLinks.isNotEmpty()) return true
        }

        if (host(url).orEmpty().contains("rumble.com")) {
            val extracted = runCatching { loadExtractor(url, referer, subtitleCallback, callback) }.getOrDefault(false)
            if (extracted) return true
            val rumble = runCatching {
                app.get(url, referer = referer, headers = wioHeaders, timeout = 20000L).text
            }.getOrNull()
            if (rumble != null) {
                val normalized = rumble.replace("\\/", "/")
                val streams = Regex("""https?://[^\s'"<>]+\.(?:m3u8|mp4)(?:\?[^\s'"<>]*)?""", RegexOption.IGNORE_CASE)
                    .findAll(normalized).map { it.value }.distinct().toList()
                if (streams.isNotEmpty()) {
                    streams.forEach { stream ->
                        callback(newExtractorLink("Rumble", "Rumble", stream, isDirect(stream) ?: INFER_TYPE) {
                            this.referer = "https://rumble.com/"
                            quality = Regex("""(?:^|[/_-])(1080|720|480|360)(?:p|[/_.-])""").find(stream)
                                ?.groupValues?.getOrNull(1)?.toIntOrNull() ?: Qualities.Unknown.value
                        })
                    }
                    return true
                }
            }
        }

        if (isMediaHost(url)) {
            val loaded = runCatching { loadExtractor(url, referer, subtitleCallback, callback) }.getOrDefault(false)
            if (loaded) return true
        }

        val sameHost = host(url) != null && host(url) == host(mainUrl)
        val nestedCandidate = sameHost && (
            url.contains("player", true) || url.contains("embed", true) ||
                url.contains("source", true) || url.contains("video", true) ||
                url.contains("trembed=", true) || url.contains("trid=", true)
            )

        if (depth < 2 && nestedCandidate && url != referer) {
            val nested = runCatching {
                app.get(url, headers = wioHeaders, referer = referer, timeout = 20000L)
            }.getOrNull() ?: return false
            var found = false
            collect(nested.document, nested.text, url)
                .filterNot { it == url || it == referer }
                .take(30)
                .forEach { child ->
                    found = resolveCandidate(child, url, subtitleCallback, callback, depth + 1) || found
                }
            return found
        }

        return false
    }

    protected suspend fun resolvePublicPage(
        data: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val item = readPayload(data)
        val response = runCatching {
            app.get(item.page, headers = wioHeaders, timeout = 30000L)
        }.getOrNull()

        var found = false
        var inferredTitle: String? = null
        val resolvedLinks = mutableListOf<ExtractorLink>()
        if (response != null) {
            inferredTitle = pageTitle(response.document)
            collect(response.document, response.text, item.page).take(40).forEach { url ->
                found = resolveCandidate(url, item.page, subtitleCallback, { resolvedLinks.add(it) }, 0) || found
            }
        }

        val candidates = resolvedLinks.map { link ->
            QualityVariantSelection.Candidate(
                url = link.url,
                referer = link.referer,
                headers = link.headers,
                source = link.source,
                part = QualityVariantSelection.partKey(link.name, link.source),
                quality = link.quality,
                isHls = link.type == ExtractorLinkType.M3U8,
                explicitMaster = QualityVariantSelection.isExplicitMaster(link.name, link.type == ExtractorLinkType.M3U8)
            )
        }
        QualityVariantSelection.keepIndexes(candidates).forEach { callback(resolvedLinks[it]) }
        val query = WioKidsYoutubeFallback.buildQuery(
            seriesTitle = item.series ?: inferredTitle,
            episodeTitle = item.episodeTitle ?: inferredTitle,
            season = item.season,
            episode = item.episode,
        )
        val youtubeFound = if (!found) WioKidsYoutubeFallback.append(query, subtitleCallback, callback) else false
        return found || youtubeFound
    }
}
