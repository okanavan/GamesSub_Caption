package com.gamessub.caption.model

import androidx.compose.ui.graphics.Color

/**
 * 2D Point normalized to (0.0f .. 1.0f) relative to the full camera frame.
 */
data class NormalizedPoint(
    val x: Float,
    val y: Float
) {
    fun clamp(): NormalizedPoint = NormalizedPoint(
        x = x.coerceIn(0.01f, 0.99f),
        y = y.coerceIn(0.01f, 0.99f)
    )
}

/**
 * Represents the 4 INDEPENDENT corners of the TV/Monitor screen in the camera view.
 * Each corner (Top-Left, Top-Right, Bottom-Right, Bottom-Left) can move freely to match
 * any perspective/angle of the monitor!
 *
 * Used to perspective-warp (`Matrix.setPolyToPoly`) the monitor into a flat, proportional screen
 * AND used directly as the full-frame region for BLUR MODE.
 */
data class ScreenQuad(
    val topLeft: NormalizedPoint = NormalizedPoint(0.08f, 0.10f),
    val topRight: NormalizedPoint = NormalizedPoint(0.92f, 0.10f),
    val bottomRight: NormalizedPoint = NormalizedPoint(0.94f, 0.90f),
    val bottomLeft: NormalizedPoint = NormalizedPoint(0.06f, 0.90f)
) {
    fun clamp(): ScreenQuad = ScreenQuad(
        topLeft = topLeft.clamp(),
        topRight = topRight.clamp(),
        bottomRight = bottomRight.clamp(),
        bottomLeft = bottomLeft.clamp()
    )

    fun minX(): Float = minOf(topLeft.x, topRight.x, bottomRight.x, bottomLeft.x)
    fun maxX(): Float = maxOf(topLeft.x, topRight.x, bottomRight.x, bottomLeft.x)
    fun minY(): Float = minOf(topLeft.y, topRight.y, bottomRight.y, bottomLeft.y)
    fun maxY(): Float = maxOf(topLeft.y, topRight.y, bottomRight.y, bottomLeft.y)
}

/**
 * 4 Operating Zones in the Game Tracking HUD:
 * - Zones 1, 2, 3 (SUBTITLE, QUEST, DIALOGUE) have their own resizable rectangular frame
 *   positioned INSIDE the flattened/proportional ScreenQuad.
 * - Zone 4 (LIVE_AR_BLUR) does NOT have a separate calibration submenu; it uses the ENTIRE
 *   selected ScreenQuad (`Kameradan Ekran Seçimi` frame) and blurs its background!
 */
enum class RoiZoneType(
    val id: Int,
    val titleTr: String,
    val shortTitleTr: String,
    val subtitleTr: String,
    val badgeColor: Color,
    val isSingleShotDefault: Boolean,
    val isLiveArBlurMode: Boolean = false,
    val hasIndividualBoxCalibration: Boolean = true
) {
    SUBTITLE(
        id = 1,
        titleTr = "Ana Altyazı Seçimi",
        shortTitleTr = "Ana Altyazı",
        subtitleTr = "Seçilen monitör ekranı içinde alt-orta ana altyazı çerçevesi.",
        badgeColor = Color(0xFFFFD600),
        isSingleShotDefault = false,
        isLiveArBlurMode = false,
        hasIndividualBoxCalibration = true
    ),
    QUEST(
        id = 2,
        titleTr = "Görev Alanı Seçimi",
        shortTitleTr = "Görev Alanı",
        subtitleTr = "Seçilen monitör ekranı içinde görev (Quest) penceresi. Tek çekim okur ve uyur.",
        badgeColor = Color(0xFF00E5FF),
        isSingleShotDefault = true,
        isLiveArBlurMode = false,
        hasIndividualBoxCalibration = true
    ),
    DIALOGUE(
        id = 3,
        titleTr = "Kullanıcı Seçimi (Özel Alan)",
        shortTitleTr = "Kullanıcı Seçimi",
        subtitleTr = "Seçilen monitör ekranı içinde diyalog seçimi, envanter veya eşya açıklaması alanı.",
        badgeColor = Color(0xFF69F0AE),
        isSingleShotDefault = false,
        isLiveArBlurMode = false,
        hasIndividualBoxCalibration = true
    ),
    LIVE_AR_BLUR(
        id = 4,
        titleTr = "Blur Modu (Tüm Seçili Ekran)",
        shortTitleTr = "Blur Modu",
        subtitleTr = "Ayrı çerçeve seçilmez; 'Kameradan Ekran Seçimi' ile belirlenen tüm ekran çerçevesini bulanıklaştırıp çevirir.",
        badgeColor = Color(0xFFE040FB),
        isSingleShotDefault = false,
        isLiveArBlurMode = true,
        hasIndividualBoxCalibration = false
    );

    companion object {
        val CALIBRATABLE_SUB_ZONES = listOf(SUBTITLE, QUEST, DIALOGUE)
    }
}

