package com.gamessub.caption.util

import kotlin.math.max
import kotlin.math.min

/**
 * Prevents subtitle flickering caused by moving backgrounds (explosions, camera motion)
 * by comparing newly detected OCR text against the last stable OCR text.
 *
 * If similarity is >= 85% (default threshold = 0.85f), re-translation and TTS are skipped.
 */
object TextStabilityFilter {

    fun normalizeForComparison(text: String): String {
        return text
            .lowercase()
            .replace(Regex("[\\s\\r\\n]+"), " ")
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), "")
            .trim()
    }

    /**
     * Returns a similarity ratio between 0.0f and 1.0f using Levenshtein distance.
     */
    fun calculateSimilarity(previousText: String, currentText: String): Float {
        val normPrev = normalizeForComparison(previousText)
        val normCurr = normalizeForComparison(currentText)

        if (normPrev.isEmpty() && normCurr.isEmpty()) return 1.0f
        if (normPrev.isEmpty() || normCurr.isEmpty()) return 0.0f
        if (normPrev == normCurr) return 1.0f

        val maxLength = max(normPrev.length, normCurr.length)
        val distance = levenshteinDistance(normPrev, normCurr)
        return (1.0f - (distance.toFloat() / maxLength.toFloat())).coerceIn(0.0f, 1.0f)
    }

    /**
     * Returns true if the new OCR text is essentially the same subtitle (>= 85% similar)
     * and should NOT trigger a new translation or TTS interruption.
     */
    fun isDuplicateOrFlicker(
        previousText: String,
        currentText: String,
        threshold: Float = 0.85f
    ): Boolean {
        if (currentText.isBlank()) return true
        if (previousText.isBlank()) return false
        return calculateSimilarity(previousText, currentText) >= threshold
    }

    private fun levenshteinDistance(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLen = lhs.length
        val rhsLen = rhs.length

        var cost = IntArray(lhsLen + 1) { it }
        var newCost = IntArray(lhsLen + 1)

        for (i in 1..rhsLen) {
            newCost[0] = i
            for (j in 1..lhsLen) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = min(min(costInsert, costDelete), costReplace)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhsLen]
    }
}

