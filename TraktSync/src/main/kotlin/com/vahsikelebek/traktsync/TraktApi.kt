package com.vahsikelebek.traktsync

import android.util.Log
import com.lagradost.cloudstream3.app
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** CloudStream syncData haritasından IMDb/TMDb kimliklerini çözer. */
object TraktIds {
    private val IMDB_REGEX = Regex("""^tt\d{7,9}$""")

    /** Dönen çiftin sırası: (imdbId, tmdbId). */
    fun resolve(syncData: Map<String, String>?): Pair<String?, String?> {
        if (syncData.isNullOrEmpty()) return null to null
        var imdb: String? = null
        var tmdb: String? = null
        for ((key, value) in syncData) {
            val v = value.trim()
            when {
                v.startsWith("{") -> {
                    runCatching {
                        val map = JSONObject(v)
                        for (k in map.keys()) {
                            val raw = map.optString(k).trim()
                            when (k.lowercase()) {
                                "imdb" -> if (raw.isNotBlank()) imdb = raw
                                "tmdb" -> if (raw.isNotBlank()) tmdb = raw.toIntOrNull()?.toString() ?: raw
                            }
                        }
                    }
                }
                IMDB_REGEX.matches(v) -> imdb = v
                v.all { it.isDigit() } && key.equals("tmdb", ignoreCase = true) -> tmdb = v
                key.equals("imdb", ignoreCase = true) && IMDB_REGEX.matches(v) -> imdb = v
            }
        }
        return imdb to tmdb
    }
}

/**
 * Trakt.tv public API istemcisi (Authorization Code + PKCE akışı).
 * Hiçbir yan etki olmadan çağrılabilir; hata durumunda null/false döner.
 */
object TraktApi {
    private const val API = "https://api.trakt.tv"
    private const val TAG = "TraktSyncApi"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private fun publicHeaders(): Map<String, String> = mapOf(
        "Content-Type" to "application/json",
        "trakt-api-version" to "2",
        "trakt-api-key" to TraktSyncConfig.clientId,
    )

    private fun authHeaders(): Map<String, String> =
        publicHeaders() + ("Authorization" to "Bearer ${TraktSyncConfig.accessToken}")

    private suspend fun postJson(url: String, body: JSONObject, auth: Boolean): JSONObject? = try {
        val resp = app.post(
            url,
            headers = if (auth) authHeaders() else publicHeaders(),
            requestBody = body.toString().toRequestBody(JSON),
        )
        JSONObject(resp.text)
    } catch (e: Exception) {
        Log.w(TAG, "POST $url başarısız: ${e.message}")
        null
    }

    private suspend fun getJson(url: String): JSONObject? = try {
        val resp = app.get(url, headers = authHeaders())
        JSONObject(resp.text)
    } catch (e: Exception) {
        Log.w(TAG, "GET $url başarısız: ${e.message}")
        null
    }

    private suspend fun getArray(url: String): org.json.JSONArray? = try {
        val resp = app.get(url, headers = authHeaders())
        org.json.JSONArray(resp.text)
    } catch (e: Exception) {
        Log.w(TAG, "GET $url başarısız: ${e.message}")
        null
    }

    // ---------------------------------------------------------------- auth

    private suspend fun saveToken(body: String): Boolean = try {
        val j = JSONObject(body)
        val access = j.optString("access_token")
        if (access.isBlank()) {
            false
        } else {
            TraktSyncConfig.accessToken = access
            TraktSyncConfig.refreshToken = j.optString("refresh_token")
            TraktSyncConfig.expiresAt =
                System.currentTimeMillis() / 1000 + j.optInt("expires_in", 7_776_000).toLong()
            fetchProfile()
            true
        }
    } catch (e: Exception) {
        false
    }

    /**
     * Token süresi yaklaşıyorsa refresh token ile yeniler.
     * PKCE herkese açık (public) istemci akışı olduğu için client_secret GEREKMEZ;
     * yalnızca kayıtlı bir secret varsa isteğe eklenir.
     */
    suspend fun ensureFreshToken(): Boolean {
        if (TraktSyncConfig.expiresAt > System.currentTimeMillis() / 1000 + 3600) return true
        val refresh = TraktSyncConfig.refreshToken
        if (refresh.isBlank() || TraktSyncConfig.clientId.isBlank()) return false
        val body = JSONObject()
            .put("refresh_token", refresh)
            .put("client_id", TraktSyncConfig.clientId)
            .put("grant_type", "refresh_token")
        if (TraktSyncConfig.clientSecret.isNotBlank()) {
            body.put("client_secret", TraktSyncConfig.clientSecret)
        }
        val resp = try {
            app.post(
                "$API/oauth/token",
                headers = publicHeaders(),
                requestBody = body.toString().toRequestBody(JSON),
            )
        } catch (e: Exception) {
            Log.w(TAG, "token yenilenemedi: ${e.message}")
            return false
        }
        return saveToken(resp.text)
    }

