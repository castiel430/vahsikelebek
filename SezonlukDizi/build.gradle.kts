version = 4

cloudstream {
    authors     = listOf("NeO", "Blockades", "vahsikelebek")
    language    = "tr"
    description = "SezonlukDizi (sezonlukdizi.cc) - altyazılı ve dublajlı yabancı diziler. Not: sitenin bazı kaynakları Cloudflare doğrulaması istediği için captcha'sız kaynaklar (VideoMoly/Filemoon/Streamruby) kullanılır."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 2 // bazı kaynaklar captcha arkasında
    tvTypes = listOf("TvSeries")
    iconUrl = "https://www.google.com/s2/favicons?domain=sezonlukdizi.cc&sz=%size%"
}
