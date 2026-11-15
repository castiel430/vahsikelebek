package com.vahsikelebek.traktsync

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.widget.CheckBox
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Eklenti ayarları:
 *  - "Trakt'a Bağlan" (PKCE + WebView): kullanıcı sadece e-posta + Trakt'tan gelen kodu girer.
 *  - "Cihaz kodu" (yedek): Trakt'ın cihaz akışı (client secret gerektirir).
 */
object TraktSettingsDialog {

    fun show(context: Context) {
        val activity = context as? Activity ?: return
        val padding = 48

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
                "Eklentiye gömülü bir Trakt uygulaması vardır; kullanmak için ek bir " +
                "Client ID/Secret girmenize gerek yoktur.\n" +
                "Sadece \"Trakt'a Bağlan\"'a basın → e-postanıza gelen kodu girin → bitti.\n\n" +
                "Not: Gömülü uygulama \u201cDevelopment\u201d türündedir; yalnızca sizin hesabınız ve\n" +
                "My Apps sayfasından ekleyeceğiniz 16 kişiye kadar geçerli çalışır.\n" +
                "Herkes için erişilebilir yapmak isterseniz uygulamayı trakt.tv/developer →\n" +
                "My Apps → Production erişimi istersiniz."
            textSize = 12f
            setTextColor(Color.DKGRAY)
            setPadding(0, 8, 0, 16)
        })

        layout.addView(TextView(activity).apply { text = "Client ID (isteğe bağlı)"; textSize = 12f })
        val idInput = EditText(activity).apply {
            hint = "Gömülü uygulama kullanılır (değiştirmek isterseniz yazın)"
            setText(if (TraktSyncConfig.clientId == TraktSyncConfig.EMBEDDED_CLIENT_ID) "" else TraktSyncConfig.clientId)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        layout.addView(idInput)

        layout.addView(TextView(activity).apply {
            text = "Client Secret (isteğe bağlı — yalnız cihaz akışı için)"; textSize = 12f; setPadding(0, 8, 0, 0)
        })
        val secretInput = EditText(activity).apply {
            hint = "trakt.tv uygulama client_secret"
            setText(TraktSyncConfig.clientSecret)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        layout.addView(secretInput)

        val statusText = TextView(activity).apply {
            textSize = 13f
            setPadding(0, 16, 0, 4)
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

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        fun saveFields() {
            val cid = idInput.text?.toString()?.trim().orEmpty()
            // Boş bırakılırsa gömülü uygulamayı kullan
            TraktSyncConfig.clientId = if (cid.isBlank()) TraktSyncConfig.EMBEDDED_CLIENT_ID else cid
            TraktSyncConfig.clientSecret = secretInput.text?.toString().orEmpty()
        }

        val dialog = AlertDialog.Builder(activity)
            .setView(ScrollView(activity).apply { addView(layout) })
            .setPositiveButton("Kapat") { _, _ -> saveFields() }
            .create()

        fun connectPkce() {
            saveFields()
            if (TraktSyncConfig.clientId.isBlank()) {
                Toast.makeText(activity, "Client ID gerekli", Toast.LENGTH_LONG).show()
                return
            }
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

        fun connectDeviceCode() {
            saveFields()
            if (TraktSyncConfig.clientId.isBlank() || TraktSyncConfig.clientSecret.isBlank()) {
                Toast.makeText(activity, "Cihaz akışı için Client ID ve Secret gerekli", Toast.LENGTH_LONG).show()
                return
            }
            scope.launch(Dispatchers.IO) {
                val code = TraktApi.requestDeviceCode(TraktSyncConfig.clientId)
                if (code == null) {
                    launch(Dispatchers.Main) {
                        Toast.makeText(activity, "Trakt'a bağlanılamadı. Client ID/Secret doğru mu?", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                launch(Dispatchers.Main) {
                    val msg = "1) Tarayıcıda ${code.verificationUrl} adresine gidin\n" +
                        "2) Giriş yapıp şu kodu girin: ${code.userCode}\n\n" +
                        "Kod ${code.expiresIn / 60} dakika geçerli. Onaylandığında otomatik bağlanacağız..."
                    val waitDialog = AlertDialog.Builder(activity)
                        .setTitle("Trakt Cihaz Kodu")
                        .setMessage(msg)
                        .setNegativeButton("İptal", null)
                        .setCancelable(false)
                        .create()
                    waitDialog.show()
                    scope.launch {
                        val deadline = System.currentTimeMillis() + code.expiresIn * 1000L
                        var done = false
                        while (!done && System.currentTimeMillis() < deadline) {
                            kotlinx.coroutines.delay(code.interval * 1000L)
                            done = TraktApi.pollDeviceToken(code.deviceCode)
                        }
                        runCatching { waitDialog.dismiss() }
                        if (done) {
                            refreshStatus()
                            Toast.makeText(activity, "Trakt'a bağlandı ✓", Toast.LENGTH_SHORT).show()
                            scope.launch(Dispatchers.IO) { runCatching { TraktSyncManager.syncOnce() } }
                        } else {
                            Toast.makeText(activity, "Süre doldu veya kod onaylanmadı, tekrar deneyin", Toast.LENGTH_LONG).show()
                        }
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
            setOnClickListener { connectPkce() }
        })
        btnRow.addView(Button(activity).apply {
            text = "Cihaz Kodu (yedek)"
            setOnClickListener { connectDeviceCode() }
        })
        layout.addView(btnRow)

        val btnRow2 = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow2.addView(Button(activity).apply {
            text = "Şimdi Eşitle"
            setOnClickListener {
                saveFields()
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
                "Trakt API uygulaması gömülüdür — ekstra kurulum gerekmez.\n" +
                "Sadece \"Trakt'a Bağlan\"'a basın, e-postanıza gelen kodu girin.\n\n" +
                "Gelişmiş: kendi Trakt uygulamanızı kullanmak isterseniz Client ID alanına\n" +
                "onun Client ID'sini yazabilirsiniz (boş bırakınca gömülü uygulama kullanılır).\n" +
                "Dikkat: gömülü uygulama Development türündedir; yalnızca sizin hesabınız\n" +
                "ve My Apps'ten davet ettiğiniz 16 kişi bağlanabilir."
            textSize = 11f
            setTextColor(Color.DKGRAY)
            setPadding(0, 16, 0, 8)
        })

        dialog.show()
    }
}
