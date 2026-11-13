package com.vahsidizi

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class VahsiDiziPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(VahsiDiziProvider())
    }
}
