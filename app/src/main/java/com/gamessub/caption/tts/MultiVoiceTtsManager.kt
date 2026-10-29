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
 *    - Allows manual override of any character's voice profile from Settings.
 *    - Falls back to Subtitle Text Color (Yellow / Cyan / Green / Amber) OR Automatic Turn-Taking
 *      (Konuşmacı Sırası Değişimi: Ses A <-> Ses B) when no speaker name is written in the subtitle.
 * 3. User-Adjustable Speech Rate (`0.75x` .. `1.75x`) + Auto Catch-Up.
 */
class MultiVoiceTtsManager(
    context: Context,
    private val onReadyChanged: (Boolean) -> Unit = {}
) : TextToSpeech.OnInitListener {

    data class CharacterVoiceProfile(
        val id: Int,
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
    private var turnTakingToggle = false

    val voiceTemplates = listOf(
        CharacterVoiceProfile(0, "Ses A (Derin / Tok Erkek)", pitch = 0.78f, baseRateMultiplier = 0.98f, preferredVoiceIndex = 0),
        CharacterVoiceProfile(1, "Ses B (Berrak / İnce Kadın)", pitch = 1.24f, baseRateMultiplier = 1.04f, preferredVoiceIndex = 1),
        CharacterVoiceProfile(2, "Ses C (Doğal Anlatıcı)", pitch = 1.00f, baseRateMultiplier = 1.00f, preferredVoiceIndex = 2),
        CharacterVoiceProfile(3, "Ses D (Genç / Dinamik)", pitch = 1.14f, baseRateMultiplier = 1.07f, preferredVoiceIndex = 3),
        CharacterVoiceProfile(4, "Ses E (Ağırbaşlı / Kıdemli)", pitch = 0.86f, baseRateMultiplier = 0.95f, preferredVoiceIndex = 4)
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

    fun getAssignedCharacters(): Map<String, CharacterVoiceProfile> = characterVoiceMap.toMap()

    fun cycleCharacterVoiceProfile(speakerName: String): CharacterVoiceProfile {
        val key = speakerName.trim().lowercase()
        val current = characterVoiceMap[key] ?: voiceTemplates[0]
        val nextIndex = (current.id + 1) % voiceTemplates.size
        val updated = voiceTemplates[nextIndex]
        characterVoiceMap[key] = updated
        return updated
    }

    fun speakDialogue(
        parsedLine: ParsedDialogueLine,
        voiceMode: TtsVoiceMode,
        enabled: Boolean,
        userBaseSpeed: Float = 1.05f,
        autoCatchUpEnabled: Boolean = true
    ): String {
        val engine = tts ?: return "TTS Kapalı"
        if (!enabled || !isInitialized) return "Sessiz Mod"

        val textToSpeak = parsedLine.cleanSpokenText.trim()
        if (textToSpeak.isEmpty()) return "Hazır"

        val wasSpeaking = engine.isSpeaking

        val profile = when (voiceMode) {
            TtsVoiceMode.SINGLE_NARRATOR -> {
                voiceTemplates[2].copy(label = "Anlatıcı (Tek Ses)")
            }
            TtsVoiceMode.MULTI_CHARACTER -> {
                resolveMultiCharacterProfile(parsedLine.speakerName, parsedLine.dominantColor)
            }
        }

        applyDeviceVoiceIfAvailable(engine, profile.preferredVoiceIndex)

        val catchUpBoost = if (autoCatchUpEnabled) {
            val lengthFactor = when {
                textToSpeak.length > 120 -> 1.22f
                textToSpeak.length > 75 -> 1.12f
                else -> 1.0f
            }
            val interruptBoost = if (wasSpeaking) 1.10f else 1.0f
            (lengthFactor * interruptBoost).coerceIn(1.0f, 1.40f)
        } else {
            1.0f
        }

        val finalRate = (userBaseSpeed * profile.baseRateMultiplier * catchUpBoost).coerceIn(0.65f, 1.85f)
        engine.setPitch(profile.pitch)
        engine.setSpeechRate(finalRate)

        engine.speak(
            textToSpeak,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "utt_${System.currentTimeMillis()}"
        )

        val speedTag = String.format(Locale.US, "%.2fx", finalRate)
        return "${profile.label} • $speedTag"
    }

    private fun resolveMultiCharacterProfile(
        speakerName: String?,
        dominantColor: DominantSubtitleColor
    ): CharacterVoiceProfile {
        if (!speakerName.isNullOrBlank()) {
            val key = speakerName.trim().lowercase()
            val assigned = characterVoiceMap.getOrPut(key) {
                val index = abs(key.hashCode()) % voiceTemplates.size
                voiceTemplates[index]
            }
            return assigned.copy(label = "$speakerName (${assigned.label})")
        }

        // If the subtitle is colored (Yellow / Cyan / Green / Amber), use color-mapped voice
        if (dominantColor != DominantSubtitleColor.WHITE) {
            return when (dominantColor) {
                DominantSubtitleColor.YELLOW -> voiceTemplates[0].copy(label = "Sarı Altyazı Sesi")
                DominantSubtitleColor.CYAN_BLUE -> voiceTemplates[1].copy(label = "Mavi Altyazı Sesi")
                DominantSubtitleColor.GREEN -> voiceTemplates[3].copy(label = "Yeşil Altyazı Sesi")
                DominantSubtitleColor.AMBER_RED -> voiceTemplates[4].copy(label = "Kehribar Altyazı Sesi")
                else -> voiceTemplates[2]
            }
        }

        // Turn-taking fallback when two unnamed characters speak back-and-forth in white text
        turnTakingToggle = !turnTakingToggle
        return if (turnTakingToggle) {
            voiceTemplates[0].copy(label = "Diyalog Konuşmacı 1")
        } else {
            voiceTemplates[1].copy(label = "Diyalog Konuşmacı 2")
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
