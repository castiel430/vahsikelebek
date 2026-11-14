package com.vahsikelebek.trakt

import android.content.Context
import com.lagradost.cloudstream3.Settings
import kotlinx.coroutines.MutableSharedFlow
import kotlinx.coroutines.SharedFlow

/**
 * Trakt TV ayarları.
 * Kullanıcı Trakt.tv client ID'ını girerek izleme takibini etkinleştirebilir.
 */
object TraktorSettings : Settings() {

    /** Trakt.tv client ID (Trakt.tv'den alınan) */
    var traktClientId: String by default("")

    /** Trakt API endpoint'i */
    const val API_BASE_URL = "https://api.trakt.tv"

    /** Trakt scope'u */
    const val API_SCOPE = "public"

    /** SharedFlow izleme değişiklikleri için */
    val watchedChanges: MutableSharedFlow<TraktWatchEvent> = kotlinx.coroutines.channelFlow {

    }.asMutableSharedFlow()

    /** Trakt izleme değişikliği olayı */
    data class TraktWatchEvent(
        val traktId: String,
        val action: String, // "watched", "unwatched", "rating"
        val mediaType: String, // "movie", "show", "episode"
        val title: String
    )
}