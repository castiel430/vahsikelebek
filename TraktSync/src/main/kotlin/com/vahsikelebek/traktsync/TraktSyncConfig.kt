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

    /**
     * Eklentiye gömülü Trakt API uygulaması (PKCE, client_secret yok).
     * Geliştirme (Development) uygulamasıdır; yalnızca hesap sahibi ve 16 davetli
     * kullanıcı giriş yapabilir. Kendi uygulamanızı kullanmak isterseniz
     * Client ID alanına kendi değerinizi yazabilirsiniz.
     */
    const val EMBEDDED_CLIENT_ID = "1TF_r9MwpFwHHTjzAAQ0n7hWIqwNBXZ2MU9-nuKLleQ"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    private fun p(): SharedPreferences = prefs
        ?: throw IllegalStateException("Trakt Senkron henüz başlatılmadı")

    var clientId: String
        get() = p().getString("client_id", EMBEDDED_CLIENT_ID).orEmpty()
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

    /** Son başarılı eşitlemenin zamanı (unix ms). 0 = hiç eşitlenmedi. */
    var lastSyncAt: Long
        get() = p().getLong("last_sync_at", 0L)
        set(v) = p().edit().putLong("last_sync_at", v).apply()

    /** Son eşitlemenin kısa özeti ("Gönderilen: 3 · Alınan: 2 işaret" gibi). */
    var lastSyncSummary: String
        get() = p().getString("last_sync_summary", "").orEmpty()
        set(v) = p().edit().putString("last_sync_summary", v).apply()

    /** Trakt kullanıcısı bağlı mı? */
    val connected: Boolean get() = accessToken.isNotBlank()

    /** Daha önce Trakt'tan çekilip uygulanan (ep:<seriId>:<sezon>:<bölüm>) kayıtlar. */
    fun pulledApplied(): Set<String> = p().getStringSet("pulled_applied", emptySet()) ?: emptySet()

    fun addPulledApplied(key: String) {
        val s = HashSet(p().getStringSet("pulled_applied", emptySet()) ?: emptySet())
        s.add(key)
        // Aşırı büyümeyi engelle
        if (s.size > 5000) s.clear()
        p().edit().putStringSet("pulled_applied", s).apply()
    }

    /** Çekme tarafındaki uygulanmış işaret kayıtlarını sıfırlar (yeniden uygulama için). */
    fun resetPulledApplied() {
        p().edit().remove("pulled_applied").apply()
    }

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
            .remove("last_sync_at")
            .remove("last_sync_summary")
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
            .remove("pulled_applied")
            .apply()
    }
}
