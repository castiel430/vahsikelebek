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
- `VahsiKelebek/` — **özel eklenti modülü** (bize ait kaynak kod)
  - `src/main/kotlin/com/vahsikelebek/VahsiKelebekPlugin.kt` — eklenti girişi
  - `src/main/kotlin/com/vahsikelebek/VahsiKelebekProvider.kt` — sağlayıcı (MainAPI) iskeleti
- `.github/workflows/build.yml` — push sonrası `.cs3` ve `plugins.json` üretip `builds` dalına gönderir

## Özel eklenti modülleri (tamamı bu repoya özel yazıldı)

| Modül | Açıklama | Türler |
|---|---|---|
| `VahsiKelebek` | Genel amaçlı özel sağlayıcı iskeleti | Film, Dizi |
| `VahsiFilm` | Film odaklı özel sağlayıcı iskeleti | Film |
| `VahsiDizi` | Dizi odaklı özel sağlayıcı iskeleti | Dizi |
| `VahsiAnime` | Anime/animasyon odaklı özel sağlayıcı iskeleti | Anime, AnimeMovie, OVA |
| `VahsiBelgesel` | Belgesel odaklı özel sağlayıcı iskeleti | Belgesel |

Tüm modüller `com.vahsi*` paketleri altında, örneklerden esinlenerek **birebir kopyalanmadan** bu projeye özel olarak yazılmıştır. `getMainPage`, `search`, `load`, `loadLinks` metotları kendi kaynak mantığınızı eklemeniz için hazırdır.

## Derleme ve yayınlama (GitHub Actions)

Bu makinede Android SDK/Java kurulu olmadığı için yerelde derleme yapılmadı; ancak depo GitHub Actions ile derlenmeye hazırdır.

1. `main` dalına yaptığınız değişiklikleri push edin.
2. **Actions** sekmesinde "Build" iş akışının başarıyla tamamlandığını kontrol edin.
3. İş akışı `builds` dalına `*.cs3` ve `plugins.json` dosyalarını yazar.
4. Cloudstream, `repo.json` üzerinden bu listeyi kullanarak eklentiyi sunar.

> Yerelde derlemek isterseniz: JDK 11 ve Android SDK kurulu bir ortamda `./gradlew make makePluginsJson` komutunu çalıştırabilirsiniz.

## Yeni eklenti modülü ekleme

Yeni bir klasör (ör. `YeniEklenti/`) oluşturup içinde `build.gradle.kts` ve `src/main/...` kaynaklarını ekleyin. `settings.gradle.kts` kök dizindeki modülleri otomatik algılar; GitHub Actions derlemesi yeni modülü de paketler.

## Kaynak/Referans

Yapı, [Cloudstream eklenti sistemi](https://github.com/recloudstream/cloudstream) ve yaygın Cloudstream depo şablonları incelenerek **bu projeye özel** şekilde hazırlanmıştır.
