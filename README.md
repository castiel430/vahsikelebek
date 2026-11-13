# Vahşi Kelebek - Cloudstream Eklenti Deposu

Bu depo, [Cloudstream](https://github.com/recloudstream/cloudstream) için hazırlanmış Türkçe eklenti deposu şablonudur.

## Depo Ekleme

Cloudstream uygulamasında:

1. **Ayarlar** > **Eklentiler** > **Depolar** bölümüne gidin.
2. **Depo ekle** diyerek aşağıdaki kısa kodu veya URL'yi girin:

- **Depo URL:** `https://raw.githubusercontent.com/castiel430/vahsikelebek/main/repo.json`

## İçerik

- `repo.json` — Cloudstream depo manifest dosyası
- `plugins.json` — Eklenti listesi (başlangıçta boş)

## Eklenti Ekleme

Derlenmiş `.cs3` eklenti dosyalarınızı bu depoya ekleyip `plugins.json` dosyasını aşağıdaki örnek formata göre güncelleyebilirsiniz:

```json
[
  {
    "name": "OrnekEklenti",
    "internalName": "OrnekEklenti",
    "description": "Örnek açıklama",
    "url": "https://raw.githubusercontent.com/castiel430/vahsikelebek/main/OrnekEklenti.cs3",
    "repositoryUrl": "https://github.com/castiel430/vahsikelebek",
    "authors": ["vahsikelebek"],
    "version": 1,
    "language": "tr",
    "tvTypes": ["Movie", "TvSeries"],
    "status": 1,
    "iconUrl": "https://raw.githubusercontent.com/castiel430/vahsikelebek/main/icon.png",
    "apiVersion": 1
  }
]
```

## Lisans

Bu depo kişisel kullanım içindir.
