# Real 1.8.5 — Kontrol raporu

Kaynak: 1.8 reinstall (versionCode 11). Güncel paket: versionCode 16.

1.8.5'te `Düzenle` eyleminin otomatik odak ve klavye davranışı kaldırıldı.
Cam renk seçicideki iki kategori tonu notla birlikte saklanıyor ve ana liste
kartında gradient olarak kullanılıyor. Renk alanları isteğe bağlı olduğundan
eski kayıtlar değiştirilmeden açılmaya devam ediyor.

1.8.4'te tarih–saat paneline uygulamadaki diğer yüzeylerle uyumlu giriş/çıkış
animasyonu eklendi. Detay ekranındaki tarih alanı yalnızca düzenleme modunda
gösteriliyor.

Eski klasörün üstüne çıkarıldığında kalabilen `ScheduledReminderReceiver.kt`
dosyası, güncel `ReminderAlarmReceiver` sistemine yönlenen uyumluluk sınıfıyla değiştirildi.

1.8.3'te tarih–saat penceresi seçili Glass/Buz yüzeyine bağlandı, bulanıklık
azaltıldığında aynı düzenin düz koyu karşılığı korunuyor. İsteğe bağlı `Yıl gir`
seçimi ve yıl seçilmediğinde en yakın gelecek tarihi kullanma davranışı eklendi.
Kategori kartlarındaki sayaçlar kaldırıldı.

## Değişiklikler

- Kayıt işlemleri ana arayüz iş parçacığından taşındı; arka plan alıcılarıyla aynı kilidi kullanıyor. Bozuk verinin boş liste sayılıp üzerine yazılması önlendi.
- Dinamik bulanıklık kapalıyken sahne kaydı atlanıyor. Panel örneği yarım çözünürlükte; ekranın tamamına büyük bulanıklık uygulanmıyor.
- Logo küçük çözünürlükte açılıp önbelleğe alınıyor. Mikrofon seviyesi en fazla 80 ms'de bir güncelleniyor; animasyon azaltıldığında hareket devre dışı.
- Buz tasarımı menü, giriş, detay eylemleri ve silme onayında tutarlı. Ayarlar kaydırılabilir ve canlı önizlemeli.
- Tarih/saat seçimi, ay sonu ve artık yıl düzeltmesi, geçmiş saat ve yaz saati boşluğu kontrolü eklendi.
- Tek sonraki alarm kullanılıyor. Gönderilen tarihin kaydı, uygulamayı açınca aynı alarmın tekrar gönderilmesini önlüyor. Tamamlanan/silinen/yeniden planlanan notlar güncel kayıt üzerinden değerlendirilir.
- Açılış, yeniden başlatma, uygulama güncellemesi, saat ve alarm izni değişiminde planlar yenilenir. Sürekli çalışan servis yok.

## Doğrulama

XML kaynakları ve dosya kodlamaları kontrol edildi.

- 1.8.2 tabanı Gradle 8.11.1, JDK 17 ve Android SDK 36 ile `:app:testDebugUnitTest :app:assembleOptimized`: **BUILD SUCCESSFUL**.
- **20 test geçti; 0 hata, 0 başarısız test.** Tarih/zamanlama kuralları için 10; mevcut not, kategori ve ses metni davranışları için 10 test.
- Debug ve optimize Kotlin derlemesi başarılı. R8 küçültmesi, kaynak optimizasyonu, lintVital kontrolü ve optimize APK paketlemesi başarılı.
- Derlemeyi engellemeyen uyarılar: eski status-bar contrast API'si ve üçüncü taraf grafik kitaplığının sembollerinin ayıklanmaması.

Kullanıcının isteği üzerine 1.8.5 için tam Gradle/optimized build çalıştırılmadı.
Kotlin/KTS sözdizimi, XML, çağrı imzaları ve ZIP içeriği statik olarak kontrol edildi;
son derleme Windows'taki `build.ps1` ile kullanıcı tarafından yapılacaktır.

Bu ortamın imzası telefonundaki kurulumla farklı olabileceğinden APK yerine kendi bilgisayarında derleyeceğin kaynak proje teslim edilir.

Fiziksel Samsung A53 üzerinde görsel, ses tanıma, alarm teslimi ve FPS ölçümü bu ortamda yapılmadı. Yarım çözünürlük örneklemesi ölçülmüş FPS artışı anlamına gelmez. Dahil edilen ölçüm betiği kare süresi raporu üretir.

## Teknik kaynaklar

Arka planda zamanlama ve alarm izni: [Android alarm rehberi](https://developer.android.com/develop/background-work/services/alarms).

Liste anahtarları ve yeniden hesaplamayı azaltma: [Compose performans rehberi](https://developer.android.com/develop/ui/compose/performance/bestpractices).
