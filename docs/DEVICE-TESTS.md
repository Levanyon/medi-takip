# Physical-device verification (1.2)

One combined checklist for the Samsung phone, covering Phases 1–6. It is written in Turkish because it follows the Turkish UI labels. For each item, write down the result (✓ / ✗ + a short note), the Android version and the date. Nothing here has been run on a device by the development session.

## 0. Güncellemeden önce (veri güvenliği)

1. Telefondaki MediTakip'in sürümünü not al (Ayarlar → Uygulama Hakkında; 1.1 bekleniyor).
2. 1.1'de yedek özelliği yok. Telefonda gerçek kayıt varsa **Geçmiş tablosunun ve kişi/ilaç listelerinin ekran görüntülerini al.**
3. Bilgisayarda `apksigner verify --print-certs` ile `app-release.apk`'nın SHA-256 değerinin `84e80aac…0687e9817f` olduğunu doğrula (RELEASE.md).
4. APK'yı **kaldırmadan** mevcut uygulamanın üzerine kur. "Çakışma" ya da "sürüm düşürme" hatası çıkarsa dur; hiçbir şeyi silme.

## 1. Güncelleme ve veri (migration 1 → 2)

5. Uygulama açılıyor. Kişiler, ilaçlar ve Geçmiş, ekran görüntüleriyle birebir aynı.
6. Eski işaretlenmiş dozlar Geçmiş'te "✓ İçildi • saat" olarak duruyor; geçmiş günlerdeki ilaç adı ve doz o günkü haliyle görünüyor.
7. Bugün'de bir dozu İçildi yap → Geri Al → tekrar İçildi. Uygulamayı kapatıp aç: kayıt duruyor.

## 2. Kişiler, ilaçlar, arşiv

8. Yeni kişi ve birden fazla saatli, farklı günlü bir ilaç ekle. Kaydet düğmesi hem üç tuşlu gezinmede hem hareket gezinmesinde görünüyor; klavye açıkken de ulaşılabiliyor.
9. Boş ad, geçersiz saat, hiç gün seçilmemesi, bitişin başlangıçtan önce olması: anlaşılır uyarı var, kayıt oluşmuyor.
10. Bir ilacın dozunu düzenle: bugünden sonrası yeni dozla görünüyor, geçmiş günler eski dozla kalıyor.
11. İlacı **Arşivle** → Bugün'den ve hatırlatmalardan kalkıyor, Geçmiş'te duruyor. **Arşivden Çıkar** → bugünden itibaren geri geliyor, geçmiş değişmiyor.
12. Kişiyi Arşivle → Arşivden Çıkar: kişi geri geliyor, ilaçları arşivde kalıyor ve bunu söyleyen bir bilgi mesajı çıkıyor.

## 3. Bugün ve günlük kullanım

13. Kişi seçici çalışıyor; seçim Geçmiş ile ortak ve uygulama yeniden açıldığında hatırlanıyor.
14. Yalnızca ilk bekleyen doz vurgulu, ama her bekleyen dozun kendi İçildi düğmesi var. İşaretleyince Geri Al'lı bir Snackbar çıkıyor.
15. **Geç işaretleme:** saati geçmiş bir dozu gerçek saatiyle işaretle ("Saati değiştir"); Geçmiş'te o saat görünüyor.
16. **Atla:** notlu ve notsuz dene. "Atlandı" görünüyor ve ilerleme sayısı buna göre değişiyor.
17. **Stok:** bir ilaçta stok takibini aç (ör. 5 adet, doz başı 1). İçildi'de stok 1 azalıyor, Geri Al'da geri geliyor. Eşiğin altına inince "Azalıyor" satırı ve **bir** bildirim çıkıyor. "Stok ekle" ile artırınca satır kalkıyor.

## 4. Geçmiş

18. Tarih sütunu sabit kalıyor, ilaç sütunları birlikte yatay kayıyor. Küçük bir tablo ortalanıyor.
19. Hücre durumları doğru: ✓ saatle, ○ işaretlenmedi, – planlanmadı. Hücreye dokununca ayrıntı açılıyor.
20. **Düzeltme:** geçmiş bir gün için İçildi işaretle, saati değiştir, kaydı kaldır. Her birinde tarihi söyleyen bir onay çıkıyor, yalnızca o hücre değişiyor.

