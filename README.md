# Vahşi Kelebek — CloudStream Türkçe Eklenti Deposu

[![Build](https://github.com/castiel430/vahsikelebek/actions/workflows/build.yml/badge.svg)](https://github.com/castiel430/vahsikelebek/actions/workflows/build.yml)

Bu depo, **CloudStream** için hazırlanmış Türkçe film, dizi, anime ve belgesel
senkron/sağlayıcı eklentilerini içerir. Derlenmiş eklentiler (`.cs3`) ve `plugins.json`,
GitHub Actions tarafından [`builds`](https://github.com/castiel430/vahsikelebek/tree/builds)
dalında yayımlanır; depo `main` dalındaki [`repo.json`](repo.json) herkese açıktır.

## Depoyu CloudStream'e ekleme

CloudStream uygulamasında: **Ayarlar → Eklentiler → Depolar → Depo ekle** ve şu adresi girin:

```text
https://raw.githubusercontent.com/castiel430/vahsikelebek/main/repo.json
```

> Not: CloudStream, `repo.json` → `plugins.json` → `.cs3` dosyalarını **anonim olarak**
> `raw.githubusercontent.com` üzerinden indirir. Bu deponun **public** olması bu yüzden
> gereklidir (bkz. [Gizlilik](#gizlilik-depoyu-private-yapmak)).

## Depo yapısı

- `repo.json` — CloudStream depo manifesti (CloudStream bu dosyayı okur)
- `settings.gradle.kts`, `build.gradle.kts`, `gradle/` — çoklu eklenti (multi-module) Gradle projesi
- `*/` — her eklenti için ayrı modül (`build.gradle.kts` + `src/main/...`); `build.gradle.kts` içeren her üst dizin otomatik dahil edilir
- `.github/workflows/build.yml` — push sonrası `.cs3` ve `plugins.json` üretip `builds` dalına gönderir
- `scripts/` — domain kontrol aracı (`check_domains.py`, `domains.json`)

## Eklenti listesi

Her eklentinin kaynak kodu kendi modülünde bulunur. Tam liste için
[`builds/plugins.json`](https://raw.githubusercontent.com/castiel430/vahsikelebek/builds/plugins.json).

### Sağlayıcı modülleri (66)

| Modül | Hedef site |
|---|---|
| AnimeciX | animecix.tv |
| ATV | www.atv.com.tr |
| BelgeselX | belgeselx.com |
| CanliDizi | canlidizi.sbs |
| CanliTV | iptv-org.github.io |
| CizgiFilmIzleInfo | www.cizgifilmizle.info |
| CizgiFilmTv | www.cizgifilm.tv |
| CizgiMax | cizgimax.online |
| CizgiSite | www.cizgi.site |
| CizgiVeDizi | cizgivedizi.net |
| Ddizi | www.ddizi.tel |
| DiziBal | dizibal.org |
| DiziBol | dizibol.org |
| DiziBox | www.dizibox.live |
| Dizigom | www.dizigom.news |
| DiziKorea | dizikorea3.com |
| DiziLife | dizi85.life |
| Dizilla | dizilla.la |
| DiziMom | ddizimom.net |
| DiziPal | dizipal2145.com |
| DiziPalGuncel | dizipal1593.com |
| DiziPalOrjinal | dizipalorjinal18.com |
| DiziPod | dizipod.com |
| DiziYou | www.diziyou.one |
| DMAX | www.dmax.com.tr |
| FilmMakinesi | filmmakinesi.to |
| FilmModu | filmmodu.news |
| FullHDFilm | fullhdfilmizlebox.vip |
| FullHDFilmizlesene | www.fullhdfilmizlesene.now |
| HDFilmCehennemi | www.hdfilmcehennemi.nl |
| HDFilmCehennemi2 | www.hdfilmcehennemi2.biz |
| HDFilmDelisi | hdfilmdelisi1.com |
| HDFilmDiziIzle | www.hdfilmdiziizle.com |
| InatBox | - |
| JetFilmizle | jetfilmizle.app |
| KanalD | www.kanald.com.tr |
| KoreanTurk | koreanturk.tv |
| Kultfilmler | kultfilmler.net |
| LoveFilm | lovefilmizle.net |
| MinikaCocuk | www.minikacocuk.com.tr |
| MinikaGO | www.minikago.com.tr |
| NetflixMirror | iosmirror.cc |
| PuhuTV | puhutv.com |
| RareFilmm | ok.ru |
| RecTV | a.psrectv80.xyz |
| SelcukFlix | selcukflix.app |
| SetFilmIzle | www.setfilmizle.ltd |
| SezonlukDizi | sezonlukdizi.cc |
| ShowTV | www.showtv.com.tr |
| SinemaCX | sinemacc.com |
| Sinezy | sinezy.si |
| StarTV | www.startv.com.tr |
| SuperFilmGeldi | mixplayhd.com |
| TLCtr | www.tlctv.com.tr |
| TurkAnime | turkanime.live |
| TurkAnimeTV | turkanime.live |
| TV8 | www.tv8.com.tr |
| UgurFilm | ugurfilm8.com |
| UltraFilmizle | ufilmizle.com |
| Watch2Movies | watch2movies.net |
| WebDramaTurkey | webdramaturkey2.com |
| WebdramaTurkey2 | webdramaturkey2.com |
| WebteIzle | webteizle.info |
| Yabancidizi | ybncidizi.com |
| YesilCamTv | yesilcamtv.com.tr |
| YouTube | www.youtube.com |

### Eşzamanlama / senkron eklentisi

- **TraktSync** — kütüphanenizi Trakt.tv ile eşitler; bkz. [TraktSync (Trakt.tv)](#traktsync-trakt-tv-eklentisi)

> Kullanılmayan iskelet modüller (eski `VahsiKelebek`, `VahsiFilm`, `VahsiDizi`,
> `VahsiAnime`, `VahsiBelgesel`) silinmiştir; depoda yalnızca gerçek site sağlayıcıları
> ve TraktSync bulunur.

> **SezonlukDizi notu:** Site, kaynaklarının çoğunu Cloudflare Turnstile (captcha)
> arkasına aldı. Eklenti captcha istemeyen kaynakları (Streamruby/Filemoon/VideoMoly)
> kullanır; captcha'lı kaynaklar otomatik atlanır.

> Not: Hazır modüllerin bir kısmı açık kaynak eklenti depolarındaki kodlardan
> alınmış/uyalanmıştır; site yapıları ve alan adları zamanla değişebilir.
> Alan adları değişirse `scripts/check_domains.py` ile kontrol edip modülü güncelleyin.

## TraktSync (Trakt.tv) eklentisi

CloudStream kütüphanenizi Trakt.tv hesabınızla eşitler; yalnızca CloudStream ve
Trakt.tv API'si kullanılır — başka kodlardan kopyalanmamıştır.

**Özellikler:**

- Oynat menüsüne **"✓ Trakt'a İzledim Olarak İşaretle"** düğmesi eklenir — dokunduğunuz
  içerik (film: tamamı, dizi: ilgili sezon/bölüm) Trakt izleme geçmişinize yazılır.
- Kütüphane işaretleri değiştikçe ~60 saniyede bir otomatik eşitlenir:
  - `COMPLETED` → Trakt izleme geçmişine (`sync/history`) yazılır
    (dizide son bilinen sezon/bölüm)
  - `WATCHING` / `ONHOLD` / `PLANTOWATCH` → Trakt izleme listesine (`sync/watchlist`) eklenir
- **Ayarlar → Eklenti ayarları → Trakt Senkron** düğmesiyle Client ID/Secret girip
  cihaz-kodu (OAuth) akışıyla bağlanın.

**Kurulum:**

1. https://trakt.tv/settings/api → **New Application** oluşturun
   (Name: ör. `CloudStream`, Redirect URI: `urn:ietf:wg:oauth:2.0:oob`).
2. CloudStream → **Ayarlar → Eklenti ayarları → Trakt Senkron**.
3. Uygulamadaki **Client ID** ve **Client Secret** değerlerini girip **Trakt'a Bağlan**'a basın.
4. Açılan tarayıcıda `trakt.tv/activate` adresine gidip gösterilen kodu girin.
5. Onay sonrası otomatik bağlanır; eşitleme hemen başlar.

> Her kullanıcı kendi Trakt API uygulamasını oluşturmalıdır; Client Secret'ınız
> yalnızca kendi cihazınızda `vahsi_trakt_sync` SharedPreferences dosyasında saklanır.
> IMDb/TMDb kimliği `syncData` üzerinden okunur (çoğu sağlayıcımız bunu doldurur);
> kimliği olmayan dizi içerikler sessizce atlanır. Film için başlıkla arama denenir.

## Derleme ve yayınlama (GitHub Actions)

1. `main` dalına yaptığınız değişiklikleri push edin.
2. **Actions** sekmesinde "Build" iş akışının başarıyla tamamlandığını kontrol edin.
3. İş akışı `builds` dalına `*.cs3` ve `plugins.json` dosyalarını yazar.
4. CloudStream, `repo.json` üzerinden bu listeyi kullanarak eklentiyi sunar.

Yerelde derlemek isterseniz JDK 17 ve Android SDK kurulu bir ortamda:

```bash
./gradlew make makePluginsJson
```

## Yeni eklenti modülü ekleme

Yeni bir klasör (ör. `YeniEklenti/`) oluşturup içine `build.gradle.kts` ve `src/main/...`
kaynaklarını ekleyin. `settings.gradle.kts` üst dizindeki modülleri otomatik algılar;
Actions derlemesi yeni modülü de paketler.

## Domain değişikliklerini kontrol etme (Python)

```bash
python scripts/check_domains.py
python scripts/check_domains.py --file scripts/domains.json
python scripts/check_domains.py --json
```

- İzlenen siteler: `scripts/domains.json`
- Script, verilen URL'lere istek atar, yönlendirmeleri takip eder ve olası alan adı
  değişikliklerini raporlar.

## Gizlilik: depoyu private yapmak?

Kısa cevap: **Bu repo private yapılırsa eklentiler kullanıcılara ulaşmaz.**

CloudStream, `repo.json` ve `plugins.json`/`.cs3` dosyalarını **anonim HTTPS** ile
`raw.githubusercontent.com` üzerinden çeker. Repo private olunca bu adresler 404
döndürür (GitHub token'ı olmadan raw erişim yok), dolayısıyla:

- Depo CloudStream'de hiç eklenemez / güncellenemez,
- Depodan önceden kurulmuş eklentiler yerelde çalışmaya devam eder ancak
  güncellemeler gelmez,
- Yeni eklentiler hiç görünmez.

CloudStream'in depo URL'leri için kimlik doğrulama (token/header) desteği yoktur.

**"Sadece bize özel kalsın ama eklentiler kullanılsın" isterseniz:**

1. `main` repo private kalır (kaynak kod size özel),
2. Yayınlama için ayrı, **public** bir dağıtım reposu açın (ör. `vahsikelebek-builds`) —
   Actions, derlenmiş `.cs3` ve `plugins.json`'u oraya push eder,
3. `repo.json` içindeki `pluginLists` bu public repoyu işaret eder.

Böylece kaynak kod gizli kalır, ama eklentiler herkes tarafından kurulabilir. Bunu
istiyorsanız söyleyin; iş akışını 5 dakikada ayırabilirim.

## Kaynak/Referans

Yapı, [CloudStream eklenti sistemi](https://github.com/recloudstream/cloudstream) ve
yaygın CloudStream depo şablonları incelenerek bu projeye özgü şekilde hazırlanmıştır.
Kaynak siteler üzerindeki tüm haklar ilgili sahiplerine aittir.
