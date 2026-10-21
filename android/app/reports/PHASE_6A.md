# REAL 0.7.4 — Phase 6A

Taban: kullanıcının sağladığı Real_0.7.3.5.zip. Önceki, iptal edilen UI çalışması bu pakete taşınmadı. Commit/push yapılmadı.

## Uygulanan temel akış

- FILE: Düzenle ekranındaki belge simgesi ACTION_OPEN_DOCUMENT açar. Dosya adı, türü ve varsa boyutu “Ekler” içindeki kompakt, en az 68dp satırda görünür. Dosya kendi MIME türüyle Android görüntüleyicisine açılır. PDF render edilmez.
- Mevcut web bağlantısı: aynı `referenceUrl` alanı korunur; görünümü ana karttan “Ekler” bölümüne taşındı. Düzenle içindeki kaldırma işlemi Kaydet ile kalıcılaşır, Vazgeç ile geri alınır.
- TEXT_SNIPPET: WhatsApp / E-posta / Not kaynak türleri. Kart dört satırla sınırlıdır; “Metni aç” tam metni kaydırılabilir okuyucuda gösterir. Düzenle sırasında yalnızca seçilen alıntı kaldırılır.
- ACTION_SEND ve ACTION_PROCESS_TEXT: kullanıcı “Hatırlatıcıya ekle” ekranında arama yapıp mevcut hedef hatırlatıcıyı seçer ve Ekle’ye basar. Tek başına HTTP(S) URL mevcut link alanına gider; URL içeren normal cümle metin alıntısı olarak kalır.
- Kaynak uygulama tahmin edilmez. Varsayılan Not’tur; kullanıcı küçük seçiciden WhatsApp veya E-posta’yı seçebilir.
- Kaynakta tamamlanmış ACTION_SEND_MULTIPLE desteği de bulunur; son kapsam daraltmasından sonra bunun için ek geliştirme yapılmadı.

## Detay yerleşimi ve mevcut sistemler

Sıra: ana içerik → mevcut fotoğraf kolajı → Ekler → metin alıntıları → GPS → günlük → mevcut zaman bilgileri.

Fotoğraf boyutlandırma, kolaj ve fotoğraf kutucuğu fonksiyonları taban ZIP ile aynı kaldı. Yeni dosyalar fotoğraf kotasını tüketmez. İçerik yüzeyleri sakin; Liquid kartlarında mevcut LiquidGlassContentSurface, içe aktarma üst/alt çubuklarında mevcut DesignPanel/Liquid chrome kullanılır. Tam metin okuyucusu ayrı bir pencere olduğu için aynı materyalin statik fallback yolunu kullanır.

Now Bar, favori, tarih/tekrar seçicisi, tema adları, haptik, sesli giriş ve bildirim denetleyicileri yeniden tasarlanmadı. Hatırlatıcıdan başlatılmış geçerli Google Maps dönüşü mevcut MainActivity akışına yönlendirilir.

## Veri modeli ve uyumluluk

`ReminderAttachment(id, localPath)` korunarak şu isteğe bağlı alanlar eklendi:

- `type`: IMAGE / FILE / TEXT_SNIPPET; eksik alan eski IMAGE anlamına gelir.
- `uri`, `mimeType`, `displayName`, `sizeBytes`.
- `text`, `source`, `sourceTitle`.

Aynı `attachments` JSON dizisi ve aynı reminder depolama anahtarları kullanılır. Eski fotoğraflar ve `referenceUrl` yüklenmeye devam eder. Yıkıcı veya toplu veri dönüşümü yoktur. Kaynak türü tanınmazsa Not kullanılır. Eksik ek dizisi boş liste olarak açılır.

İçe aktarma mevcut kayda yalnızca ekleri/linki birleştirir; zamanlama, tekrar, kategori ve reminder metnini değiştirmez. Kararlı ek kimlikleri aynı hazırlanmış paylaşımın yeniden denenmesinde çoğalmayı önler. Edit Kaydet çağrısı başarı sonucunu bekler; kayıt başarısızsa düzenleyici açık kalır.

## Manifest ve URI stratejisi

- Yeni AttachmentImportActivity: SEND, SEND_MULTIPLE (`*/*`) ve PROCESS_TEXT (`text/plain`) intent filtreleri.
- MainActivity launcher ve mevcut harita dönüş davranışı korunur. Doğrudan gelen genel paylaşım intent’i de yeni hedef seçimine yönlendirilir.
- Yeni, dışa kapalı AttachmentFileProvider; yalnızca `files/reminder_attachments/` dizini paylaşılabilir.
- Yeni depolama veya hesap erişimi izni yok.
- Document picker’ın kalıcı okuma izni alınabiliyorsa URI saklanır; dosya gereksiz yere kopyalanmaz.
- Geçici paylaşım URI’si kalıcılaştırılamıyorsa tamponlu akışla özel depoya kopyalanır. Fotoğraflar mevcut özel-dosya fotoğraf sistemine gider.
- Kopyalama ve metadata sorguları IO iş parçacığında yürür. Dosya içeriği metadata göstermek için yüklenmez.
- Açma sırasında content URI, MIME ve FLAG_GRANT_READ_URI_PERMISSION kullanılır. Eksik görüntüleyici veya erişilemeyen dosya kullanıcıya bildirilir.
- Kopyalanmış dosya ancak metadata değişikliği başarıyla kaydedildikten ve başka kayıt tarafından kullanılmadığı görüldükten sonra silinir. Dış sağlayıcıdaki kaynak dosya silinmez. Vazgeç, kullanılmayan özel kopyaları temizler.
- Hazırlanmış paylaşım yerel olarak kontrol noktalarıyla saklanır. Ekran/işlem yeniden oluşturulduğunda hedef seçiminden devam edebilir.

