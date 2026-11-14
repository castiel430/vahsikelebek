version = 1

cloudstream {
    authors     = listOf("vahsikelebek")
    language    = "en"
    description = "Trakt TV integration - Trakt.tv izleme takibi ve sync"

    status  = 1
    tvTypes = listOf("Movie", "TvSeries")
    iconUrl = "https://trakt.tv/apple-touch-icon.png"
}

android {
    sourceSets["main"].java.srcDir(rootProject.file("Traktor/src/main/kotlin"))
}