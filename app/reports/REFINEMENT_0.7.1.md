# REAL 0.7.1 — Selective Liquid Glass refinement

24 Eylül 2026. Başlangıç: bu konuşmada teslim edilen Real_0.7.0_LiquidGlass.zip.
Referanslar: gönderilen üç cam kontrol örneği, REAL ana ekran/ayar ekranı ve iki BitChord ekranı. Referanslardaki kapsül, ince kenar ışığı ve koyu materyal dili kullanıldı; ekran düzenleri kopyalanmadı.

**Doğrulama durumu:** Kod ve statik kontroller tamamlandı. Bu ortamda Gradle dağıtımı ağ nedeniyle indirilemedi; Kotlin derlemesi, unit test, render ve A53 performans testi doğrulanmadı.

## Görsel plan ve uygulama

| Sıra | Yüzey | Uygulanan değişiklik |
|---|---|---|
| 1 | Ana ekran ve composer | Zeminin alt %44'ünde siyahtan çok koyu nötr tona geçiş; viewport ile sabit kalır ve ortak backdrop kaydına girer. Composer aynı ölçü ve işlevlerle ortak füme malzemeyi kullanır. Daha ince, yönlü kenar ışığı; 5dp temel blur; nötr koyu gövde. |
| 1 | Profil, filtre, sıralama | Profilin içteki ikinci dairesi kaldırıldı; tek cam çember ve kişi ikonu kaldı. Filtre/sıralama/düzenleme kontrolleri aynı statik optik malzemeyi ve kısa basış ölçeğini kullanır. Aktif filtre hafif seçili kenar alır. |
| 1 | Üst overlay | Liquid modunda 18dp blur ve 40dp ek uzama. Ana ekranda başlık görünürken normal yükseklik korunur; başlık kaydırılarak çıktıktan sonra 180ms ile uzar. Böylece ilk ekranda REAL ve profil kendi blur alanının altında kalmaz. |
| 2 | Stil seçimi | Seçili Liquid Glass kartının mavi-gri düz dolgusu kaldırıldı. Nötr, yarı saydam füme dolgu; üst/yan kenar ışığı ve küçük açık tonlu tik kullanıldı. Önizleme de nötrleştirildi. Koyu tema seçeneği Liquid modunda parlak beyaz blok yerine aynı cam kapsül diline geçti. |
| 3 | Kategori seçme/oluşturma | Yalnızca geçici panel kabuğuna daha yoğun `Sheet` cam malzemesi bağlandı. İç satırlar shader kullanmaz; seçili satır nötr kenar/derinlik ile belirtilir. Kategori renkleri ve üyelik kuralları korunur. |
| 3 | Tarih/saat | Panel kabuğu Sheet camı kullanır. Tarih/Saat için tek hareketli seçili kapsül eklendi; 200ms geçiş. Rakamların merkez satırının arkasına sabit ve çok hafif bir pencere vurgusu kondu. Çarklar, yıl, tekrar, zaman doğrulaması ve onay callback'leri korunur. |
| 4 | Detay | Geri/yıldız ve alt aksiyon barı yeni ortak füme malzemeyi alır. Normal detay gövdesinin morumsu-gri tonu azaltıldı; harita/Journal çerçeveleri sakinleştirildi. Özel kart renkleri ve yıldız kenarı önceliği korunur. |

## Malzeme ve sınırlar

- Ana gövde rengi nötr `#0C0C0E`; normal içerik `#111113`. Mavi panel dolgusu eklenmedi.
- Az miktarda soğuk ton yalnızca küçük seçim tiki/kenarı ve mevcut aktif işlev renklerinde kalır.
- İçerik kartlarına dinamik shader eklenmedi. Kategori kartlarının mevcut renkleri korunur.
- Ana ekranın kaynak katmanı içinde kalan profil/filtre/sıralama kontrolleri **statik optik** yüzey kullanır. Kendi kaydedildikleri katmanı tekrar örneklemezler. Composer, detayın yüzen kontrolleri ve açık sheet kabukları dinamik motoru kullanır.
- Sheet gövdesi metinleri korumak için daha yoğun ve 9dp blur'ludur; kırılma normal chrome'a göre %30 daha düşüktür. Bütün panel, çark sürüklenirken sıkışıp büyümez. İç kontroller statiktir; çift blur yoktur.
- Liste/hatırlatıcı düzeni, bilgi sırası, filtre verileri, Journal/konum/ek saklama ve bildirim mantığı değişmedi.
- Glass ve Buz yolları korunur; bu değişikliklerin görsel dalları LiquidGlass ile sınırlıdır.

## Erişilebilirlik ve performans

Dinamik bulanıklığı azalt ayarında veya API 33 altında Liquid statik yüzeye döner. Üst overlay de kaynak kaydıyla aynı API koşulunu kullanır. Azaltılmış hareket yeni sekme kapsülünü ve üst alan uzamasını anında uygular; basış/ışık hareketleri kapanır. Sekmeler `Role.Tab` ve seçili durum semantiği kullanır. Dokunma işlevleri aynı callback'lere bağlıdır.

Kaynak ekran kaydı paylaşılır. Yeni ikinci bir tam ekran kaynak yoktur. Açık panelin bölgesel örneklemesi yarım çözünürlüktedir; normal liste kartları ve sayı satırları shader kullanmaz. Shader nesnesi korunur; sürekli animasyon/timer eklenmedi. Yeni görsel tasarım A53'te ölçülmüş değildir.

