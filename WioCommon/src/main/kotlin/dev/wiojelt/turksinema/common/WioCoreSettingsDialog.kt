package dev.wiojelt.turksinema.common

import android.app.Activity
import android.app.Dialog
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.NestedScrollView
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WioProviderSettingItem(
    val id: String,
    val displayName: String,
    val isDirectTmdb: Boolean = false,
    var domain: String? = null,
    var isEnabled: Boolean = true,
    val isForeign: Boolean = false,
    val capabilityBadges: String? = null,
    val onToggle: (Boolean) -> Unit = {},
    val onDomainEdit: ((newDomain: String) -> Unit)? = null
)

data class WioSelectionPreset(
    val id: String,
    val title: String,
    val description: String,
    val providerIds: Set<String>
)

data class WioSettingsConfig(
    val brandPrefix: String = "WIO",
    val brandSuffix: String = "SİNEMA",
    val brandTitleTag: String = "Kontrol Paneli",
    val summarySubtitle: String? = null,
    val brandColor: Int = Color.parseColor("#FFB703"),
    val brandColorBorder: Int = Color.parseColor("#33FFFFFF"),
    val sheetBg: Int = Color.parseColor("#F20D0E12"),
    val cardBg: Int = Color.parseColor("#E6161820"),
    val cardBgSubtle: Int = Color.parseColor("#B3161820"),
    val textPrimary: Int = Color.parseColor("#FFFFFF"),
    val textSecondary: Int = Color.parseColor("#8E95A5"),
    val textMuted: Int = Color.parseColor("#6E7681"),
    /** Switches every shared settings-panel label to English for global packages. */
    val englishLabels: Boolean = false,
    val isTvBoxSupported: Boolean = true,
    val isTvBoxActive: () -> Boolean = { false },
    val onTvBoxToggled: (Boolean) -> Unit = {},
    val isScanDurationSupported: Boolean = false,
    val scanDurationSeconds: () -> Int = { 30 },
    val onScanDurationChanged: (Int) -> Unit = {},
    val minScanDurationSeconds: Int = 10,
    val maxScanDurationSeconds: Int = 30,
    val tvBoxScanDurationSeconds: Int = 10,
    val items: List<WioProviderSettingItem> = emptyList(),
    val selectionTitle: String? = "Dil tercihi",
    val compactSelectionPresets: Boolean = false,
    val selectionPresets: List<WioSelectionPreset> = emptyList(),
    val onSelectionPreset: ((presetId: String, providerIds: Set<String>) -> Unit)? = null,
    val onSelectAll: (() -> Unit)? = null,
    val onSelectRecommended: (() -> Unit)? = null,
    val onSelectNone: (() -> Unit)? = null,
    val onWizardClick: (() -> Unit)? = null,
    val extraActionLabel: String? = null,
    val onExtraAction: (() -> Unit)? = null,
    val onCustomListClick: (() -> Unit)? = null,
    val customListSummary: String? = null,
    val onAutoScanDomains: (suspend (statusCallback: (String) -> Unit) -> Pair<Int, Int>)? = null,
    val onSaveAndClose: (() -> Unit)? = null,
    val providersCollapsible: Boolean = false,
    val telegramUrl: String? = "https://t.me/wiolandcs3",
    val coffeeUrl: String? = "https://kreosus.com/wiojelt"
)

object WioCoreSettingsDialog {

