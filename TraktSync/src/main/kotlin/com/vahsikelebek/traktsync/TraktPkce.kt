package com.vahsikelebek.traktsync

import android.util.Log
import com.lagradost.cloudstream3.app
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Trakt OAuth 2.0 Authorization Code + PKCE akışı.
 * client_secret GEREKTİRMEZ — sadece client_id ile çalışır.
 * Redirect URI: uygulama kaydında `https://app.trakt.tv/callback` olmalı.
 */
object TraktPkce {
    private const val TAG = "TraktPkce"
    private const val API = "https://api.trakt.tv"
    private const val AUTHORIZE = "https://auth.trakt.tv/oauth/authorize"
    private const val TOKEN = "/oauth/token"

    const val REDIRECT_URI = "https://app.trakt.tv/callback"
    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val secureRandom = SecureRandom()

    fun generateState(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun generateCodeVerifier(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        return (1..64).map { chars[secureRandom.nextInt(chars.length)] }.joinToString("")
    }

    fun codeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.UTF_8))
        return android.util.Base64.encodeToString(
            digest,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP,
        )
    }

    fun authorizeUrl(clientId: String, state: String, codeChallenge: String): String =
        "$AUTHORIZE?response_type=code" +
            "&client_id=${java.net.URLEncoder.encode(clientId, "UTF-8")}" +
            "&redirect_uri=${java.net.URLEncoder.encode(REDIRECT_URI, "UTF-8")}" +
            "&scope=public" +
            "&state=${java.net.URLEncoder.encode(state, "UTF-8")}" +
            "&code_challenge=${java.net.URLEncoder.encode(codeChallenge, "UTF-8")}" +
            "&code_challenge_method=S256"

    /** PKCE token takası — client_secret yok. */
    suspend fun exchangeCode(code: String, codeVerifier: String, clientId: String): Boolean = try {
        val body = JSONObject()
            .put("code", code)
            .put("client_id", clientId)
            .put("redirect_uri", REDIRECT_URI)
            .put("grant_type", "authorization_code")
            .put("code_verifier", codeVerifier)
        val resp = app.post(
            "$API$TOKEN",
            headers = mapOf(
                "Content-Type" to "application/json",
                "trakt-api-version" to "2",
                "trakt-api-key" to clientId,
            ),
            requestBody = body.toString().toRequestBody(JSON),
        )
        val j = JSONObject(resp.text)
        val access = j.optString("access_token")
        if (access.isBlank()) {
            Log.w(TAG, "token boş: ${resp.text}")
            false
        } else {
            TraktSyncConfig.accessToken = access
            TraktSyncConfig.refreshToken = j.optString("refresh_token")
            TraktSyncConfig.expiresAt =
                System.currentTimeMillis() / 1000 + j.optInt("expires_in", 7_776_000).toLong()
            true
        }
    } catch (e: Exception) {
        Log.w(TAG, "token takası başarısız: ${e.message}")
        false
    }

    /** Kullanıcı profilini çeker (ad). */
    suspend fun fetchProfile(): String? = try {
        val resp = app.get(
            "$API/users/me",
            headers = mapOf(
                "Content-Type" to "application/json",
                "trakt-api-version" to "2",
                "trakt-api-key" to TraktSyncConfig.clientId,
                "Authorization" to "Bearer ${TraktSyncConfig.accessToken}",
            ),
        )
        val j = JSONObject(resp.text)
        val user = j.optJSONObject("user") ?: j
        user.optString("username").ifBlank { user.optString("name").ifBlank { null } }
    } catch (e: Exception) {
        null
    }
}
