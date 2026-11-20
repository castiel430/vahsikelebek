# Legal / Telif Bildirimi

> Bu belge hukuki tavsiye değildir; yalnızca projenin teknik ve idari duruşunu
> özetler.

## Projenin niteliği

Bu depo, [CloudStream](https://github.com/recloudstream/cloudstream) uygulaması
için yazılmış **genel amaçlı HTTP/katalog istemcileri**nin (eklentilerin) kaynak
kodunu içerir.

- Depoda **hiçbir film, dizi, müzik, görsel veya telifli medya dosyası barındırılmaz**.
  Dağıtım reposunda yalnızca `.cs3` derleme çıktıları, `plugins.json` ve
  `repo.json` bulunur.
- Eklentiler, herkese açık web sayfalarındaki **bağlantıları okuyan** ve
  oynatıcıya ileten otomatik istemcilerdir. İçerik yüklemez, saklamaz,
  kopyalamaz, yeniden dağıtmaz ve barındırmaz.
- Kullanım tamamen kullanıcının sorumluluğundadır. Proje, herhangi bir içeriğin
  yasal olup olmadığını garanti etmez.

## Erişim kontrolü / koruma bypass'ı yok

Bu proje **teknik koruma önlemlerini aşan kod içermez**:

- Cloudflare Turnstile / reCAPTCHA çözümü, Proof-of-Work çözümü, parmak izi
  (fingerprint) sahteciliği, DRM veya şifreli akış (AES anahtar/iv çıkarma)
  uygulanmaz.
- Böyle bir korumaya geçtiği tespit edilen kaynaklar kaldırılır; örneğin
  `SezonlukDizi` modülü, tüm kaynakları Turnstile arkasına alıp kalan tek
  kaynak da PoW + parmak izi doğrulaması isteyince depodan çıkarılmıştır.
- `StreamFix` yalnızca **erişilemeyen/eski alan adlarını aynı servisin güncel
  alan adına yönlendirir**; parola, token, imza veya doğrulama üretmez.

Bu ayrım önemlidir: DMCA'nın en güçlü hükümleri içerik kopyalamadan çok
**erişim kontrolünü atlatma** (anti-circumvention) üzerinedir. Proje bu
kapsamın dışında kalmak için bilinçli olarak tasarlanmıştır.

## Telif hakkı sahibiyseniz

Bir içerik veya bağlantı hakkında bildirim alırsak:

1. İlgili **tek modülü kaldırırız** (depoyu cezalandırmak yerine hedefli müdahale).
2. Sürüm artışı yapıp CI'ı yeniden çalıştırarak yayındaki `.cs3` ve
   `plugins.json`'u güncelleriz.
3. Gerekiyorsa karşı-bildirim (counter-notice) sürecini belgeleriz.

Bildirim için iletişim: **GitHub Issues**
(`https://github.com/castiel430/vahsikelebek-builds/issues/new`) veya GitHub
profilinden doğrudan mesaj. Başlıkta modül adını belirtmeniz yeterlidir.

Bildirimde şunları belirtmeniz yeterlidir: hak sahibi olduğunuz içerik, ilgili
modül/site, bağlantı veya açıklama ve iletişim bilgileriniz.

## Yasal kullanım

Kullanıcılar yalnızca erişmeye yasal olarak hakları olan içerikleri oynatmalı;
yerel yayın kurallarına (BTK/TBMM yasaları dahil) uymaktan kullanıcı sorumludur.
