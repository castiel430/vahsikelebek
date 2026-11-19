# Vahşi Kelebek — CloudStream Türkçe Eklenti Deposu

Bu depo, **CloudStream** için hazırlanmış Türkçe film, dizi, anime ve belgesel
senkron/sağlayıcı eklentilerinin **kaynak kodunu** içerir. Kaynak repo **private**'tır;
derlenmiş eklentiler (`.cs3`), `plugins.json` ve `repo.json` ise public
[`vahsikelebek-builds`](https://github.com/castiel430/vahsikelebek-builds) reposunda
yayımlanır (GitHub Actions ile her push sonrası güncellenir).

## Depoyu CloudStream'e ekleme

CloudStream uygulamasında: **Ayarlar → Eklentiler → Depolar → Depo ekle** ve şu adresi girin:

```text
https://raw.githubusercontent.com/castiel430/vahsikelebek-builds/main/repo.json
```

> Not: CloudStream, `repo.json` → `plugins.json` → `.cs3` dosyalarını **anonim olarak**
> public `vahsikelebek-builds` reposundan indirir. Bu `repo.json`'un
> `pluginLists` alanı da o repo'daki `plugins.json`'u işaret eder.

## Depo yapısı

- `repo.json` — CloudStream depo manifesti (dağıtım reposuna Actions tarafından kopyalanır)
- `settings.gradle.kts`, `build.gradle.kts`, `gradle/` — çoklu eklenti (multi-module) Gradle projesi
- `*/` — her eklenti için ayrı modül (`build.gradle.kts` + `src/main/...`); `build.gradle.kts` içeren her üst dizin otomatik dahil edilir
- `.github/workflows/build.yml` — push sonrası `.cs3` ve `plugins.json` üretip **public `vahsikelebek-builds` reposuna** gönderir
- `scripts/` — domain kontrol aracı (`check_domains.py`, `domains.json`)

## Eklenti listesi

Her eklentinin kaynak kodu kendi modülünde bulunur. Tam liste için
[`builds/plugins.json`](https://raw.githubusercontent.com/castiel430/vahsikelebek-builds/main/plugins.json).

### Sağlayıcı modülleri (58)

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
| Dizilla | dizilla.la |
| DiziMom | ddizimom.com |
| DiziPal | dizipal1596.com |
| DiziPod | dizipod.com |
| DiziYou | diziyou.to |
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
| PuhuTV | puhutv.com |
| RareFilmm | rarefilmm.com |

| SelcukFlix | selcukflix.app |
| SetFilmIzle | www.setfilmizle.ltd |
| SezonlukDizi | sezonlukdizi.cc |
| ShowTV | www.showtv.com.tr |
| SinemaCX | sinemacc.com |
| Sinezy | sinezy.si |
| StarTV | www.startv.com.tr |
| SuperFilmGeldi | superfilmizle.icu |
| TLCtr | www.tlctv.com.tr |
| TurkAnimeTV | turkanime.live |
| TV8 | www.tv8.com.tr |

| UltraFilmizle | ultraiz.net |
| WebdramaTurkey2 | webdramaturkey2.com |
| WebteIzle | webteizle.info |
| Yabancidizi | ybncidizi.com |
| YesilCamTv | yesilcamtv.com.tr |
| YouTube | www.youtube.com |

### Eşzamanlama / senkron eklentisi

- **TraktSync** — kütüphanenizi Trakt.tv ile eşitler; bkz. [TraktSync (Trakt.tv)](#traktsync-trakt-tv-eklentisi)

> Kullanılmayan iskelet modüller (eski `VahsiKelebek`, `VahsiFilm`, `VahsiDizi`,
> `VahsiAnime`, `VahsiBelgesel`) ve ölü sağlayıcılar (`Watch2Movies`, `DiziPal`,
> `DiziPalGuncel`, `DiziPalOrjinal`, `DiziLife`, `NetflixMirror`,
> `UgurFilm` - alan adı park edilmiş, `RecTV` - backend Cloudflare Access
> duvarının arkasında ve Firebase Remote Config'teki `api_url` da oraya işaret
> ediyor) silinmiştir; depoda yalnızca çalışan gerçek site sağlayıcıları ve
> TraktSync bulunur.

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
- Kütüphane işaretleri değiştikçe ~60 saniyede bir otomatik, çift yönlü eşitlenir:
  - `COMPLETED` → Trakt izleme geçmişine (`sync/history`) yazılır
    (dizide son bilinen sezon/bölüm)
  - `WATCHING` / `ONHOLD` / `PLANTOWATCH` → Trakt izleme listesine (`sync/watchlist`) eklenir
  - **Trakt → CloudStream:** Trakt.tv'de izledikleriniz CloudStream'de izlendi görünür —
    film → `COMPLETED` ve yeşil ✓; dizi bölümleri → bölüm listesinde yeşil ✓;
    dizinin tüm yayındaki bölümleri izlendiyse `COMPLETED`, en az biri izlendiyse
    `WATCHING` (`DROPPED` / `ONHOLD` durumlarına dokunulmaz)
- **Ayarlar → Eklenti ayarları → Trakt Senkron** → **Trakt'a Bağlan** düğmesine
  dokunun; açılan pencere **uygulama içinde** Trakt sitesini açar — e-posta +
  e-postanıza gelen 6 haneli kodu girin, otomatik bağlanın.

**Kurulum:**

1. CloudStream → **Ayarlar → Eklenti ayarları → Trakt Senkron**.
2. **Trakt'a Bağlan** düğmesine basın (uygulama içinde pencere açılır).
3. E-postanızı yazın → Trakt'tan gelen 6 haneli kodu girin → otomatik bağlanır.
4. Uygulama içi pencere açılmazsa (cihazda sistem WebView'i yoksa) **"Tarayıcıda Aç"**
   düğmesini kullanın (o akışta uygulama otomatik bağlanmaz; aynı pencerede
   tamamlayın).

> Gömülü uygulama "Development" türündedir: sadece sizin hesabınız ve My Apps
> sayfasından ekleyeceğiniz 16 kullanıcı bağlanabilir. Herkese açık kullanmak için
> trakt.tv/developer → My Apps → Production erişimi isteyin (uygulama incelemeye girer,
> birkaç iş günü sürer).
> Kimlik çözümü: `syncData` varsa o kullanılır; yoksa eklenti **başlık (+ yıl) ile
> Trakt'ta arayıp** ilk sonucu alır (yanlış eşleşme olasılığı vardır; emin
> olmadığınızda "✓ Trakt'a İzledim Olarak İşaretle" düğmesini kullanın).
> IMDb/TMDb kimliği olmayan dizi içeriklerin sezon/bölümü bilinmiyorsa sıra atlar;
> film ve dizilerin izleme listesi başlıkla aranır ve eşleşirse eklenir.

## Derleme ve yayınlama (GitHub Actions)

1. `main` dalına yaptığınız değişiklikleri push edin.
2. **Actions** sekmesinde "Build" iş akışının başarıyla tamamlandığını kontrol edin.
3. İş akışı `.cs3`, `plugins.json` ve `repo.json` dosyalarını **public
   [vahsikelebek-builds](https://github.com/castiel430/vahsikelebek-builds)** reposuna push eder
   (`BUILDS_TOKEN` secret'i ile).
4. CloudStream, public repodaki `repo.json` üzerinden eklentileri sunar.

> Dağıtım reposunun tekrar oluşturulması gerekirse: repo public olmalı, `main` dalında
> boş bir başlangıç commit'i bulunmalı ve bu kaynak repoda `BUILDS_TOKEN` secret'ine
> yazma yetkisi olan bir GitHub token'ı tanımlanmalıdır.

Yerelde derlemek isterseniz JDK 17 ve Android SDK kurulu bir ortamda:

```bash
./gradlew make makePluginsJson
```

## Yeni eklenti modülü ekleme

Yeni bir klasör (ör. `YeniEklenti/`) oluşturup içine `build.gradle.kts` ve `src/main/...`
kaynaklarını ekleyin. `settings.gradle.kts` üst dizindeki modülleri otomatik algılar;
Actions derlemesi yeni modülü de paketler.

## Domain değişikliklerini kontrol etme (Python)

Tüm sağlayıcıların `mainUrl` değerlerini tarar, gerçekten açılıp açılmadığını
denetler ve yeni domaine yönlenenleri **isteğe bağlı olarak** kaynağa yazar:

```bash
python scripts/auto_domains.py                 # rapor (hiçbir dosyayı değiştirmez)
python scripts/auto_domains.py --only DiziBox  # tek modül
python scripts/auto_domains.py --apply         # yönlenenleri yaz + sürümü artır
```

- Denetim **Türkiye'deki ağdan** çalışmalıdır. GitHub Actions sunucuları yurt
  dışında olduğu için BTK'nın DNS zehirlemesini/SNI engelini göremez; bu yüzden
  iş akışına eklenmez, yerelde çalıştırılır.
- DNS zehirlemesi `195.175.254.2` / `2a01:358:4014::/48` adreslerine düşer.
  Rapor `POISON`, `NXDOMAIN`, `CONN-ERR` ve `HTTP 403` durumlarını ayırır.
- `--apply` yalnızca yönlendirme **yeni bir domaine** gidiyorsa ve yeni domain
  içerik döndürüyorsa çalışır; ilgili modülün sürümünü de artırır.
- Eski, basit denetleyici: `python scripts/check_domains.py` (yalnızca
  `scripts/domains.json` listesine bakar).

## Gizlilik: kaynak repo private, dağıtım repo public

CloudStream, `repo.json` ve `plugins.json`/`.cs3` dosyalarını **anonim HTTPS** ile
public `vahsikelebek-builds` reposundan çeker. Bu kaynak repo (`vahsikelebek`) private'tır:

- Kaynak kodunuz (eklentilerin yazıldığı Kotlin kodu) başkalarına görünmez,
- Derlenmiş eklentiler (`.cs3`) yine de herkes tarafından CloudStream'e kurulabilir ve
  güncellenebilir,
- `.cs3` dosyaları bayt düzeyinde public olsa da, kaynak koda ulaşmak isteyen birinin
  `.cs3`'ü decompile etmesi gerekir (çok daha zor).

Sınırlar: önceden kurulum yapmış kullanıcıların tek ihtiyacı public adres; dolayısıyla
**dağıtım reposu asla private yapılmamalıdır**, aksi halde CloudStream depoyu hiç
gösteremez. Kaynak repo ziyaret edilemez, klonlanamaz, Issues açma yetkisi yoktur.

Token güvenliği: Actions, dağıtım reposuna push etmek için bu kaynak repoda tanımlı
`BUILDS_TOKEN` secret'ini kullanır. İsterseniz daha dar yetkili (yalnızca
`vahsikelebek-builds`, `Contents: Read and write`) bir fine-grained PAT oluşturup
`gh secret set BUILDS_TOKEN --body "..."` ile değiştirebilirsiniz.

## Kaynak/Referans

Yapı, [CloudStream eklenti sistemi](https://github.com/recloudstream/cloudstream) ve
yaygın CloudStream depo şablonları incelenerek bu projeye özgü şekilde hazırlanmıştır.
Kaynak siteler üzerindeki tüm haklar ilgili sahiplerine aittir.
