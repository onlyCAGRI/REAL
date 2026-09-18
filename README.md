# Real 1.8.5

1.8 sürümünün devamıdır. Uygulama kimliği ve mevcut notların kayıt biçimi korunur.
Eski proje klasöründe kalabilen `ScheduledReminderReceiver` için uyumluluk düzeltmesi içerir.

Tarih–saat penceresi Glass/Buz seçimine uyar. Yeni notlarda yıl çarkı,
`Yıl gir` seçeneği açılana kadar gizlidir; yıl seçilmezse en yakın gelecek tarih kullanılır.
Kategori kartlarında sayaç gösterilmez.

Tarih–saat paneli uygulamanın diğer yüzeyleriyle aynı yumuşak giriş/çıkış
animasyonunu kullanır. Hatırlatıcı detayında tarih alanı yalnızca `Düzenle`
modundayken gösterilir.

`Düzenle` artık klavyeyi kendiliğinden açmaz veya metni seçmez. Düzenleme
eylemlerinin üstündeki cam renk panelinden kategori paletindeki iki renk
seçilebilir; `Kaydet` sonrasında bu iki renk ana ekrandaki hatırlatıcı kartında
gradient olarak gösterilir. Eski notların kayıt biçimi geriye uyumludur.

## Derleme ve kurulum

Android Studio'nun JDK 17 veya üstü ve SDK Platform 36 kurulumu gereklidir.
Eski projenizdeki `local.properties` dosyasını yeni proje klasörüne kopyalayabilirsiniz.
Aynı Windows hesabında derleyin; eski uygulamayı kaldırmadan güncelleyin.

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1 -Optimized -Test -Install
```

`-Install` USB ile yükler; yalnızca APK için bu seçeneği çıkarın.
Optimize APK: `Real-optimized.apk`. Normal geliştirme derlemesi: `build.ps1 -Test`, çıktı `BigBosCagri-debug.apk`.
Optimize sürüm küçültme ve kod optimizasyonu kullanır, debug değildir; mevcut bilgisayarınızdaki debug imzasını kullanır. Farklı bilgisayarın imzası eski kuruluma uymayabilir; notları korumak için uygulamayı kaldırmayın.

## Kullanım

- Tarihsiz not: klavyedeki Tamam ile anında kaydedilir ve bildirim gönderilir.
- Takvim simgesi: gün/ay/yıl ve saat/dakikayı kaydırarak seçin, Bitti'ye basın, sonra notu klavyeden onaylayın.
- Tarihli not seçilen anda bildirilir; Planlı ve uygun günün Bugün bölümünde görünür.
- Detayda `Düzenle`ye bastıktan sonra tarihi değiştirebilir veya kaldırabilirsiniz. Çarpı tarihi kaldırır; notu silmez.
- Yıldız aktif notu sabit önemli bildirim olarak gösterir. Tamamlama, silme veya yıldızı kaldırma bu bildirimi kaldırır.
- Mikrofon sağdadır; ilk basış Türkçe dinler, ikinci basış dinlemeyi bitirip kaydeder. Metindeki süre ifadeleri otomatik tarih olarak yorumlanmaz.
- Ayarlar → Tasarım → Buz: koyu buzlu yüzeyler ve canlı önizleme.

Bildirim izni gereklidir. Android 12 ve üstünde tam zamanlı hatırlatma için Ayarlar'daki Alarmlar ve hatırlatıcılar iznini açın; izin yoksa Android zamanı geciktirebilir. Uygulamayı sistemden zorla durdurduktan sonra yeniden açın. Android 14 ve üstünde işletim sistemi sabit bildirimlerin tek tek kaydırılmasına izin verebilir.

## Performans

Bulanıklık kapalıyken arka sahne kaydı da durur. Efekt sadece panel boyutunda, yarım çözünürlükte örneklenir. Not okuma/yazma arka iş parçacığına taşındı. Logo görünür boyutuna göre arka planda açılır; mikrofon ses seviyesi güncellemeleri seyrekleştirildi. Kategori sayımları tek geçişte hesaplanır.

Gerçek cihaz kare sürelerini ölçmek için:

```powershell
.\measure-performance.ps1 -Adb "C:\Users\CAGRI\Downloads\platform-tools-latest-windows\platform-tools\adb.exe" -Label optimized
```

Aynı not sayısı, aynı ekran ve aynı kaydırma hareketi ile karşılaştırın. Bu pakette fiziksel telefon FPS ölçümü yapılmadı. Doğrulama sonucu `KONTROL_RAPORU.md` dosyasındadır.
