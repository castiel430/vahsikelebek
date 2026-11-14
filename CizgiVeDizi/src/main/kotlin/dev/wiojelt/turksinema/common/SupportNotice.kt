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
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import java.util.Calendar

object SupportNotice {
    private const val PREFS = "wio_global_support_notice"
    private const val LAST_DAY = "last_day"
    @Volatile private var isShownThisSession = false

    private val MESSAGES = listOf(
        "🍿 Çaylar, kahveler hazır mı? Eklentilerimiz tamamen reklamsız ve ücretsiz! Sunucuların kapanmaması ve kesintisiz yayın keyfi için Kreosus'tan küçük bir destekle yanımızda olabilirsiniz.",
        "🚀 Eklentiler hep açık kalsın, linkler hiç kopmasın! Geliştirmeye tam gaz devam edebilmemiz ve sunucuları yaşatabilmemiz için bir kahve ısmarlayabilirsiniz.",
        "🎉 Reklamsız dizi, film ve spor keyfi paha biçilemez! Projeyi hep birlikte yaşatmak ve eklentilerin kapanmasını önlemek için Kreosus'ta desteğinizi bekliyoruz.",
        "☕ Bir fincan kahve ile eklentilerin ömrünü uzatın! Reklamsız, tertemiz yayınların kesintisiz sürmesi sizlerin güzel desteğiyle mümkün.",
        "✨ Keyfiniz hiç bölünmesin, yayınlar kapanmasın! Gönüllü olarak sürdürdüğümüz bu projeye Kreosus'tan küçük bir bağışla omuz verebilirsiniz.",
        "⚡ Kesintisiz sinema ve spor heyecanı hep sürsün! Sunucularımızın ayakta kalması ve yeni kaynakların eklenmesi için bir kahvelik desteğiniz çok değerli.",
        "🎯 Sıfır reklam, maksimum keyif! Eklentilerimizin uzun süre kapanmadan hizmet vermesi için Kreosus üzerinden bize katılabilirsiniz.",
        "🥳 Birlikte daha güçlüyüz! Eklentileri yaşatmak, linkleri güncel tutmak ve sunucu masraflarını karşılamak için Kreosus'tan destek olabilirsiniz.",
        "🌟 En sevdiğin içerikler her an elinin altında! Bu güzel topluluğu ve eklentileri ayakta tutmak için küçük bir bağış harikalar yaratır.",
        "💪 Hiçbir ticari gelirimiz yok, sadece tutku var! Eklentilerin kapanmaması ve linklerin hep canlı kalması için desteklerinizi esirgemeyin."
    )

    fun showIfDue(context: Context, appName: String = "WioSinema", force: Boolean = false) {
        // Popups permanently disabled
        return
    }

    private fun showDialog(activity: Activity, appName: String) {
        val density = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val screenWidth = activity.resources.displayMetrics.widthPixels
        val dialogWidth = (screenWidth * 0.88f).toInt().coerceIn(dp(320), dp(430))

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)

        // Nothing OS Translucent Frosted Glass Card
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F20D0E12")) // Deep obsidian translucent glass
                cornerRadius = dp(28).toFloat()
                setStroke(dp(1), Color.parseColor("#33FFFFFF")) // Subtle translucent glass border
            }
            setPadding(dp(22), dp(22), dp(22), dp(20))
        }

        // Header Pill Badge: Nothing Red Dot + "TOPLULUK & DESTEK"
        val badgePill = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#181A20"))
                cornerRadius = dp(100).toFloat()
                setStroke(dp(1), Color.parseColor("#33FFFFFF"))
            }
            setPadding(dp(12), dp(5), dp(14), dp(5))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dp(14))
            }
        }

        val redDot = TextView(activity).apply {
            text = "●"
            textSize = 9f
            setTextColor(Color.parseColor("#E01E2B")) // Nothing Red Dot
            setPadding(0, 0, dp(6), 0)
        }
        badgePill.addView(redDot)

        val badgeText = TextView(activity).apply {
            text = "TOPLULUK & DESTEK"
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#EEEEEE"))
            letterSpacing = 0.08f
        }
        badgePill.addView(badgeText)
        root.addView(badgePill)

        // Title: Centered
        val titleView = TextView(activity).apply {
            text = "$appName Topluluğu"
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dp(12))
            }
        }
        root.addView(titleView)

        // Description Card (Frosted Translucent Container)
        val descCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#16181F"))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.parseColor("#2E3342"))
            }
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dp(16))
            }
        }

        val descView = TextView(activity).apply {
            text = MESSAGES.random()
            textSize = 12.5f
            setTextColor(Color.parseColor("#8E95A5"))
            gravity = Gravity.CENTER
            setLineSpacing(dp(2).toFloat(), 1.25f)
        }
        descCard.addView(descView)
        root.addView(descCard)

        // Button 1: Kreosus Support (Nothing Gold/Amber Glass Capsule Pill)
        val btnKreosus = MaterialButton(activity).apply {
            text = "Kreosus ile Destek Ol ☕"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FFD54F"))
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#26200A"))
            strokeColor = ColorStateList.valueOf(Color.parseColor("#664D03"))
            strokeWidth = dp(1)
            cornerRadius = dp(100)
            insetTop = 0
            insetBottom = 0
            isAllCaps = false
            maxLines = 1
            gravity = Gravity.CENTER
            isFocusable = true
            isFocusableInTouchMode = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply {
                setMargins(0, 0, 0, dp(10))
            }
            setOnClickListener {
                open(activity, "https://kreosus.com/wiojelt")
                dialog.dismiss()
            }
        }
        root.addView(btnKreosus)

        // Button 2: Telegram (Nothing Translucent Dark Glass Capsule Pill)
        val btnTelegram = MaterialButton(activity).apply {
            text = "Telegram Kanalı ↗"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#181B22"))
            strokeColor = ColorStateList.valueOf(Color.parseColor("#3B4252"))
            strokeWidth = dp(1)
            cornerRadius = dp(100)
            insetTop = 0
            insetBottom = 0
            isAllCaps = false
            maxLines = 1
            gravity = Gravity.CENTER
            isFocusable = true
            isFocusableInTouchMode = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply {
                setMargins(0, 0, 0, dp(10))
            }
            setOnClickListener {
                open(activity, "https://t.me/wiolandcs3")
                dialog.dismiss()
            }
        }
        root.addView(btnTelegram)

        // Button 3: Close (Minimal Nothing Pill)
        val btnClose = MaterialButton(activity).apply {
            text = "Daha Sonra Hatırlat"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#9CA3AF"))
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#141519"))
            strokeColor = ColorStateList.valueOf(Color.parseColor("#242730"))
            strokeWidth = dp(1)
            cornerRadius = dp(100)
            insetTop = 0
            insetBottom = 0
            isAllCaps = false
            maxLines = 1
            gravity = Gravity.CENTER
            isFocusable = true
            isFocusableInTouchMode = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40))
            setOnClickListener { dialog.dismiss() }
        }
        root.addView(btnClose)

        dialog.setContentView(root)
        dialog.window?.let { win ->
            win.setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
            win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            win.setDimAmount(0.75f)
        }
        dialog.show()
    }

    private fun open(context: Context, url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
