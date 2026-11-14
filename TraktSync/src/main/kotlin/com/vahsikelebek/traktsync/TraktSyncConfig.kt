package com.vahsikelebek.traktsync

import android.content.Context
import android.content.SharedPreferences

/**
 * Trakt Senkron eklentisinin yapılandırma deposu.
 * Kendi özel SharedPreferences dosyasında saklanır, CloudStream ayarlarıyla karışmaz.
 */
object TraktSyncConfig {
    private const val FILE = "vahsi_trakt_sync"
    const val DEFAULT_AUTO_SYNC = true

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    private fun p(): SharedPreferences = prefs
        ?: throw IllegalStateException("Trakt Senkron henüz başlatılmadı")

    var clientId: String
        get() = p().getString("client_id", "").orEmpty()
        set(v) = p().edit().putString("client_id", v.trim()).apply()

    var clientSecret: String
        get() = p().getString("client_secret", "").orEmpty()
        set(v) = p().edit().putString("client_secret", v.trim()).apply()

    var accessToken: String
        get() = p().getString("access_token", "").orEmpty()
        set(v) = p().edit().putString("access_token", v).apply()

    var refreshToken: String
        get() = p().getString("refresh_token", "").orEmpty()
        set(v) = p().edit().putString("refresh_token", v).apply()

    /** Unix epoch (saniye). */
    var expiresAt: Long
        get() = p().getLong("expires_at", 0L)
        set(v) = p().edit().putLong("expires_at", v).apply()

    var userName: String
        get() = p().getString("user_name", "").orEmpty()
        set(v) = p().edit().putString("user_name", v).apply()

    var autoSync: Boolean
        get() = p().getBoolean("auto_sync", DEFAULT_AUTO_SYNC)
        set(v) = p().edit().putBoolean("auto_sync", v).apply()

    /** Trakt kullanıcısı bağlı mı? */
    val connected: Boolean get() = accessToken.isNotBlank()

    /** Daha önce eşitlenen (id:latestUpdatedTime) kayıtlar. */
    fun syncedIds(): Set<String> = p().getStringSet("synced_ids", emptySet()) ?: emptySet()

    fun addSyncedId(id: String) {
        val s = HashSet(p().getStringSet("synced_ids", emptySet()) ?: emptySet())
        s.add(id)
        // Aşırı büyümeyi engelle
        if (s.size > 5000) s.clear()
        p().edit().putStringSet("synced_ids", s).apply()
    }

    /** Oturumu ve kimlik bilgilerini temizler (Client ID/Secret korunur). */
    fun clearSession() {
        p().edit()
            .remove("access_token")
            .remove("refresh_token")
            .remove("expires_at")
            .remove("user_name")
            .apply()
    }

    /** Client ID/Secret dahil her şeyi siler. */
    fun clearAll() {
        clearSession()
        p().edit()
            .remove("client_id")
            .remove("client_secret")
            .remove("auto_sync")
            .remove("synced_ids")
            .apply()
    }
}
