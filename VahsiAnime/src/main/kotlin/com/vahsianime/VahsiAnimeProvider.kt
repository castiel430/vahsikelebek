package com.vahsianime

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink

class VahsiAnimeProvider : MainAPI() {
    override var mainUrl = "https://example.org"
    override var name = "Vahşi Anime"
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)
    override var lang = "tr"

    override val hasMainPage = true
    override val mainPage = mainPageOf("$mainUrl/" to "Anime")

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? =
        newHomePageResponse(request.name, emptyList())

    override suspend fun search(query: String): List<SearchResponse> = emptyList()

    override suspend fun load(url: String): LoadResponse? = null

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean = false
}
