# REAL avatar parçaları

Kendi çizdiğimiz parçalar buraya, ilgili sekmenin klasörüne konur. Sonra:

```
node android/avatar/build.mjs
```

Betik parçaları editöre gömer ve hem Android (`assets/avatar/editor.html`) hem iOS (`Resources/Avatar/avatar-editor.html`) kopyasını yazar. Editör internet istemez.

## Klasörler ve ölçüler

| Klasör     | Editör sekmesi | viewBox        |
|------------|----------------|----------------|
| `body/`    | Gövde (üst)    | `0 0 80 80`    |
| `bottom/`  | Alt            | `0 0 80 107`   |
| `head/`    | Kafa           | `0 0 80 80`    |
| `item/`    | Eşya           | `0 0 80 80`    |
| `glasses/` | Gözlük         | `0 0 80 80`    |

Ölçü tutmazsa betik durur. En kolay yol: Humation'ın aynı sekmedeki bir parçasını (ör. `hoodie`) Figma'ya alıp onun üzerinde çizmek; kol, boyun ve bel hizası böyle tutar. Humation'ın SVG'leri: https://cdn.jsdelivr.net/npm/@humation/assets-humation-1@1.0.3/assets/humation-1/

## Renkler

Sabit renk yerine Humation'ın değişkenlerini kullan, yoksa editördeki renk seçici o parçayı boyamaz:

- üst kıyafet dolgusu: `fill="var(--hm-clothes, #FFFFFF)"`
- alt kıyafet dolgusu: `fill="var(--hm-bottom, #000000)"`
- çizgiler: `stroke="var(--hm-stroke, #000000)"` (ya da çizgi dolgusu `fill="var(--hm-stroke, #000000)"`)
- ten görünen yerler: `var(--hm-skin, #FFFFFF)`, saç: `var(--hm-hair, #000000)`

Bilerek sabit renk (logo, desen) kullanılabilir; betik yalnızca uyarır.

## Dosya adı

Küçük harf, rakam ve tire: `forma-kirmizi.svg`. Editörde `real-forma-kirmizi` adıyla, Humation parçalarından sonra çıkar.
Bir parçayı sonradan silmek ya da adını değiştirmek güvenli: onu seçmiş avatarlar o sekmede varsayılana döner.

## İzin verilmeyenler

SVG içinde `<script>`, `onclick=` gibi olaylar, `<image>`, `<foreignObject>` ve dış bağlantı yasak; betik durur.

## Lisans

Humation'ın kodu ve görselleri MIT lisanslı (`../vendor/LICENSE-Humation.txt`); değiştirmek ve üzerine eklemek serbest, telif notu uygulamada (Ayarlar → Lisanslar) kalmalı. Kendi çizdiğimiz parçalar bizimdir; yalnızca "Humation" adıyla resmi parça gibi sunulmaz.
