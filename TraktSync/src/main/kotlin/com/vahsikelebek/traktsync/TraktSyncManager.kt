package com.vahsikelebek.traktsync

import android.content.Context
import android.util.Log
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.utils.DataStoreHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * CloudStream kütüphanesini Trakt.tv ile eşitler.
 *
 * Eşleme kuralı:
 *  - COMPLETED  → Trakt izleme geçmişine "izlendi" olarak eklenir
 *                 (film: tam; dizi: son bilinen bölüm — sezon/bölüm varsa)
 *  - WATCHING / ONHOLD / PLANTOWATCH → Trakt izleme listesine eklenir
 *  - DROPPED    → dokunulmaz
 *
 * Tekrar eklemeyi önlemek için kitap işareti noktasına `id:latestUpdatedTime`
 * anahtarı kullanılır.
 */
object TraktSyncManager {
    private const val TAG = "TraktSync"
    private const val INTERVAL_MS = 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var worker: kotlinx.coroutines.Job? = null

    fun start(context: Context) {
        if (worker?.isActive == true) return
        worker = scope.launch {
            // Uygulama açılışında hemen bir kez, sonra düzenli aralıklarla
            var first = true
            while (true) {
                try {
                    syncOnce()
                } catch (e: Exception) {
                    Log.w(TAG, "eşitleme turu hata: ${e.message}")
                }
                kotlinx.coroutines.delay(if (first) 5_000L else INTERVAL_MS)
                first = false
            }
        }
    }

    /** Tek eşitleme turu (manuel "Şimdi Eşitle" düğmesi de kullanır). */
    suspend fun syncOnce() {
        if (!TraktSyncConfig.autoSync) return
        if (!TraktSyncConfig.connected) return
        if (!TraktApi.ensureFreshToken()) {
            Log.w(TAG, "token geçersiz, oturum kapatıldı")
            TraktSyncConfig.clearSession()
            return
        }

        val bookmarks = runCatching { DataStoreHelper.getAllBookmarkedData() }.getOrNull().orEmpty()
        for (b in bookmarks) {
            val id = b.id ?: continue
            val syncKey = "$id:${b.latestUpdatedTime}"
            if (TraktSyncConfig.syncedIds().contains(syncKey)) continue

            val state = runCatching { DataStoreHelper.getResultWatchState(id) }.getOrNull() ?: continue
            if (state == WatchType.NONE || state == WatchType.DROPPED) continue

            val (imdb, tmdb) = TraktIds.resolve(b.syncData)
            val isShow = b.type == TvType.TvSeries || b.type == TvType.Anime ||
                b.type == TvType.Cartoon || b.type == TvType.AsianDrama ||
                b.type == TvType.OVA

            if (state == WatchType.COMPLETED) {
                val ok = when {
                    isShow -> {
                        val last = runCatching { DataStoreHelper.getLastWatched(id) }.getOrNull()
                        if (last?.season != null && last.episode != null && (imdb != null || tmdb != null)) {
                            TraktApi.addHistory(imdb, tmdb, last.season, last.episode)
                        } else {
                            // Sezon/bölüm bilinmiyorsa bütün diziyi geçmişe yazmak yerine atla
                            Log.i(TAG, "${b.name}: sezon/bölüm yok, atlandı")
                            false
                        }
                    }
                    imdb != null || tmdb != null -> TraktApi.addHistory(imdb, tmdb, null, null)
                    else -> b.name?.let { TraktApi.addHistoryBySearch(it, b.year, isShow = false) } ?: false
                }
                if (ok) TraktSyncConfig.addSyncedId(syncKey)
            } else {
                // WATCHING / ONHOLD / PLANTOWATCH → izleme listesi
                if (imdb == null && tmdb == null) continue
                if (TraktApi.addToWatchlist(imdb, tmdb, isShow)) {
                    TraktSyncConfig.addSyncedId(syncKey)
                }
            }
        }
    }
}
