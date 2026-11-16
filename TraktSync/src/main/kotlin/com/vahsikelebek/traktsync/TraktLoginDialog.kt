package com.vahsikelebek.traktsync

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * PKCE + WebView giriş penceresi (uygulama içinde açılır).
 * Kullanıcı e-postasını ve Trakt'ın e-postayla gönderdiği kodu girer;
 * Trakt yönlendirmeyi https://app.trakt.tv/callback?code=... adresine yapar,
 * biz de navigasyonu yakalayıp kodla token değiş tokuşu yaparız.
 */
object TraktLoginDialog {
    private const val TAG = "TraktLogin"

    fun show(activity: Activity, onDone: (Boolean) -> Unit) {
        val clientId = TraktSyncConfig.clientId
        if (clientId.isBlank()) {
            Toast.makeText(activity, "Önce Client ID girin", Toast.LENGTH_LONG).show()
            return
        }

        val verifier = TraktPkce.generateCodeVerifier()
        val challenge = TraktPkce.codeChallenge(verifier)
        val state = TraktPkce.generateState()
        val url = TraktPkce.authorizeUrl(clientId, state, challenge)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        val webView = try {
            WebView(activity).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.defaultTextEncodingName = "UTF-8"
                settings.userAgentString =
                    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
            }
        } catch (e: Throwable) {
            Toast.makeText(activity, "Uygulama içi pencere açılamadı: ${e.message}", Toast.LENGTH_LONG).show()
            onDone(false)
            return
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle("Trakt'a Bağlan")
            .setView(webView)
            .setNegativeButton("İptal") { _, _ -> onDone(false) }
            .setOnCancelListener { onDone(false) }
            .setNeutralButton("Tarayıcıda Aç") { _, _ -> openExternal(activity, url) }
            .create()

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                handle(request?.url?.toString().orEmpty())

            /** Android < 21 fallback */
            @Suppress("DEPRECATION")
            @android.annotation.SuppressLint("WebViewClientOnPageStarted")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                handle(url.orEmpty())

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                super.onReceivedError(view, request, error)
                // Ana sayfa yüklenemezse kullanıcıyı bilgilendir (uygulama içi pencere boş kalmasın)
                if (request?.isForMainFrame != false) {
                    scope.launch {
                        Toast.makeText(
                            activity,
                            "Sayfa yüklenemedi (${error?.errorCode}): ${error?.description}. İnterneti denetleyin ya da \"Tarayıcıda Aç\"'a dokunun.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }

            private fun handle(target: String): Boolean {
                if (!target.startsWith(TraktPkce.REDIRECT_URI)) return false
                val uri = Uri.parse(target)
                val returnedState = uri.getQueryParameter("state")
                val error = uri.getQueryParameter("error")
                if (returnedState != state) {
                    scope.launch {
                        runCatching { dialog.dismiss() }
                        Toast.makeText(activity, "Giriş reddedildi (state uyuşmazlığı)", Toast.LENGTH_LONG).show()
                    }
                    onDone(false)
                    return true
                }
                if (error != null) {
                    scope.launch {
                        runCatching { dialog.dismiss() }
                        Toast.makeText(activity, "Trakt girişi ret: $error", Toast.LENGTH_LONG).show()
                    }
                    onDone(false)
                    return true
                }
                val code = uri.getQueryParameter("code")
                if (code.isNullOrBlank()) {
                    scope.launch {
                        runCatching { dialog.dismiss() }
                        Toast.makeText(activity, "Kod alınamadı, tekrar deneyin", Toast.LENGTH_LONG).show()
                    }
                    onDone(false)
                    return true
                }
                scope.launch(Dispatchers.IO) {
                    val ok = TraktPkce.exchangeCode(code, verifier, clientId)
                    if (ok) {
                        try {
                            TraktPkce.fetchProfile()?.let { TraktSyncConfig.userName = it }
                        } catch (_: Exception) {
                            // profil adı alınamadı — bağlantı yine de geçerli
                        }
                    }
                    launch(Dispatchers.Main) {
                        runCatching { dialog.dismiss() }
                        Toast.makeText(
                            activity,
                            if (ok) "Trakt'a bağlandı ✓" else "Token takası başarısız oldu, tekrar deneyin",
                            Toast.LENGTH_SHORT,
                        ).show()
                        onDone(ok)
                    }
                }
                return true
            }
        }

        dialog.setOnShowListener {
            dialog.window?.setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        dialog.show()
        webView.loadUrl(url)
    }

    /** Yedek: sistem tarayıcısında aç (callback uygulamayı yakalayamaz!). */
    private fun openExternal(activity: Activity, url: String) {
        runCatching {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)),
            )
            Toast.makeText(
                activity,
                "Not: Tarayıcıdan giriş yaparsanız uygulama otomatik bağlanamaz. \"Uygulama Aç\" akışına geri dönün.",
                Toast.LENGTH_LONG,
            ).show()
        }.onFailure {
            Toast.makeText(activity, "Tarayıcı açılamadı: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }
}
