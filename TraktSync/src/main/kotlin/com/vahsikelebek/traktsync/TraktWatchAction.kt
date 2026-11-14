package com.vahsikelebek.traktsync

import android.content.Context
import android.widget.Toast
import com.lagradost.cloudstream3.actions.VideoClickAction
import com.lagradost.cloudstream3.ui.result.LinkLoadingResult
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.UiText
import com.lagradost.cloudstream3.utils.txt

/**
 * Oynatma düğmesine basıldığında seçenek olarak listelenir.
 * Tıklandığında içeriği Trakt.tv'de "izlendi" olarak işaretler.
 */
class TraktWatchAction : VideoClickAction() {

    override val name: UiText = txt("✓ Trakt'a İzledim Olarak İşaretle")

    override val isPlayer: Boolean = false

    override val oneSource: Boolean = true

    override fun shouldShow(context: Context?, video: ResultEpisode?): Boolean =
        TraktSyncConfig.connected

    override suspend fun runAction(
        context: Context?,
        video: ResultEpisode,
        result: LinkLoadingResult,
        index: Int?,
    ) {
        val ctx = context ?: return
        val ids = TraktIds.resolve(result.syncData)

        val season = video.season
        val episode = video.episode
        val isEpisode = season != null && episode != null

        val ok = runCatching {
            if (!TraktApi.ensureFreshToken()) return@runCatching false
            if (ids.first != null || ids.second != null) {
                TraktApi.addHistory(ids.first, ids.second, season, episode)
            } else if (!isEpisode && !video.name.isNullOrBlank()) {
                TraktApi.addHistoryBySearch(video.name.orEmpty(), year = null, isShow = false)
            } else {
                false
            }
        }.getOrDefault(false)

        val msg = if (ok) {
            if (isEpisode) {
                "Trakt'a izlendi işaretlendi: S${season}E${episode}"
            } else {
                "Trakt'a izlendi işaretlendi: ${video.name}"
            }
        } else {
            "Trakt işaretleme başarısız (kimlik bilgisi bulunamadı veya bağlantı yok)"
        }
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
    }
}