    private suspend fun fetchProfile() {
        val j = getJson("$API/users/me") ?: return
        val user = j.optJSONObject("user") ?: j
        TraktSyncConfig.userName = user.optString("username").ifBlank {
            user.optString("name").ifBlank { "trakt" }
        }
    }

    // ---------------------------------------------------------------- sync

    private fun idsJson(imdb: String?, tmdb: String?): JSONObject? {
        val ids = JSONObject()
        imdb?.takeIf { it.startsWith("tt") }?.let { ids.put("imdb", it) }
        tmdb?.toIntOrNull()?.let { ids.put("tmdb", it) }
        return ids.takeIf { it.length() > 0 }
    }

    private fun payloadMovie(imdb: String?, tmdb: String?, watchedAt: String?): JSONObject? {
        val ids = idsJson(imdb, tmdb) ?: return null
        val movie = JSONObject().put("ids", ids)
        watchedAt?.let { movie.put("watched_at", it) }
        return JSONObject().put("movies", org.json.JSONArray().put(movie))
    }

    private fun payloadShowEpisode(
        imdb: String?,
        tmdb: String?,
        season: Int,
        episode: Int,
        watchedAt: String?,
    ): JSONObject? {
        val ids = idsJson(imdb, tmdb) ?: return null
        val ep = JSONObject().put("number", episode)
        watchedAt?.let { ep.put("watched_at", it) }
        val seasonJson = JSONObject()
            .put("number", season)
            .put("episodes", org.json.JSONArray().put(ep))
        val show = JSONObject()
            .put("ids", ids)
            .put("seasons", org.json.JSONArray().put(seasonJson))
        return JSONObject().put("shows", org.json.JSONArray().put(show))
    }