## 5. Hatırlatmalar (en önemli bölüm)

21. Ayarlar → **Hatırlatma Durumu:** her gereksinim (bildirim izni, kanal, alarm izni, pil) doğru durumda ve "Düzelt" düğmeleri ilgili Android ayarını açıyor. **Test bildirimi gönder** ile bildirim geliyor.
22. 3–4 dakika sonrasına bir doz ekle, uygulamadan çık, ekranı kilitle. Bildirim zamanında geliyor ve **başlıkta önce kişi adı** var.
23. Kilitliyken bildirimdeki **İçildi**: doz Bugün'de işaretli, bildirim kapanıyor, ikinci hatırlatma gelmiyor.
24. **10 dk ertele:** 10 dakika sonra aynı doz yeniden bildiriliyor.
25. İşaretlemezsen **ikinci hatırlatma** varsayılan olarak 30 dk sonra geliyor. Ayarlar'dan kapatınca gelmiyor.
26. Aynı saatte iki ilaç: bildirimler kişi bazında gruplanıyor, ikisi de ayrı ayrı işaretlenebiliyor.
27. **Yeniden başlatma:** alarmdan önce telefonu kapatıp aç, kilidi açma; bildirim yine geliyor.
28. **Doze:** telefonu 1–2 saat hiç dokunmadan, ekranı kapalı bırak; hatırlatma geliyor (en fazla birkaç dakika gecikmeyle).
29. Uygulamayı son uygulamalardan kaydırıp kapat: hatırlatma yine geliyor. (Zorla durdurmada Android, uygulama yeniden açılana kadar alarm vermez; bu beklenen bir durum.)
30. Saati veya saat dilimini elle değiştir: gelecek hatırlatmalar yeni yerel saate uyuyor, çift kayıt oluşmuyor.
31. Bildirim iznini, kanalı ve alarm iznini ayrı ayrı kapat: uygulama çökmüyor, Hatırlatma Durumu durumu açıklıyor. Tekrar açınca hatırlatmalar geri geliyor.

## 6. Yedek ve rapor

32. Ayarlar → Yedekleme → **Yedek Oluştur:** yerel bir klasöre (ör. İndirilenler) kaydet. "Son yedek" bugünü gösteriyor, Bugün'ün altındaki hatırlatma satırı kalkıyor.
33. **Yedekten Geri Yükle:** aynı dosyayı seç. Önizleme (tarih, kişi, ilaç, kayıt sayısı) ve açık bir onay çıkıyor. Onaylamadan vazgeç: hiçbir şey değişmiyor.
34. Rastgele bir dosya (ör. bir fotoğraf) seç: "Yedek dosyası bozuk…" mesajı çıkıyor, veriler değişmiyor.
35. Bir kayıt ekle, sonra yedeği geri yükle: veriler yedekteki hale dönüyor ve hatırlatmalar yeniden kuruluyor. Ayarlar'daki **Geri yükleme öncesi kopyayı incele** geri yüklemeden önceki hali gösteriyor.
36. Kişi Detay → **Doktor raporu oluştur / paylaş:** PDF paylaşım ekranı açılıyor ve bir PDF görüntüleyicide açılabiliyor. Türkçe harfler doğru, uzun listelerde sayfa geçişi düzgün, her sayfanın altında "Bu rapor kullanıcının girdiği kayıtlardan oluşturulmuştur." yazıyor. Raporda yorum veya öneri yok.

## 7. Görünüm ve erişilebilirlik

37. Yazı boyutunu en büyüğe al: Bugün, Kişiler, Geçmiş, Ayarlar ve formlarda hiçbir şey kesilmiyor, Kaydet ulaşılabilir.
38. Açık ve koyu tema, yatay ekran: sistem çubuklarının altında kalan içerik yok.
39. TalkBack ile Bugün'de bir dozu işaretle: düğmeler anlamlı okunuyor.
40. Uygulama simgesi, açılış ekranı ve Ayarlar'daki "MediTakip 1.2" doğru.

## 8. Paralel kullanım

41. Ailenin mevcut hatırlatma yöntemini **1–2 hafta** bırakmadan ikisini birlikte kullan. Kaçan veya geciken her hatırlatmayı tarih ve saatiyle, ayrıca Hatırlatma Durumu'ndaki son hataları not et.
