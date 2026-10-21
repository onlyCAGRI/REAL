# REAL 0.7.0 — Liquid Glass / Visual V2

Kaynak: Real_0.6.17_GPT(1).zip. Tarih: 23 Eylül 2026.

**Durum:** Kod uygulandı; derlenmiş veya fiziksel cihazda doğrulanmış sürüm değildir. Bu ortamda Gradle dağıtımı ağ engeli nedeniyle indirilemedi. Görsel kalite, AGSL sürücü davranışı ve A53 performansı telefonda doğrulanmalıdır.

## 1. Bulunan görsel mimari

`DesignStyle` Glass/Ice enum'u ve MainActivity SharedPreferences kaydıyla çalışıyordu. `GlassPanel`/`IcePanel`, `FrostedSurface` üzerinden panel büyüklüğünde yarım çözünürlüklü arka plan bulanıklığı kullanıyordu. `DesignPanel` bu iki stili seçiyordu. Ana liste ve detay ayrı `rememberGraphicsLayer` sahnelerini `drawWithContent` içinde kaydediyordu; `TopGlassOverlay` aynı kaynağı örnekliyordu. Composer, hesap menüsü ve seçim barı kendi Glass/Ice dallarını içeriyordu. Ayarlar bağımsız, düz koyu gruplardan oluşuyordu. Hatırlatıcı kartlarının renk/gradient ve yıldız kenarı sistemi bulunuyordu.

İncelenen araç zinciri: Kotlin ve Compose plugin 2.1.20; Compose BOM 2025.05.01; AGP 8.10.1; Gradle 8.11.1; Java/JVM 17; minSdk 26; compileSdk/targetSdk 36. Yeni bir dependency eklemeden önce bu değerler okundu.

## 2. Motor seçenekleri

| Seçenek | Değerlendirme |
|---|---|
| Haze 2 / haze-glass | Regular/Clear, refraction, depth blur ve etkileşim desteği var. İncelenen 2.0 changelog'u Kotlin 2.4.20 ve Jetpack Compose 1.12.1 araç zincirine işaret ediyor. Bu projeye doğrudan taşınmadı. |
| Kyant0 AndroidLiquidGlass / Backdrop | Gerçek backdrop/lens sistemi ve bileşen örnekleri var. İncelenen 1.0.0 POM'u dahi Kotlin 2.2.21 ve Compose 1.9.4 bağımlılıkları içeriyor. Güncel hattı dayatmak yerine mevcut sürüm çizgisi korundu. |
| Proje içi GraphicsLayer + AGSL | Projede zaten bulunan sahne kaydı kullanılabiliyor. Yeni kütüphane veya araç zinciri yükseltmesi gerektirmiyor; shader ve görsel QA sorumluluğu projede kalıyor. Seçilen yaklaşım. |

Bu inceleme, herhangi bir harici kütüphanenin tüm eski sürümlerinin uyumsuz olduğunu iddia etmez. Mevcut araç zinciri için en düşük entegrasyon değişikliği olan yerel yol seçildi; bir kütüphaneyi zorla eklemek için sürüm yükseltmesi yapılmadı.

Kaynaklar:
- https://chrisbanes.github.io/haze/dev/changelog/
- https://chrisbanes.github.io/haze/dev/effects/glass/
- https://github.com/Kyant0/AndroidLiquidGlass
- https://central.sonatype.com/artifact/io.github.kyant0/backdrop/1.0.0
- https://developer.android.com/develop/ui/views/graphics/agsl
- https://developer.android.com/reference/android/graphics/RenderEffect
- https://developer.apple.com/design/human-interface-guidelines/materials

Apple'ın kontrol/navigasyon katmanı ile içerik katmanını ayıran yaklaşımı esas alındı. “iOS 27 ile birebir” veya Apple'ın özel render motorunun kopyası iddiası yoktur; bu Android için özgün bir optik yorumdur.

## 3. Seçilen motor ve uyumluluk