/**
 * Coordinates (0.0f .. 1.0f) relative to the PROPORTIONAL / RECTIFIED Monitor Screen (`ScreenQuad`).
 */
data class NormalizedRoiRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceIn(0.06f, 0.98f)
    val height: Float get() = (bottom - top).coerceIn(0.05f, 0.98f)

    fun clamp(): NormalizedRoiRect {
        val minW = 0.08f
        val minH = 0.06f
        val l = left.coerceIn(0.01f, 0.99f - minW)
        val t = top.coerceIn(0.01f, 0.99f - minH)
        val r = right.coerceIn(l + minW, 0.99f)
        val b = bottom.coerceIn(t + minH, 0.99f)
        return NormalizedRoiRect(l, t, r, b)
    }

    companion object {
        fun defaultFor(zone: RoiZoneType): NormalizedRoiRect = when (zone) {
            RoiZoneType.SUBTITLE -> NormalizedRoiRect(0.10f, 0.72f, 0.90f, 0.94f)
            RoiZoneType.QUEST -> NormalizedRoiRect(0.58f, 0.10f, 0.96f, 0.46f)
            RoiZoneType.DIALOGUE -> NormalizedRoiRect(0.18f, 0.46f, 0.82f, 0.76f)
            // Blur mode uses the entire selected monitor screen (0% .. 100% of ScreenQuad)
            RoiZoneType.LIVE_AR_BLUR -> NormalizedRoiRect(0.0f, 0.0f, 1.0f, 1.0f)
        }
    }
}

enum class OcrScriptType(val displayName: String) {
    LATIN("Latin (TR, EN, ES, DE, FR, RU...)"),
    JAPANESE("Japonca (日本語 - Kanji/Kana)"),
    KOREAN("Korece (한국어 - Hangul)"),
    CHINESE("Çince (中文 - Basitleştirilmiş/Geleneksel)")
}

enum class TranslationEngineType(
    val title: String,
    val requiresPro: Boolean,
    val description: String
) {
    OFFLINE_MLKIT(
        title = "Offline (ML Kit On-Device)",
        requiresPro = false,
        description = "İnternetsiz, ücretsiz ve hızlı cihaz içi çeviri (59+ dil)."
    ),
    DEEPL_BYOK(
        title = "DeepL API (Kendi Anahtarın - BYOK)",
        requiresPro = true,
        description = "Kendi ücretsiz/Pro DeepL API anahtarınızla yüksek doğruluklu çeviri."
    ),
    GEMINI_AI_BYOK(
        title = "Gemini AI / Bağlam Duyarlı (BYOK)",
        requiresPro = true,
        description = "Son 4 altyazıyı hafızada tutarak oyun hikayesine ve karaktere uygun AI çeviri."
    )
}

enum class TtsVoiceMode(val title: String, val requiresPro: Boolean, val subtitle: String) {
    SINGLE_NARRATOR(
        title = "Tek Ses (Anlatıcı Modu)",
        requiresPro = false,
        subtitle = "Tüm altyazıları ve görev metinlerini tek ve doğal bir anlatıcı sesiyle okur."
    ),
    MULTI_CHARACTER(
        title = "Çoklu Karakter Sesi (Canlı Dublaj)",
        requiresPro = true,
        subtitle = "'İsim:' etiketini okumadan atlar; karakter ismine, yazı rengine veya diyalog sırasına göre sesi otomatik değiştirir."
    )
}

