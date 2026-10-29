package com.gamessub.caption.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.gamessub.caption.model.DominantSubtitleColor
import com.gamessub.caption.model.ParsedDialogueLine
import com.gamessub.caption.model.TtsVoiceMode
import java.util.Locale
import kotlin.math.abs

/**
 * Live Dubbing (TTS) Engine with:
 * 1. Single Narrator Mode (Tek Ses)
 * 2. Multi-Character Mode (Çoklu Karakter Sesi):
 *    - Extracts character name ("Geralt:", "[Yennefer]") and skips speaking the name aloud.
 *    - Assigns a persistent Voice/Pitch profile per character name across the whole game session.
 *    - Falls back to Subtitle Text Color (White / Yellow / Cyan / Green / Amber) when no character name is present.
 * 3. Auto Catch-Up:
 *    - If a new subtitle arrives while the previous line is still speaking, interrupts cleanly and
 *      scales speech rate (1.0x .. 1.45x) based on subtitle length so dubbing never lags behind.
 */
class MultiVoiceTtsManager(
    context: Context,
    private val onReadyChanged: (Boolean) -> Unit = {}
) : TextToSpeech.OnInitListener {

    data class CharacterVoiceProfile(
        val label: String,
        val pitch: Float,
        val baseRateMultiplier: Float,
        val preferredVoiceIndex: Int
    )

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false
    private var currentLocale: Locale = Locale("tr", "TR")

    // Character Name -> Persistent Voice Profile
    private val characterVoiceMap = mutableMapOf<String, CharacterVoiceProfile>()

    // Predefined distinct voice profiles (Deep Male, Warm Female, Crisp Young, Resonant Veteran, etc.)
    private val voiceTemplates = listOf(
        CharacterVoiceProfile("Profil A (Derin / Tok Ses)", pitch = 0.80f, baseRateMultiplier = 0.98f, preferredVoiceIndex = 0),
        CharacterVoiceProfile("Profil B (Berrak / İnce Ses)", pitch = 1.22f, baseRateMultiplier = 1.04f, preferredVoiceIndex = 1),
        CharacterVoiceProfile("Profil C (Doğal Dengeli)", pitch = 1.00f, baseRateMultiplier = 1.00f, preferredVoiceIndex = 2),
        CharacterVoiceProfile("Profil D (Genç / Dinamik)", pitch = 1.14f, baseRateMultiplier = 1.08f, preferredVoiceIndex = 3),
        CharacterVoiceProfile("Profil E (Ağırbaşlı / Karakteristik)", pitch = 0.88f, baseRateMultiplier = 0.95f, preferredVoiceIndex = 4)
    )

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.language = currentLocale
            onReadyChanged(true)
        } else {
            isInitialized = false
            onReadyChanged(false)
        }
    }

    fun setTargetLanguage(langCode: String) {
        val locale = when (langCode.lowercase()) {
            "tr" -> Locale("tr", "TR")
            "en" -> Locale.US
            "ja" -> Locale.JAPAN
            "ko" -> Locale.KOREA
            "zh" -> Locale.SIMPLIFIED_CHINESE
            "de" -> Locale.GERMAN
            "fr" -> Locale.FRENCH
            "es" -> Locale("es", "ES")
            "ru" -> Locale("ru", "RU")
            else -> Locale(langCode)
        }
        currentLocale = locale
        if (isInitialized) {
            tts?.language = locale
        }
    }

    /**
     * Speaks the parsed subtitle line and returns the active voice profile description for HUD display.
     */
    fun speakDialogue(
        parsedLine: ParsedDialogueLine,
        voiceMode: TtsVoiceMode,
        enabled: Boolean,
        userBaseSpeed: Float = 1.0f,
        autoCatchUpEnabled: Boolean = true
    ): String {
        val engine = tts ?: return "TTS Kapalı"
        if (!enabled || !isInitialized) return "Sessiz Mod"

        val textToSpeak = parsedLine.cleanSpokenText.trim()
        if (textToSpeak.isEmpty()) return "Hazır"

        val wasSpeaking = engine.isSpeaking

        // Determine Pitch & Voice profile
        val profile = when (voiceMode) {
            TtsVoiceMode.SINGLE_NARRATOR -> {
                CharacterVoiceProfile("Anlatıcı (Tek Ses)", pitch = 1.0f, baseRateMultiplier = 1.0f, preferredVoiceIndex = 0)
            }
            TtsVoiceMode.MULTI_CHARACTER -> {
                resolveMultiCharacterProfile(parsedLine.speakerName, parsedLine.dominantColor)
            }
        }

        // Select hardware voice from device pool if multiple voices exist for the target language
        applyDeviceVoiceIfAvailable(engine, profile.preferredVoiceIndex)

        // Auto Catch-Up: boost speed slightly if previous line was still playing or line is long
        val catchUpBoost = if (autoCatchUpEnabled) {
            val lengthFactor = when {
                textToSpeak.length > 120 -> 1.25f
                textToSpeak.length > 75 -> 1.14f
                else -> 1.0f
            }
            val interruptBoost = if (wasSpeaking) 1.12f else 1.0f
            (lengthFactor * interruptBoost).coerceIn(1.0f, 1.45f)
        } else {
            1.0f
        }

        val finalRate = (userBaseSpeed * profile.baseRateMultiplier * catchUpBoost).coerceIn(0.75f, 1.65f)
        engine.setPitch(profile.pitch)
        engine.setSpeechRate(finalRate)

        // QUEUE_FLUSH immediately interrupts unfinished stale subtitle so audio stays in sync with screen
        engine.speak(
            textToSpeak,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "utt_${System.currentTimeMillis()}"
        )

        val speedTag = String.format(Locale.US, "%.2fx", finalRate)
        return "${profile.label} • Hız: $speedTag"
    }

    private fun resolveMultiCharacterProfile(
        speakerName: String?,
        dominantColor: DominantSubtitleColor
    ): CharacterVoiceProfile {
        if (!speakerName.isNullOrBlank()) {
            val key = speakerName.trim().lowercase()
            return characterVoiceMap.getOrPut(key) {
                val index = abs(key.hashCode()) % voiceTemplates.size
                val template = voiceTemplates[index]
                template.copy(label = "$speakerName (${template.label})")
            }
        }

        // Color-based fallback when the game uses colored subtitles per speaker instead of names
        return when (dominantColor) {
            DominantSubtitleColor.WHITE -> voiceTemplates[2].copy(label = "Beyaz Altyazı Sesi")
            DominantSubtitleColor.YELLOW -> voiceTemplates[0].copy(label = "Sarı Altyazı (Ana Karakter)")
            DominantSubtitleColor.CYAN_BLUE -> voiceTemplates[1].copy(label = "Mavi/Cyan Altyazı (Yan Karakter)")
            DominantSubtitleColor.GREEN -> voiceTemplates[3].copy(label = "Yeşil Altyazı Sesi")
            DominantSubtitleColor.AMBER_RED -> voiceTemplates[4].copy(label = "Kehribar Altyazı Sesi")
        }
    }

    private fun applyDeviceVoiceIfAvailable(engine: TextToSpeech, preferredIndex: Int) {
        try {
            val voices: Set<Voice> = engine.voices ?: return
            val matching = voices.filter {
                it.locale.language.equals(currentLocale.language, ignoreCase = true) &&
                    !it.isNetworkConnectionRequired
            }.sortedBy { it.name }

            if (matching.isNotEmpty()) {
                val chosen = matching[preferredIndex % matching.size]
                engine.voice = chosen
            }
        } catch (_: Exception) {
            // Keep default voice if device TTS implementation restricts voice enumeration
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}