Android 13/API 33 ve üzerinde RuntimeShader + RenderEffect çalışacak şekilde uygulandı. Yüzey dışını da görebilmek için 24dp dolgulu, yarım çözünürlüklü bölgesel sahne örneklemesi var. Rounded-rectangle signed-distance alanı ve normal vektörü, kenara yaklaştıkça örnekleme koordinatını değiştiriyor. Hafif temel blur, kenar derinlik yumuşaması, sınırlı renk ayrışması ve yönlü ışık ekleniyor. Bu, yalnızca üstüne çizilmiş şeffaf bir gradient değildir.

Metin ve ikonlar shader'dan sonra çizilir; kendileri bulanıklaştırılmaz. Regular koyu gövde üst kenarda da korunur. RuntimeShader derlenemezse yüzey koyu statik dolguya döner. GPU çalışması bu ortamda ölçülmedi.

## 4. Bağımlılıklar

Yeni veya yükseltilmiş bağımlılık yok. Kotlin/Compose/AGP/Gradle/SDK değişmedi. Paket sürümü `versionCode=20`, `versionName=0.7.0` oldu. Linux `gradlew` dosyasının CRLF satır sonları LF'ye çevrildi ve çalıştırılabilir yapıldı; `gradlew.bat` ve `build.ps1` değiştirilmedi.

## 5. Değişen dosyalar

Tam liste raporun sonundadır. Veri, bildirim ve speech klasörleri, manifest ve araç zinciri dosyaları orijinal ZIP ile byte düzeyinde karşılaştırıldı. Bu alanlar değişmedi. `app/build.gradle.kts` içindeki tek değişiklik uygulama sürümüdür.

## 6. Ortak bileşenler ve token'lar

- `LiquidGlassTokens`: radius, koyu dolgu, fallback, kenar, shadow, blur, lens genişliği/kırılma, örnekleme oranı, dispersion, basış ve toparlanma süreleri.
- `LiquidGlassSurface`: dinamik işlevsel yüzey; arka plan örnekleme + gövde + kenar/ışık + etkileşim.
- `LiquidGlassContentSurface`: shader ve backdrop kaydı olmayan içerik yüzeyi.
- `LiquidGlassIconButton` / `DesignIconButton`: optik ikon kontrolü ve eski stillere geçiş.
- `liquidChrome`: kaydedilen sahnenin içindeki kontroller için statik optik görünüm.
- `SettingsLayout` / `LiquidAppearanceSelector`: ayrı kaynak/toolbar katmanları ve açıklamalı stil seçimi.
- `LocalDesignStyle`, `LocalLiquidReduceMotion`, `LocalInsideLiquidGlass`: stil, azaltılmış hareket ve iç içe sampling koruması.
- `shouldCaptureBackdrop`: kaynak kaydı ile tüketicinin API/fallback koşulunu eşleştirir.

## 7. Üç stilin birlikte çalışması

Eski `glass` ve `ice` kayıt değerleri aynı kalır. Yeni değer `liquid_glass` olur; eksik/bilinmeyen değer Glass'a döner. Ayarlarda Glass, Buz (mevcut Türkçe Ice etiketi) ve Liquid Glass bulunur. Yeni görünüm opt-in'dir; ilk açılışta otomatik seçilmez. Eski Glass/Ice render dalları korunmuştur. Eski stillerin görünümü telefonda ayrıca karşılaştırılmalıdır; ekran görüntüsü regresyon testi yapılmadı.

## 8. Ana ekran

AMOLED siyah zemin ve REAL wordmark korunur. Hesap düğmesi, sıralama ve renk/kategori düzenleme kontrolleri Liquid modunda statik optik görünüm kazanır. Hesap menüsünün kendisi dinamik malzemedir. Status bar aynı ortak sahneyi kullanır. Hatırlatıcı davranışı, tamamlanan önizlemesi ve filtre kuralları değişmez.

## 9. Composer

Kapsül LiquidGlassSurface kullanır; mevcut ölçü, köşe animasyonu, mikrofon basışı, giriş genişlemesi ve gönderim akışı korunur. Yeni malzeme yalnızca yüzeye eklenmiştir. İçeriği yakalamaz; arkasındaki listeyi örnekler. Aktif basışta yaklaşık %1.4 sıkışma ve hafif ışık güçlenmesi vardır.