enum class GamerHudTheme(
    val title: String,
    val subtitle: String,
    val backgroundColor: Color,
    val cardSurfaceColor: Color,
    val primaryTextColor: Color,
    val accentColor: Color,
    val speakerBadgeColor: Color,
    val requiresPro: Boolean
) {
    STUDIO_SLATE(
        title = "Modern Koyu Slate (Studio Dark)",
        subtitle = "Lacivert/Antrasit modern uygulama zemini + Parlak Beyaz yazı",
        backgroundColor = Color(0xFF0F172A),
        cardSurfaceColor = Color(0xFF1E293B),
        primaryTextColor = Color(0xFFF8FAFC),
        accentColor = Color(0xFF38BDF8),
        speakerBadgeColor = Color(0xFFFACC15),
        requiresPro = false
    ),
    OLED_STEALTH(
        title = "OLED Tam Siyah (Stealth)",
        subtitle = "#000000 Saf Siyah zemin + Sıcak Beyaz yazı (Maksimum Pil Tasarrufu)",
        backgroundColor = Color(0xFF000000),
        cardSurfaceColor = Color(0xFF111111),
        primaryTextColor = Color(0xFFF5F5F0),
        accentColor = Color(0xFF90CAF9),
        speakerBadgeColor = Color(0xFFFFD54F),
        requiresPro = false
    ),
    CINEMATIC_OBSIDIAN(
        title = "Sinematik Antrasit & Sarı",
        subtitle = "#121318 Yumuşak Sinema Antrasiti + Klasik Altyazı Sarısı",
        backgroundColor = Color(0xFF121318),
        cardSurfaceColor = Color(0xFF1D1F27),
        primaryTextColor = Color(0xFFFFD600),
        accentColor = Color(0xFFFFEA00),
        speakerBadgeColor = Color(0xFF80D8FF),
        requiresPro = true
    ),
    WITCHER_EMERALD(
        title = "Zümrüt RPG (Emerald Forest)",
        subtitle = "#0B1914 Koyu Zümrüt Yeşili zemin + Buz Yeşili Beyaz yazı",
        backgroundColor = Color(0xFF0B1914),
        cardSurfaceColor = Color(0xFF132A22),
        primaryTextColor = Color(0xFFE6F4EA),
        accentColor = Color(0xFF69F0AE),
        speakerBadgeColor = Color(0xFFFFD54F),
        requiresPro = true
    ),
    CYBER_MIDNIGHT(
        title = "Siber Gece (Cyberpunk Midnight)",
        subtitle = "#0D0B1E Derin Siber Mor/Lacivert zemin + Neon Camgöbeği yazı",
        backgroundColor = Color(0xFF0D0B1E),
        cardSurfaceColor = Color(0xFF181436),
        primaryTextColor = Color(0xFF00E5FF),
        accentColor = Color(0xFFFF4081),
        speakerBadgeColor = Color(0xFF69F0AE),
        requiresPro = true
    ),
    EYE_CARE_AMBER(
        title = "Eye-Care Amber (Gece Sıcak Kahve)",
        subtitle = "#160F07 Sıcak Koyu Kahve zemin + Kehribar/Turuncu gece yazısı",
        backgroundColor = Color(0xFF160F07),
        cardSurfaceColor = Color(0xFF24190C),
        primaryTextColor = Color(0xFFFFAB00),
        accentColor = Color(0xFFFF6D00),
        speakerBadgeColor = Color(0xFFFFE57F),
        requiresPro = true
    )
}

enum class DominantSubtitleColor {
    WHITE,
    YELLOW,
    CYAN_BLUE,
    GREEN,
    AMBER_RED
}

data class ParsedDialogueLine(
    val rawText: String,
    val speakerName: String?,
    val cleanSpokenText: String,
    val dominantColor: DominantSubtitleColor = DominantSubtitleColor.WHITE
)

data class SupportedLanguage(
    val code: String,
    val nameTr: String,
    val nameNative: String
)

object SupportedLanguages {
    val ALL = listOf(
        SupportedLanguage("en", "İngilizce", "English"),
        SupportedLanguage("tr", "Türkçe", "Türkçe"),
        SupportedLanguage("ja", "Japonca", "日本語"),
        SupportedLanguage("ko", "Korece", "한국어"),
        SupportedLanguage("zh", "Çince", "中文"),
        SupportedLanguage("ru", "Rusça", "Русский"),
        SupportedLanguage("de", "Almanca", "Deutsch"),
        SupportedLanguage("fr", "Fransızca", "Français"),
        SupportedLanguage("es", "İspanyolca", "Español"),
        SupportedLanguage("pt", "Portekizce", "Português"),
        SupportedLanguage("it", "İtalyanca", "Italiano"),
        SupportedLanguage("pl", "Lehçe", "Polski"),
        SupportedLanguage("ar", "Arapça", "العربية")
    )
}
