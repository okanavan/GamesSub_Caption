package com.gamessub.caption.util

import com.gamessub.caption.model.DominantSubtitleColor
import com.gamessub.caption.model.ParsedDialogueLine

/**
 * Extracts speaker names from game subtitles so that:
 * 1. The speaker name is NOT spoken aloud by TTS ("Geralt: Merhaba" -> speaks only "Merhaba").
 * 2. Each character gets a consistent, distinct TTS voice profile (pitch & speech rate).
 *
 * Supported patterns:
 * - "Geralt: Wind's howling."
 * - "[Yennefer] Hold on!"
 * - "(Johnny Silverhand): Wake up, Samurai."
 * - "Arthur Morgan - We need more money."
 */
object SpeakerParser {

    // Pattern 1: [Speaker Name] Dialogue... or (Speaker Name): Dialogue...
    private val bracketSpeakerRegex = Regex(
        pattern = "^\\s*[\\[\\(]([\\p{L}\\p{N}\\s._'-]{2,28})[\\]\\)]\\s*[:\\-]?\\s*(.+)$",
        options = setOf(RegexOption.DOT_MATCHES_ALL)
    )

    // Pattern 2: Speaker Name: Dialogue...
    private val colonSpeakerRegex = Regex(
        pattern = "^\\s*([\\p{L}\\p{N}\\s._'-]{2,25})\\s*:\\s+(.+)$",
        options = setOf(RegexOption.DOT_MATCHES_ALL)
    )

    fun parse(
        text: String,
        dominantColor: DominantSubtitleColor = DominantSubtitleColor.WHITE
    ): ParsedDialogueLine {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return ParsedDialogueLine(
                rawText = "",
                speakerName = null,
                cleanSpokenText = "",
                dominantColor = dominantColor
            )
        }

        bracketSpeakerRegex.matchEntire(trimmed)?.let { match ->
            val candidateName = match.groupValues[1].trim()
            val spokenBody = match.groupValues[2].trim()
            if (isValidSpeakerName(candidateName) && spokenBody.isNotEmpty()) {
                return ParsedDialogueLine(
                    rawText = trimmed,
                    speakerName = candidateName,
                    cleanSpokenText = spokenBody,
                    dominantColor = dominantColor
                )
            }
        }

        colonSpeakerRegex.matchEntire(trimmed)?.let { match ->
            val candidateName = match.groupValues[1].trim()
            val spokenBody = match.groupValues[2].trim()
            if (isValidSpeakerName(candidateName) && spokenBody.isNotEmpty()) {
                return ParsedDialogueLine(
                    rawText = trimmed,
                    speakerName = candidateName,
                    cleanSpokenText = spokenBody,
                    dominantColor = dominantColor
                )
            }
        }

        return ParsedDialogueLine(
            rawText = trimmed,
            speakerName = null,
            cleanSpokenText = trimmed,
            dominantColor = dominantColor
        )
    }

    private fun isValidSpeakerName(name: String): Boolean {
        // Avoid treating UI labels like "Görev", "Quest", "Objective", "Hint" as character names
        val words = name.split(Regex("\\s+"))
        if (words.size > 4) return false
        val lower = name.lowercase()
        val ignoredLabels = setOf(
            "quest", "görev", "objective", "hedef", "hint", "ipucu",
            "warning", "uyarı", "tutorial", "system", "sistem"
        )
        return lower !in ignoredLabels
    }
}

