# Vahşi Kelebek - Cloudstream Eklenti Deposu (Özel)

Bu depo, **Cloudstream** için sıfırdan hazırlanmış **özel (custom)** bir eklenti deposudur. İncelediğim Cloudstream repo yapıları (`repo.json`, `pluginLists` → `plugins.json`, `.cs3` eklenti kayıtları ve Gradle tabanlı eklenti kaynak kodu) referans alınarak yazılmıştır; kodlar bu depoya özel olarak oluşturulmuştur.

## Depoyu Cloudstream'e ekleme

Cloudstream uygulamasında: **Ayarlar > Eklentiler > Depolar > Depo ekle** ve şu adresi girin:

```text
https://raw.githubusercontent.com/castiel430/vahsikelebek/main/repo.json
```

> Not: Derlenmiş eklenti listesi GitHub Actions ile `builds` dalında yayınlanır (`repo.json` bu listeyi işaret eder). İlk başarılı derlemeden sonra eklentiler listede görünür.

## Depo yapısı

- `repo.json` — Cloudstream depo manifesti (Cloudstream bu dosyayı okur)
- `build.gradle.kts`, `settings.gradle.kts`, `gradle/` — çoklu eklenti (multi-module) Gradle projesi
- `*/` — her eklenti için ayrı modül (`build.gradle.kts` + `src/main/...`)
- `.github/workflows/build.yml` — push sonrası `.cs3` ve `plugins.json` üretip `builds` dalına gönderir
- `scripts/` — domain kontrol aracı (`check_domains.py`, `domains.json`)

## Eklenti modülleri

### Hazır/çalışır sağlayıcı modülleri (nuviotr listesindeki açık kaynak Cloudstream depolarından alınarak bu repoya dahil edildi)
| Modül | Hedef site | Türler |
|---|---|---|
| `HDFilmCehennemi` | hdfilmcehennemi.nl | Film, Dizi |
| `SezonlukDizi` | sezonlukdizi.cc | Dizi |
| `FilmMakinesi` | filmmakinesi.to | Film, Dizi |
| `FullHDFilmizlesene` | fullhdfilmizlesene.de | Film |
| `DiziYou` | diziyou.one | Dizi |
| `SetFilmIzle` | setfilmizle.de | Film, Dizi |
| `DiziBox` | dizibox.live | Dizi |
| `DiziMom` | ddizimom.net | Dizi |
| `DiziPal` | dizipal2145.com | Dizi |
| `BelgeselX` | belgeselx.com | Belgesel/Video |
| `AnimeciX` | animecix.tv | Anime |

### Özel iskelet modüller (bu repoda özel yazıldı)
| Modül | Açıklama | Türler |
|---|---|---|
| `VahsiKelebek` | Genel amaçlı özel sağlayıcı iskeleti | Film, Dizi |
| `VahsiFilm` | Film odaklı özel sağlayıcı iskeleti | Film |
| `VahsiDizi` | Dizi odaklı özel sağlayıcı iskeleti | Dizi |
| `VahsiAnime` | Anime/animasyon odaklı özel sağlayıcı iskeleti | Anime, AnimeMovie, OVA |
| `VahsiBelgesel` | Belgesel odaklı özel sağlayıcı iskeleti | Belgesel |

> Not: Hazır modüller ilgili açık kaynak eklenti depolarındaki kaynaklardan alınmış olup; site yapıları ve alan adları zamanla değişebilir. `scripts/check_domains.py` ile domain durumlarını kontrol edebilir, gerekli güncellemeleri modül içinden yapabilirsiniz.

## Derleme ve yayınlama (GitHub Actions)

Bu makinede Android SDK/Java kurulu olmadığı için yerelde derleme yapılmadı; ancak depo GitHub Actions ile derlenmeye hazırdır.

1. `main` dalına yaptığınız değişiklikleri push edin.
2. **Actions** sekmesinde "Build" iş akışının başarıyla tamamlandığını kontrol edin.
3. İş akışı `builds` dalına `*.cs3` ve `plugins.json` dosyalarını yazar.
4. Cloudstream, `repo.json` üzerinden bu listeyi kullanarak eklentiyi sunar.

> Yerelde derlemek isterseniz: JDK 11 ve Android SDK kurulu bir ortamda `./gradlew make makePluginsJson` komutunu çalıştırabilirsiniz.

## Yeni eklenti modülü ekleme

Yeni bir klasör (ör. `YeniEklenti/`) oluşturup içinde `build.gradle.kts` ve `src/main/...` kaynaklarını ekleyin. `settings.gradle.kts` kök dizindeki modülleri otomatik algılar; GitHub Actions derlemesi yeni modülü de paketler.

## Domain değişikliklerini kontrol etme (Python)

Web sitelerinin güncel adres/domain değişikliklerini kontrol etmek için `scripts/check_domains.py` aracı eklenmiştir.

```bash
python scripts/check_domains.py
python scripts/check_domains.py --file scripts/domains.json
python scripts/check_domains.py --json
```

- İzlenen siteler: `scripts/domains.json`
- Script, verilen URL'lere istek atar, yönlendirmeleri takip eder ve giriş domaini ile son domaini karşılaştırarak olası değişiklikleri raporlar.

## Kaynak/Referans

Yapı, [Cloudstream eklenti sistemi](https://github.com/recloudstream/cloudstream) ve yaygın Cloudstream depo şablonları incelenerek **bu projeye özel** şekilde hazırlanmıştır.
