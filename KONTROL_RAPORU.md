# Real 0.5.0 — Kontrol Raporu

Bu rapor, güncel `antigravity-work` branch'indeki Real 0.5.0 kaynak ağacını özetler.

## Güncel durum

- Uygulama adı: Real
- Sürüm: 0.5.0
- compileSdk / targetSdk: 36
- minSdk: 26
- JDK: 17
- Gradle: 8.11.1
- Aktif geliştirme branch'i: `antigravity-work`

## Mevcut ana özellikler

- Metin tabanlı reminder oluşturma
- Tarih / saat seçimi
- Tekrarlayan reminder mimarisi
- Türkçe voice reminder parser
- Checklist reminder
- Özel kategori sistemi
- Reminder card renk / gradient desteği
- Tamamlananlar geçmişi
- Yıldızlı / önemli reminder ve pinned notification
- Notification Tamamla / 10 dk ertele aksiyonları
- Notification -> reminder detail açılışı
- Share to Real
- Contextual notification ve microphone permission sheet
- Glass / Buz tasarım sistemi
- Reduce Animations desteği
- Motion Language v1 mikro-etkileşimleri

## Zamanlama ve recurrence

Tek sonraki alarm yaklaşımı kullanılır. Tekrarlayan hatırlatıcılarda `anchorAlertAt` korunarak ay sonu ve leap-year drift riskleri azaltılır.

Recurring reminder teslim edildiğinde bir sonraki occurrence hesaplanır; reminder normal tamamlanan geçmişine yanlışlıkla taşınmaz.

## Bildirimler

Normal reminder bildirimleri ve pinned önemli bildirimler ayrı notification channel kullanır.

Notification action receiver üzerinden:

- Tamamla
- 10 dk ertele

aksiyonları desteklenir.

Pinned notification, yıldız kaldırıldığında veya reminder tamamlandığında/silindiğinde kaldırılır.

## Motion Language v1

Güncel motion sistemi şunları içerir:

- gesture tüketmeyen `pressScale`
- yeni reminder için hedefli arrival animation
- completion circle fill / pulse / progressive strikethrough
- star ve checklist settle motion
- date/time picker center-item emphasis
- voice draft arrival
- custom category selected-state transition
- route geçişleri
- composer placeholder shimmer

`reduceAnimations` açıkken dekoratif animasyonlar atlanır.

## Son doğrulamalar

Son geliştirme turunda:

- `:app:compileDebugKotlin` başarılı
- `:app:compileOptimizedKotlin` başarılı
- Debug unit testleri başarılı
- 22 test geçti, 0 failure

## Bilinen inceleme alanları

0.5.0 için hâlâ cihaz üzerinde gözlemlenmesi gereken başlıklar:

- UI haptic feedback gerçek cihaz davranışı
- notification vibration davranışı
- motion strength ince ayarı
- placeholder shimmer sıklığı
- farklı Android üreticilerinde notification / haptic tutarlılığı

Bu başlıklar iş mantığı hatası olarak değil, cihazlar arası UX doğrulama alanı olarak izlenmektedir.