## 10. Kategoriler

Yerleşik ve özel kategori renkleri, iki uçlu gradient, ikon, seçim ve düzenleme davranışları korunur. Liquid modunda renkli dolgu daha sakin, kenar ayrımı biraz belirgin olur. Her kutuya ayrı blur/mercek eklenmez. Etiketlerin mevcut kırpılma düzeltmeleri korunur.

## 11. Detay ve alt işlem çubuğu

Geri ve yıldız kontrolleri Liquid modunda kaydedilen içerikten ayrılıp yüzen üst katmana taşındı. Alt tamamla/düzenle/paylaş/sil veya vazgeç/kaydet çubuğu dinamik Liquid Glass olur. Eski stillerde başlık eski içerik konumunda kalır. Ana detay → harita → Journal → zaman damgaları sırası ve aksiyon callback'leri korunur. Journal/mood, ekler ve harita render kodu değiştirilmedi.

## 12. Ayarlar

Liquid modunda sahneden ayrı, yüzen koyu cam başlık ve okunaklı koyu ayar grupları var. Stil seçimi üç materyali örnek ve açıklamayla gösteren radio satırlarına dönüşür. Android switch davranışı korunur. İzin ve NowBar ayarları aynı callback'leri kullanır.

## 13. Sheet, picker ve diğer yüzeyler

Silme onayı kompakt dinamik cam kullanır. Tarih/saat, kategori oluşturma/seçme, renk ve diğer DesignPanel içerikleri Liquid modunda statik koyu içerik malzemesine geçer. Tarih/saatin seçili sekmesi ve alt aksiyon grubu statik optik bitiş alır; çarklar okunaklı kalır. İzin/konum rehberi ortak PermissionSheet üzerinden koyu gövde ve statik optik onay kontrolü kullanır. Konum haritası, fotoğraflar ve Journal üzerindeki içerik yakalanıp yeniden bulanıklaştırılmaz. Sistem bildirim şablonlarının görünümü değiştirilmez.

## 14. Hareket

Yeni malzeme basışı 140ms, toparlanması 200ms. Sürekli çalışan zaman uniform'u/idle animasyonu yok. Mikrofon, composer genişlemesi, tarih/saat geçişi ve kart-detay animasyonları korunur. Liquid izin sheet'inde uzun kaydırma yerine 180/140ms fade vardır. Kaydırmada bar küçültme veya temel aksiyonları gizleme eklenmedi; kullanım alanını sabit tutmak tercih edildi. Hareketli arka plan zaten cam örneklemesine yansır.

## 15. Erişilebilirlik ve fallback

Dinamik bulanıklığı azalt açıldığında Liquid sahne kaydı ve lens kapanır; sabit koyu yüzey kalır. API 26–32'de Liquid aynı statik yolu kullanır. Glass/Ice'ın API 31 blur desteği korunur. Animasyonu azalt seçeneği yeni basış ölçeği/ışık animasyonunu kaldırır; yerleşim değişmez. Stil seçicisi radio semantiği kullanır; geri/yıldız açıklamaları korunur. Büyük yazı/TalkBack fiziksel doğrulaması bekliyor.

## 16. Performans önlemleri

Aktif ekran başına ortak kaynak; yüzey başına yalnızca dolgulu küçük bölge örneklenir. Yarım çözünürlük kullanılır. Her liste kartına shader yok. Bitmap/screenshot döngüsü ve idle ticker yok. Shader nesnesi yüzeyin ömrü boyunca tutulur; efekt zinciri draw cache içinde boyut/köşe değişiminde hazırlanır. İç içe Liquid tüketicisi dinamik sampling yapmaz. Statik fallback kaynak kaydını da kapatır. Fiziksel GPU/frame-time/bellek ölçümü yapılmadı; akıcılık garantisi verilmez.

## 17. Bilinçli olarak dinamik yapılmayanlar