    /** Settings are persisted and caches cleared by the caller before this notice is shown. */
    fun showRestartRequiredNotice(context: Context, english: Boolean = false) {
        Handler(Looper.getMainLooper()).post {
            AlertDialog.Builder(context)
                .setTitle(if (english) "Restart CloudStream" else "CloudStream'i yeniden başlatın")
                .setMessage(
                    if (english) {
                        "Your settings have been saved and the cache has been cleared. Close and reopen CloudStream to apply the changes to every provider."
                    } else {
                        "Ayarlar kaydedildi ve önbellek temizlendi. Değişikliklerin tüm sağlayıcılara uygulanması için CloudStream'i kapatıp yeniden açın."
                    }
                )
                .setPositiveButton(if (english) "Close app" else "Uygulamayı kapat") { _, _ ->
                    (context as? Activity)?.let { activity ->
                        activity.finishAndRemoveTask()
                        Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, 180)
                    }
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun deleteRecursive(file: java.io.File?): Long {
        if (file == null || !file.exists()) return 0L
        var bytes = 0L
        if (file.isDirectory) {
            val children = file.listFiles()
            if (children != null) {
                for (child in children) {
                    bytes += deleteRecursive(child)
                }
            }
        }
        val len = file.length()
        if (file.delete()) {
            bytes += len
        }
        return bytes
    }

    fun clearCache(context: Context): Long {
        var totalFreed = 0L
        runCatching {
            context.cacheDir?.listFiles()?.forEach { file ->
                totalFreed += deleteRecursive(file)
            }
            context.externalCacheDir?.listFiles()?.forEach { file ->
                totalFreed += deleteRecursive(file)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                context.codeCacheDir?.listFiles()?.forEach { file ->
                    totalFreed += deleteRecursive(file)
                }
            }
        }
        return totalFreed
    }

    fun isAutoDetectedTvOrLowRam(context: Context): Boolean {
        return runCatching {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            Build.MODEL.contains("TV", ignoreCase = true) ||
            Build.DEVICE.contains("TV", ignoreCase = true) ||
            (Runtime.getRuntime().maxMemory() < 192 * 1024 * 1024)
        }.getOrDefault(false)
    }

    fun showEditDomainDialog(
        context: Context,
        providerName: String,
        currentUrl: String,
        englishLabels: Boolean = false,
        brandColor: Int = Color.parseColor("#FFB703"),
        brandColorBorder: Int = Color.parseColor("#66FFB703"),
        onSave: (String) -> Unit
    ) {
        fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()

        val editDialog = Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(20))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(28).toFloat()
                setColor(Color.parseColor("#F20D0E12"))
                setStroke(dp(1), Color.parseColor("#33FFFFFF"))
            }
        }

