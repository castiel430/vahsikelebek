package com.vahsikelebek.trakt

import android.content.Context
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.metaproviders.TraktProvider
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TraktMedia
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.loadExtractor
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType

@TraktProvider
class TraktorProvider : MainAPI() {

    override val traktId: String?
        get() = traktMedia?.ids?.trakt

    override val traktSlug: String?
        get() = traktMedia?.ids?.slug

    override suspend fun loadDetails(media: TraktMedia): TraktMedia {
        // Framework will handle Trakt details loading
        return media
    }

    // Trakt API configuration
    companion object {
        const val TRAKT_API_BASE = "https://api.trakt.tv"
        const val TRAKT_SCOPE = "public"
    }
}