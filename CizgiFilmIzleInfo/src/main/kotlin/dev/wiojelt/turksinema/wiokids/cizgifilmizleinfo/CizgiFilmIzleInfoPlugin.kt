package dev.wiojelt.turksinema.wiokids.cizgifilmizleinfo

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class CizgiFilmIzleInfoPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(CizgiFilmIzleInfoProvider())
    }
}
