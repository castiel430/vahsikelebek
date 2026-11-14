package com.vahsikelebek.traktsync

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
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
 * Eklenti ayarları: Client ID/Secret girişi, cihaz-kodu (device code) ile
 * Trakt.tv bağlantısı, manuel eşitleme ve bağlantıyı kesme.
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
                "Önce trakt.tv/settings/api adresinden bir API uygulaması oluşturun " +
                "(İsim + Redirect URI olarak \"urn:ietf:wg:oauth:2.0:oob\" yazabilirsiniz). " +
                "Sonra Client ID ve Secret'ı aşağıya girin ve Bağlan düğmesine basın."
            textSize = 12f
            setTextColor(Color.DKGRAY)
            setPadding(0, 8, 0, 16)
        })

        layout.addView(TextView(activity).apply { text = "Client ID"; textSize = 12f })
        val idInput = EditText(activity).apply {
            hint = "trakt.tv uygulama client_id"
            setText(TraktSyncConfig.clientId)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        layout.addView(idInput)

        layout.addView(TextView(activity).apply { text = "Client Secret"; textSize = 12f; setPadding(0, 8, 0, 0) })
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
            statusText.setTextColor(if (TraktSyncConfig.connected) Color.parseColor("#2E7D32") else Color.parseColor("#B71C1C"))
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
            TraktSyncConfig.clientId = idInput.text?.toString().orEmpty()
            TraktSyncConfig.clientSecret = secretInput.text?.toString().orEmpty()
        }

        val dialog = AlertDialog.Builder(activity)
            .setView(ScrollView(activity).apply { addView(layout) })
            .setPositiveButton("Kapat") { _, _ -> saveFields() }
            .create()

        fun connect() {
            saveFields()
            if (TraktSyncConfig.clientId.isBlank() || TraktSyncConfig.clientSecret.isBlank()) {
                Toast.makeText(activity, "Client ID ve Secret gerekli", Toast.LENGTH_LONG).show()
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
                    // Tarayıcıyı otomatik aç
                    runCatching {
                        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(code.verificationUrl)))
                    }
                    val msg = "1) Tarayıcıda ${code.verificationUrl} adresine gidin\n" +
                        "2) Giriş yapıp şu kodu girin: ${code.userCode}\n\n" +
                        "Kod ${code.expiresIn / 60} dakika geçerli. Onayladığınızda otomatik bağlanacağız..."
                    val waitDialog = AlertDialog.Builder(activity)
                        .setTitle("Trakt'a Bağlan")
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
                            // İlk eşitlemeyi hemen başlat
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
            setOnClickListener { connect() }
        })
        btnRow.addView(Button(activity).apply {
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
        layout.addView(btnRow)

        val btnRow2 = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow2.addView(Button(activity).apply {
            text = "Bağlantıyı Kes"
            setTextColor(Color.parseColor("#B71C1C"))
            setOnClickListener {
                TraktSyncConfig.clearSession()
                refreshStatus()
                Toast.makeText(activity, "Trakt oturumu kapatıldı", Toast.LENGTH_SHORT).show()
            }
        })
        layout.addView(btnRow2)

        layout.addView(TextView(activity).apply {
            text = "Nasıl çalışır?\n" +
                "• Kütüphanede COMPLETED (izlendi) olarak işaretlediğiniz içerikler Trakt geçmişine eklenir.\n" +
                "• WATCHING / ONHOLD / PLANTOWATCH içerikler Trakt izleme listesine eklenir.\n" +
                "• Video oynatma seçeneklerinde \"✓ Trakt'a İzledim Olarak İşaretle\" düğmesi ile tek dokunuşla işaretleyebilirsiniz."
            textSize = 11f
            setTextColor(Color.DKGRAY)
            setPadding(0, 16, 0, 8)
        })

        dialog.show()
    }
}
