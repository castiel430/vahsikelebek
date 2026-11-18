package com.vahsikelebek.traktsync

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Trakt Senkron ayar penceresi.
 *
 * İki durum:
 *  - Bağlı değil: tek ve net çağrı — "Trakt'a Bağlan" (kırmızı, tam genişlik).
 *  - Bağlı: birincil eylem "Şimdi Eşitle"; altında eşit genişlikte, aynı stilde
 *    "Hesap Değiştir" ve "Bağlantıyı Kes".
 *
 * Kullanıcıdan Client ID / Secret alınmaz; eklentiye gömülü, PKCE
 * (client_secret gerektirmeyen) Trakt API uygulaması kullanılır.
 */
object TraktSettingsDialog {

    private const val TRAKT_RED = 0xFFED1C24.toInt()
    private const val OK_GREEN = 0xFF2E7D32.toInt()
    private const val ERR_RED = 0xFFB71C1C.toInt()
    private const val CARD_BG = 0x1A808080.toInt() // iki temada da okunan nötr kutu

    fun show(context: Context) {
        val activity = context as? Activity ?: return
        val density = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        // Temaya uyumlu metin renkleri (koyu/açık)
        val ta = activity.obtainStyledAttributes(
            intArrayOf(android.R.attr.textColorPrimary, android.R.attr.textColorSecondary),
        )
        val primaryText = ta.getColor(0, Color.BLACK)
        val secondaryText = ta.getColor(1, Color.DKGRAY)
        ta.recycle()

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(4))
        }

        // ---------- başlık ----------
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        header.addView(TextView(activity).apply {
            text = "T"
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(TRAKT_RED)
            }
        }, LinearLayout.LayoutParams(dp(52), dp(52)).apply { setMargins(0, 0, dp(12), 0) })
        header.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(activity).apply {
                text = "Trakt Senkron"
                textSize = 19f
                setTextColor(primaryText)
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(TextView(activity).apply {
                text = "Kâtüphanen ⇄ Trakt.tv izleme listen"
                textSize = 12f
                setTextColor(secondaryText)
            })
        })
        layout.addView(header)

        // ---------- durum kartı ----------
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(CARD_BG)
            }
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val statusRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val dot = View(activity).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ERR_RED)
            }
        }
        statusRow.addView(dot, LinearLayout.LayoutParams(dp(12), dp(12)).apply {
            setMargins(0, 0, dp(10), 0)
        })
        val statusText = TextView(activity).apply {
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
        }
        statusRow.addView(statusText)
        card.addView(statusRow)
        val lastSyncText = TextView(activity).apply {
            textSize = 11f
            setTextColor(secondaryText)
            setPadding(dp(22), dp(6), 0, 0)
        }
        card.addView(lastSyncText)
        layout.addView(card, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(10) })

        // ---------- otomatik eşitleme ----------
        val autoCheck = CheckBox(activity).apply {
            text = "Otomatik eşitle (~60 saniyede bir)"
            setTextColor(primaryText)
            textSize = 13f
            isChecked = TraktSyncConfig.autoSync
            setPadding(dp(2), dp(2), 0, dp(8))
            setOnCheckedChangeListener { _, checked -> TraktSyncConfig.autoSync = checked }
        }
        layout.addView(autoCheck)

        // ---------- düğmeler ----------
        fun rounded(color: Int): GradientDrawable = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(color)
        }
        fun Button.tune() {
            minWidth = 0
            minHeight = 0
            minimumWidth = 0
            minimumHeight = 0
            isAllCaps = false
            textSize = 14f
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }

        // Birincil düğme: bağlı değilse "Trakt'a Bağlan", bağlıysa "Şimdi Eşitle"
        val primaryBtn = Button(activity).apply {
            tune()
            setTextColor(Color.WHITE)
            background = rounded(TRAKT_RED)
        }
        layout.addView(primaryBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(48),
        ))

        // Bağlı durumda ikincil eylemler: eşit genişlik, aynı stil
        val secondaryRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val switchBtn = Button(activity).apply {
            tune()
            text = "Hesap Değiştir"
            setTextColor(primaryText)
            background = rounded(CARD_BG)
        }
        val disconnectBtn = Button(activity).apply {
            tune()
            text = "Bağlantıyı Kes"
            setTextColor(ERR_RED)
            background = rounded(CARD_BG)
        }
        secondaryRow.addView(switchBtn, LinearLayout.LayoutParams(0, dp(44), 1f))
        secondaryRow.addView(View(activity), LinearLayout.LayoutParams(dp(8), 1))
        secondaryRow.addView(disconnectBtn, LinearLayout.LayoutParams(0, dp(44), 1f))
        layout.addView(secondaryRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(8) })

        // Yeniden uygula (işaretler kaçtıysa kurtarır) — sadece bağlıyken
        val reapplyBtn = Button(activity).apply {
            tune()
            textSize = 12f
            text = "İşaretleri Yeniden Uygula"
            setTextColor(secondaryText)
            background = null
        }
        layout.addView(reapplyBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(2) })

        // ---------- durum yenileyici ----------
        fun refreshStatus() {
            val connected = TraktSyncConfig.connected
            (dot.background as? GradientDrawable)?.setColor(if (connected) OK_GREEN else ERR_RED)
            statusText.text = if (connected) {
                "Bağlı — ${TraktSyncConfig.userName.ifBlank { "Trakt kullanıcısı" }}"
            } else {
                "Bağlı değil"
            }
            statusText.setTextColor(if (connected) OK_GREEN else ERR_RED)

            lastSyncText.text = if (TraktSyncConfig.lastSyncAt > 0) {
                val fmt = SimpleDateFormat("d MMM yyyy, HH:mm", Locale("tr"))
                val when_ = fmt.format(Date(TraktSyncConfig.lastSyncAt))
                val summary = TraktSyncConfig.lastSyncSummary
                if (summary.isBlank()) "Son eşitleme: $when_"
                else "Son eşitleme: $when_\n$summary"
            } else {
                if (connected) "Henüz eşitlenmedi — birazdan otomatik eşitlenir"
                else "Bağlanınca eşitleme başlar"
            }

            // İki durum arasında net geçiş — bağlıyken "Bağlan" yazmaz
            if (connected) {
                primaryBtn.text = "Şimdi Eşitle"
                secondaryRow.visibility = View.VISIBLE
                reapplyBtn.visibility = View.VISIBLE
            } else {
                primaryBtn.text = "Trakt'a Bağlan"
                secondaryRow.visibility = View.GONE
                reapplyBtn.visibility = View.GONE
            }
        }
        refreshStatus()

        // ---------- eylemler ----------
        fun runSyncThenRefresh() {
            primaryBtn.isEnabled = false
            primaryBtn.text = "Eşitleniyor…"
            scope.launch(Dispatchers.IO) {
                runCatching { TraktSyncManager.syncOnce() }
                launch(Dispatchers.Main) {
                    primaryBtn.isEnabled = true
                    refreshStatus()
                    Toast.makeText(activity, "Trakt eşitlemesi tamamlandı", Toast.LENGTH_SHORT).show()
                }
            }
        }

        fun connect() {
            TraktLoginDialog.show(activity) { success ->
                refreshStatus()
                if (success) {
                    scope.launch(Dispatchers.IO) {
                        kotlinx.coroutines.delay(500)
                        runCatching { TraktSyncManager.syncOnce() }
                        launch(Dispatchers.Main) { refreshStatus() }
                    }
                }
            }
        }

        primaryBtn.setOnClickListener {
            if (TraktSyncConfig.connected) {
                runSyncThenRefresh()
            } else {
                connect()
            }
        }
        switchBtn.setOnClickListener { connect() }
        disconnectBtn.setOnClickListener {
            AlertDialog.Builder(activity)
                .setTitle("Bağlantı kesilsin mi?")
                .setMessage("Trakt oturumu bu cihazdan kaldırılır. Trakt.tv'deki listen ve izleme geçmişin etkilenmez.")
                .setPositiveButton("Kes") { _, _ ->
                    TraktSyncConfig.clearSession()
                    refreshStatus()
                    Toast.makeText(activity, "Trakt oturumu kapatıldı", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Vazgeç", null)
                .show()
        }
        reapplyBtn.setOnClickListener {
            AlertDialog.Builder(activity)
                .setTitle("İşaretler yeniden uygulansın mı?")
                .setMessage("Trakt'ta izlediklerinin tamamı kâtüphanedeki içeriklere tekrar işlenir. " +
                    "Daha önce işaretlenmişler zaten yeşil görünür.")
                .setPositiveButton("Uygula") { _, _ ->
                    TraktSyncConfig.resetPulledApplied()
                    runSyncThenRefresh()
                }
                .setNegativeButton("Vazgeç", null)
                .show()
        }

        // ---------- açıklama ----------
        layout.addView(TextView(activity).apply {
            text = "Nasıl çalışır?"
            textSize = 13f
            setTextColor(primaryText)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(14), 0, dp(4))
        })
        layout.addView(TextView(activity).apply {
            text = "→ CloudStream'de izlendi (COMPLETED) işaretlediklerin Trakt izleme " +
                "geçmişine yazılır; WATCHING / PLANTOWATCH içerikler Trakt izleme listene eklenir.\n" +
                "← Trakt'ta izlediklerin buraya işlenir: filmler COMPLETED + yeşil ✓ olur, dizi " +
                "bölümleri bölüm listesinde yeşil ✓ alır. Tüm yayındaki bölümler izlendiyse " +
                "COMPLETED, en az biri izlendiyse WATCHING olur (DROPPED / ONHOLD'a dokunulmaz).\n\n" +
                "Not: Eşleme yalnızca kâtüphanene eklediğin (kitap işaretli) içeriklere uygulanır. " +
                "Gömülü Trakt uygulaması “Development” türündedir; yalnızca uygulamanın sahibi ve " +
                "16 davetli kullanıcı bağlanabilir."
            textSize = 11.5f
            setTextColor(secondaryText)
            setLineSpacing(0f, 1.2f)
        })

        AlertDialog.Builder(activity)
            .setView(ScrollView(activity).apply { addView(layout) })
            .setPositiveButton("Kapat", null)
            .create()
            .show()
    }
}