Hatırlatıcı ve kategori kutuları, Journal/mood içeriği, büyük ayar grupları, çark gövdeleri ve renk paletleri statiktir. Kaynak sahnenin içindeki ana ekran hesap/sıralama/filtre düğmeleri statik tutulur; kaynağın kendi kendisini örneklemesi önlenir. Clear altyapıda bulunur fakat metin kontrastını korumak için üretim çağrılarında Regular varsayılandır. Harita ve fotoğraflar için ikinci bir capture mekanizması kurulmadı.

## 18. Diff / statik kontrol

`git --no-pager diff --check`: PASS. Girdi CRLF olduğu için Git çalışma kontrolünde `core.whitespace=cr-at-eol` kullanıldı; gerçek sondaki boşluklar ayrıca kontrol edildi. Yeni Kotlin dosyaları ayrıca whitespace kontrolünden geçti. Değişen/yeni Kotlin dosyalarının parantez, string ve yorum dengesi kontrolü geçti. `bash -n gradlew`: PASS. Bunlar Kotlin derlemesinin yerini tutmaz.

## 19. Derleme

`./gradlew :app:compileOptimizedKotlin`: BAŞLATILAMADI. Gradle 8.11.1 dağıtımı indirilirken `java.net.SocketException: Network is unreachable`. Kotlin task'ına ulaşılmadı; derleme başarı iddiası yok. Tam çıktı `compileOptimizedKotlin.log` dosyasında. Bu ortamda Android SDK kurulumu da mevcut değildi.

## 20. Unit test

Derleme başarılı olmadığı için koşullu `:app:testOptimizedUnitTest` çalıştırılmadı. Mevcut bir testin geçtiği/başarısız olduğu söylenemez; raporlanabilir başarısız test adı yoktur. Eklenen `LiquidGlassStyleTest`: eski kayıtların anlamı, yeni stil round-trip, bilinmeyen tercihin varsayılana dönüşü, blur azaltmada tüm capture'ların kapanması ve API 31/33 sınırları için beş test içerir. Bunlar da henüz çalıştırılmadı.

Windows'ta proje kökünde sırayla:

```powershell
.\gradlew.bat :app:compileOptimizedKotlin
# Yalnızca başarılı olursa:
.\gradlew.bat :app:testOptimizedUnitTest
```

Clean, install, emulator, commit ve push çalıştırılmadı.

## 21. Fiziksel Galaxy A53 test listesi

1. Önce derleme ve test komutlarını çalıştır; hata varsa ilk Kotlin hata bloğunu paylaş. Mevcut build.ps1 ile APK üretme sonraki yerel adım olabilir.
2. Glass ve Buz'da ana ekran, composer, detay, tarih/saat ve ayarları mevcut APK ile karşılaştır.
3. Liquid Glass'ı seç; uygulamayı tamamen kapat/aç ve seçimin korunduğunu kontrol et. Glass/Buz'a dönüşü de dene.
4. Renkli kartların olduğu uzun listeyi composer arkasından kaydır. Kenarda kırılma/hafif renk alımı görünmeli; composer metni/ikonları net kalmalı. Saf siyahın üzerinde etki doğal olarak daha sakindir.
5. Klavyeyi aç/kapat; çok satırlı giriş, mikrofon, checklist ve mevcut WhatsApp seçeneklerini dene. Yeni katman dokunma/yazma/scroll'u engellememeli.
6. Hatırlatıcı oluştur: mevcut anlık bildirim davranışı; yıldız ekle/çıkar: kalıcı bildirim; bildirime dokun: doğru detay. Yeni tarih/saat ve tekrar planla.
7. Yerleşik/özel kategorilerde filtre, renk filtresi, sıralama, oluştur/düzenle/sil ve çoklu seçimi dene. Etiketleri büyük yazı boyutunda kontrol et.
8. Detayda uzun metin, harita, Journal/mood ve fotoğraf birlikteyken kaydır. Geri/yıldız ve alt işlem çubuğunda eski kare izi, siyah boşluk veya kendi kendini büyüten görüntü olmamalı.
9. Detay içerik sırasını; tamamla/geri al, düzenle/kaydet/vazgeç, paylaş ve silme onayını kontrol et. Onay açıkken arkadaki kontroller yanlışlıkla tetiklenmemeli.
10. Tarih/saatin iki sekmesi, küçük yıl seçimi, tekrar ve hata mesajları; kategori/renk/konum/izin panelleri okunaklı olmalı. Yeni bir otomatik klavye açılması olmamalı.
11. Dinamik bulanıklığı azaltı aç: koyu statik yüzey, düzgün kenar ve net metin kalmalı. Aç/kapat sonrası ekran görüntüsü eski karede takılı kalmamalı.
12. Animasyonu azaltı aç: yeni kapsül sıkışması ve ışık tepkisi kapanmalı; ikon/aksiyon konumları aynı kalmalı.
13. Yazı ölçeği 1.3 ve mümkünse 1.5; TalkBack; gesture ve üç düğmeli gezinme; klavyeli görünüm; yatay konum. Buton metni taşmaması ve dokunma alanları kontrol edilmeli.
14. Aynı uzun listede Glass ve Liquid ile 30'ar saniye seri kaydırma + 10 kez detay aç/kapat. A53'te jank, dokunma gecikmesi, GPU süresi ve ısınmayı karşılaştır. İstenirse `adb shell dumpsys gfxinfo com.cagri.bigboscagri reset` ile sayaç sıfırlanıp tur sonunda `adb shell dumpsys gfxinfo com.cagri.bigboscagri framestats` çıktısı alınabilir. Aynı yenileme hızı/veri/cihaz sıcaklığıyla karşılaştır.
15. Uygulamayı arka plana al/geri getir ve birkaç dakika kullan. Shader boşluğu, kapanma veya bildirim/titreşim davranışında sapma varsa logcat ve ekran videosuyla bildir. API 26–32 fallback için ayrıca eski bir fiziksel cihaz gerekir.

