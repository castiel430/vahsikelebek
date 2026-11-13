package com.vahsifilm

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class VahsiFilmPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(VahsiFilmProvider())
    }
}
