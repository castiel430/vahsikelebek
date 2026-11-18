package com.neowatch

import com.lagradost.cloudstream3.network.CloudflareKiller
import okhttp3.Interceptor

/**
 * Cloudflare "Just a momentâ€¦" (managed challenge) sayfasÄ± yakalayÄ±cÄ±.
 *
 * Normal yanÄ±tlarda olduÄŸu gibi dÃ¶ner; gÃ¶vde bir Cloudflare challenge'Ä±ysa
 * isteÄŸi WebView Ã¼zerinden yeniden Ã§Ã¶zmeyi dener (CloudflareKiller).
 * Challenge tespiti gÃ¶vdenin ilk ~1 MB'Ä±nda, KAÃ‡TIRÄ°CÄ° marker'lar ile yapÄ±lÄ±r.
 */
object CfBypass {
    private val killer by lazy { CloudflareKiller() }

    val interceptor: Interceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        val body = response.peekBody(1_048_576L).string().lowercase()
        if (CHALLENGE_MARKERS.any { it in body }) killer.intercept(chain) else response
    }

    private val CHALLENGE_MARKERS = listOf(
        "just a moment",
        "gÃ¼venlik taramasÄ±ndan geÃ§iriliyorsunuz",
        "cf-chl",
        "challenge-platform",
        "attention required",
        "checking your browser",
        "enable javascript and cookies",
    )
}
