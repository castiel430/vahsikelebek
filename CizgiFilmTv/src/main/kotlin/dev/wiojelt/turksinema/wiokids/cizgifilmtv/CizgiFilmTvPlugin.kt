package dev.wiojelt.turksinema.wiokids.cizgifilmtv

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class CizgiFilmTvPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(CizgiFilmTvProvider())
    }
}
