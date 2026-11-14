package dev.wiojelt.turksinema.common

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.util.TypedValue
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.plugins.Plugin
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URI

object DomainUpdateUi {
    private const val DIRECTORY = "https://raw.githubusercontent.com/Wiojelt/TurkSinema/main/domains.json"

    fun attach(plugin: Plugin, context: Context, providers: List<MainAPI>) {
        val prefs = context.getSharedPreferences("turksinema_domains", Context.MODE_PRIVATE)
        providers.forEach { provider ->
            prefs.getString(provider.name, null)?.takeIf(::validOrigin)?.let { provider.mainUrl = it }
        }
        plugin.openSettings = { ui -> show(ui, providers) }
    }

    private fun validOrigin(value: String): Boolean = runCatching {
        val uri = URI(value.trim())
        (uri.scheme == "https" || uri.scheme == "http") && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

    private fun origin(value: String): String? = runCatching {
        val uri = URI(value.trim())
        "${uri.scheme}://${uri.authority}".removeSuffix("/")
    }.getOrNull()?.takeIf(::validOrigin)

    fun show(
        context: Context,
        providers: List<MainAPI>,
        brandPrefix: String? = null,
        brandSuffix: String? = null,
        brandTitleTag: String? = null
    ) {
        val prefs = context.getSharedPreferences("turksinema_domains", Context.MODE_PRIVATE)
        val disabledPrefs = context.getSharedPreferences("turksinema_disabled_providers", Context.MODE_PRIVATE)

        val items = providers.map { provider ->
            WioProviderSettingItem(
                id = provider.name,
                displayName = provider.name,
                domain = provider.mainUrl,
                isEnabled = !disabledPrefs.getBoolean(provider.name, false),
                onToggle = { isChecked ->
                    disabledPrefs.edit().putBoolean(provider.name, !isChecked).apply()
                    WioCoreSettingsDialog.clearCache(context)
                },
                onDomainEdit = { newDomain ->
                    provider.mainUrl = newDomain
                    prefs.edit().putString(provider.name, newDomain).apply()
                }
            )
        }

        val isSingle = providers.size == 1
        val pName = providers.firstOrNull()?.name ?: "TurkSinema"

        val config = WioSettingsConfig(
            brandPrefix = brandPrefix ?: if (isSingle) pName else "TURK",
            brandSuffix = brandSuffix ?: if (isSingle) "" else "SİNEMA",
            brandTitleTag = brandTitleTag ?: if (isSingle) "Alan Adı & Ayarlar" else "Alan Adı Yönetimi",
            summarySubtitle = if (isSingle) "Adres değiştiğinde 'Düzenle ✎' ile güncelleyebilir veya otomatik tarama yapabilirsiniz." else "Tüm kaynaklar tek merkezde · Adres değiştiğinde 'Düzenle ✎' ile güncelleyin",
            isTvBoxSupported = false,
            items = items,
            onSelectAll = if (providers.size > 1) {
                {
                    providers.forEach { disabledPrefs.edit().putBoolean(it.name, false).apply() }
                    items.forEach { it.isEnabled = true }
                    WioCoreSettingsDialog.clearCache(context)
                }
            } else null,
            onSelectNone = if (providers.size > 1) {
                {
                    providers.forEach { disabledPrefs.edit().putBoolean(it.name, true).apply() }
                    items.forEach { it.isEnabled = false }
                    WioCoreSettingsDialog.clearCache(context)
                }
            } else null,
            onAutoScanDomains = { statusCallback ->
                update(context, providers)
            },
            onSaveAndClose = {
                val freed = WioCoreSettingsDialog.clearCache(context)
                val mb = freed / (1024.0 * 1024.0)
                val msg = if (mb >= 0.1) {
                    String.format(java.util.Locale.US, "✓ Ayarlar kaydedildi · %.1f MB önbellek temizlendi", mb)
                } else {
                    "✓ Ayarlar kaydedildi · Önbellek temizlendi"
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            },
            telegramUrl = "https://t.me/wiolandcs3",
            coffeeUrl = "https://kreosus.com/wiojelt"
        )

        WioCoreSettingsDialog.show(context, config)
    }

    private fun showEditDialog(context: Context, provider: MainAPI, onSave: (String) -> Unit) {
        WioCoreSettingsDialog.showEditDomainDialog(
            context = context,
            providerName = provider.name,
            currentUrl = provider.mainUrl,
            onSave = onSave
        )
    }

    private suspend fun update(context: Context, providers: List<MainAPI>): Pair<Int, Int> {
        val prefs = context.getSharedPreferences("turksinema_domains", Context.MODE_PRIVATE)
        val directory = runCatching { JSONObject(app.get(DIRECTORY, timeout = 10).text) }.getOrNull()
        var reachable = 0
        var changed = 0

        val tlds = listOf("to", "org", "net", "com", "co", "me", "cc", "tv", "is", "one", "top")

        providers.forEach { provider ->
            val host = runCatching { URI(provider.mainUrl).host ?: "" }.getOrDefault("")
            val baseName = host.substringBeforeLast('.').removePrefix("www.")

            val dynamicCandidates = if (baseName.isNotBlank()) {
                val baseCandidates = tlds.map { "https://$baseName.$it" }
                val digitsMatch = Regex("""(\d+)""").findAll(baseName).lastOrNull()
                val numericCandidates = if (digitsMatch != null) {
                    val num = digitsMatch.value.toLongOrNull()
                    if (num != null) {
                        (1..5).flatMap { step ->
                            val incrementedBase = baseName.replaceRange(digitsMatch.range, (num + step).toString())
                            tlds.map { "https://$incrementedBase.$it" }
                        }
                    } else emptyList()
                } else emptyList()
                baseCandidates + numericCandidates
            } else emptyList()

            val candidates = buildList {
                directory?.optJSONArray(provider.name)?.let { array -> for (i in 0 until array.length()) add(array.optString(i)) }
                add(provider.mainUrl)
                addAll(dynamicCandidates)
            }.mapNotNull(::origin).distinct()

            for (candidate in candidates) {
                val response = runCatching { app.get(candidate, timeout = 5) }.getOrNull() ?: continue
                if (response.code in 200..399) {
                    val resolved = origin(response.url) ?: candidate
                    reachable++
                    if (provider.mainUrl != resolved) {
                        provider.mainUrl = resolved
                        changed++
                    }
                    prefs.edit().putString(provider.name, resolved).apply()
                    break
                }
            }
        }
        return reachable to changed
    }
}
