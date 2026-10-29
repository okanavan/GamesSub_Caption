package com.gamessub.caption.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import com.gamessub.caption.model.DominantSubtitleColor
import com.gamessub.caption.model.NormalizedRoiRect
import com.gamessub.caption.model.ScreenQuad
import kotlin.math.max
import kotlin.math.min

/**
 * Two-Stage Perspective & ROI Image Processor:
 *
 * Stage 1: Perspective Warp (`warpScreenQuadToFlatBitmap`)
 *   Takes the 4 independent corners of the monitor selected in the camera (`ScreenQuad`)
 *   and uses `Matrix.setPolyToPoly` (Homography) to flatten/rectify the angled monitor into a
 *   clean, proportional rectangular screen bitmap.
 *
 * Stage 2: Sub-ROI Cropping & High-Contrast Filtering
 *   - For SUBTITLE, QUEST, and DIALOGUE: Crops the user's sub-frame inside the flattened monitor bitmap.
 *   - For BLUR MODE (`LIVE_AR_BLUR`): Uses the entire flattened monitor bitmap (no sub-frame required!).
 */
object RoiImageProcessor {

    data class ProcessedRoiFrame(
        val croppedBitmap: Bitmap,
        val dominantTextColor: DominantSubtitleColor
    )

    /**
     * Warps the 4-corner polygon (`ScreenQuad`) from the raw camera frame into a flat rectangular bitmap.
     */
    fun warpScreenQuadToFlatBitmap(
        fullFrame: Bitmap,
        screenQuad: ScreenQuad,
        targetWidth: Int = 960,
        targetHeight: Int = 540
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
        if (!success) {
            val copy = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val c = Canvas(copy)
            c.drawBitmap(
                fullFrame,
                null,
                android.graphics.Rect(0, 0, targetWidth, targetHeight),
                null
            )
            return copy
        }

        val rectified = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(rectified)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
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
        // 1. Perspective-warp the 4 independent monitor corners into a flat proportional screen
        val flatScreenBitmap = warpScreenQuadToFlatBitmap(fullFrame, screenQuad)

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

        val filtered = applySubtitleContrastFilter(cropped)
        if (filtered !== cropped) {
            cropped.recycle()
        }
        return ProcessedRoiFrame(filtered, dominantColor)
    }

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

            val lum = (r * 77 + g * 150 + b * 29) shr 8

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
