package com.vahsikelebek

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class VahsiKelebekPlugin : Plugin() {
    override fun load(context: Context) {
        // Sağlayıcıyı kaydet
        registerMainAPI(VahsiKelebekProvider())
    }
}
