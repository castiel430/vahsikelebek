package com.vahsikelebek.trakt

import com.bigbull.common.BaseSettings

/**
 * Trakt TV ayarları.
 * Kullanıcı Trakt.tv client ID'ını girerek izleme takibini etkinleştirebilir.
 */
object TraktorSettings : BaseSettings("traktor_trakt") {

    /** Trakt.tv client ID (Trakt.tv'den alınan) */
    var traktClientId: String by default("")

    /** Trakt API endpoint'i */
    const val API_BASE_URL = "https://api.trakt.tv"

    /** Trakt scope'u */
    const val API_SCOPE = "public"
}