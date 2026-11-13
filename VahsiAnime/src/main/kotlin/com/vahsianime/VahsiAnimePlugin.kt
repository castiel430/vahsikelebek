package com.vahsianime

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class VahsiAnimePlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(VahsiAnimeProvider())
    }
}
