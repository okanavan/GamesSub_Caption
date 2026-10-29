package com.gamessub.caption.ocr

import android.graphics.Bitmap
import android.graphics.Color
import com.gamessub.caption.model.DominantSubtitleColor
import com.gamessub.caption.model.NormalizedRoiRect
import kotlin.math.max
import kotlin.math.min

/**
 * Handles step 1 & step 2 of the 3-stage Anti-Flicker OCR pipeline:
 * 1. Crops ONLY the active ROI rectangle from the camera frame (saves 80%+ CPU/Battery).
 * 2. Detects the dominant text color (White, Yellow, Cyan/Blue, Green, Amber) for multi-voice TTS.
 * 3. Applies an optional High-Contrast Thresholding filter to isolate bright subtitle letters
 *    with dark outlines from moving game backgrounds (explosions, foliage, weather).
 */
object RoiImageProcessor {

    data class ProcessedRoiFrame(
        val croppedBitmap: Bitmap,
        val dominantTextColor: DominantSubtitleColor
    )

    fun cropAndProcess(
        fullFrame: Bitmap,
        roi: NormalizedRoiRect,
        applyHighContrastFilter: Boolean
    ): ProcessedRoiFrame {
        val clamped = roi.clamp()
        val frameW = fullFrame.width
        val frameH = fullFrame.height

        val leftPx = (clamped.left * frameW).toInt().coerceIn(0, max(0, frameW - 16))
        val topPx = (clamped.top * frameH).toInt().coerceIn(0, max(0, frameH - 16))
        val widthPx = (clamped.width * frameW).toInt().coerceIn(16, frameW - leftPx)
        val heightPx = (clamped.height * frameH).toInt().coerceIn(16, frameH - topPx)

        val cropped = Bitmap.createBitmap(fullFrame, leftPx, topPx, widthPx, heightPx)
        val dominantColor = analyzeDominantBrightColor(cropped)

        if (!applyHighContrastFilter) {
            return ProcessedRoiFrame(cropped, dominantColor)
        }

        val filtered = applySubtitleContrastFilter(cropped)
        if (filtered !== cropped) {
            cropped.recycle()
        }
        return ProcessedRoiFrame(filtered, dominantColor)
    }

    /**
     * Samples bright pixels in the ROI to determine whether the subtitle text is
     * White, Yellow, Cyan/Blue, Green, or Amber/Orange.
     */
    private fun analyzeDominantBrightColor(bitmap: Bitmap): DominantSubtitleColor {
        val w = bitmap.width
        val h = bitmap.height
        val stepX = max(1, w / 32)
        val stepY = max(1, h / 16)

        var yellowVotes = 0
        var cyanVotes = 0
        var greenVotes = 0
        var amberVotes = 0
        var whiteVotes = 0

        val hsv = FloatArray(3)
        for (y in 0 until h step stepY) {
            for (x in 0 until w step stepX) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val maxCh = max(r, max(g, b))
                if (maxCh < 165) continue // Ignore dark background pixels

                Color.RGBToHSV(r, g, b, hsv)
                val hue = hsv[0]
                val sat = hsv[1]

                if (sat < 0.22f) {
                    whiteVotes++
                } else {
                    when (hue) {
                        in 15f..42f -> amberVotes++
                        in 42f..72f -> yellowVotes++
                        in 72f..160f -> greenVotes++
                        in 165f..235f -> cyanVotes++
                        else -> whiteVotes++
                    }
                }
            }
        }

        val maxColored = max(max(yellowVotes, cyanVotes), max(greenVotes, amberVotes))
        if (maxColored < 6 || whiteVotes > maxColored * 2) {
            return DominantSubtitleColor.WHITE
        }
        return when (maxColored) {
            yellowVotes -> DominantSubtitleColor.YELLOW
            cyanVotes -> DominantSubtitleColor.CYAN_BLUE
            greenVotes -> DominantSubtitleColor.GREEN
            amberVotes -> DominantSubtitleColor.AMBER_RED
            else -> DominantSubtitleColor.WHITE
        }
    }

    /**
     * Suppresses mid-tone moving background noise while keeping high-luminance subtitle glyphs crisp.
     */
    private fun applySubtitleContrastFilter(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        for (i in pixels.indices) {
            val c = pixels[i]
            val r = Color.red(c)
            val g = Color.green(c)
            val b = Color.blue(c)

            // Perceived luminance (0..255)
            val lum = (r * 77 + g * 150 + b * 29) shr 8

            // Subtitle glyphs are usually bright (> 155) with dark stroke (< 65).
            val boosted = when {
                lum >= 165 -> min(255, lum + 45)
                lum <= 95 -> max(0, lum - 55)
                else -> (lum - 95) * 2
            }.coerceIn(0, 255)

            pixels[i] = Color.rgb(boosted, boosted, boosted)
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }
}

