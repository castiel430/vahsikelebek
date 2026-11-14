package dev.wiojelt.turksinema.common

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import java.util.Calendar

object WioPollNotice {
    private const val PREFS = "wio_global_poll_notice"
    private const val LAST_DAY = "poll_post_23_last_day"
    private const val KEY_VOTED = "poll_post_23_voted"

    // Poll ends 7 days after 2026-09-20 12:20:37 UTC (2026-09-27 12:20:37 UTC)
    const val POLL_END_TIME_MS = 1790511637000L

    const val URL_STRAWPOLL_SINEMA = "https://strawpoll.com/bVg8BO3NryY"
    const val URL_STRAWPOLL_SPOR = "https://strawpoll.com/61gD9paj9Zw"
    const val URL_TELEGRAM_POST = "https://t.me/wiolandcs3/23"

    @Volatile
    private var isShownThisSession = false

    fun getRemainingTimeText(): String {
        val diff = POLL_END_TIME_MS - System.currentTimeMillis()
        if (diff <= 0) return "Sona Erdi"
        val days = (diff / (1000L * 60 * 60 * 24)).toInt()
        val hours = ((diff / (1000L * 60 * 60)) % 24).toInt()
        return if (days > 0) "$days gün $hours saat kaldı" else "$hours saat kaldı"
    }

    fun showIfDue(context: Context, category: String = "ALL", force: Boolean = false) {
        // Poll pop-ups are disabled. A plugin may remain cached after removal,
        // so do not schedule any delayed dialog from a provider load.
        return
        /*
        if (!force && isShownThisSession) return

        val activity = context as? Activity ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        if (!force) {
            if (System.currentTimeMillis() >= POLL_END_TIME_MS) return
            if (prefs.getBoolean(KEY_VOTED, false)) return
            val today = Calendar.getInstance().run { "${get(Calendar.YEAR)}-${get(Calendar.DAY_OF_YEAR)}" }
            if (prefs.getString(LAST_DAY, "") == today) return
            prefs.edit().putString(LAST_DAY, today).apply()
        }

        isShownThisSession = true

        Handler(Looper.getMainLooper()).postDelayed({
            if (activity.isFinishing || activity.isDestroyed) return@postDelayed
            runCatching {
                showDialog(activity, prefs, category)
            }
        }, 800L)
        */
    }

    private fun showDialog(activity: Activity, prefs: android.content.SharedPreferences, category: String) {
        val density = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val screenWidth = activity.resources.displayMetrics.widthPixels
        val dialogWidth = (screenWidth * 0.88f).toInt().coerceIn(dp(320), dp(430))

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        // Prevent dialog from closing when tapping outside or pressing back accidentally
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)

