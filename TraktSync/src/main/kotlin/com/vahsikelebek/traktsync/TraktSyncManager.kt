package com.vahsikelebek.traktsync

import android.content.Context
import android.util.Log
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.result.VideoWatchState
import com.lagradost.cloudstream3.utils.DataStoreHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * CloudStream kütüphanesini Trakt.tv ile çift yönlü eşitler.
 *
 * CloudStream → Trakt (gönderme):
 *  - COMPLETED  → Trakt izleme geçmişine "izlendi" olarak eklenir
 *                 (film: tam; dizi: son bilinen bölüm — sezon/bölüm varsa)
 *  - WATCHING / ONHOLD / PLANTOWATCH → Trakt izleme listesine eklenir
 *  - DROPPED    → dokunulmaz
 *
 * Trakt → CloudStream (çekme):
 *  - Trakt'ta izlendi görünen filmler CloudStream'de COMPLETED + izlendi işareti alır
 *  - Trakt'ta izlendi görünen dizi bölümleri, sağlayıcının bölüm id'sine eşlenerek
 *    CloudStream'de "izlendi" (VideoWatchState.Watched) olarak işaretlenir
 *  - Dizinin tüm yayındaki bölümleri izlendiyse WatchType.COMPLETED, en az biri
 *    izlendiyse WATCHING yapılır (DROPPED / ONHOLD / COMPLETED'a dokunulmaz)
 *
 * Tekrar eklemeyi önlemek için kitap işareti noktasına `id:latestUpdatedTime`
 * anahtarı ve çekme tarafında `ep:<seriId>:<sezon>:<bölüm>` anahtarları kullanılır.
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
                    if (TraktSyncConfig.autoSync) syncOnce()
                } catch (e: Exception) {
                    Log.w(TAG, "eşitleme turu hata: ${e.message}")
                }
                kotlinx.coroutines.delay(if (first) 5_000L else INTERVAL_MS)
                first = false
            }
        }
    }

    /** Tek eşitleme turu (manuel "Şimdi Eşitle" düğmesi de kullanır). autoSync'e bakılmadan çalışır. */
    suspend fun syncOnce() {
        if (!TraktSyncConfig.connected) return
        if (!TraktApi.ensureFreshToken()) {
            Log.w(TAG, "token geçersiz, oturum kapatıldı")
            TraktSyncConfig.clearSession()
            return
        }

        pushToTrakt()
        try {
            pullFromTrakt()
        } catch (e: Exception) {
            // Çekme hatası gönderme tarafını etkilemesin
            Log.w(TAG, "trakt'tan çekme başarısız: ${e.message}")
        }
    }

    private fun isShowType(type: TvType?): Boolean =
        type == TvType.TvSeries || type == TvType.Anime ||
            type == TvType.Cartoon || type == TvType.AsianDrama ||
            type == TvType.OVA

    private suspend fun pushToTrakt() {
        val bookmarks = runCatching { DataStoreHelper.getAllBookmarkedData() }.getOrNull().orEmpty()
        for (b in bookmarks) {
            val id = b.id ?: continue
            val syncKey = "$id:${b.latestUpdatedTime}"
            if (TraktSyncConfig.syncedIds().contains(syncKey)) continue

            val state = runCatching { DataStoreHelper.getResultWatchState(id) }.getOrNull() ?: continue
            if (state == WatchType.NONE || state == WatchType.DROPPED) continue

            // 1) Kimlik syncData'dan; yoksa başlıkla Trakt'ta ara
            var (imdb, tmdb) = TraktIds.resolve(b.syncData)
            val isShow = isShowType(b.type)
            if (imdb == null && tmdb == null) {
                val title = b.name?.takeIf { it.isNotBlank() }
                if (title != null) {
                    val found = runCatching { TraktApi.searchIdsByTitle(title, b.year, isShow) }.getOrNull()
                    imdb = found?.first
                    tmdb = found?.second
                }
            }

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

    /**
     * Trakt'taki izleme geçmişini CloudStream'e işler.
     * Kitap işareti olarak ekli içeriklerle imdb/tmdb üzerinden eşleştirir.
     */
    private suspend fun pullFromTrakt() {
        val movies = TraktApi.watchedMovies()
        val shows = TraktApi.watchedShows()
        if (movies.isEmpty() && shows.isEmpty()) {
            Log.i(TAG, "Trakt izleme geçmişi boş")
            return
        }

        val moviesByImdb = movies.mapNotNull { m -> m.imdb?.let { it to m } }.toMap()
        val moviesByTmdb = movies.mapNotNull { m -> m.tmdb?.let { it to m } }.toMap()
        val showsByImdb = shows.mapNotNull { s -> s.imdb?.let { it to s } }.toMap()
        val showsByTmdb = shows.mapNotNull { s -> s.tmdb?.let { it to s } }.toMap()

        val bookmarks = runCatching { DataStoreHelper.getAllBookmarkedData() }.getOrNull().orEmpty()
        var changed = 0
        for (b in bookmarks) {
            val id = b.id ?: continue
            val isShow = isShowType(b.type)

            var (imdb, tmdb) = TraktIds.resolve(b.syncData)
            if (imdb == null && tmdb == null) {
                // İtme tarafıyla aynı kimlik çözümleme (başlıkla arama)
                val title = b.name?.takeIf { it.isNotBlank() }
                if (title != null) {
                    val found = runCatching { TraktApi.searchIdsByTitle(title, b.year, isShow) }.getOrNull()
                    imdb = found?.first
                    tmdb = found?.second
                }
            }

            if (!isShow) {
                val watched = imdb?.let(moviesByImdb::get) ?: tmdb?.let(moviesByTmdb::get)
                if (watched == null || watched.plays <= 0) continue
                if (markVideoWatched(id)) changed++
                val state = runCatching { DataStoreHelper.getResultWatchState(id) }.getOrNull() ?: WatchType.NONE
                if (state == WatchType.NONE || state == WatchType.WATCHING || state == WatchType.PLANTOWATCH) {
                    DataStoreHelper.setResultWatchState(id, WatchType.COMPLETED.internalId)
                    changed++
                }
                continue
            }

            // ---- dizi ----
            val match = imdb?.let(showsByImdb::get) ?: tmdb?.let(showsByTmdb::get)
            if (match == null) continue

            // 1) Bölüm bazında "izlendi" işareti (sağlayıcının bölüm id şemasına eşle)
            val applied = TraktSyncConfig.pulledApplied()
            for (season in match.seasons) {
                for (ep in season.episodes) {
                    if (ep.plays <= 0) continue
                    val key = "ep:$id:${season.number}:${ep.number}"
                    if (applied.contains(key)) continue
                    markEpisodeWatched(id, b.type, season.number, ep.number)
                    TraktSyncConfig.addPulledApplied(key)
                    changed++
                }
            }

            // 2) Sonuç durumu: hepsi izlendiyse COMPLETED, en az biriyse WATCHING.
            //    DROPPED / ONHOLD / COMPLETED kullanıcı tercihidir, dokunma.
            val state = runCatching { DataStoreHelper.getResultWatchState(id) }.getOrNull() ?: WatchType.NONE
            if (state == WatchType.DROPPED || state == WatchType.ONHOLD || state == WatchType.COMPLETED) continue

            val progressId = imdb ?: tmdb
            val progress = progressId?.let { pid ->
                runCatching { TraktApi.showWatchedProgress(pid) }.getOrNull()
            }
            val target = if (progress != null && progress.first > 0 && progress.second >= progress.first) {
                WatchType.COMPLETED
            } else {
                WatchType.WATCHING
            }
            if (state != target) {
                DataStoreHelper.setResultWatchState(id, target.internalId)
                changed++
            }
        }
        if (changed > 0) Log.i(TAG, "trakt'tan $changed işaret uygulandı")
    }

    /** VideoWatchState.Watched'ı yazar; zaten işaretliyse false döner. */
    private fun markVideoWatched(id: Int): Boolean = runCatching {
        if (DataStoreHelper.getVideoWatchState(id) == VideoWatchState.Watched) return false
        DataStoreHelper.setVideoWatchState(id, VideoWatchState.Watched)
        true
    }.getOrDefault(false)

    /**
     * Sağlayıcının bölüm id'sini hesaplayıp izlendi olarak işaretler.
     *
     * CloudStream bölüm id şemaları (ResultViewModel2):
     *  - TvSeriesLoadResponse: parentId + season * 100_000 + episode + 1  (season null → +0)
     *  - AnimeLoadResponse:    parentId + episode + dubId * 1_000_000 + season * 10_000
     *    dubId: Subbed = 0, Dubbed = 1, None = -1  (season null → +0)
     *  - MovieLoadResponse:    parentId (filmle aynı id)
     *
     * Sağlayıcı season alanı vermeyebildiği için iki varyant da işaretlenir.
     */
    private fun markEpisodeWatched(parentId: Int, type: TvType?, season: Int, episode: Int) {
        val targets = linkedSetOf(
            parentId + season * 100_000 + episode + 1,
            parentId + episode + 1,
        )
        if (type == TvType.Anime || type == TvType.OVA) {
            for (dubId in intArrayOf(-1, 0, 1)) {
                targets += parentId + episode + dubId * 1_000_000 + season * 10_000
                targets += parentId + episode + dubId * 1_000_000
            }
        }
        for (t in targets) {
            runCatching {
                if (DataStoreHelper.getVideoWatchState(t) != VideoWatchState.Watched) {
                    DataStoreHelper.setVideoWatchState(t, VideoWatchState.Watched)
                }
            }
        }
    }
}
