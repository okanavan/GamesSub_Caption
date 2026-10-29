# GamesSub_Caption — Agent Context & Project Instructions

Bu çalışma alanı (**`GamesSub_Caption`**), Android için geliştirilen **Oyun Altyazı & Görev Çevirici (GamesSub Caption)** projesidir.

Kullanıcı yeni bir sohbet başlattığında veya *"devam edelim"* dediğinde, tüm mimari ve kararlar için önce [PROJE_MIMARISI.md](file:///c:/Users/okan/Documents/GamesSub_Caption/PROJE_MIMARISI.md) dosyasını oku.

## Hızlı Özet
1. **Çoklu ROI (Alan) Seçimi & Tam Ekran Okuma Modu:** Kamera ile monitörden Altyazı, Görev (Quest) ve Diyalog alanları seçilir; seçim sonrası kamera önizlemesi kapatılır (`Previewless CameraX`) ve tam ekran `#000000` OLED siyah çeviri ekranına geçilir. Tek tuşla veya fiziksel ses tuşlarıyla alanlar arası geçiş yapılır.
2. **Hareketli Arka Plan & Anti-Flicker OCR:** Sadece aktif ROI kırpılır, 300-500 ms (durgunlukta 1000 ms) aralıklarla kontrast eşikleme + ML Kit OCR v2 çalıştırılır, %85 Levenshtein metin benzerlik filtresi ile titreme önlenir.
3. **Çapraz Dil & Hibrit Çeviri:** Tüm diller arası (Latin, Japonca, Korece, Çince vb.) Offline ML Kit Translation + Kullanıcının kendi API anahtarını girdiği (BYOK) DeepL API ve bağlam duyarlı Gemini AI / OpenAI desteği.
4. **Canlı Dublaj (TTS - Tek Ses & Çoklu Karakter Sesi):** Altyazıdaki `İsim:` veya `[İsim]` etiketlerinden ya da yazı renginden konuşan karakteri ayıklayıp ismi okumadan her karaktere farklı ses/ton (Pitch/Voice) atayan çoklu ses sistemi + Anlatıcı (Tek ses) modu + Otomatik hızlandırma (Auto Catch-Up).
5. **Gelir Modeli & Çift Çıktı (VIP APK vs. Play Store AAB):**
   - Mağaza sürümü (`playstore` flavor): 7 gün ücretsiz PRO deneme, sonrasında aylık abonelik (Google Play Billing) veya çeviriyi kapatmayan alt %18 sabit AdMob banner reklamlı ücretsiz sürüm (sadece tek alan + offline çeviri).
   - Kullanıcıya özel sürüm (`vipAdmin` flavor) + Ayarlar'da 7 tıklamalı gizli Master Key: Ömür boyu %100 sınırsız, reklamsız PRO yetki.
