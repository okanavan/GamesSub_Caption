# GamesSub_Caption (Oyun Altyazı & Görev Çevirici) — Proje Mimarisi ve Yol Haritası

Bu belge, **GamesSub_Caption (Oyun Altyazı)** Android uygulamasının tüm teknik mimarisini, özelliklerini, gelir modelini ve geliştirme adımlarını içerir. Yeni proje oturumunda kaldığımız yerden devam etmek için referans dosyasıdır.

---

## 1. Uygulama Özeti ve Temel Konsept
* **Proje / Klasör Adı:** `GamesSub_Caption`
* **Mağaza İsimleri:**
  * **Global (Google Play):** *GamesSub Caption: Screen & Quest Translator*
  * **Türkiye:** *Oyun Altyazı: Ekran ve Görev Çevirici*
* **Çalışma Prensibi:**
  1. Telefon monitör/TV karşısına sabitlenir.
  2. Kamera açılır ve ekranda çevrilmesi istenen bölgeler (**Region of Interest - ROI**) çerçeve içine alınarak seçilir:
     * **Alan 1:** Altyazı Alanı (Alt orta)
     * **Alan 2:** Görev (Quest) / İlerleme Penceresi
     * **Alan 3:** Diyalog Seçim / Eşya / Envanter Kutusu
  3. Alanlar seçildikten sonra **kamera görüntüsü ekrandan tamamen kaldırılır**.
  4. Ekranda sadece **tam ekran çeviri metni**, **seslendirme kontrolleri** ve **alanlar arası tek tuşla geçiş butonları** (Altyazı $\leftrightarrow$ Görev $\leftrightarrow$ Ek Alan) yer alır.
  5. Ayrıca ekrana bakmadan geçiş için **Fiziksel Ses Tuşları (Volume Up/Down)** kısayol olarak kullanılabilir.

---

## 2. Görüntü İşleme, Hareketli Arka Plan Filtresi ve OCR
Oyunlarda altyazı arkasındaki hareketli sahnelerin (patlamalar, karakter hareketleri) sahte tetikleme ve titreme yapmasını önlemek için **3 Aşamalı Akıllı Filtre**:
1. **Sadece Seçili Alanı Kırpma (ROI Cropping):** Kameranın gördüğü tüm ekran yerine sadece o an aktif olan kutunun pikselleri kırpılarak işlenir (%80+ CPU/Pil tasarrufu).
2. **Hareketli Arka Plan Filtresi & Dinamik Tarama Sıklığı:**
   * **Kontrast / Eşikleme (Thresholding):** Harf piksellerini (beyaz/sarı metin + siyah kontur) arka plan hareketinden ayırmak için yüksek kontrast filtresi uygulanır.
   * **Tarama Aralığı:** Aktif diyalog sırasında **300–500 ms** (saniyede 2-3 kare).
   * **Dinamik Uyku (Idle Mode):** 10 saniye boyunca altyazı çıkmazsa tarama hızı otomatik olarak **1000 ms (1 sn)** seviyesine düşer; yeni yazı algılanınca tekrar hızlanır.
   * **Görev Modu (On-Demand / Tek Çekim):** Görev penceresi seçildiğinde sürekli tarama yapmak yerine 1 kez net okuma yapıp çevirir ve kamerayı uyku moduna alır.
3. **Metin Kararlılık Filtresi (Anti-Flicker / Levenshtein Benzerliği):**
   * Yeni okunan OCR metni bir önceki metinle **%85 ve üzeri oranda aynıysa** yeniden çeviri ve seslendirme tetiklenmez; ekrandaki metin sabit tutulur.

---

## 3. Çapraz Dil Desteği ve Hibrit Çeviri Mimarisi (Offline + BYOK AI)
* **Uluslararası Çapraz Dil (Any-to-Any):**
  * **OCR Alfabesi:** Google ML Kit Text Recognition v2 (Latin, Japonca, Korece, Çince, Kiril/vb. destekli).
  * **Dil Çiftleri:** 59+ dil arasında çapraz çeviri (İngilizce, Türkçe, İspanyolca, Portekizce, Rusça, Almanca, Fransızca, Japonca, Korece, Çince, Arapça vb.).
  * **Çok Dilli Arayüz:** Uygulama menüleri global pazar için çoklu dil (`strings.xml`) destekli.
* **Çeviri Motorları (Sıfır Sunucu Maliyetli Yapı):**
  1. **Offline (Tamamen İnternetsiz - Varsayılan):** Google ML Kit On-Device Translation (İndirilen dil paketleriyle internetsiz ve ücretsiz çalışır).
  2. **DeepL API (BYOK - Kendi Anahtarını Gir):** Kullanıcının kendi ücretsiz/ücretli DeepL API anahtarını girerek kullandığı yüksek kaliteli çeviri.
  3. **Gemini AI / OpenAI API (BYOK - Bağlam Duyarlı):** Kullanıcının kendi API anahtarını girdiği, son 3-4 altyazıyı hafızada tutarak oyunun hikayesine ve karakterin konuşma tarzına göre çeviri yapan yapay zeka modu. (Uygulama içinde 3 adımlı *"Ücretsiz API Anahtarı Nasıl Alınır?"* rehberi yer alır).

