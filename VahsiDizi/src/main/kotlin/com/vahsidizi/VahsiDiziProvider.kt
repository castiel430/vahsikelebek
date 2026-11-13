package com.vahsidizi

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink

class VahsiDiziProvider : MainAPI() {
    override var mainUrl = "https://example.org"
    override var name = "Vahşi Dizi"
    override val supportedTypes = setOf(TvType.TvSeries)
    override var lang = "tr"

    override val hasMainPage = true
    override val mainPage = mainPageOf("$mainUrl/" to "Diziler")

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