        val headerTag = TextView(context).apply {
            text = if (englishLabels) "● UPDATE DOMAIN" else "● ALAN ADI GÜNCELLEME"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#D71921"))
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(100).toFloat()
                setColor(Color.parseColor("#26D71921"))
                setStroke(dp(1), Color.parseColor("#40D71921"))
            }
        }
        val headerTagWrapper = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(headerTag)
        }
        container.addView(headerTagWrapper)

        val titleView = TextView(context).apply {
            text = if (englishLabels) "$providerName Domain" else "$providerName Alan Adı"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, dp(8), 0, dp(2))
        }
        container.addView(titleView)

        val subtitleView = TextView(context).apply {
            text = if (englishLabels) "Enter the new working domain:" else "Yeni çalışan alan adını girin:"
            textSize = 12f
            setTextColor(Color.parseColor("#8E95A5"))
            setPadding(0, 0, 0, dp(14))
        }
        container.addView(subtitleView)

        val input = EditText(context).apply {
            setText(currentUrl)
            setSelection(text.length)
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#6E7681"))
            hint = if (englishLabels) "https://example.com" else "https://ornekadres.com"
            setPadding(dp(14), dp(12), dp(14), dp(12))
            isSingleLine = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#E6161820"))
                setStroke(dp(1), Color.parseColor("#26FFFFFF"))
            }
        }
        container.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(10)
        })

        fun getIncrementedUrl(raw: String): String? {
            val text = raw.trim()
            if (text.isEmpty()) return null
            val match = Regex("""(\d+)""").findAll(text).lastOrNull() ?: return null
            val num = match.value.toLongOrNull() ?: return null
            return text.replaceRange(match.range, (num + 1).toString())
        }

        val btnIncrement = MaterialButton(context).apply {
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#4ADE80"))
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#142B1E"))
            strokeColor = ColorStateList.valueOf(Color.parseColor("#16A34A"))
            strokeWidth = dp(1)
            cornerRadius = dp(100)
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            setPadding(dp(12), dp(4), dp(12), dp(4))
        }

        fun updateIncrementBtn() {
            val next = getIncrementedUrl(input.text.toString())
            if (next != null) {
                btnIncrement.visibility = android.view.View.VISIBLE
                val hostOnly = runCatching { java.net.URI(next).host }.getOrNull() ?: next.substringAfter("://").substringBefore("/")
                btnIncrement.text = if (englishLabels) "⚡ +1 Domain: $hostOnly" else "⚡ +1 Adres: $hostOnly"
                btnIncrement.setOnClickListener {
                    input.setText(next)
                    input.setSelection(input.text.length)
                    updateIncrementBtn()
                }
            } else {
                btnIncrement.visibility = android.view.View.GONE
            }
        }
        updateIncrementBtn()

        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updateIncrementBtn() }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        val incrementRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(btnIncrement)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(16)
            }
        }
        container.addView(incrementRow)

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val btnCancel = MaterialButton(context).apply {
            text = if (englishLabels) "Cancel" else "İptal"
            textSize = 12.5f
            setTextColor(Color.parseColor("#8E95A5"))
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#161820"))
            strokeColor = ColorStateList.valueOf(Color.parseColor("#26FFFFFF"))
            strokeWidth = dp(1)
            cornerRadius = dp(100)
            isAllCaps = false
            setPadding(dp(16), dp(6), dp(16), dp(6))
            setOnClickListener { editDialog.dismiss() }
        }

        val btnSave = MaterialButton(context).apply {
            text = if (englishLabels) "✓ Save" else "✓ Kaydet"
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.BLACK)
            backgroundTintList = ColorStateList.valueOf(Color.WHITE)
            cornerRadius = dp(100)
            isAllCaps = false
            setPadding(dp(20), dp(6), dp(20), dp(6))
            setOnClickListener {
                var raw = input.text.toString().trim()
                if (raw.isBlank()) {
                    Toast.makeText(context, if (englishLabels) "Domain cannot be empty!" else "Alan adı boş bırakılamaz!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (!raw.startsWith("http://") && !raw.startsWith("https://")) {
                    raw = "https://$raw"
                }
                runCatching {
                    val uri = java.net.URI(raw)
                    if (uri.host.isNullOrBlank()) throw IllegalArgumentException("Geçersiz host")
                    val validOrigin = "${uri.scheme}://${uri.authority}".removeSuffix("/")
                    onSave(validOrigin)
                    Toast.makeText(context, if (englishLabels) "$providerName domain updated!" else "$providerName adresi güncellendi!", Toast.LENGTH_SHORT).show()
                    editDialog.dismiss()
                }.onFailure {
                    Toast.makeText(context, if (englishLabels) "Invalid domain format!" else "Geçersiz adres formatı!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnRow.addView(btnCancel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)).apply {
            marginEnd = dp(10)
        })
        btnRow.addView(btnSave, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)))
        container.addView(btnRow)

        editDialog.setContentView(container)
        editDialog.show()
    }

    fun show(context: Context, config: WioSettingsConfig) {
        fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

        fun focusableGlassDrawable(
            normalBg: Int = config.cardBg,
            normalBorder: Int = Color.parseColor("#26FFFFFF"),
            focusedBg: Int = Color.parseColor("#E6252936"),
            focusedBorder: Int = Color.parseColor("#80FFFFFF"),
            radiusDp: Float = 16f
        ): StateListDrawable {
            val normal = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(radiusDp.toInt()).toFloat()
                setColor(normalBg)
                setStroke(dp(1), normalBorder)
            }
            val focused = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(radiusDp.toInt()).toFloat()
                setColor(focusedBg)
                setStroke(dp(2), focusedBorder)
            }
            return StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), focused)
                addState(intArrayOf(android.R.attr.state_pressed), focused)
                addState(intArrayOf(), normal)
            }
        }

        val dialog = Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setCanceledOnTouchOutside(false)
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(24))
            background = GradientDrawable().apply {
                setColor(config.sheetBg)
                cornerRadius = dp(28).toFloat()
                setStroke(dp(1), Color.parseColor("#33FFFFFF"))
            }
        }

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(14))
        }

        val titleCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val brandTitleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val p1 = TextView(context).apply {
            text = config.brandPrefix
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(config.brandColor)
        }
        val p2 = TextView(context).apply {
            text = config.brandSuffix
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(config.textPrimary)
        }
        val p3 = TextView(context).apply {
            text = " ${config.brandTitleTag}"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(config.textSecondary)
        }
        brandTitleRow.addView(p1)
        brandTitleRow.addView(p2)
        brandTitleRow.addView(p3)
        titleCol.addView(brandTitleRow)

        val summaryText = TextView(context).apply {
            textSize = 12f
            setTextColor(config.textSecondary)
            text = config.summarySubtitle ?: "Tüm kaynak ve optimizasyonlar tek merkezde"
            setPadding(0, dp(2), 0, 0)
        }
        titleCol.addView(summaryText)
        headerRow.addView(titleCol)

        val closeBtn = MaterialButton(context).apply {
            text = if (config.englishLabels) "✕ Close" else "✕ Kapat"
            textSize = 12f
            setTextColor(Color.parseColor("#9CA3AF"))
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#16181D"))
            strokeColor = ColorStateList.valueOf(Color.parseColor("#292D38"))
            strokeWidth = dp(1)
            cornerRadius = dp(100)
            isAllCaps = false
            setPadding(dp(12), dp(4), dp(12), dp(4))
            setOnClickListener { dialog.dismiss() }
        }
        headerRow.addView(closeBtn)
        root.addView(headerRow)

        val topActionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(12)
            }
            layoutParams = lp
        }

        val btnClearCache = MaterialButton(context).apply {
            text = if (config.englishLabels) "🧹 Clear Cache" else "🧹 Önbelleği Temizle"
            textSize = 12f
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#16181D"))
            strokeColor = ColorStateList.valueOf(Color.parseColor("#292D38"))
            strokeWidth = dp(1)
            cornerRadius = dp(14)
            val lp = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                marginEnd = dp(8)
            }
            layoutParams = lp
            isAllCaps = false
            setOnClickListener {
                scope.launch {
                    val freedBytes = withContext(Dispatchers.IO) {
                        clearCache(context)
                    }
                    val mb = freedBytes / (1024.0 * 1024.0)
                    val msg = if (mb >= 0.1) {
                        if (config.englishLabels) String.format(java.util.Locale.US, "✓ %.1f MB cache cleared", mb) else String.format(java.util.Locale.US, "✓ %.1f MB önbellek temizlendi", mb)
                    } else {
                        if (config.englishLabels) "✓ Cache is already clear" else "✓ Önbellek zaten temiz"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }

        val btnSaveClose = MaterialButton(context).apply {
            text = if (config.englishLabels) "✓ Save and Close" else "✓ Kaydet ve Kapat"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.BLACK)
            backgroundTintList = ColorStateList.valueOf(config.brandColor)
            cornerRadius = dp(14)
            val lp = LinearLayout.LayoutParams(0, dp(44), 1.2f)
            layoutParams = lp
            isAllCaps = false
            setOnClickListener {
                config.onSaveAndClose?.invoke()
                dialog.dismiss()
            }
        }

        topActionRow.addView(btnClearCache)
        topActionRow.addView(btnSaveClose)
        root.addView(topActionRow)

        config.onCustomListClick?.let { onCustomList ->
            val customListBtn = MaterialButton(context).apply {
                text = config.customListSummary ?: "📋 Özel M3U / IPTV Listelerim (Ekle / Kaldır)"
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.BLACK)
                backgroundTintList = ColorStateList.valueOf(config.brandColor)
                cornerRadius = dp(100)
                isAllCaps = false
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply {
                    bottomMargin = dp(12)
                }
                layoutParams = lp
                setOnClickListener {
                    dialog.dismiss()
                    onCustomList()
                }
            }
            root.addView(customListBtn)
        }

        var scanDurationLabel: TextView? = null
        var scanDurationSeekBar: SeekBar? = null

        fun refreshScanDurationUi(tvBoxActive: Boolean) {
            if (!config.isScanDurationSupported) return
            val seconds = if (tvBoxActive) {
                config.tvBoxScanDurationSeconds
            } else {
                config.scanDurationSeconds().coerceIn(config.minScanDurationSeconds, config.maxScanDurationSeconds)
            }
            scanDurationLabel?.text = if (tvBoxActive) {
                "Kaynak arama süresi: ${seconds} sn · TV Box sabit"
            } else {
                "Kaynak arama süresi: ${seconds} sn"
            }
            scanDurationSeekBar?.apply {
                isEnabled = !tvBoxActive
                alpha = if (tvBoxActive) 0.45f else 1f
                progress = seconds - config.minScanDurationSeconds
            }
        }

        if (config.isTvBoxSupported) {
            val isTv = isAutoDetectedTvOrLowRam(context)
            val tvBoxCard = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(12), dp(16), dp(12))
                background = focusableGlassDrawable()
                isFocusable = true
                isClickable = true
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = dp(12)
                layoutParams = lp
            }

            val tvBoxInfo = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvBoxTitle = TextView(context).apply {
                text = "📺 TV Box / Düşük Bellek Modu"
                textSize = 14.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(config.textPrimary)
            }

            val tvBoxDesc = TextView(context).apply {
                textSize = 11f
                setTextColor(if (isTv) Color.parseColor("#00E676") else config.textSecondary)
                text = if (isTv) "✓ TV / Kısıtlı RAM tespit edildi · Eşzamanlı taramayı sınırlar"
                       else "Eşzamanlı kaynak taramasını sınırlandırır (Düşük RAM donmalarını önler)"
                setPadding(0, dp(3), 0, 0)
            }

            tvBoxInfo.addView(tvBoxTitle)
            tvBoxInfo.addView(tvBoxDesc)

            val tvBoxSwitch = SwitchMaterial(context).apply {
                isChecked = config.isTvBoxActive()
                thumbTintList = ColorStateList.valueOf(config.brandColor)
                trackTintList = ColorStateList.valueOf(Color.argb(80, Color.red(config.brandColor), Color.green(config.brandColor), Color.blue(config.brandColor)))
                isFocusable = false
            }

            tvBoxCard.setOnClickListener {
                tvBoxSwitch.isChecked = !tvBoxSwitch.isChecked
            }
            tvBoxSwitch.setOnCheckedChangeListener { _, isChecked ->
                config.onTvBoxToggled(isChecked)
                refreshScanDurationUi(isChecked)
            }

            tvBoxCard.addView(tvBoxInfo)
            tvBoxCard.addView(tvBoxSwitch)
            root.addView(tvBoxCard)
        }

        if (config.isScanDurationSupported) {
            val durationCard = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(10), dp(16), dp(8))
                background = focusableGlassDrawable(config.cardBgSubtle, Color.parseColor("#20FFFFFF"), radiusDp = 14f)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(10)
                }
            }

            scanDurationLabel = TextView(context).apply {
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(config.textPrimary)
            }
            durationCard.addView(scanDurationLabel)

            scanDurationSeekBar = SeekBar(context).apply {
                max = (config.maxScanDurationSeconds - config.minScanDurationSeconds).coerceAtLeast(0)
                progressTintList = ColorStateList.valueOf(config.brandColor)
                thumbTintList = ColorStateList.valueOf(config.brandColor)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (!fromUser || config.isTvBoxActive()) return
                        val seconds = config.minScanDurationSeconds + progress
                        scanDurationLabel?.text = "Kaynak arama süresi: ${seconds} sn"
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        if (config.isTvBoxActive()) return
                        val seconds = config.minScanDurationSeconds + (seekBar?.progress ?: 0)
                        config.onScanDurationChanged(seconds)
                        refreshScanDurationUi(false)
                    }
                })
            }
            durationCard.addView(scanDurationSeekBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)))
            root.addView(durationCard)
            refreshScanDurationUi(config.isTvBoxSupported && config.isTvBoxActive())
        }

        if (config.extraActionLabel != null && config.onExtraAction != null) {
            val extraAction = MaterialButton(context).apply {
                text = config.extraActionLabel
                textSize = 12f
                setTextColor(Color.WHITE)
                backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1B222A"))
                strokeColor = ColorStateList.valueOf(config.brandColorBorder)
                strokeWidth = dp(1)
                cornerRadius = dp(10)
                isAllCaps = false
                setOnClickListener { config.onExtraAction.invoke() }
            }
            root.addView(extraAction, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)).apply {
                bottomMargin = dp(10)
            })
        }

        if (config.onWizardClick != null) {
            val wizardBtn = MaterialButton(context).apply {
                text = if (config.englishLabels) "🪄 Setup Wizard & Mode Selection" else "🪄 Mod Seçimi & Kurulum Sihirbazı"
                textSize = 12f
                setTextColor(Color.WHITE)
                backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1B222A"))
                strokeColor = ColorStateList.valueOf(config.brandColorBorder)
                strokeWidth = dp(1)
                cornerRadius = dp(10)
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40))
                lp.bottomMargin = dp(10)
                layoutParams = lp
                isAllCaps = false
                setOnClickListener {
                    dialog.dismiss()
                    config.onWizardClick.invoke()
                }
            }
            root.addView(wizardBtn)
        }

        if (config.onCustomListClick != null) {
            val customListCard = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(12), dp(16), dp(12))
                background = focusableGlassDrawable()
                isFocusable = true
                isClickable = true
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(10)
                }
                layoutParams = lp
                setOnClickListener {
                    config.onCustomListClick.invoke()
                }
            }

            val iconText = TextView(context).apply {
                text = "📋"
                textSize = 18f
                setPadding(0, 0, dp(12), 0)
            }
            customListCard.addView(iconText)

            val textCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val titleView = TextView(context).apply {
                text = "Özel M3U / IPTV Listelerim"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(config.textPrimary)
            }
            val descView = TextView(context).apply {
                text = config.customListSummary ?: "M3U listesi ekle, yönet veya kaldır"
                textSize = 11f
                setTextColor(config.brandColor)
                setPadding(0, dp(2), 0, 0)
            }
            textCol.addView(titleView)
            textCol.addView(descView)
            customListCard.addView(textCol)

            val arrowView = TextView(context).apply {
                text = "➔"
                textSize = 14f
                setTextColor(config.textSecondary)
            }
            customListCard.addView(arrowView)

            root.addView(customListCard)
        }

        val switchMap = mutableMapOf<String, SwitchMaterial>()
        var isBulkUpdating = false
        var updateCounterText: () -> Unit = {}

        val presetButtons = mutableMapOf<String, MaterialButton>()

        fun refreshPresetButtons() {
            val activeIds = config.items.filter { it.isEnabled }.mapTo(linkedSetOf()) { it.id }
            config.selectionPresets.forEach { preset ->
                presetButtons[preset.id]?.text = if (activeIds == preset.providerIds) {
                    "✓ ${preset.title} (${preset.providerIds.size})"
                } else {
                    "${preset.title} (${preset.providerIds.size})"
                }
            }
        }

        if (config.selectionPresets.isNotEmpty()) {
            config.selectionTitle?.let { title ->
                val presetTitle = TextView(context).apply {
                    text = title
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(config.brandColor)
                    setPadding(dp(2), dp(4), dp(2), dp(6))
                }
                root.addView(presetTitle)
            }

            val compactPresetRow = if (config.compactSelectionPresets) {
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = dp(10)
                    }
                }
            } else null

            config.selectionPresets.forEachIndexed { index, preset ->
                val presetButton = MaterialButton(context).apply {
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1B222A"))
                    strokeColor = ColorStateList.valueOf(config.brandColorBorder)
                    strokeWidth = dp(1)
                    cornerRadius = dp(12)
                    isAllCaps = false
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    contentDescription = "${preset.title}. ${preset.description}"
                    setOnClickListener {
                        isBulkUpdating = true
                        try {
                            config.items.forEach { item ->
                                item.isEnabled = item.id in preset.providerIds
                            }
                            switchMap.forEach { (id, sw) -> sw.isChecked = id in preset.providerIds }
                            config.onSelectionPreset?.invoke(preset.id, preset.providerIds)
                            updateCounterText()
                            refreshPresetButtons()
                        } finally {
                            isBulkUpdating = false
                        }
                    }
                }
                presetButtons[preset.id] = presetButton
                if (compactPresetRow != null) {
                    compactPresetRow.addView(presetButton, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                        if (index < config.selectionPresets.lastIndex) marginEnd = dp(8)
                    })
                } else {
                    root.addView(presetButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply {
                        bottomMargin = dp(6)
                    })
                    root.addView(TextView(context).apply {
                        text = preset.description
                        textSize = 10.5f
                        setTextColor(config.textSecondary)
                        setPadding(dp(8), 0, dp(8), dp(6))
                    })
                }
            }
            compactPresetRow?.let { root.addView(it) }
        }

        val providerControlsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val providersHeaderRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(4), dp(2), dp(6))
        }

        val providersHeader = TextView(context).apply {
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(config.brandColor)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        
        updateCounterText = {
            val enabledCount = config.items.count { it.isEnabled }
                providersHeader.text = if (config.englishLabels) "Providers ($enabledCount/${config.items.size})" else "Sağlayıcılar ($enabledCount/${config.items.size})"
        }
        updateCounterText()
        providersHeaderRow.addView(providersHeader)
        providerControlsContainer.addView(providersHeaderRow)

        if (config.onSelectAll != null || config.onSelectRecommended != null || config.onSelectNone != null) {
            val batchRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = dp(10)
                layoutParams = lp
            }

            config.onSelectAll?.let { onAll ->
                val btnAll = MaterialButton(context).apply {
                    text = if (config.englishLabels) "Enable All" else "Tümünü Aç"
                    textSize = 11f
                    setTextColor(Color.WHITE)
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor("#161820"))
                    strokeColor = ColorStateList.valueOf(Color.parseColor("#26FFFFFF"))
                    strokeWidth = dp(1)
                    cornerRadius = dp(100)
                    val lp = LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(6) }
                    layoutParams = lp
                    isAllCaps = false
                    setOnClickListener {
                        isBulkUpdating = true
                        try {
                            onAll()
                            config.items.forEach { it.isEnabled = true }
                            switchMap.values.forEach { it.isChecked = true }
                            updateCounterText()
                            refreshPresetButtons()
                        } finally {
                            isBulkUpdating = false
                        }
                    }
                }
                batchRow.addView(btnAll)
            }

            config.onSelectRecommended?.let { onRec ->
                val btnRec = MaterialButton(context).apply {
                    text = if (config.englishLabels) "Recommended" else "Önerilenler"
                    textSize = 11f
                    setTextColor(Color.WHITE)
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor("#161820"))
                    strokeColor = ColorStateList.valueOf(Color.parseColor("#26FFFFFF"))
                    strokeWidth = dp(1)
                    cornerRadius = dp(100)
                    val lp = LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(6) }
                    layoutParams = lp
                    isAllCaps = false
                    setOnClickListener {
                        isBulkUpdating = true
                        try {
                            onRec()
                            switchMap.forEach { (id, sw) ->
                                val item = config.items.find { it.id == id }
                                sw.isChecked = item?.isEnabled ?: false
                            }
                            updateCounterText()
                            refreshPresetButtons()
                        } finally {
                            isBulkUpdating = false
                        }
                    }
                }
                batchRow.addView(btnRec)
            }

            config.onSelectNone?.let { onNone ->
                val btnNone = MaterialButton(context).apply {
                    text = if (config.englishLabels) "Disable All" else "Tümünü Kapat"
                    textSize = 11f
                    setTextColor(config.textSecondary)
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor("#161820"))
                    strokeColor = ColorStateList.valueOf(Color.parseColor("#26FFFFFF"))
                    strokeWidth = dp(1)
                    cornerRadius = dp(100)
                    val lp = LinearLayout.LayoutParams(0, dp(36), 1f)
                    layoutParams = lp
                    isAllCaps = false
                    setOnClickListener {
                        isBulkUpdating = true
                        try {
                            onNone()
                            config.items.forEach { it.isEnabled = false }
                            switchMap.values.forEach { it.isChecked = false }
                            updateCounterText()
                            refreshPresetButtons()
                        } finally {
                            isBulkUpdating = false
                        }
                    }
                }
                batchRow.addView(btnNone)
            }

            providerControlsContainer.addView(batchRow)
        }

        val isWide = context.resources.displayMetrics.widthPixels > dp(580)
        val columnsContainer = LinearLayout(context).apply {
            orientation = if (isWide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        }

        val col1 = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            if (isWide) {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(8)
                }
            }
        }
        val col2 = if (isWide) {
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
        } else null

        columnsContainer.addView(col1)
        col2?.let { columnsContainer.addView(it) }

        config.items.forEachIndexed { idx, item ->
            val targetCol = if (isWide && idx % 2 != 0) col2 ?: col1 else col1

            val card = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = focusableGlassDrawable(config.cardBgSubtle, Color.argb(40, Color.red(config.brandColor), Color.green(config.brandColor), Color.blue(config.brandColor)), radiusDp = 12f)
                isFocusable = true
                isClickable = true
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(6)
                }
                layoutParams = lp
            }

            val textCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val nameView = TextView(context).apply {
                text = buildString {
                    append(item.displayName)
                    if (item.isForeign) append("  🌐")
                    item.capabilityBadges?.takeIf { it.isNotBlank() }?.let { append("  ").append(it) }
                }
                textSize = 13.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (item.isForeign) config.textSecondary else config.textPrimary)
            }
            textCol.addView(nameView)

            val domainText = item.domain
            val domainView = if (domainText != null) {
                TextView(context).apply {
                    text = "$domainText ✎"
                    textSize = 11f
                    setTextColor(config.textSecondary)
                    setPadding(0, dp(2), 0, 0)
                }
            } else null
            domainView?.let { textCol.addView(it) }

            card.addView(textCol)

            if (item.onDomainEdit != null) {
                val editBtn = TextView(context).apply {
                    text = if (config.englishLabels) "Edit ✎" else "Düzenle ✎"
                    textSize = 11f
                    setTextColor(config.brandColor)
                    setPadding(dp(8), dp(6), dp(8), dp(6))
                    isClickable = true
                    setOnClickListener {
                        showEditDomainDialog(
                            context = context,
                            providerName = item.displayName,
                            currentUrl = item.domain ?: "",
                            englishLabels = config.englishLabels,
                            brandColor = config.brandColor,
                            brandColorBorder = config.brandColorBorder,
                            onSave = { newUrl ->
                                item.domain = newUrl
                                domainView?.text = "$newUrl ✎"
                                item.onDomainEdit.invoke(newUrl)
                            }
                        )
                    }
                }
                card.addView(editBtn)
            }

            val sw = SwitchMaterial(context).apply {
                isChecked = item.isEnabled
                thumbTintList = ColorStateList.valueOf(config.brandColor)
                trackTintList = ColorStateList.valueOf(Color.argb(80, Color.red(config.brandColor), Color.green(config.brandColor), Color.blue(config.brandColor)))
                isFocusable = false
                isClickable = true
            }
            switchMap[item.id] = sw

            card.setOnClickListener {
                sw.toggle()
            }

            sw.setOnCheckedChangeListener { _, isChecked ->
                if (isBulkUpdating) return@setOnCheckedChangeListener
                item.isEnabled = isChecked
                item.onToggle(isChecked)
                updateCounterText()
                refreshPresetButtons()
            }

            card.addView(sw)
            targetCol.addView(card)
        }

        providerControlsContainer.addView(columnsContainer)

        if (config.providersCollapsible) {
            providerControlsContainer.visibility = android.view.View.GONE
            val providerToggle = MaterialButton(context).apply {
                text = if (config.englishLabels) "⚙ Choose Providers" else "⚙ Sağlayıcıları Kendin Seç"
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(config.textPrimary)
                backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1B222A"))
                strokeColor = ColorStateList.valueOf(config.brandColorBorder)
                strokeWidth = dp(1)
                cornerRadius = dp(12)
                isAllCaps = false
                setOnClickListener {
                    val willOpen = providerControlsContainer.visibility != android.view.View.VISIBLE
                    providerControlsContainer.visibility = if (willOpen) android.view.View.VISIBLE else android.view.View.GONE
                    text = if (willOpen) {
                        if (config.englishLabels) "▲ Hide Provider Panel" else "▲ Sağlayıcı Panelini Kapat"
                    } else {
                        if (config.englishLabels) "⚙ Choose Providers" else "⚙ Sağlayıcıları Kendin Seç"
                    }
                }
            }
            root.addView(providerToggle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply {
                bottomMargin = dp(8)
            })
        }
        root.addView(providerControlsContainer)

        if (config.onAutoScanDomains != null) {
            val scanSection = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(6), 0, dp(6))
            }

            val scanStatus = TextView(context).apply {
                textSize = 11.5f
                setTextColor(config.textSecondary)
                text = "Alan adlarını test edip en güncel adreslere geçmek için tarayın."
                setPadding(0, 0, 0, dp(6))
            }
            scanSection.addView(scanStatus)

            val scanProgress = ProgressBar(context).apply {
                isIndeterminate = true
                visibility = android.view.View.GONE
            }
            scanSection.addView(scanProgress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)).apply {
                bottomMargin = dp(6)
            })

            val scanBtn = MaterialButton(context).apply {
                text = "⚡ Otomatik Tara ve Güncelle"
                textSize = 12.5f
                setTextColor(Color.WHITE)
                backgroundTintList = ColorStateList.valueOf(Color.parseColor("#161820"))
                strokeColor = ColorStateList.valueOf(Color.parseColor("#33FFFFFF"))
                strokeWidth = dp(1)
                cornerRadius = dp(100)
                isAllCaps = false
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply {
                    bottomMargin = dp(10)
                }
                layoutParams = lp
            }

            scanBtn.setOnClickListener {
                scanBtn.isEnabled = false
                scanProgress.visibility = android.view.View.VISIBLE
                scanStatus.text = "Alan adları kontrol ediliyor..."
                scope.launch {
                    val res = withContext(Dispatchers.IO) {
                        runCatching {
                            config.onAutoScanDomains.invoke { msg ->
                                scanStatus.text = msg
                            }
                        }.getOrDefault(0 to 0)
                    }
                    scanProgress.visibility = android.view.View.GONE
                    scanBtn.isEnabled = true
                    scanStatus.text = "✓ ${res.first} kaynak erişilebilir · ${res.second} adres güncellendi"
                    Toast.makeText(context, "Tarama tamamlandı!", Toast.LENGTH_SHORT).show()
                }
            }

            scanSection.addView(scanBtn)
            root.addView(scanSection)
        }


        if (config.telegramUrl != null || config.coffeeUrl != null) {
            val linkRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                layoutParams = lp
            }

            config.telegramUrl?.let { tg ->
                val tgBtn = MaterialButton(context).apply {
                    text = "Telegram ↗"
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.parseColor("#2AABEE"))
                    cornerRadius = dp(100)
                    val lp = LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(8) }
                    layoutParams = lp
                    isAllCaps = false
                    setOnClickListener {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                }
                linkRow.addView(tgBtn)
            }

            config.coffeeUrl?.let { cf ->
                val coffeeBtn = MaterialButton(context).apply {
                    text = "Destek ol ☕"
                    setTextColor(Color.BLACK)
                    setBackgroundColor(Color.parseColor("#FFDD00"))
                    cornerRadius = dp(100)
                    val lp = LinearLayout.LayoutParams(0, dp(42), 1f)
                    layoutParams = lp
                    isAllCaps = false
                    setOnClickListener {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(cf)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                }
                linkRow.addView(coffeeBtn)
            }

            root.addView(linkRow)
        }

        val scroll = NestedScrollView(context).apply {
            addView(root)
            isVerticalScrollBarEnabled = false
        }

        dialog.setContentView(scroll)
        dialog.setOnDismissListener { scope.cancel() }
        dialog.setOnShowListener {
            val metrics = context.resources.displayMetrics
            dialog.window?.setLayout(
                (metrics.widthPixels * 0.94f).toInt().coerceAtMost(dp(980)),
                (metrics.heightPixels * 0.90f).toInt()
            )
            refreshPresetButtons()
        }
        dialog.show()
    }
}
