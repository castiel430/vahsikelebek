package com.vahsikelebek.traktsync

import android.app.Activity
import android.app.AlertDialog
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * PKCE + WebView giriş penceresi.
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

        val webView = WebView(activity).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.defaultTextEncodingName = "UTF-8"
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle("Trakt'a Bağlan")
            .setView(webView)
            .setNegativeButton("İptal") { _, _ -> onDone(false) }
            .setOnCancelListener { onDone(false) }
            .create()

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                handle(request?.url?.toString().orEmpty())

            /** Android < 21 fallback */
            @Suppress("DEPRECATION")
            @android.annotation.SuppressLint("WebViewClientOnPageStarted")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                handle(url.orEmpty())

            private fun handle(target: String): Boolean {
                if (!target.startsWith(TraktPkce.REDIRECT_URI)) return false
                val uri = android.net.Uri.parse(target)
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
                        runCatching { TraktPkce.fetchProfile()?.let { TraktSyncConfig.userName = it } }
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
}
