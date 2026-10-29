# GamesSub_Caption — Güncelleme ve Modernizasyon Planı (v1.2 — Perspektif & 4 Bağımsız Köşe Mimarisi)

Bu belge, **GamesSub_Caption** uygulamasının menü mimarisini, **4 Köşesi Bağımsız Hareket Edebilen Kameradan Ekran Seçimi (Perspective Quad / Homography)** sistemini, düzleştirilmiş ekran üzerinde alt alan seçimlerini, yazı odaklı ultra-minimal oyun takip ekranını ve **Blur Modu** çalışma prensibini içerir.

---

## 1. Giriş Ana Menüsü (Home Dashboard)
Uygulama açıldığında modern bir **Giriş Ana Menüsü** karşılar ve **3 Ana Kart** yer alır:
1. **🎮 Oyuna Başla (Takip Ekranı):** Kayıtlı ekran ve alan ayarlarıyla doğrudan çeviri/dublaj ekranını başlatır.
2. **📐 Ekran Ayarı (Kameradan Ekran & Alan Seçimi):** Monitörün 4 köşesini bağımsız seçme ve içindeki alt alanları ayarlama menüsünü açar.
3. **⚙️ Ayarlar:** Tema (Arka Plan + Yazı Rengi), Ses Hızı (`0.75x – 1.75x`), Çoklu Karakter Ses Stüdyosu, Dil ve API (Offline ML Kit / DeepL / Gemini AI) ayarlarını içerir.

---

## 2. İki Aşamalı Ekran Ayarı: (1) 4 Bağımsız Köşe İle Monitör Seçimi $\rightarrow$ (2) Düzleştirilmiş Ekran Üzerinde Alt Alanlar

Telefon monitör veya TV karşısına tam dik değil, açılı/çapraz konulmuş olabilir. Bu yüzden **Ekran Ayarı** menüsü şu 2 temel mantıkla çalışır:

### Aşama 1: Kameradan Ekran Seçimi (4 Bağımsız Köşe / Serbest Dörtgen)
* İlk alt menü: **`1. Kameradan Ekran Seçimi (4 Bağımsız Köşe)`**
* Tam ekran kamera açılır. Ekranda sabit bir dikdörtgen yerine, **her bir köşesi (`Sol-Üst`, `Sağ-Üst`, `Sağ-Alt`, `Sol-Alt`) birbirinden tamamen bağımsız hareket edebilen 4 noktalı bir poligon (Quad)** bulunur.
* Kullanıcı bu 4 köşe noktasını monitörün/TV'nin gerçek 4 köşesine sürükleyip bırakır.
* Sistem, bu 4 bağımsız köşeyi **Perspektif Düzeltme (`android.graphics.Matrix.setPolyToPoly` / Homography Warp)** ile tam karşıdan bakıyormuş gibi düzgün 16:9 / 16:10 dikdörtgen bir ekrana dönüştürür (**Orantılı Düzleştirilmiş Ekran**).

### Aşama 2: Düzleştirilmiş Ekran Üzerinde Alt Alanların Seçimi
Monitörün 4 köşesi seçilip ekrana orantılı şekilde yerleştirildikten sonra, diğer 3 alt menü **bu düzleştirilmiş monitör görüntüsü içinde** seçilir:
* **`2. Ana Altyazı Seçimi`:** Düzleştirilmiş monitör çerçevesi içinde alt-orta altyazı kutusu (4 köşesinden ve 4 yan kenarından büyütülüp küçültülebilir).
* **`3. Görev Alanı Seçimi`:** Düzleştirilmiş monitör çerçevesi içinde görev/quest penceresi.
* **`4. Kullanıcı Seçimi (Özel Alan)`:** Düzleştirilmiş monitör çerçevesi içinde diyalog/eşya alanı.

---

## 3. Blur Modu (Ayrı Alan Seçimi Yok — Seçili Ekranın Tamamını Kullanır!)
* **Blur Modu için ayrı bir çerçeve/ekran seçimi yapılmaz.**
* Kullanıcı oyun takip ekranında **Blur Modu** sekmesine geçtiğinde:
  1. **Aşama 1'de seçilen "Kameradan Ekran Seçimi" (4 bağımsız köşeyle belirlenen monitör çerçevesinin tamamı)** baz alınır.
  2. Bu çerçeve içindeki görüntü bulanıklaştırılır (Frosted Blur) ve algılanan yazıların çevirisi doğrudan bu seçili ekran çerçevesinin üzerinde gösterilir.

---

## 4. Oyunu Takip Ekranı (Yazı Odaklı Ultra-Minimal HUD & Ses Hızı Kontrolü)
* **Üst Şeffaf Bar (Çok Dar & Yarı Saydam):**
  * Sol üstte şeffaf `← Menü` tuşu, sağ üstte şeffaf `Hız: 1.05x` (dokununca değişen ses hızı) ve `🔊 Ses Aç/Kapa` tuşu.
* **Merkez Alan (%90+ Ekran Alanı):**
  * Gereksiz büyük butonlar olmadan tamamen çeviri yazısına odaklanır.
* **Alt Şeffaf Bar (Çok Dar & Yarı Saydam Geçiş Şeridi):**
  * `Ana Altyazı` | `Görev Alanı` | `Kullanıcı Seçimi` | `Blur Modu (Tüm Ekran)`

---

## 5. Gelişmiş Tema Sistemi (Arka Plan + Yazı Rengi) & Çoklu Karakter Seslendirme
* **Temalar:** Sadece yazı rengi değil, arka plan rengi de değişir (*Modern Koyu Slate `#0F172A`*, *OLED Tam Siyah `#000000`*, *Sinematik Antrasit `#121318`*, *Zümrüt RPG `#0B1914`*, *Siber Gece `#0D0B1E`*, *Eye-Care Amber `#160F07`*).
* **Çoklu Karakter Seslendirme:** Altyazıdaki karakter ismine (`Geralt:`, `[Yennefer]`), altyazı rengine veya karşılıklı diyalog sırasına göre sesi otomatik değiştirir; karakter ismini sesli okumadan atlar.