## Tam dosya listesi

- `app/build.gradle.kts`
- `app/src/main/java/com/cagri/bigboscagri/MainActivity.kt`
- `app/src/main/java/com/cagri/bigboscagri/ui/DesignStyle.kt`
- `app/src/main/java/com/cagri/bigboscagri/ui/ReminderDetailScreen.kt`
- `app/src/main/java/com/cagri/bigboscagri/ui/ReminderScreen.kt`
- `app/src/main/java/com/cagri/bigboscagri/ui/SettingsScreen.kt`
- `app/src/main/java/com/cagri/bigboscagri/ui/components/AccountMenu.kt`
- `app/src/main/java/com/cagri/bigboscagri/ui/components/CategoryColorFilterBar.kt`
- `app/src/main/java/com/cagri/bigboscagri/ui/components/CategoryGrid.kt`
- `app/src/main/java/com/cagri/bigboscagri/ui/components/DateTimePicker.kt`
- `app/src/main/java/com/cagri/bigboscagri/ui/components/DeleteReminderPrompt.kt`
- `app/src/main/java/com/cagri/bigboscagri/ui/components/FrostedSurface.kt`
- `app/src/main/java/com/cagri/bigboscagri/ui/components/GlassComposer.kt`
- `app/src/main/java/com/cagri/bigboscagri/ui/components/GlassPanel.kt`
- `app/src/main/java/com/cagri/bigboscagri/ui/components/HomeSortControl.kt`
- `app/src/main/java/com/cagri/bigboscagri/ui/components/LiquidGlassSurface.kt`
- `app/src/main/java/com/cagri/bigboscagri/ui/components/LiquidGlassTokens.kt`
- `app/src/main/java/com/cagri/bigboscagri/ui/components/LiquidSettingsLayout.kt`
- `app/src/main/java/com/cagri/bigboscagri/ui/components/NotificationPermissionSheet.kt`
- `app/src/main/java/com/cagri/bigboscagri/ui/components/ReminderCard.kt`
- `app/src/main/java/com/cagri/bigboscagri/ui/components/SelectionActionButtons.kt`
- `app/src/test/java/com/cagri/bigboscagri/LiquidGlassStyleTest.kt`
- `gradlew`
- `reports/VISUAL_V2_REPORT.md` (bu rapor)
- `reports/compileOptimizedKotlin.log` (derleme girişimi)
