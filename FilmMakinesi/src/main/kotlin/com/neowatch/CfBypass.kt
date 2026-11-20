package com.neowatch

import com.lagradost.cloudstream3.network.CloudflareKiller
import okhttp3.Interceptor

/**
 * Cloudflare "Just a moment…" (managed challenge) sayfası yakalayıcı.
 *
 * Normal yanıtlarda olduğu gibi döner; gövde bir Cloudflare challenge'ıysa
 * isteği WebView üzerinden yeniden çözmeyi dener (CloudflareKiller).
 * Challenge tespiti gövdenin ilk ~1 MB'ında, KAÇTIRİCİ marker'lar ile yapılır.
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
        "güvenlik taramasından geçiriliyorsunuz",
        "cf-chl",
        "challenge-platform",
        "attention required",
        "checking your browser",
        "enable javascript and cookies",
    )
}
