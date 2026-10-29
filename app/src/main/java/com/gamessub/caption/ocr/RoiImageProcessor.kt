package com.gamessub.caption.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import com.gamessub.caption.model.DominantSubtitleColor
import com.gamessub.caption.model.NormalizedRoiRect
import com.gamessub.caption.model.ScreenQuad
import kotlin.math.max
import kotlin.math.min

/**
 * Two-Stage High-Definition (Full HD 1920x1080) Perspective & ROI Image Processor:
 *
 * Stage 1: Full HD Perspective Warp (`warpScreenQuadToFlatBitmap`)
 *   Takes the 4 independent corners of the monitor selected in the camera (`ScreenQuad`)
 *   and uses `Matrix.setPolyToPoly` (Homography) with bilinear/anti-alias filtering + subtle
 *   contrast enhancement to flatten the angled monitor into a crisp 1920x1080 Full HD screen bitmap!
 *
 * Stage 2: Sub-ROI Cropping & High-Contrast Subtitle Glyph Enhancement
 *   Isolates bright subtitle letters and dark outlines from moving background textures.
 */
object RoiImageProcessor {

    data class ProcessedRoiFrame(
        val croppedBitmap: Bitmap,
        val dominantTextColor: DominantSubtitleColor
    )

    /**
     * Warps the 4-corner polygon (`ScreenQuad`) from the raw camera frame into a crisp
     * Full HD (`1920x1080` default) rectangular bitmap with micro-contrast enhancement for text clarity.
     */
    fun warpScreenQuadToFlatBitmap(
        fullFrame: Bitmap,
        screenQuad: ScreenQuad,
        targetWidth: Int = 1920,
        targetHeight: Int = 1080
    ): Bitmap {
        val w = fullFrame.width.toFloat()
        val h = fullFrame.height.toFloat()
        val q = screenQuad.clamp()

        val srcPoly = floatArrayOf(
            q.topLeft.x * w, q.topLeft.y * h,
            q.topRight.x * w, q.topRight.y * h,
            q.bottomRight.x * w, q.bottomRight.y * h,
            q.bottomLeft.x * w, q.bottomLeft.y * h
        )

        val dstPoly = floatArrayOf(
            0f, 0f,
            targetWidth.toFloat(), 0f,
            targetWidth.toFloat(), targetHeight.toFloat(),
            0f, targetHeight.toFloat()
        )

        val matrix = Matrix()
        val success = try {
            matrix.setPolyToPoly(srcPoly, 0, dstPoly, 0, 4)
        } catch (_: Exception) {
            false
        }

        // Subtle contrast boost (+14% contrast, slight anti-glare offset) so monitor pixels look crisp
        val clarityMatrix = ColorMatrix(
            floatArrayOf(
                1.14f, 0f, 0f, 0f, -12f,
                0f, 1.14f, 0f, 0f, -12f,
                0f, 0f, 1.14f, 0f, -12f,
                0f, 0f, 0f, 1f, 0f
            )
        )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(clarityMatrix)
        }

        if (!success) {
            val copy = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val c = Canvas(copy)
            c.drawBitmap(
                fullFrame,
                null,
                android.graphics.Rect(0, 0, targetWidth, targetHeight),
                paint
            )
            return copy
        }

        val rectified = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(rectified)
        try {
            canvas.drawBitmap(fullFrame, matrix, paint)
        } catch (_: Exception) {
        }
        return rectified
    }

    fun cropAndProcess(
        fullFrame: Bitmap,
        screenQuad: ScreenQuad,
        roiInsideScreen: NormalizedRoiRect,
        applyHighContrastFilter: Boolean
    ): ProcessedRoiFrame {
        // 1. Perspective-warp the 4 independent monitor corners into a Full HD 1920x1080 flat screen
        val flatScreenBitmap = warpScreenQuadToFlatBitmap(
            fullFrame = fullFrame,
            screenQuad = screenQuad,
            targetWidth = 1920,
            targetHeight = 1080
        )

        val clamped = roiInsideScreen.clamp()
        val frameW = flatScreenBitmap.width
        val frameH = flatScreenBitmap.height

        val leftPx = (clamped.left * frameW).toInt().coerceIn(0, max(0, frameW - 16))
        val topPx = (clamped.top * frameH).toInt().coerceIn(0, max(0, frameH - 16))
        val widthPx = (clamped.width * frameW).toInt().coerceIn(16, frameW - leftPx)
        val heightPx = (clamped.height * frameH).toInt().coerceIn(16, frameH - topPx)

        val cropped = if (leftPx == 0 && topPx == 0 && widthPx == frameW && heightPx == frameH) {
            flatScreenBitmap
        } else {
            val sub = Bitmap.createBitmap(flatScreenBitmap, leftPx, topPx, widthPx, heightPx)
            flatScreenBitmap.recycle()
            sub
        }

        val dominantColor = analyzeDominantBrightColor(cropped)

        if (!applyHighContrastFilter) {
            return ProcessedRoiFrame(cropped, dominantColor)
        }

        val filtered = applySubtitleContrastAndSharpenFilter(cropped)
        if (filtered !== cropped) {
            cropped.recycle()
        }
        return ProcessedRoiFrame(filtered, dominantColor)
    }

    private fun analyzeDominantBrightColor(bitmap: Bitmap): DominantSubtitleColor {
        val w = bitmap.width
        val h = bitmap.height
        val stepX = max(1, w / 36)
        val stepY = max(1, h / 18)

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
                if (maxCh < 165) continue

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
     * Enhances glyph readability across all zone types (Subtitle, Quest, Dialogue) by applying
     * 4-neighbor Laplacian unsharp mask + adaptive contrast stretching that preserves mid-tone
     * and colored UI text without crushing darker quest/dialogue letters.
     */
    private fun applySubtitleContrastAndSharpenFilter(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val lumArr = IntArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            lumArr[i] = (Color.red(c) * 77 + Color.green(c) * 150 + Color.blue(c) * 29) shr 8
        }

        for (y in 1 until h - 1) {
            val rowOffset = y * w
            for (x in 1 until w - 1) {
                val idx = rowOffset + x
                val center = lumArr[idx]
                // 4-neighbor Laplacian unsharp mask for crisp letter edges
                val neighbors = lumArr[idx - 1] + lumArr[idx + 1] + lumArr[idx - w] + lumArr[idx + w]
                val sharpened = (center * 5 - neighbors).coerceIn(0, 255)

                // Smooth adaptive contrast curve: preserves dark/medium quest logs while making letters pop
                val boosted = when {
                    sharpened >= 180 -> min(255, sharpened + 35)
                    sharpened in 80..179 -> {
                        // Smooth S-curve transition so grey/cyan quest text and green dialogue options are never crushed
                        val norm = (sharpened - 80) / 100f
                        val stretched = 80 + (norm * norm * (3 - 2 * norm) * 100f).toInt()
                        stretched.coerceIn(0, 255)
                    }
                    else -> max(0, (sharpened * 0.75f).toInt())
                }.coerceIn(0, 255)

                pixels[idx] = Color.rgb(boosted, boosted, boosted)
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }
}
