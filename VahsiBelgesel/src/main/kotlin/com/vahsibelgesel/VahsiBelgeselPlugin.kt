package com.vahsibelgesel

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class VahsiBelgeselPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(VahsiBelgeselProvider())
    }
}
