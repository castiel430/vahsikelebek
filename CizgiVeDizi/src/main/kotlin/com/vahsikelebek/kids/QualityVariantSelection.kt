package com.vahsikelebek.kids

import java.net.URI

/** Keep an HLS master instead of emitting its quality renditions as sources.
 * Different mirrors remain separate, including different referers/tokens.
 */
object QualityVariantSelection {
    data class Candidate(
        val url: String,
        val referer: String,
        val headers: Map<String, String>,
        val source: String,
        val part: String,
        val quality: Int,
        val isHls: Boolean,
        val explicitMaster: Boolean = false
    )

    fun isExplicitMaster(name: String, isHls: Boolean): Boolean =
        isHls && Regex("(?i)\\bmaster\\s*hls\\b").containsMatchIn(name)

    fun partKey(name: String, fallback: String): String = name
        .replace(Regex("(?i)\\b(?:master\\s*hls|(?:2160|1440|1080|720|480|360)p?|4k)\\b"), "")
        .replace(Regex("(?i)\\b\\d{3,4}\\s*[x×]\\s*\\d{3,4}\\b"), "")
        .replace(Regex("\\s+"), " ")
        .trim(' ', '-', '•', '|')
        .ifBlank { fallback }

    fun keepIndexes(links: List<Candidate>): List<Int> {
        val unique = links.indices.distinctBy { Triple(links[it].url, links[it].referer, links[it].headers) }
        val selected = unique.filter { index ->
            val link = links[index]
            if (!link.isHls || link.explicitMaster) true else {
                val masters = unique.filter { masterIndex ->
                    val master = links[masterIndex]
                    master.explicitMaster && master.source.equals(link.source, true) &&
                        master.part.equals(link.part, true) && master.referer == link.referer &&
                        master.headers == link.headers && sameStreamFamily(master.url, link.url)
                }
                masters.size != 1
            }
        }
        return selected
    }

    private fun sameStreamFamily(master: String, rendition: String): Boolean {
        val masterUri = runCatching { URI(master) }.getOrNull() ?: return false
        val renditionUri = runCatching { URI(rendition) }.getOrNull() ?: return false
        val masterHost = masterUri.host ?: return false
        if (renditionUri.host != null && !masterHost.equals(renditionUri.host, true)) return false
        val masterPath = masterUri.path.orEmpty().trimEnd('/')
        val renditionPath = renditionUri.path.orEmpty()
        val parent = masterPath.substringBeforeLast('/', "")
        return renditionPath.startsWith("$masterPath/") ||
            (parent.isNotEmpty() && renditionPath.substringBeforeLast('/', "") == parent) ||
            (parent.isEmpty() && renditionPath.count { it == '/' } == 1)
    }
}
