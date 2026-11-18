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
 * Trakt Senkron ayar penceresi — çift yönlü eşitlemeyi gösterir.
 *
 * Kullanıcıdan Client ID / Secret alınmaz; eklentiye gömülü, PKCE
 * (client_secret gerektirmeyen) Trakt API uygulaması kullanılır.
 * Giriş, e-posta + Trakt'tan gelen kodla (uygulama içi WebView) yapılır.
 */
object TraktSettingsDialog {

    private const val TRAKT_RED = 0xFFED1C24.toInt()
    private const val OK_GREEN = 0xFF2E7D32.toInt()
    private const val ERR_RED = 0xFFB71C1C.toInt()
    private const val CARD_BG = 0x1A808080.toInt() // her iki temada da okunur nötr kutu

    fun show(context: Context) {
        val activity = context as? Activity ?: return
        val density = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        // Temaya uyumlu metin renkleri (koyu/açık tema ikisinde de okunur)
        val ta = activity.obtainStyledAttributes(
            intArrayOf(android.R.attr.textColorPrimary, android.R.attr.textColorSecondary),
        )
        val primaryText = ta.getColor(0, Color.BLACK)
        val secondaryText = ta.getColor(1, Color.DKGRAY)
        ta.recycle()

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        // ---------- kök ----------
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(4))
        }

        // ---------- başlık: Trakt "T" logosu + ad ----------
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
                text = "CloudStream ⇄ Trakt.tv çift yönlü eşitleme"
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
            textSize = 11.5f
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
            setPadding(dp(2), dp(2), 0, dp(6))
            setOnCheckedChangeListener { _, checked -> TraktSyncConfig.autoSync = checked }
        }
        layout.addView(autoCheck)

        // ---------- düğmeler ----------
        fun roundedBg(color: Int): GradientDrawable = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(color)
        }

        val connectBtn = Button(activity).apply {
            textSize = 13f
            setTextColor(Color.WHITE)
            background = roundedBg(TRAKT_RED)
            isAllCaps = false
        }
        val syncBtn = Button(activity).apply {
            text = "Şimdi Eşitle"
            textSize = 13f
            setTextColor(primaryText)
            background = roundedBg(CARD_BG)
            isAllCaps = false
        }
        val btnRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        btnRow.addView(connectBtn, LinearLayout.LayoutParams(0, dp(46), 1f))
        btnRow.addView(View(activity), LinearLayout.LayoutParams(dp(8), 1))
        btnRow.addView(syncBtn, LinearLayout.LayoutParams(0, dp(46), 1f))
        layout.addView(btnRow)

        val disconnectBtn = Button(activity).apply {
            text = "Bağlantıyı Kes"
            textSize = 12f
            setTextColor(ERR_RED)
            background = null
            isAllCaps = false
        }
        layout.addView(disconnectBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(4) })

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
                if (summary.isBlank()) "Son eşitleme: $when_" else "Son eşitleme: $when_  ·  $summary"
            } else {
                if (connected) "Henüz eşitlenmedi — birazdan otomatik eşitlenir" else "Bağlanınca eşitleme başlar"
            }

            connectBtn.text = if (connected) "Başka Hesapla Bağlan" else "Trakt'a Bağlan"
            disconnectBtn.visibility = if (connected) View.VISIBLE else View.GONE
        }
        refreshStatus()

        // ---------- eylemler ----------
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
        connectBtn.setOnClickListener { connect() }

        syncBtn.setOnClickListener {
            if (!TraktSyncConfig.connected) {
                Toast.makeText(activity, "Önce Trakt'a bağlanın", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            syncBtn.isEnabled = false
            syncBtn.text = "Eşitleniyor…"
            scope.launch(Dispatchers.IO) {
                runCatching { TraktSyncManager.syncOnce() }
                launch(Dispatchers.Main) {
                    syncBtn.isEnabled = true
                    syncBtn.text = "Şimdi Eşitle"
                    refreshStatus()
                    Toast.makeText(activity, "Trakt eşitlemesi tamamlandı", Toast.LENGTH_SHORT).show()
                }
            }
        }

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

        // ---------- nasıl çalışır ----------
        layout.addView(TextView(activity).apply {
            text = "Nasıl çalışır?"
            textSize = 13f
            setTextColor(primaryText)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(14), 0, dp(4))
        })
        layout.addView(TextView(activity).apply {
            text = "Senkron çift yönlüdür:\n" +
                "→ CloudStream'de COMPLETED işaretlediklerin Trakt izleme geçmişine yazılır; " +
                "WATCHING / ONHOLD / PLANTOWATCH içerikler Trakt izleme listene eklenir.\n" +
                "← Trakt'ta izlediklerin de buraya işlenir: filmler COMPLETED olur, dizi " +
                "bölümleri bölüm listesinde yeşil ✓ alır. Dizinin yayındaki tüm bölümleri " +
                "izlendiyse COMPLETED, en az biri izlendiyse WATCHING yapılır " +
                "(DROPPED / ONHOLD'a dokunulmaz).\n\n" +
                "Oynat menüsündeki “✓ Trakt'a İzledim Olarak İşaretle” düğmesiyle tek " +
                "dokunuşla da işaretleyebilirsin."
            textSize = 12f
            setTextColor(secondaryText)
            setLineSpacing(0f, 1.15f)
        })

        layout.addView(TextView(activity).apply {
            text = "Gömülü Trakt uygulaması “Development” türündedir: yalnızca uygulamanın " +
                "sahibi ve geliştirme süresince eklenen 16 kullanıcı bağlanabilir. Herkese " +
                "açık sürüm için geliştiricinin Production erişimi istemesi gerekir. " +
                "Kimlik çözülemezse (imdb/tmdb/syncData yoksa) ve başlıkla aramada " +
                "bulunamazsa içerik eşitleme sırasını atlar."
            textSize = 10.5f
            setTextColor(secondaryText)
            setLineSpacing(0f, 1.1f)
            setPadding(0, dp(10), 0, dp(6))
        })

        AlertDialog.Builder(activity)
            .setView(ScrollView(activity).apply { addView(layout) })
            .setPositiveButton("Kapat", null)
            .create()
            .show()
    }
}
