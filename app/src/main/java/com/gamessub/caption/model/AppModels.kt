package com.gamessub.caption.model

import androidx.compose.ui.graphics.Color

/**
 * 3 ROI (Region of Interest) zones selected on the monitor screen.
 * Coordinates are normalized (0.0f .. 1.0f) relative to camera frame dimensions.
 */
enum class RoiZoneType(
    val id: Int,
    val titleTr: String,
    val titleEn: String,
    val badgeColor: Color,
    val isSingleShotDefault: Boolean
) {
    SUBTITLE(
        id = 1,
        titleTr = "Altyazı (Alan 1)",
        titleEn = "Subtitle (Zone 1)",
        badgeColor = Color(0xFFFFD600),
        isSingleShotDefault = false
    ),
    QUEST(
        id = 2,
        titleTr = "Görev / Quest (Alan 2)",
        titleEn = "Quest / Objective (Zone 2)",
        badgeColor = Color(0xFF00E5FF),
        isSingleShotDefault = true // Quest mode: single clean read & sleep
    ),
    DIALOGUE(
        id = 3,
        titleTr = "Diyalog / Eşya (Alan 3)",
        titleEn = "Dialogue / Item (Zone 3)",
        badgeColor = Color(0xFF69F0AE),
        isSingleShotDefault = false
    )
}

data class NormalizedRoiRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceIn(0.05f, 1.0f)
    val height: Float get() = (bottom - top).coerceIn(0.05f, 1.0f)

    fun clamp(): NormalizedRoiRect {
        val l = left.coerceIn(0.02f, 0.90f)
        val t = top.coerceIn(0.02f, 0.90f)
        val r = right.coerceIn(l + 0.08f, 0.98f)
        val b = bottom.coerceIn(t + 0.06f, 0.98f)
        return NormalizedRoiRect(l, t, r, b)
    }

    companion object {
        fun defaultFor(zone: RoiZoneType): NormalizedRoiRect = when (zone) {
            // Bottom-center subtitle area
            RoiZoneType.SUBTITLE -> NormalizedRoiRect(0.12f, 0.72f, 0.88f, 0.92f)
            // Top-right or left quest tracker window
            RoiZoneType.QUEST -> NormalizedRoiRect(0.62f, 0.12f, 0.95f, 0.45f)
            // Center-bottom dialogue choices / item description box
            RoiZoneType.DIALOGUE -> NormalizedRoiRect(0.20f, 0.50f, 0.80f, 0.78f)
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
        subtitle = "'İsim:' veya '[İsim]' etiketini okumadan atlar; her karaktere ve yazı rengine farklı ses tonu atar."
    )
}

enum class GamerHudTheme(
    val title: String,
    val backgroundColor: Color,
    val primaryTextColor: Color,
    val accentColor: Color,
    val speakerBadgeColor: Color,
    val requiresPro: Boolean
) {
    OLED_STEALTH(
        title = "OLED Stealth (Tam Siyah + Sıcak Beyaz)",
        backgroundColor = Color(0xFF000000),
        primaryTextColor = Color(0xFFF5F5F0),
        accentColor = Color(0xFF90CAF9),
        speakerBadgeColor = Color(0xFFFFD54F),
        requiresPro = false
    ),
    CINEMATIC_YELLOW(
        title = "Sinematik Sarı (Klasik Altyazı)",
        backgroundColor = Color(0xFF000000),
        primaryTextColor = Color(0xFFFFD600),
        accentColor = Color(0xFFFFEA00),
        speakerBadgeColor = Color(0xFF80D8FF),
        requiresPro = true
    ),
    EYE_CARE_AMBER(
        title = "Eye-Care Amber (Gece Görüş Kehribar)",
        backgroundColor = Color(0xFF000000),
        primaryTextColor = Color(0xFFFFAB00),
        accentColor = Color(0xFFFF6D00),
        speakerBadgeColor = Color(0xFFFFE57F),
        requiresPro = true
    ),
    CYBER_HUD_NEON(
        title = "Cyber HUD (Siber Camgöbeği / Neon)",
        backgroundColor = Color(0xFF000000),
        primaryTextColor = Color(0xFF00E5FF),
        accentColor = Color(0xFF00B8D4),
        speakerBadgeColor = Color(0xFF69F0AE),
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