---

## 4. Metni Sese Çevirme (TTS - Canlı Dublaj & Sesli Okuma)
* **Tek Ses (Anlatıcı Modu) vs. Çoklu Ses (Dinamik Karakter Ayrımı):**
  1. **İsimden Otomatik Karakter Ayrımı:** Altyazıda `Geralt: ...` veya `[Yennefer] ...` gibi karakter ismi varsa sistem ismi ayıklar, **ismi sesli okumadan atlar** ve her karaktere cihazdaki ses havuzundan farklı bir ses/ton (`Pitch` ve `Voice Profile` — Kalın Erkek, Kadın, Genç Ses vb.) atayarak oyun boyunca o karakteri hep aynı sesle okur.
  2. **Renkten Karakter Ayrımı:** İsim yazmayan ama farklı karakterleri farklı renkte (Beyaz / Sarı / Mavi) gösteren oyunlarda yazı rengine göre ses profili değiştirir.
  3. **Tek Ses (Anlatıcı Modu):** Ayrım olmayan altyazılarda veya görev (Quest) / oyun içi kitap okumalarında tek ve doğal bir anlatıcı sesi kullanır.
* **Akıllı Hızlandırma (Auto Catch-Up):** Yeni altyazı geldiğinde önceki okuma bitmediyse otomatik olarak yeni cümleye geçer ve konuşma hızını (`1.0x` – `1.5x`) senkronize eder.

---

## 5. Pil, Ekran Optimizasyonu ve "Gamer HUD" Görsel Teması
* **Previewless CameraX:** Alan seçimi bittikten sonra kamera önizlemesi (`PreviewView`) tamamen kapatılır; GPU yükü sıfırlanır.
* **AMOLED Tam Siyah (`#000000`) Arayüz:** OLED ekranlarda sadece harf pikselleri yanar, minimum pil harcar ve karanlık odada göz almaz.
* **Otomatik Karartma:** Uzun süre altyazı gelmediğinde ekran parlaklığını kısar, yazı geldiğinde normal seviyeye çıkarır.
* **Görsel Temalar:**
  1. *OLED Stealth:* `#000000` Siyah zemin + Sıcak Beyaz yazı
  2. *Sinematik Sarı:* `#000000` Siyah zemin + Klasik Altyazı Sarısı (`#FFD600`)
  3. *Eye-Care Amber:* Gece görüşü kehribar/turuncu (`#FFAB00`)
  4. *Cyber HUD / Neon:* Siber camgöbeği (`#00E5FF`) / Retro yeşil terminal stili

---

## 6. Gelir Modeli (Google Play Store) & VIP APK Ayrımı
* **Ücretsiz 7 Gün PRO Deneme + Aylık Abonelik (Google Play Billing):**
  * **İlk 7 Gün (Deneme) & Aylık PRO Aboneler:**
    * Çoklu alan seçimi (Altyazı + Görev + Diyalog)
    * %100 Reklamsız tam ekran
    * Kendi API Anahtarını (DeepL / Gemini AI) kullanabilme
    * Çoklu Karakter Seslendirme (Dinamik Dublaj) ve Ses Tuşlarıyla Geçiş
    * Tüm Gamer HUD temaları
  * **7 Gün Sonrası Ücretsiz (Free) Sürüm:**
    * Tek alan seçimi (Sadece Altyazı)
    * Offline çeviri + Tek sesli standart okuma
    * **Çeviriyi Engellemeyen Reklam Alanı:** Ekranın üst/orta %82'si tamamen çeviriye ayrılır, en alt %18'lik sabit şeritte çeviri metninin üstüne asla binmeyen AdMob Adaptive Banner yer alır.
* **İki Ayrı Derleme Çıktısı (Build Flavors) + Gizli VIP Kodu:**
  1. **`GamesSub_VIP_Admin.apk` (Senin Kendi Cihazın İçin):** Reklam, abonelik ve 7 gün sınırı kod seviyesinde tamamen devre dışı; ömür boyu %100 sınırsız PRO.
  2. **`GamesSub_PlayStore_Release.aab` (Google Play Mağaza Paketi):** 7 gün deneme, abonelik ve reklam kuralları aktif. Ayrıca Ayarlar'da sürüm numarasına 7 kez tıklayınca açılan **Gizli Master Şifre** ile istediğin cihazda sınırsız PRO aktif edilebilir.
