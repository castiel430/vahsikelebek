package com.vahsikelebek.kids

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.loadExtractor
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale

object KidsYoutubeFallback {
    private data class Candidate(
        val id: String,
        val title: String,
        val author: String? = null,
        val duration: Int? = null,
    )

    private val staticInvidious = listOf(
        "https://invidious.f5.si",
        "https://inv.nadeko.net",
        "https://invidious.nerdvpn.de",
    )

    private val youtubeHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Mobile Safari/537.36",
        "Accept-Language" to "tr-TR,tr;q=0.9,en;q=0.7",
        "Cookie" to "CONSENT=YES+cb.20210328-17-p0.en+FX+410",
    )

    private fun normalize(value: String): String {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)
            .replace('ı', 'i')
            .replace(Regex("[^a-z0-9]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun buildQuery(
        seriesTitle: String?,
        episodeTitle: String?,
        season: Int? = null,
        episode: Int? = null,
    ): String {
        val parts = linkedSetOf<String>()
        seriesTitle?.trim()?.takeIf { it.isNotBlank() }?.let(parts::add)
        episodeTitle?.trim()?.takeIf { it.isNotBlank() && !it.equals(seriesTitle, true) }?.let(parts::add)
        if (season != null && episode != null) parts.add("Sezon $season Bölüm $episode")
        else if (episode != null) parts.add("Bölüm $episode")
        parts.add("Türkçe çizgi film")
        return parts.joinToString(" ")
    }

    private fun score(candidate: Candidate, query: String): Int {
        val generic = setOf("turkce", "cizgi", "film", "izle", "bolum", "sezon", "full", "hd")
        val tokens = normalize(query)
            .split(' ')
            .filter { it.length >= 3 && it !in generic && it.toIntOrNull() == null }
            .distinct()
        val title = normalize(candidate.title)

        var score = tokens.count { title.contains(it) } * 20
        if (tokens.isNotEmpty() && tokens.all { title.contains(it) }) score += 50
        if (title.contains("turkce") || title.contains("dublaj")) score += 10
        if (candidate.duration != null && candidate.duration in 120..10800) score += 4
        if (
            title.contains("reaction") || title.contains("tepki") ||
            title.contains("inceleme") || title.contains("oyun") ||
            title.contains("fragman") || title.contains("trailer")
        ) score -= 35
        return score
    }

    private fun textOf(parent: JSONObject?, key: String): String? {
        val obj = parent?.optJSONObject(key) ?: return null
        obj.optString("simpleText").trim().takeIf(String::isNotBlank)?.let { return it }
        val runs = obj.optJSONArray("runs") ?: return null
        return buildString {
            for (i in 0 until runs.length()) {
                append(runs.optJSONObject(i)?.optString("text").orEmpty())
            }
        }.trim().takeIf(String::isNotBlank)
    }

    private fun parseDuration(text: String?): Int? {
        val parts = text?.trim()?.split(':')?.mapNotNull(String::toIntOrNull) ?: return null
        if (parts.isEmpty()) return null
        return parts.fold(0) { acc, value -> acc * 60 + value }.takeIf { it > 0 }
    }

    private fun collectVideoRenderers(value: Any?, out: MutableList<Candidate>) {
        when (value) {
            is JSONObject -> {
                value.optJSONObject("videoRenderer")?.let { video ->
                    val id = video.optString("videoId").trim()
                    val title = textOf(video, "title").orEmpty()
                    if (id.length == 11 && title.isNotBlank()) {
                        out += Candidate(
                            id = id,
                            title = title,
                            author = textOf(video, "ownerText") ?: textOf(video, "shortBylineText"),
                            duration = parseDuration(textOf(video, "lengthText")),
                        )
                    }
                }
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    collectVideoRenderers(value.opt(key), out)
                }
            }
            is JSONArray -> {
                for (i in 0 until value.length()) collectVideoRenderers(value.opt(i), out)
            }
        }
    }

    private suspend fun searchYoutube(query: String): List<Candidate> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val html = runCatching {
            app.get(
                "https://www.youtube.com/results?search_query=$encoded&hl=tr&gl=TR",
                headers = youtubeHeaders,
                timeout = 20000L,
            ).text
        }.getOrNull() ?: return emptyList()

        val key = Regex(""""INNERTUBE_API_KEY":"([^"]+)"""")
            .find(html)?.groupValues?.getOrNull(1)
            ?: return emptyList()
        val version = Regex(""""INNERTUBE_CLIENT_VERSION":"([^"]+)"""")
            .find(html)?.groupValues?.getOrNull(1)
            ?: return emptyList()

        val request = mapOf(
            "context" to mapOf(
                "client" to mapOf(
                    "clientName" to "WEB",
                    "clientVersion" to version,
                    "hl" to "tr",
                    "gl" to "TR",
                )
            ),
            "query" to query,
        )

        val response = runCatching {
            app.post(
                "https://www.youtube.com/youtubei/v1/search?prettyPrint=false&key=$key",
                json = request,
                headers = youtubeHeaders + mapOf(
                    "Content-Type" to "application/json",
                    "X-YouTube-Client-Name" to "1",
                    "X-YouTube-Client-Version" to version,
                ),
                timeout = 20000L,
            ).text
        }.getOrNull() ?: return emptyList()

        val root = runCatching { JSONObject(response) }.getOrNull() ?: return emptyList()
        val candidates = mutableListOf<Candidate>()
        collectVideoRenderers(root, candidates)
        return candidates.distinctBy { it.id }
    }

    private suspend fun discoverInvidious(): List<String> {
        val dynamic = runCatching {
            val root = JSONArray(
                app.get(
                    "https://api.invidious.io/instances.json?sort_by=api,type",
                    timeout = 12000L,
                ).text
            )
            buildList {
                for (i in 0 until root.length()) {
                    val row = root.optJSONArray(i) ?: continue
                    val domain = row.optString(0).trim()
                    val meta = row.optJSONObject(1) ?: continue
                    if (domain.isNotBlank() && meta.optBoolean("api") && meta.optString("type") == "https") {
                        add("https://$domain")
                    }
                }
            }
        }.getOrDefault(emptyList())
        return (dynamic + staticInvidious).distinct().take(6)
    }

    private suspend fun searchInvidious(query: String): List<Candidate> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        for (base in discoverInvidious()) {
            val text = runCatching {
                app.get(
                    "$base/api/v1/search?q=$encoded&type=video&sort_by=relevance&page=1",
                    timeout = 12000L,
                ).text
            }.getOrNull() ?: continue
            val array = runCatching { JSONArray(text) }.getOrNull() ?: continue
            val results = buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("videoId").trim()
                    val title = item.optString("title").trim()
                    if (id.length != 11 || title.isBlank()) continue
                    add(
                        Candidate(
                            id = id,
                            title = title,
                            author = item.optString("author").trim().takeIf(String::isNotBlank),
                            duration = item.optInt("lengthSeconds").takeIf { it > 0 },
                        )
                    )
                }
            }
            if (results.isNotEmpty()) return results
        }
        return emptyList()
    }

    private fun queryVariants(query: String): List<String> {
        val original = query.trim()
        if (original.isBlank()) return emptyList()

        val variants = linkedSetOf<String>()
        variants += original

        val compactTurkish = original
            .replace(
                Regex(
                    """\bTürkçe\s+çizgi\s+film\b""",
                    RegexOption.IGNORE_CASE,
                ),
                "Türkçe",
            )
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (compactTurkish.isNotBlank()) variants += compactTurkish

        val seasonEpisode = Regex(
            """\bSezon\s+(\d+)\s+Bölüm\s+(\d+)\b""",
            RegexOption.IGNORE_CASE,
        ).find(compactTurkish)

        if (seasonEpisode != null) {
            val episodeNo = seasonEpisode.groupValues[2]
            val simplified = compactTurkish
                .replace(seasonEpisode.value, "Bölüm $episodeNo")
                .replace(Regex("""\s+"""), " ")
                .trim()
            if (simplified.isNotBlank()) variants += simplified
        }

        val withoutGenericSuffix = compactTurkish
            .replace(
                Regex(
                    """\b(?:Türkçe\s+)?(?:çizgi\s+film|izle)\b""",
                    setOf(RegexOption.IGNORE_CASE),
                ),
                " ",
            )
            .replace(Regex("""\s+"""), " ")
            .trim()

        if (withoutGenericSuffix.isNotBlank()) {
            variants += "$withoutGenericSuffix Türkçe"
        }

        return variants
            .map { it.trim() }
            .filter(String::isNotBlank)
            .distinct()
            .take(4)
    }

    private suspend fun search(query: String): List<Candidate> {
        val collected = linkedMapOf<String, Candidate>()

        for (variant in queryVariants(query)) {
            searchYoutube(variant).forEach { candidate ->
                collected.putIfAbsent(candidate.id, candidate)
            }

            val goodEnough = collected.values.count { score(it, query) >= 35 }
            if (goodEnough >= 4) break
        }

        if (collected.isEmpty()) {
            for (variant in queryVariants(query)) {
                searchInvidious(variant).forEach { candidate ->
                    collected.putIfAbsent(candidate.id, candidate)
                }
                if (collected.isNotEmpty()) break
            }
        }

        return collected.values
            .map { it to score(it, query) }
            .filter { it.second >= 35 }
            .sortedByDescending { it.second }
            .map { it.first }
            .distinctBy { it.id }
    }

    suspend fun append(
        query: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        maxResults: Int = 2,
    ): Boolean {
        if (query.isBlank()) return false
        var found = false
        search(query).take(maxResults).forEach { candidate ->
            val extractedLinks = mutableListOf<ExtractorLink>()
            val ok = runCatching {
                loadExtractor(
                    "https://www.youtube.com/watch?v=" + candidate.id,
                    subtitleCallback,
                    { extractedLinks += it },
                )
            }.getOrDefault(false)
            extractedLinks.forEach { link ->
                val renditions = if (link.type == ExtractorLinkType.M3U8) runCatching {
                    M3u8Helper.generateM3u8(link.name, link.url, link.referer, headers = link.headers)
                }.getOrDefault(emptyList()) else emptyList()
                (renditions.takeIf { it.isNotEmpty() } ?: listOf(link)).forEach(callback)
            }
            found = ok || extractedLinks.isNotEmpty() || found
        }
        return found
    }
}