Android referansları: [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files), [FileProvider](https://developer.android.com/reference/androidx/core/content/FileProvider).

## Kontroller ve testler

- `git --no-pager diff --check`: geçti.
- Değişen/eklenen 16 Kotlin dosyasında sözcüksel parantez/metin/yorum dengesi: geçti; bu bir Kotlin derlemesi değildir.
- Manifest ve provider XML ayrıştırması: geçti.
- Korunan 20 bildirim/ses/Maps/seçici/tema/haptik dosyası taban ZIP ile bayt düzeyinde aynı. Fotoğraf kolaj fonksiyonları aynı.
- `./gradlew :app:compileOptimizedKotlin`: çalıştırıldı; Gradle 8.11.1 indirilirken `java.net.SocketException: Network is unreachable` ile durdu. Kotlin derleyicisi başlamadı. Derleme başarılı olarak doğrulanamadı.
- `:app:testOptimizedUnitTest`: derleme ön koşulu sağlanamadığından çalıştırılmadı.
- Sekiz odaklı JUnit testi eklendi: eski fotoğraf/link yükleme; FILE ve tüm snippet türlerinin round-trip’i; URL/metin yönlendirmesi; bekleyen paylaşımın saklanması; tekrar denemede çoğalmama/zamanlamanın korunması; fotoğraf kotası; açık link kaldırma; özel dizin dışına taşmayan dosya silme. Eski dosya silme testi güvenli API’ye uyarlandı.
- Cihaz testi, görsel çalışma zamanı testi ve gerçek belge sağlayıcısı testi yapılmadı. Doğrulanmamış çalışma zamanı davranışı başarılı ilan edilmez.

## Bilinen sınırlar / tamamlanmayan doğrulama

Temel istenen yollar kaynakta uygulanmıştır; açık bir yarım kod veya TODO uygulaması bırakılmadı. Derleme ve cihaz doğrulaması ortam nedeniyle tamamlanamadı.

- Mevcut veri modeli hatırlatıcı başına tek web bağlantısı tutar. Farklı bağlantı içe aktarılırken kullanıcı değiştirmeyi açıkça onaylar.
- Mevcut üç fotoğraf sınırı korunur. Bir paylaşımda en fazla 20 akış, metinde 100.000 karakter; özel depoya kopyalanacak her dosyada 100 MiB sınırı vardır. Kalıcı URI ile tutulan belge için boyut nedeniyle kopyalama yapılmaz.
- Aynı anda bir paylaşım seçimi açıktır. Açık seçim sırasında gelen yeni paylaşımın yeniden gönderilmesi istenir; açık içerik sessizce değiştirilmez.
- Hedef seçimi mevcut hatırlatıcılar içindir. Hiç kayıt yoksa önce REAL’da bir hatırlatıcı oluşturulmalıdır.
- Dış sağlayıcı belgeyi siler veya izni kaldırırsa kalıcı URI de okunamayabilir; açma hatası gösterilir. Kullanılmayan kalıcı URI izinlerinin otomatik bırakılması bu kapsamda yoktur.
- Kaynak türü kullanıcı seçimiyle belirlenir; WhatsApp/e-posta hesabı entegrasyonu veya içerik geçmişi erişimi yoktur.

## Kesin değişen dosyalar
- `README_PHASE_6A.md`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/cagri/bigboscagri/AttachmentFileProvider.kt`
- `app/src/main/java/com/cagri/bigboscagri/AttachmentImportActivity.kt`
- `app/src/main/java/com/cagri/bigboscagri/MainActivity.kt`
- `app/src/main/java/com/cagri/bigboscagri/data/AttachmentJson.kt`
- `app/src/main/java/com/cagri/bigboscagri/data/AttachmentStorage.kt`
- `app/src/main/java/com/cagri/bigboscagri/data/IncomingContent.kt`
- `app/src/main/java/com/cagri/bigboscagri/data/PendingContentStore.kt`
- `app/src/main/java/com/cagri/bigboscagri/data/ReminderItem.kt`
- `app/src/main/java/com/cagri/bigboscagri/data/ReminderRepository.kt`
- `app/src/main/java/com/cagri/bigboscagri/data/SharedPayload.kt`
- `app/src/main/java/com/cagri/bigboscagri/ui/ReminderDetailScreen.kt`
- `app/src/main/java/com/cagri/bigboscagri/ui/ReminderScreen.kt`
- `app/src/main/java/com/cagri/bigboscagri/ui/components/AttachmentImportScreen.kt`
- `app/src/main/java/com/cagri/bigboscagri/ui/components/ReminderContentSections.kt`
- `app/src/main/res/xml/attachment_paths.xml`
- `app/src/test/java/com/cagri/bigboscagri/AttachmentContentTest.kt`
- `app/src/test/java/com/cagri/bigboscagri/ReminderAttachmentTest.kt`
- `gradlew` — yalnızca çalıştırılabilir dosya izni.
- `reports/PHASE_6A.md`
- `reports/STATIC_PHASE_6A.txt`
- `reports/compilePhase6A.log`
