# Yazı tipi ve tasarım referansları

Bu projede **Inter** yazı tipi kullanılır. Kaynak: [Inter / Google Fonts](https://github.com/google/fonts/tree/main/ofl/inter).
Inter Project Authors; SIL Open Font License 1.1. Lisans metni APK'nın içinde de
`assets/licenses/Inter-OFL.txt` olarak bulunur. Kaynak dosyanın telif bildirimi korunmuştur.

Değişiklik: değişken yazı tipinden FontTools ile optik boyut 20 ve ağırlık 400, 500,
600, 700 için statik TTF dosyaları üretildi. Türkçe karakterlerin varlığı kontrol edildi.

Kullanıcının sağladığı BitChord APK'sında SF Pro Display ailesi tespit edildi.
Bu fontun gömülü lisansındaki platform/dağıtım sınırlaması nedeniyle projeye
SF Pro dosyaları eklenmedi; benzer oranlara sahip Inter seçildi.

BitChord ve Samsung Reminder APK'ları tasarım referansı olarak incelendi.
Uygulamalarının kodu, görselleri veya marka varlıkları pakete kopyalanmadı.
Samsung Reminder'ın yüzen bileşen kaynaklarında görülen 200 ms saydamlık,
250 ms kapanış ve 350 ms açılış süreleri geçişlerde referans alındı.
Bu uygulamadaki cam yüzey, ikonlar ve ekranlar projeye ait Compose koduyla çizilir.

Uygulama simgesi için kullanıcı tarafından sağlanan
`image.png_2K_20260917144930.jpeg` (Real) görseli kullanılır. Orijinal JPEG
değiştirilmeden `app/src/main/res/drawable-nodpi/real_logo.jpg` olarak eklenmiştir.
Android uyarlanabilir simgesinin boşluğu ve yerleşimi XML ile tanımlanır.

Android API referansları:

- [SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer)
- [RecognizerIntent](https://developer.android.com/reference/android/speech/RecognizerIntent)
- [Compose liste animasyonları](https://developer.android.com/develop/ui/compose/lists#item-animations)
- [Compose grafik katmanları](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers)
- [Android 14 sabit bildirim davranışı](https://developer.android.com/about/versions/14/behavior-changes-all#non-dismissable-notifications)
- [Notification.Builder.setOngoing](https://developer.android.com/reference/android/app/Notification.Builder#setOngoing(boolean))
