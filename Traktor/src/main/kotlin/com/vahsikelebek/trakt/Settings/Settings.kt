package com.vahsikelebek.trakt

/**
 * Trakt TV ayarları.
 * Kullanıcı Trakt.tv client ID'ını girerek izleme takibini etkinleştirebilir.
 * Client ID: Trakt.tv'den alınan API key.
 */
object TraktorSettings {

    /** Trakt.tv client ID (Boş bırakılırsa otomatik kullanılmaz) */
    var traktClientId: String = ""

    /** Trakt API endpoint'i */
    const val API_BASE_URL = "https://api.trakt.tv"

    /** Trakt scope'u */
    const val API_SCOPE = "public"
}