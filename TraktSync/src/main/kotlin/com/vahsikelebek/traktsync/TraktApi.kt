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
 * Trakt.tv public API istemcisi (OAuth 2.0 Device Code flow).
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

    // ---------------------------------------------------------------- auth

    data class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUrl: String,
        val expiresIn: Int,
        val interval: Int,
    )

    /** Adım 1: trakt.tv/activate'de girilecek kodu alır. */
    suspend fun requestDeviceCode(clientId: String): DeviceCode? {
        val resp = try {
            app.post(
                "$API/oauth/device/code",
                headers = mapOf(
                    "Content-Type" to "application/json",
                    "trakt-api-version" to "2",
                    "trakt-api-key" to clientId,
                ),
                requestBody = JSONObject()
                    .put("client_id", clientId)
                    .toString()
                    .toRequestBody(JSON),
            )
        } catch (e: Exception) {
            Log.w(TAG, "device/code isteği başarısız: ${e.message}")
            return null
        }
        return try {
            val j = JSONObject(resp.text)
            DeviceCode(
                deviceCode = j.getString("device_code"),
                userCode = j.getString("user_code"),
                verificationUrl = j.optString("verification_url", "https://trakt.tv/activate"),
                expiresIn = j.optInt("expires_in", 600),
                interval = j.optInt("interval", 5),
            )
        } catch (e: Exception) {
            Log.w(TAG, "device/code yanıtı çözülemedi: ${e.message}")
            null
        }
    }

    /**
     * Adım 2: kullanıcı trakt.tv/activate'ta kodu girdikten sonra çağırın.
     * Henüz onaylanmamışsa false, onaylandıysa token'ı kaydedip true döner.
     */
    suspend fun pollDeviceToken(deviceCode: String): Boolean {
        val resp = try {
            app.post(
                "$API/oauth/device/token",
                headers = publicHeaders(),
                requestBody = JSONObject()
                    .put("device_code", deviceCode)
                    .put("client_id", TraktSyncConfig.clientId)
                    .put("client_secret", TraktSyncConfig.clientSecret)
                    .toString()
                    .toRequestBody(JSON),
            )
        } catch (e: Exception) {
            // 400 authorization_pending / 429 slow_down: henüz onaylanmadı
            return false
        }
        return saveToken(resp.text)
    }

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

    /** Token süresi yaklaşıyorsa refresh token ile yeniler. */
    suspend fun ensureFreshToken(): Boolean {
        if (TraktSyncConfig.expiresAt > System.currentTimeMillis() / 1000 + 3600) return true
        val refresh = TraktSyncConfig.refreshToken
        if (refresh.isBlank() || TraktSyncConfig.clientSecret.isBlank()) return false
        val resp = try {
            app.post(
                "$API/oauth/token",
                headers = publicHeaders(),
                requestBody = JSONObject()
                    .put("refresh_token", refresh)
                    .put("client_id", TraktSyncConfig.clientId)
                    .put("client_secret", TraktSyncConfig.clientSecret)
                    .put("grant_type", "refresh_token")
                    .toString()
                    .toRequestBody(JSON),
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

    /** Başlık (+ yıl) ile Trakt'ta arayıp ilk sonucu işaretlemeyi dener. (imdb/tmdb yoksa kullanılır) */
    suspend fun addHistoryBySearch(title: String, year: Int?, isShow: Boolean): Boolean {
        val path = if (isShow) "show" else "movie"
        val q = java.net.URLEncoder.encode(title, "UTF-8")
        val url = buildString {
            append("$API/search/$path?query=$q&limit=1")
            if (year != null) append("&years=$year")
        }
        val resp = try {
            app.get(url, headers = authHeaders())
        } catch (e: Exception) {
            return false
        }
        return try {
            val arr = org.json.JSONArray(resp.text)
            if (arr.length() == 0) return false
            val first = arr.optJSONObject(0) ?: return false
            val media = first.optJSONObject(path) ?: return false
            val ids = media.optJSONObject("ids") ?: return false
            val imdb = ids.optString("imdb").ifBlank { null }
            val tmdb = ids.optInt("tmdb", -1).takeIf { it > 0 }?.toString()
            if (imdb == null && tmdb == null) return false
            if (isShow) {
                // Sezon/bölüm bilgisi yoksa yalnızca izleme listesine ekle, geçmişe dokunma
                false
            } else {
                addHistory(imdb, tmdb, null, null)
            }
        } catch (e: Exception) {
            false
        }
    }
}
