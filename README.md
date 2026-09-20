# Real 0.5.0

Real, hızlı hatırlatıcı oluşturma ve doğru zamanda teslim etme odağında geliştirilen yerel-first bir Android hatırlatıcı uygulamasıdır.

Bu sürümde uygulamanın temel hatırlatıcı akışı, Türkçe sesli hatırlatıcı oluşturma, tekrar eden hatırlatıcılar, checklist desteği, özel kategoriler, bildirim aksiyonları ve premium motion sistemi birlikte çalışır.

## Öne çıkan özellikler

- Hızlı metin tabanlı hatırlatıcı oluşturma
- Tarih ve saat planlama
- Tekrarlayan hatırlatıcılar
- Türkçe sesli hatırlatıcı ayrıştırma
- Checklist hatırlatıcıları
- Özel kategoriler ve kategori renkleri
- Hatırlatıcı kartlarında tek veya çift renk gradient
- Önemli/yıldızlı hatırlatıcılar için sabit bildirim
- Bildirim üzerinden Tamamla ve 10 dk ertele aksiyonları
- Bildirime dokununca ilgili hatırlatıcı detayını açma
- Share to Real ile Android paylaşım menüsünden taslak oluşturma
- Tamamlananlar geçmişi
- Glass / Buz tasarım seçenekleri
- Reduce Animations ve dinamik bulanıklığı azalt seçenekleri
- İlk açılış ve ana ekran için kontrollü motion choreography
- Reminder card, kategori, picker, checklist ve detail aksiyonlarında mikro-etkileşimler

## Bildirim ve zamanlama

Real, planlı hatırlatıcılar için Android'in alarm altyapısını kullanır.

- Android 13+ için bildirim izni yalnızca gerektiğinde istenir.
- Exact alarm erişimi gerekiyorsa kullanıcı sistem ayarına yönlendirilir.
- Tekrarlayan hatırlatıcılarda anchor tabanlı zamanlama kullanılır.
- Yıldızlı aktif hatırlatıcılar sabit bildirim olarak gösterilebilir.
- Pinned bildirimler sessiz tutulur; normal reminder kanalı ayrı yönetilir.

## Sesli hatırlatıcı

Sesli giriş yerel ve deterministik bir Türkçe parser ile çalışır. Harici AI/API gerektirmez.

Örnekler:

- `Yarın saat 8'de doktora git`
- `Her cuma halı saha`
- `Her akşam ilaç al`
- `20 dakika sonra fırını kontrol et`

Tarih veya gün anlaşılıp saat belirtilmemişse Real saat uydurmaz; kullanıcıdan saat seçmesini ister.

## Motion Language v1

0.5.0 sürümünde küçük ve fiziksel mikro-etkileşimler eklendi:

- press-scale dokunma tepkileri
- yeni reminder card arrival animasyonu
- completion fill / pulse / progressive strikethrough
- star ve checklist settle motion
- date/time picker merkez seçimi
- voice transcript arrival
- kategori selection state animasyonları
- route fade / scale geçişleri

Dekoratif motion, `Reduce Animations` açıkken final state'e doğrudan geçer.

## Derleme

Gereksinimler:

- JDK 17+
- Android SDK Platform 36
- Gradle 8.11.1

Windows:

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1 -Optimized -Test -Install
```

Yalnızca APK oluşturmak için `-Install` parametresini çıkarın.

Çıktılar:

- Optimize: `Real-optimized.apk`
- Debug: `Real-debug.apk`

## Sürüm

- App version: **0.5.0**
- Package / applicationId: `com.cagri.bigboscagri`

Package adı eski kurulumlarla veri ve güncelleme uyumluluğu için şimdilik korunmaktadır.
