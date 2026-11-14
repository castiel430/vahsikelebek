package com.vahsikelebek.traktsync

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

/**
 * Trakt Senkron — CloudStream kütüphanenizi Trakt.tv ile eşitler.
 *
 * Yapılanlar:
 *  - "✓ Trakt'a İzledim Olarak İşaretle" video tıklama düğmesi (oynat menüsü)
 *  - Kütüphanedeki işaretleri 60 saniyede bir Trakt.tv ile eşitler
 *  - Ayarlar ekranına "Trakt Senkron" düğmesi ekler
 */
@CloudstreamPlugin
class TraktSyncPlugin : Plugin() {

    override fun load(context: Context) {
        TraktSyncConfig.init(context)
        TraktSyncManager.start(context)
        registerVideoClickAction(TraktWatchAction())
        openSettings = { ctx -> TraktSettingsDialog.show(ctx) }
    }
}