        // Root container: Translucent Nothing OS Frosted Glass
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F20D0E12")) // Deep obsidian glass
                cornerRadius = dp(28).toFloat()
                setStroke(dp(1), Color.parseColor("#33FFFFFF")) // Translucent glass border
            }
            setPadding(dp(22), dp(22), dp(22), dp(20))
        }

        // Header Row: Centered Badges
        val headerRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dp(14))
            }
        }

        // Pill: Nothing Red Dot + "TOPLULUK OYLAMASI"
        val badgePill = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#181A20"))
                cornerRadius = dp(100).toFloat()
                setStroke(dp(1), Color.parseColor("#33FFFFFF"))
            }
            setPadding(dp(12), dp(5), dp(14), dp(5))
        }

        val redDot = TextView(activity).apply {
            text = "●"
            textSize = 9f
            setTextColor(Color.parseColor("#E01E2B")) // Nothing Signature Red
            setPadding(0, 0, dp(6), 0)
        }
        badgePill.addView(redDot)

        val badgeText = TextView(activity).apply {
            text = "TOPLULUK OYLAMASI"
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#EEEEEE"))
            letterSpacing = 0.08f
        }
        badgePill.addView(badgeText)
        headerRow.addView(badgePill)
        root.addView(headerRow)

        // Poll Question Title (CENTERED)
        val questionTitle = TextView(activity).apply {
            text = "Hangi kaynaklar kalmalı?"
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dp(4))
            }
        }
        root.addView(questionTitle)

        // Subtitle / Guidance (CENTERED)
        val questionSub = TextView(activity).apply {
            text = "Gereksizler silinecek, sadece en çok oy alanlar kalacak.\nKullandığınız en iyi 5 sağlayıcıyı oylayın."
            textSize = 12.5f
            setTextColor(Color.parseColor("#8E95A5"))
            gravity = Gravity.CENTER
            setLineSpacing(dp(2).toFloat(), 1.25f)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dp(10))
            }
        }
        root.addView(questionSub)

        // Remaining Time Pill (ELEGANTLY INTEGRATED HERE, NOT AT TOP)
        val remainingPill = TextView(activity).apply {
            text = "⏱️ Oylamanın bitmesine ${getRemainingTimeText()}"
            textSize = 11.5f
            setTextColor(Color.parseColor("#A1A1AA"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#141519"))
                cornerRadius = dp(100).toFloat()
                setStroke(dp(1), Color.parseColor("#22FFFFFF"))
            }
            setPadding(dp(12), dp(5), dp(12), dp(5))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dp(14))
            }
        }
        root.addView(remainingPill)

        val autoTargetUrl = when (category) {
            "SPOR" -> URL_STRAWPOLL_SPOR
            "SINEMA" -> URL_STRAWPOLL_SINEMA
            else -> URL_STRAWPOLL_SINEMA
        }

        // Countdown Container with Progress Bar that fills from 0% to 100%
        val countdownBox = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#121419"))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.parseColor("#1E222B"))
            }
            setPadding(dp(12), dp(9), dp(12), dp(9))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dp(12))
            }
        }

        val countdownText = TextView(activity).apply {
            text = "⏳ 10s • Otomatik ankete yönlendiriliyorsunuz"
            textSize = 11.5f
            typeface = Typeface.MONOSPACE
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#D1D5DB"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dp(6))
            }
        }
        countdownBox.addView(countdownText)

        // Progress Bar (0% -> 100% fills left to right)
        val progressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = 0
            progressTintList = ColorStateList.valueOf(Color.parseColor("#E01E2B")) // Nothing Red
            progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#252833"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4))
        }
        countdownBox.addView(progressBar)
        root.addView(countdownBox)

        // Smooth Countdown Handler (updates every 100ms so progress bar smoothly fills baştan sona)
        val countdownHandler = Handler(Looper.getMainLooper())
        val totalDurationMs = 10000L
        var elapsedMs = 0L

        val countdownRunnable = object : Runnable {
            override fun run() {
                elapsedMs += 100L
                val progressFraction = (elapsedMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
                progressBar.progress = (progressFraction * 1000).toInt()

                val secondsRemaining = ((totalDurationMs - elapsedMs + 999) / 1000).toInt()
                if (secondsRemaining > 0) {
                    countdownText.text = "⏳ ${secondsRemaining}s • Otomatik ankete yönlendiriliyorsunuz"
                    countdownHandler.postDelayed(this, 100L)
                } else {
                    progressBar.progress = 1000
                    countdownText.text = "🚀 Yönlendiriliyor..."
                    openUrl(activity, autoTargetUrl)
                    runCatching { dialog.dismiss() }
                }
            }
        }

        dialog.setOnDismissListener {
            countdownHandler.removeCallbacksAndMessages(null)
        }

        // --- POLL CARDS (Anket Seçenekleri) ---
        // Option 1 Card: Sinema Anketi
        val cardSinema = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#16181F"))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.parseColor("#2E3342"))
            }
            setPadding(dp(16), dp(14), dp(16), dp(14))
            isFocusable = true
            isClickable = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dp(10))
            }
            setOnClickListener {
                countdownHandler.removeCallbacksAndMessages(null)
                openUrl(activity, URL_STRAWPOLL_SINEMA)
                dialog.dismiss()
            }
        }

        val sinemaContent = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val sinemaTitle = TextView(activity).apply {
            text = "🎬 Sinema & Dizi Anketi"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        val sinemaDesc = TextView(activity).apply {
            text = "66 sağlayıcı arasından en iyi 5'ini seç"
            textSize = 11.5f
            setTextColor(Color.parseColor("#8E95A5"))
            setPadding(0, dp(2), 0, 0)
        }
        sinemaContent.addView(sinemaTitle)
        sinemaContent.addView(sinemaDesc)
        cardSinema.addView(sinemaContent)

        val sinemaBadge = TextView(activity).apply {
            text = "OY VER ↗"
            textSize = 10.5f
            typeface = Typeface.MONOSPACE
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#60A5FA"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#17263C"))
                cornerRadius = dp(100).toFloat()
                setStroke(dp(1), Color.parseColor("#2563EB"))
            }
            setPadding(dp(10), dp(5), dp(10), dp(5))
        }
        cardSinema.addView(sinemaBadge)
        root.addView(cardSinema)

        // Option 2 Card: Spor Anketi
        val cardSpor = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#16181F"))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.parseColor("#2E3342"))
            }
            setPadding(dp(16), dp(14), dp(16), dp(14))
            isFocusable = true
            isClickable = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dp(14))
            }
            setOnClickListener {
                countdownHandler.removeCallbacksAndMessages(null)
                openUrl(activity, URL_STRAWPOLL_SPOR)
                dialog.dismiss()
            }
        }

        val sporContent = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val sporTitle = TextView(activity).apply {
            text = "⚽ Canlı TV & Spor Anketi"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        val sporDesc = TextView(activity).apply {
            text = "Canlı maç ve spor kaynaklarını belirle"
            textSize = 11.5f
            setTextColor(Color.parseColor("#8E95A5"))
            setPadding(0, dp(2), 0, 0)
        }
        sporContent.addView(sporTitle)
        sporContent.addView(sporDesc)
        cardSpor.addView(sporContent)

        val sporBadge = TextView(activity).apply {
            text = "OY VER ↗"
            textSize = 10.5f
            typeface = Typeface.MONOSPACE
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#4ADE80"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#142B1E"))
                cornerRadius = dp(100).toFloat()
                setStroke(dp(1), Color.parseColor("#16A34A"))
            }
            setPadding(dp(10), dp(5), dp(10), dp(5))
        }
        cardSpor.addView(sporBadge)
        root.addView(cardSpor)

        // Bottom Row: [ Oy Verdim ] (Starts neutral, turns green with check on click) + [ Kapat ]
        val bottomRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42))
        }

        // Button: Oy Verdim (Starts neutral dark, NO green, NO checkmark)
        val btnVoted = MaterialButton(activity).apply {
            text = "Oy Verdim"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#9CA3AF"))
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#16181D"))
            strokeColor = ColorStateList.valueOf(Color.parseColor("#292D38"))
            strokeWidth = dp(1)
            cornerRadius = dp(100)
            insetTop = 0
            insetBottom = 0
            isAllCaps = false
            maxLines = 1
            gravity = Gravity.CENTER
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.1f).apply {
                setMargins(0, 0, dp(8), 0)
            }
        }

        // Button: Kapat
        val btnClose = MaterialButton(activity).apply {
            text = "Kapat"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#9CA3AF"))
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#16181D"))
            strokeColor = ColorStateList.valueOf(Color.parseColor("#292D38"))
            strokeWidth = dp(1)
            cornerRadius = dp(100)
            insetTop = 0
            insetBottom = 0
            isAllCaps = false
            maxLines = 1
            gravity = Gravity.CENTER
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            setOnClickListener {
                countdownHandler.removeCallbacksAndMessages(null)
                dialog.dismiss()
            }
        }

        btnVoted.setOnClickListener {
            countdownHandler.removeCallbacksAndMessages(null)
            prefs.edit().putBoolean(KEY_VOTED, true).apply()
            countdownText.text = "✓ Oyunuz kaydedildi, teşekkür ederiz!"
            progressBar.visibility = View.GONE
            btnVoted.text = "Teşekkürler! ✓"
            btnVoted.setTextColor(Color.parseColor("#4ADE80"))
            btnVoted.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#14231A"))
            btnVoted.strokeColor = ColorStateList.valueOf(Color.parseColor("#236E3F"))
            btnVoted.isEnabled = false
            btnClose.setTextColor(Color.WHITE)
            btnClose.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#252833"))
            btnClose.strokeColor = ColorStateList.valueOf(Color.parseColor("#80FFFFFF"))
        }

        bottomRow.addView(btnVoted)
        bottomRow.addView(btnClose)
        root.addView(bottomRow)

        dialog.setContentView(root)

        // Dialog Window sizing and flags
        dialog.window?.let { win ->
            win.setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
            win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            win.setDimAmount(0.75f)
        }

        dialog.show()

        // Start countdown progress (smooth 100ms ticks)
        countdownHandler.postDelayed(countdownRunnable, 100L)
    }

    private fun openUrl(context: Context, url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