Birincil composer yazısı (`#F7F7F8`) için saf beyaz backdrop ve en yüksek yeni parlama varsayımıyla yapılan basit alfa/kontrast hesabı yaklaşık 4.54:1 verdi. Bu sayısal tasarım kontrolüdür; gerçek GPU görüntüsü veya tüm metinler için erişilebilirlik testi değildir.

## Kontroller

- `git --no-pager diff --check`: geçti. CRLF girdi için `core.whitespace=cr-at-eol` kullanıldı; yeni dosyanın gerçek satır sonu boşlukları ayrıca kontrol edildi.
- Değişen/yeni 14 Kotlin dosyasında parantez/string/yorum dengesi kontrolü geçti. Bu Kotlin derleyicisi değildir.
- 0.7.0 ZIP ile 54 korunan dosyanın byte karşılaştırması geçti: veri, bildirim, speech, mevcut testler, manifest ve araç zinciri.
- `app/build.gradle.kts` değişikliği yalnızca `versionCode=21`, `versionName=0.7.1`. Yeni/yükseltilmiş dependency yok.
- `./gradlew :app:compileOptimizedKotlin`: Gradle 8.11.1 indirimi sırasında `java.net.SocketException: Network is unreachable`; Kotlin task'ı başlamadı. Çıktı: `compileOptimizedKotlin_0.7.1.log`.
- Derleme engellendiği için `:app:testOptimizedUnitTest` çalıştırılmadı. Önceki stil/fallback testleri korunmuştur; geçtiği iddia edilmez.
- Clean, APK install, emulator, commit ve push çalıştırılmadı.

Windows'ta proje kökünde:

```powershell
.\gradlew.bat :app:compileOptimizedKotlin
# İlk komut başarılıysa:
.\gradlew.bat :app:testOptimizedUnitTest
```

## Kısa A53 kontrolü

1. Liquid Glass açıkken ana ekranı ilk aç: REAL/profil net, arka planın üst bölümü siyah, alt atmosfer çok hafif olmalı. Profil tek çember olmalı.
2. Listeyi yukarı/aşağı kaydır: başlık çıktıktan sonra üst blur daha aşağı uzamalı; geri dönünce başlık net kalmalı. Composer altında renkli kartlarda cam okunmalı, metin net kalmalı.
3. Ayarlardaki seçili Liquid Glass kartını karşılaştır: geniş mavi dolgu olmamalı. Glass/Buz'a geçip eski görünümün korunduğunu kontrol et.
4. Kategori seç/oluştur/düzenle ve renk filtresini kullan: seçili durum görünmeli, kategori rengi ve işlemler korunmalı.
5. Tarih/Saat sekmeleri, çarklar, küçük yıl seçimi, tekrar ve Bitti/İptal: panelin bütünü sürükleme sırasında ölçeklenmemeli. Büyük yazıda sekmeleri dene.
6. Detayda harita + Journal + ek içeren bir notu kaydır; sıralama, yıldız/tamamla/kaydet/sil/paylaş ve bildirim davranışlarını kontrol et.
7. Her iki azaltma ayarını ayrı ayrı ve birlikte aç/kapat; yerleşim sabit, yüzeyler dolu/net kalmalı.
8. Uzun liste ve açık picker ile A53 akıcılığı/ısınmayı 0.7.0 ile aynı koşullarda karşılaştır. Sonuç henüz ölçülmedi.

`VISUAL_V2_REPORT.md`, 0.7.0 temel uygulamasının tarihsel raporudur. Bu sürümün güncel farkları ve durumu bu dosyadadır.

## Değişen kaynak dosyaları

- `app/build.gradle.kts`
- `app/src/main/java/com/cagri/bigboscagri/ui/ReminderDetailScreen.kt`
- `app/src/main/java/com/cagri/bigboscagri/ui/ReminderScreen.kt`
- `app/src/main/java/com/cagri/bigboscagri/ui/SettingsScreen.kt`
- `app/src/main/java/com/cagri/bigboscagri/ui/components/CategoryColorFilterBar.kt`
- `app/src/main/java/com/cagri/bigboscagri/ui/components/CategorySelectionOverlay.kt`
- `app/src/main/java/com/cagri/bigboscagri/ui/components/CreateCategoryDialog.kt`
- `app/src/main/java/com/cagri/bigboscagri/ui/components/DateTimePicker.kt`
- `app/src/main/java/com/cagri/bigboscagri/ui/components/FrostedSurface.kt`
- `app/src/main/java/com/cagri/bigboscagri/ui/components/HomeSortControl.kt`
- `app/src/main/java/com/cagri/bigboscagri/ui/components/LiquidGlassSurface.kt`
- `app/src/main/java/com/cagri/bigboscagri/ui/components/LiquidGlassTokens.kt`
- `app/src/main/java/com/cagri/bigboscagri/ui/components/LiquidSegmentedControl.kt`
- `app/src/main/java/com/cagri/bigboscagri/ui/components/LiquidSettingsLayout.kt`
- `app/src/main/java/com/cagri/bigboscagri/ui/components/TopGlassOverlay.kt`
