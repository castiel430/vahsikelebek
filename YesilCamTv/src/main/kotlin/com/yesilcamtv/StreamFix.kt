package com.vahsikelebek.streamfix

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink

/**
 * BTK (Türkiye) tarafından DNS/SNI düzeyinde engellenen video host'larını,
 * aynı servisin çalışan ayna alan adlarına çevirir.
 *
 * 2001 (ERROR_CODE_IO_NETWORK_CONNECTION_FAILED) hatalarının büyük kısmı
 * `erisime_engellenmis` zehirli DNS yanıtından (195.175.254.2) gelir.
 * Eklenti tarafında yapılabilecek tek şey, oynatıcıya gitmeden önce
 * engelli host'u çalışan bir aynayla değiştirmektir.
 */
object StreamFix {
    private val HOST_REWRITES = mapOf(
        // DoodStream ailesi
        "dood.to" to "doodstream.com",
        "dood.ws" to "doodstream.com",
        "dood.li" to "doodstream.com",
        "dood.sh" to "doodstream.com",
        "dood.la" to "doodstream.com",
        "dood.pm" to "doodstream.com",
        "dood.wf" to "doodstream.com",
        "dood.cx" to "doodstream.com",
        "ds2play.com" to "dsvplay.com",

        // StreamWish ailesi
        "streamwish.to" to "awish.pro",
        "streamwish.site" to "awish.pro",
        "mwish.pro" to "awish.pro",

        // LuluStream ailesi
        "luluvdo.com" to "lulustream.com",
        "luluvdoo.com" to "lulustream.com",
    )

    /** Engelli host'u çalışan aynayla değiştirir; diğer URL'ler olduğu gibi döner. */
    fun fix(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return trimmed

        val scheme: String
        val rest: String
        when {
            trimmed.startsWith("//") -> {
                scheme = "https:"
                rest = trimmed.substring(2)
            }
            trimmed.contains("://") -> {
                scheme = trimmed.substringBefore("://") + "://"
                rest = trimmed.substringAfter("://")
            }
            else -> return trimmed
        }

        val authority = rest.substringBefore("/").substringBefore("?").substringBefore("#")
        val host = authority.substringBefore(":").removePrefix("www.").lowercase()
        val mirror = HOST_REWRITES[host] ?: return trimmed

        val port = if (authority.contains(":")) ":" + authority.substringAfter(":") else ""
        val tail = rest.substring(authority.length)
        return "$scheme//$mirror$port$tail"
    }

    suspend fun loadExtractor(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean = com.lagradost.cloudstream3.utils.loadExtractor(
        fix(url), referer, subtitleCallback, callback
    )

    suspend fun loadExtractor(
        url: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean = com.lagradost.cloudstream3.utils.loadExtractor(
        fix(url), subtitleCallback, callback
    )
}