    private fun nowIso(): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
        fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return fmt.format(java.util.Date())
    }

    /** İzleme geçmişine ekler (izlendi). */
    suspend fun addHistory(
        imdb: String?,
        tmdb: String?,
        season: Int?,
        episode: Int?,
    ): Boolean {
        val watchedAt = nowIso()
        val payload = if (season != null && episode != null) {
            payloadShowEpisode(imdb, tmdb, season, episode, watchedAt)
        } else {
            payloadMovie(imdb, tmdb, watchedAt)
        } ?: return false
        return postJson("$API/sync/history", payload, auth = true) != null
    }

    /** İzleme listesine ekler. */
    suspend fun addToWatchlist(imdb: String?, tmdb: String?, isShow: Boolean): Boolean {
        val ids = idsJson(imdb, tmdb) ?: return false
        val item = JSONObject().put("ids", ids)
        val payload = if (isShow) {
            JSONObject().put("shows", org.json.JSONArray().put(item))
        } else {
            JSONObject().put("movies", org.json.JSONArray().put(item))
        }
        return postJson("$API/sync/watchlist", payload, auth = true) != null
    }

    /** Başlık (+ yıl) ile Trakt'ta arar ve eşleşenin (imdb, tmdb) kimliklerini döner. Bulamazsa null. */
    suspend fun searchIdsByTitle(title: String, year: Int?, isShow: Boolean): Pair<String?, String?>? {
        val path = if (isShow) "show" else "movie"
        val q = java.net.URLEncoder.encode(title, "UTF-8")
        val url = buildString {
            append("$API/search/$path?query=$q&limit=1")
            if (year != null) append("&years=$year")
        }
        val resp = try {
            app.get(url, headers = authHeaders())
        } catch (e: Exception) {
            return null
        }
        return try {
            val arr = org.json.JSONArray(resp.text)
            if (arr.length() == 0) return null
            val first = arr.optJSONObject(0) ?: return null
            val media = first.optJSONObject(path) ?: return null
            val ids = media.optJSONObject("ids") ?: return null
            val imdb = ids.optString("imdb").ifBlank { null }
            val tmdb = ids.optInt("tmdb", -1).takeIf { it > 0 }?.toString()
            if (imdb == null && tmdb == null) null else imdb to tmdb
        } catch (e: Exception) {
            null
        }
    }

    /** Başlık (+ yıl) ile Trakt'ta arayıp ilk sonucu işaretlemeyi dener. (imdb/tmdb yoksa kullanılır) */
    suspend fun addHistoryBySearch(title: String, year: Int?, isShow: Boolean): Boolean {
        val found = searchIdsByTitle(title, year, isShow) ?: return false
        val imdb = found.first
        val tmdb = found.second
        if (imdb == null && tmdb == null) return false
        if (isShow) {
            // Sezon/bölüm bilgisi yoksa yalnızca izleme listesine ekle, geçmişe dokunma
            return false
        }
        return addHistory(imdb, tmdb, null, null)
    }

    // ------------------------------------------------------------- history

    data class WatchedMovie(
        val imdb: String?,
        val tmdb: String?,
        val plays: Int,
        val lastWatchedAt: String?,
    )

    data class WatchedEpisodeRef(val number: Int, val plays: Int)

    data class WatchedSeason(
        val number: Int,
        val episodes: List<WatchedEpisodeRef>,
    )

    data class WatchedShow(
        val imdb: String?,
        val tmdb: String?,
        val traktId: Int?,
        val title: String?,
        val lastWatchedAt: String?,
        val seasons: List<WatchedSeason>,
    )

    private fun idsFrom(media: JSONObject?): Triple<String?, String?, Int?> {
        val ids = media?.optJSONObject("ids")
        if (ids == null) return Triple(null, null, null)
        val imdb = ids.optString("imdb").ifBlank { null }
        val tmdb = ids.optInt("tmdb", -1).takeIf { it > 0 }?.toString()
        val trakt = ids.optInt("trakt", -1).takeIf { it > 0 }
        return Triple(imdb, tmdb, trakt)
    }

    /** Trakt izleme geçmişindeki filmler (oynatma sayısı ile). */
    suspend fun watchedMovies(): List<WatchedMovie> {
        val arr = getArray("$API/sync/watched/movies") ?: return emptyList()
        val out = mutableListOf<WatchedMovie>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val (imdb, tmdb) = idsFrom(item.optJSONObject("movie")).let { it.first to it.second }
            out += WatchedMovie(
                imdb = imdb,
                tmdb = tmdb,
                plays = item.optInt("plays", 0),
                lastWatchedAt = item.optString("last_watched_at").ifBlank { null },
            )
        }
        return out
    }

    /** Trakt izleme geçmişindeki diziler; sezon/bölüm bazında oynatma sayılarıyla. */
    suspend fun watchedShows(): List<WatchedShow> {
        val arr = getArray("$API/sync/watched/shows") ?: return emptyList()
        val out = mutableListOf<WatchedShow>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val show = item.optJSONObject("show") ?: continue
            val (imdb, tmdb, traktId) = idsFrom(show)
            val seasons = mutableListOf<WatchedSeason>()
            val sArr = item.optJSONArray("seasons")
            if (sArr != null) {
                for (s in 0 until sArr.length()) {
                    val sObj = sArr.optJSONObject(s) ?: continue
                    val episodes = mutableListOf<WatchedEpisodeRef>()
                    val eArr = sObj.optJSONArray("episodes")
                    if (eArr != null) {
                        for (e in 0 until eArr.length()) {
                            val eObj = eArr.optJSONObject(e) ?: continue
                            val num = eObj.optInt("number", -1)
                            if (num >= 0) {
                                episodes += WatchedEpisodeRef(num, eObj.optInt("plays", 0))
                            }
                        }
                    }
                    seasons += WatchedSeason(sObj.optInt("number", 0), episodes)
                }
            }
            out += WatchedShow(
                imdb = imdb,
                tmdb = tmdb,
                traktId = traktId,
                title = show.optString("title").ifBlank { null },
                lastWatchedAt = item.optString("last_watched_at").ifBlank { null },
                seasons = seasons,
            )
        }
        return out
    }

    /**
     * Dizinin Trakt'taki izleme ilerlemesini döner: (yayına giren bölüm, izlenen bölüm).
     * id: imdb (tt...) veya tmdb kimliği. Bilinmiyorsa null.
     */
    suspend fun showWatchedProgress(id: String): Pair<Int, Int>? {
        val j = getJson("$API/shows/$id/progress/watched") ?: return null
        val aired = j.optInt("aired", -1)
        val completed = j.optInt("completed", -1)
        if (aired < 0 || completed < 0) return null
        return aired to completed
    }
}
