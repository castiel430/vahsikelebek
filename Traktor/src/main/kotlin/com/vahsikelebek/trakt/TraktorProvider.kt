package com.vahsikelebek.trakt

import com.lagradost.cloudstream3.metaproviders.TraktProvider
import com.lagradost.cloudstream3.MainAPI

@TraktProvider
class TraktorProvider : MainAPI() {

    /** Trakt.tv client ID (Kullanıcı ayarılır) */
    var traktClientId: String = ""

    /** Trakt API endpoint'i */
    const val API_BASE_URL = "https://api.trakt.tv"

    /** Trakt API scope'u */
    const val API_SCOPE = "public"

    /** Trakt medyasını yükle */
    override suspend fun loadDetails(media: com.lagradost.cloudstream3.TraktMedia): com.lagradost.cloudstream3.TraktMedia {
        return media
    }
}