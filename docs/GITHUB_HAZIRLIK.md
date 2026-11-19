# MediTakip — GitHub yayın hazırlığı

İnceleme tarihi: **19 Kasım 2026**. Bu rapor kaynak kodunun paylaşım hazırlığını kapsar; tıbbi kullanım veya mağaza yayını onayı değildir.

## Bulunan proje ve yayın hedefi

- Proje: Kotlin/Jetpack Compose/Room tabanlı MediTakip; mevcut Android proje klasörü doğrulandı.
- GitHub hesabı: `Levanyon`.
- Mevcut depo: [Levanyon/medi-takip](https://github.com/Levanyon/medi-takip), **public**; varsayılan dal `main`. Yeni depo oluşturulmasına gerek yok.
- İnceleme başlangıcındaki dal: `ui/premium-redesign-v2`, HEAD `f8e2db5917e6e892c0cad23f2736497c05824f11`.
- Başlangıçta 48 değiştirilmiş ve 10 yeni dosya vardı. Bu önceden var olan uygulama geliştirmeleri korunmuştur.
- Uzak dalın 9 commit, uzak `main` dalının 11 commit ilerisindeydi. `main`, mevcut HEAD'in atasıdır; bu durum geçmişi yeniden yazmadan ilerletmeye uygundur. Push öncesi uzak durum yeniden kontrol edilmelidir.

## Bu hazırlıkta yapılan değişiklikler

1. `README.md`: Türkçe amaç, özellikler, teknik sürümler, kod rehberi, Windows kurulumu, veri/gizlilik açıklamaları ve bilinen sınırlar.
2. `.gitignore`: Android/Windows geliştirme artıkları, anahtarlar, ortam dosyaları, veritabanları, yedekler ve paket çıktıları için ek kurallar.
3. `docs/PHASE10_RELEASE_REVIEW.md`: Üç makineye özel mutlak yol, proje içi göreli yola dönüştürüldü.
4. `docs/screenshots/`: Testteki yapay örnek verilerden Bugün, Kişiler ve Geçmiş görselleri.
5. Bu rapor.

Mevcut kaynak kodu, test kodu, veritabanı şemaları, bağımlılıklar ve imzalama ayarları değiştirilmedi. Değiştirilen mevcut belgelerin özgün kopyaları proje dışında yerel çalışma klasöründe saklandı. Telefona kurulum yapılmadı, gerçek kullanıcı verisi okunmadı/değiştirilmedi, imzalama anahtarları açılmadı veya kopyalanmadı.

## Güvenlik ve kişisel bilgi kontrolü

Yöntem: Yerel dosya adı ve metin kalıbı taraması; Git'in erişilebilir tüm dallarındaki geçmiş dosya nesneleri ve commit metaverileri; ilgili yapılandırma/kod incelemesi. Gizli değerler bir dış tarama servisine gönderilmedi.

| Bulgu | Durum ve kanıt | Sonuç / kalan sınır |
| --- | --- | --- |
| Özel anahtar, bilinen servis token'ı, kimlik bilgili URL ve sabit parola kalıpları | **Doğrulandı:** Başlangıçtaki 235 aday dosya ile 56 commit içindeki 501 geçmiş dosya nesnesinde bu kalıplarda bulgu yok. | Kalıp taraması bilinmeyen biçimdeki tüm sırların yokluğunu garanti etmez; bağımlılık veya sızma testi değildir. |
| Yerel ayarlar, imzalama dosyaları ve veriler | **Doğrulandı:** İzlenen dosyalarda ve taranan geçmiş dosya adlarında `local.properties`, anahtar, veritabanı veya paket çıktısı bulunmadı. Mevcut yerel ayar dosyası hariç tutuluyor. | `.tools`, derleme/cache klasörleri ve proje dışındaki anahtar içerikleri taramaya alınmadı; paylaşım kapsamı dışındalar. |
| Kişisel bilgisayar yolları | **Doğrulandı:** Güncel belgede bulunan üç yol genelleştirildi. Eski `RELEASE.md` / `V1.1.md` sürümlerinde üç geçmiş nesnesinde yerel yol var; bunlar zaten uzak `main` geçmişinde bulunuyor. | **Potansiyel gizlilik riski:** Eski geçmişteki kullanıcı/klasör adı kalır. Sır/anahtar bulgusu yok; bu hazırlıkta geçmiş yeniden yazılmadı. |
| Commit kimliği | **Doğrulandı:** Yazar/kaydeden e-postası GitHub `noreply` adresidir; mevcut commit mesajlarında araç ortak-yazar adresleri de bulunur. | Yazar adı ve GitHub kullanıcı adı commit geçmişinde görünür olmaya devam eder. |
| Katalogdaki sayısal eşleşmeler | **Doğrulandı:** Cihaz seri numarası kalıbının eşleşmeleri katalogdaki ürün metinleriyle ilişkilidir. | Tarama eşleşmesi tek başına gerçek kişisel veri kanıtı değildir. |
| Ekran görüntüleri | **Doğrulandı:** Görseller `ScreenshotTest.kt` içindeki yapay kişi/ilaç kayıtlarından üretilir; seçilen üç görsel görsel olarak incelendi. | Gerçek telefon, sağlık kaydı veya kişisel dosya kullanılmadı. |

`.gitignore` kontrolleri; yerel SDK/önbellek, `local.properties`, `.env`, imzalama dosyaları, APK/AAB, veritabanı yan dosyaları, `.medibackup` ve uygulama yedek adlarını kapsar. Gradle yapılandırmaları, Wrapper ve `app/schemas` dosyaları paylaşımda kalır. Mevcut uygulama/test/şema dosyalarının başlangıçtaki SHA-256 özetleriyle eşleşmesi ayrıca kontrol edildi.

## Doğrulama

Mevcut yerel Windows JDK/SDK ve önceden indirilmiş bağımlılıklar kullanıldı. PowerShell'de görsel testler etkinleştirildi:

```powershell
. .\tools\env.ps1
$env:MEDITAKIP_SCREENSHOTS = '1'
.\gradlew.bat --offline testDebugUnitTest assembleDebug lintDebug --rerun-tasks
```

- **Başarılı:** 61 test paketi; 774 test, 0 başarısızlık, 0 hata, 0 atlanan test.
- **Başarılı:** `assembleDebug`; debug APK yeniden üretildi.
- **Başarılı:** `lintDebug`; 0 hata, 8 uyarı (1 VectorRaster, 7 UnusedResources).
- Gradle sonucu: `BUILD SUCCESSFUL in 4m 51s`; 56 görev yeniden çalıştırıldı.
- Üç README görseli bu test çalışmasının çıktılarından alındı ve incelendi.
- Derleyicide mevcut kullanımdan kaldırılmış API uyarıları da görülür; gizlenmedi veya kod değişikliğiyle bastırılmadı.

`git diff --check` ve yerel README bağlantı/görsel kontrolleri ayrıca yapıldı. Derleme/test çıktıları Git'e eklenmedi.

## Bilinen sınırlar

- API 36 yapılandırması, AGP 8.9'un resmi API 35 destek sınırının üzerindedir. Bu fark README'de belirtilmiştir; mevcut geliştirme ortamını değiştirecek bir sürüm yükseltmesi yapılmadı. [Resmi uyumluluk tablosu](https://developer.android.com/build/releases/agp-8-9-0-release-notes#compatibility).
- Gerçek cihazdaki alarm teslimi, kamera, kilit ekranı, yeniden başlatma, Keystore ve TalkBack kontrolü bu çalışmada yapılmadı. [Cihaz kontrol listesi](PHASE10_DEVICE_QA.md) geçerlidir.
- Temiz bir bilgisayarda ilk internetli bağımlılık indirmesi ayrıca denenmedi; Windows yönergeleri mevcut yapılandırma ve yerel derleme ile kontrol edildi.
- Projenin kendi kodu için lisans seçilmedi. Katalog için mevcut lisans metni korunmuştur.
- Taranan kapsam erişilebilir Git geçmişidir; erişilemeyen/silinmiş nesneler, uzak depodaki farklı gizli referanslar ve başka depolar kapsam dışındadır.

## Yayın için son adım

Kullanıcının onayından sonra; bu hazırlık ile önceden mevcut uygulama geliştirmeleri birlikte kayıt altına alınabilir ve mevcut public deponun `main` dalı geçmişi koruyan normal push ile ilerletilebilir. Yeni bir depo, force push, uygulama kaldırma, veri temizleme veya release anahtarı değişimi gerekmiyor.

Bu raporun hazırlandığı aşamada **GitHub'a push yapılmadı**. Yayın tamamlandığında uzak commit kimliği, dosya listesi ve README görselleri tekrar doğrulanmalıdır.


### Yayın onayı — 19 Kasım 2026

Kullanıcı mevcut public deponun güncellenmesini onayladı. Tanıtıma GitHub profilindeki Mustafa Mert Uçak adı eklendi. Claude görseli/katkıcı görünümü değişikliği talebi geri çekildi; Git geçmişi korunarak normal push uygulanacak. Yukarıdaki “push yapılmadı” ifadesi yayın öncesi inceleme anını kaydeder.
