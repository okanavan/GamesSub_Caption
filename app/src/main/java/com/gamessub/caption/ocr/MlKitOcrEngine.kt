package com.gamessub.caption.ocr

import android.graphics.Bitmap
import com.gamessub.caption.model.OcrScriptType
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * Multi-script ML Kit Text Recognition v2 engine supporting:
 * - Latin (English, Turkish, Spanish, German, French, Polish, etc.)
 * - Japanese (Kanji, Hiragana, Katakana)
 * - Korean (Hangul)
 * - Chinese (Simplified & Traditional)
 */
class MlKitOcrEngine {

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

        // Sort blocks top-to-bottom so multi-line subtitles and quest objectives stay in reading order
        val orderedLines = visionText.textBlocks
            .sortedBy { it.boundingBox?.top ?: 0 }
            .flatMap { block ->
                block.lines.sortedBy { it.boundingBox?.top ?: 0 }.map { it.text.trim() }
            }
            .filter { it.isNotEmpty() }

        return orderedLines.joinToString(separator = "\n").trim()
    }

    fun close() {
        recognizer?.close()
        recognizer = null
    }
}

