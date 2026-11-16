package com.vahsikelebek.traktsync

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Eklenti ayarları — tek düğme: "Trakt'a Bağlan".
 *
 * Kullanıcıdan hiçbir Client ID / Secret alınmaz; eklentiye gömülü, PKCE
 * (client_secret gerektirmeyen) bir Trakt API uygulaması kullanılır.
 * Giriş, sadece e-posta + Trakt'tan gelen kodla (WebView üzerinden) yapılır.
 */
object TraktSettingsDialog {

    fun show(context: Context) {
        val activity = context as? Activity ?: return
        val padding = 48

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, 32, padding, 8)
        }

        layout.addView(TextView(activity).apply {
            text = "Trakt Senkron"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
        })

        layout.addView(TextView(activity).apply {
            text = "İzleme listenizi ve izlendi işaretlerini Trakt.tv ile eşitler.\n\n" +
                "Kurulum gerekmez — eklentiye gömülü bir Trakt uygulaması kullanılır.\n" +
                "Sadece \"Trakt'a Bağlan\"'a basın, e-postanıza gelen kodu girin, bitti."
            textSize = 12f
            setTextColor(Color.DKGRAY)
            setPadding(0, 8, 0, 16)
        })

        val statusText = TextView(activity).apply {
            textSize = 13f
            setPadding(0, 8, 0, 4)
            setTypeface(typeface, Typeface.BOLD)
        }
        fun refreshStatus() {
            statusText.text = if (TraktSyncConfig.connected) {
                "Durum: Bağlı ✓  (${TraktSyncConfig.userName.ifBlank { "trakt kullanıcısı" }})"
            } else {
                "Durum: Bağlı değil"
            }
            statusText.setTextColor(
                if (TraktSyncConfig.connected) Color.parseColor("#2E7D32") else Color.parseColor("#B71C1C")
            )
        }
        refreshStatus()
        layout.addView(statusText)

        val autoCheck = CheckBox(activity).apply {
            text = "Otomatik eşitle (60 saniyede bir denetler)"
            isChecked = TraktSyncConfig.autoSync
            setPadding(0, 4, 0, 4)
            setOnCheckedChangeListener { _, checked -> TraktSyncConfig.autoSync = checked }
        }
        layout.addView(autoCheck)

        fun connect() {
            TraktLoginDialog.show(activity) { success ->
                refreshStatus()
                if (success) {
                    scope.launch(Dispatchers.IO) {
                        kotlinx.coroutines.delay(500)
                        runCatching { TraktSyncManager.syncOnce() }
                    }
                }
            }
        }

        val btnRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        btnRow.addView(Button(activity).apply {
            text = "Trakt'a Bağlan"
            setOnClickListener { connect() }
        })
        layout.addView(btnRow)

        val btnRow2 = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow2.addView(Button(activity).apply {
            text = "Şimdi Eşitle"
            setOnClickListener {
                if (!TraktSyncConfig.connected) {
                    Toast.makeText(activity, "Önce Trakt'a bağlanın", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                scope.launch(Dispatchers.IO) {
                    runCatching { TraktSyncManager.syncOnce() }
                    launch(Dispatchers.Main) {
                        Toast.makeText(activity, "Trakt eşitlemesi tamamlandı", Toast.LENGTH_SHORT).show()
                        refreshStatus()
                    }
                }
            }
        })
        layout.addView(btnRow2)

        val btnRow3 = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow3.addView(Button(activity).apply {
            text = "Bağlantıyı Kes"
            setTextColor(Color.parseColor("#B71C1C"))
            setOnClickListener {
                TraktSyncConfig.clearSession()
                refreshStatus()
                Toast.makeText(activity, "Trakt oturumu kapatıldı", Toast.LENGTH_SHORT).show()
            }
        })
        layout.addView(btnRow3)

        layout.addView(TextView(activity).apply {
            text = "Nasıl çalışır?\n" +
                "• Kütüphanede COMPLETED (izlendi) olarak işaretlediğiniz içerikler Trakt geçmişine eklenir.\n" +
                "• WATCHING / ONHOLD / PLANTOWATCH içerikler Trakt izleme listesine eklenir.\n" +
                "• Oynat menüsündeki \"✓ Trakt'a İzledim Olarak İşaretle\" düğmesiyle tek dokunuşla işaretleyebilirsiniz.\n\n" +
                "Not: Gömülü uygulamanın (Cloudstream Vahsikelebek) paylaşan Trakt hesabı sahibi\n" +
                "ve geliştirme süresince eklediği 16 kullanıcı bağlanabilir. Herkes için açık\n" +
                "sürüm için eklentinin gömülü uygulamasına Production erişimi gerekir."
            textSize = 11f
            setTextColor(Color.DKGRAY)
            setPadding(0, 16, 0, 8)
        })

        AlertDialog.Builder(activity)
            .setView(ScrollView(activity).apply { addView(layout) })
            .setPositiveButton("Kapat", null)
            .create()
            .show()
    }
}
