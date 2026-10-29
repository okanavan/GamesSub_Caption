package com.gamessub.caption.ocr

import android.graphics.Bitmap
import com.gamessub.caption.model.NormalizedRoiRect
import com.gamessub.caption.model.OcrScriptType
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import kotlin.math.max

/**
 * Multi-script ML Kit Text Recognition v2 engine supporting:
 * - Latin (English, Turkish, Spanish, German, French, Polish, etc.)
 * - Japanese (Kanji, Hiragana, Katakana)
 * - Korean (Hangul)
 * - Chinese (Simplified & Traditional)
 *
 * Also supports `recognizeLocalizedBlocks` for Blur Mode so each detected text region on the
 * screen gets its own exact bounding box and line height ratio!
 */
class MlKitOcrEngine {

    data class DetectedOcrBlock(
        val text: String,
        val normalizedBounds: NormalizedRoiRect,
        val lineHeightRatio: Float
    )

    private var currentScript: OcrScriptType? = null
    private var recognizer: TextRecognizer? = null

    private fun getOrCreateRecognizer(script: OcrScriptType): TextRecognizer {
        val existing = recognizer
        if (existing != null && currentScript == script) {
            return existing
        }
        existing?.close()
        val created = when (script) {
            OcrScriptType.LATIN -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            OcrScriptType.JAPANESE -> TextRecognition.getClient(
                JapaneseTextRecognizerOptions.Builder().build()
            )
            OcrScriptType.KOREAN -> TextRecognition.getClient(
                KoreanTextRecognizerOptions.Builder().build()
            )
            OcrScriptType.CHINESE -> TextRecognition.getClient(
                ChineseTextRecognizerOptions.Builder().build()
            )
        }
        currentScript = script
        recognizer = created
        return created
    }

    suspend fun recognizeText(bitmap: Bitmap, script: OcrScriptType): String {
        val client = getOrCreateRecognizer(script)
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val visionText = client.process(inputImage).await()

        val orderedLines = visionText.textBlocks
            .sortedBy { it.boundingBox?.top ?: 0 }
            .flatMap { block ->
                block.lines.sortedBy { it.boundingBox?.top ?: 0 }.map { it.text.trim() }
            }
            .filter { it.isNotEmpty() }

        return orderedLines.joinToString(separator = "\n").trim()
    }

    /**
     * Used in Blur Mode: Detects all text blocks across the entire selected monitor screen,
     * returning each block's exact normalized bounding box (`left, top, right, bottom`) and
     * single-line height ratio so the UI blurs ONLY the text regions and renders translations
     * at the exact position and font size of the original text.
     */
    suspend fun recognizeLocalizedBlocks(
        bitmap: Bitmap,
        script: OcrScriptType
    ): List<DetectedOcrBlock> {
        val client = getOrCreateRecognizer(script)
        val bmpW = max(1, bitmap.width).toFloat()
        val bmpH = max(1, bitmap.height).toFloat()

        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val visionText = client.process(inputImage).await()

        return visionText.textBlocks.mapNotNull { block ->
            val box = block.boundingBox ?: return@mapNotNull null
            val cleanText = block.lines
                .sortedBy { it.boundingBox?.top ?: 0 }
                .joinToString(separator = "\n") { it.text.trim() }
                .trim()

            if (cleanText.length < 2) return@mapNotNull null

            val leftNorm = (box.left / bmpW).coerceIn(0.01f, 0.96f)
            val topNorm = (box.top / bmpH).coerceIn(0.01f, 0.96f)
            val rightNorm = (box.right / bmpW).coerceIn(leftNorm + 0.03f, 0.99f)
            val bottomNorm = (box.bottom / bmpH).coerceIn(topNorm + 0.02f, 0.99f)

            val lineCount = max(1, block.lines.size)
            val avgLineHeightPx = box.height().toFloat() / lineCount.toFloat()
            val lineHeightRatio = (avgLineHeightPx / bmpH).coerceIn(0.022f, 0.12f)

            DetectedOcrBlock(
                text = cleanText,
                normalizedBounds = NormalizedRoiRect(leftNorm, topNorm, rightNorm, bottomNorm),
                lineHeightRatio = lineHeightRatio
            )
        }
    }

    fun close() {
        recognizer?.close()
        recognizer = null
    }
}
