package com.gamessub.caption.camera

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.gamessub.caption.model.NormalizedPoint
import com.gamessub.caption.model.NormalizedRoiRect
import com.gamessub.caption.model.RoiZoneType
import com.gamessub.caption.model.ScreenQuad
import com.gamessub.caption.ocr.RoiImageProcessor
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.hypot

private enum class DragHandleMode {
    NONE,
    MOVE_CENTER,
    CORNER_TOP_LEFT,
    CORNER_TOP_RIGHT,
    CORNER_BOTTOM_LEFT,
    CORNER_BOTTOM_RIGHT,
    EDGE_TOP,
    EDGE_BOTTOM,
    EDGE_LEFT,
    EDGE_RIGHT
}

private enum class QuadCornerHandle {
    NONE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_RIGHT,
    BOTTOM_LEFT,
    MOVE_ALL
}

@Composable
fun CameraXControllerEffect(
    showPreview: Boolean,
    enableFrameAnalysis: Boolean,
    previewView: PreviewView?,
    onFrameBitmap: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnFrame by rememberUpdatedState(onFrameBitmap)
    val currentAnalyzeFlag by rememberUpdatedState(enableFrameAnalysis)
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
        }
    }

    LaunchedEffect(showPreview, previewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = try {
                cameraProviderFuture.get()
            } catch (_: Exception) {
                return@addListener
            }

            cameraProvider.unbindAll()

            @Suppress("DEPRECATION")
            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                if (currentAnalyzeFlag) {
                    val bmp = imageProxy.toRotatedBitmap()
                    imageProxy.close()
                    if (bmp != null) {
                        currentOnFrame(bmp)
                    }
                } else {
                    imageProxy.close()
                }
            }

            try {
                if (showPreview && previewView != null) {
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } else {
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        imageAnalysis
                    )
                }
            } catch (_: Exception) {
            }
        }, ContextCompat.getMainExecutor(context))
    }
}

@SuppressLint("UnsafeOptInUsageError")
private fun ImageProxy.toRotatedBitmap(): Bitmap? {
    return try {
        val rawBitmap = this.toBitmap()
        val rotation = imageInfo.rotationDegrees
        if (rotation == 0) {
            rawBitmap
        } else {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            val rotated = Bitmap.createBitmap(
                rawBitmap,
                0,
                0,
                rawBitmap.width,
                rawBitmap.height,
                matrix,
                true
            )
            if (rotated !== rawBitmap) rawBitmap.recycle()
            rotated
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * STAGE 1: 4-Independent-Corner Monitor Screen Calibration ("Kameradan Ekran Seçimi")
 *
 * Each of the 4 corners (Top-Left, Top-Right, Bottom-Right, Bottom-Left) moves completely
 * independently so the user can trace the exact 4 corners of their TV/Monitor from any angle.
 * Also defines the full frame used by Blur Mode!
 */
@Composable
fun ScreenQuadCameraCalibrationScreen(
    screenQuad: ScreenQuad,
    onUpdateQuad: (ScreenQuad) -> Unit,
    onSaveAndBackToHub: () -> Unit,
    onFrameBitmap: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    CameraXControllerEffect(
        showPreview = true,
        enableFrameAnalysis = false,
        previewView = previewView,
        onFrameBitmap = onFrameBitmap
    )

    val latestQuad by rememberUpdatedState(screenQuad)
    var canvasWidthPx by remember { mutableStateOf(1f) }
    var canvasHeightPx by remember { mutableStateOf(1f) }
    var activeCorner by remember { mutableStateOf(QuadCornerHandle.NONE) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Free 4-Corner Polygon Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            if (canvasWidthPx <= 1f || canvasHeightPx <= 1f) return@detectDragGestures
                            val q = latestQuad
                            val tx = (startOffset.x / canvasWidthPx).coerceIn(0f, 1f)
                            val ty = (startOffset.y / canvasHeightPx).coerceIn(0f, 1f)

                            val dTL = hypot(tx - q.topLeft.x, ty - q.topLeft.y)
                            val dTR = hypot(tx - q.topRight.x, ty - q.topRight.y)
                            val dBR = hypot(tx - q.bottomRight.x, ty - q.bottomRight.y)
                            val dBL = hypot(tx - q.bottomLeft.x, ty - q.bottomLeft.y)
                            val minD = minOf(dTL, dTR, dBR, dBL)

                            activeCorner = if (minD < 0.12f) {
                                when (minD) {
                                    dTL -> QuadCornerHandle.TOP_LEFT
                                    dTR -> QuadCornerHandle.TOP_RIGHT
                                    dBR -> QuadCornerHandle.BOTTOM_RIGHT
                                    else -> QuadCornerHandle.BOTTOM_LEFT
                                }
                            } else {
                                QuadCornerHandle.MOVE_ALL
                            }
                        },
                        onDragEnd = { activeCorner = QuadCornerHandle.NONE },
                        onDragCancel = { activeCorner = QuadCornerHandle.NONE },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (canvasWidthPx <= 1f || canvasHeightPx <= 1f) return@detectDragGestures
                            val q = latestQuad
                            val dx = dragAmount.x / canvasWidthPx
                            val dy = dragAmount.y / canvasHeightPx

                            val updated = when (activeCorner) {
                                QuadCornerHandle.TOP_LEFT -> q.copy(
                                    topLeft = NormalizedPoint(q.topLeft.x + dx, q.topLeft.y + dy).clamp()
                                )
                                QuadCornerHandle.TOP_RIGHT -> q.copy(
                                    topRight = NormalizedPoint(q.topRight.x + dx, q.topRight.y + dy).clamp()
                                )
                                QuadCornerHandle.BOTTOM_RIGHT -> q.copy(
                                    bottomRight = NormalizedPoint(q.bottomRight.x + dx, q.bottomRight.y + dy).clamp()
                                )
                                QuadCornerHandle.BOTTOM_LEFT -> q.copy(
                                    bottomLeft = NormalizedPoint(q.bottomLeft.x + dx, q.bottomLeft.y + dy).clamp()
                                )
                                QuadCornerHandle.MOVE_ALL -> ScreenQuad(
                                    topLeft = NormalizedPoint(q.topLeft.x + dx, q.topLeft.y + dy).clamp(),
                                    topRight = NormalizedPoint(q.topRight.x + dx, q.topRight.y + dy).clamp(),
                                    bottomRight = NormalizedPoint(q.bottomRight.x + dx, q.bottomRight.y + dy).clamp(),
                                    bottomLeft = NormalizedPoint(q.bottomLeft.x + dx, q.bottomLeft.y + dy).clamp()
                                )
                                QuadCornerHandle.NONE -> q
                            }
                            onUpdateQuad(updated)
                        }
                    )
                }
        ) {
            canvasWidthPx = size.width
            canvasHeightPx = size.height

            val tl = Offset(screenQuad.topLeft.x * size.width, screenQuad.topLeft.y * size.height)
            val tr = Offset(screenQuad.topRight.x * size.width, screenQuad.topRight.y * size.height)
            val br = Offset(screenQuad.bottomRight.x * size.width, screenQuad.bottomRight.y * size.height)
            val bl = Offset(screenQuad.bottomLeft.x * size.width, screenQuad.bottomLeft.y * size.height)

            val quadPath = Path().apply {
                moveTo(tl.x, tl.y)
                lineTo(tr.x, tr.y)
                lineTo(br.x, br.y)
                lineTo(bl.x, bl.y)
                close()
            }

            val fullPath = Path().apply {
                addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
            }
            val outsideMask = Path.combine(PathOperation.Difference, fullPath, quadPath)
            drawPath(outsideMask, color = Color.Black.copy(alpha = 0.58f))

            // Polygon interior fill & stroke
            drawPath(quadPath, color = Color(0xFF38BDF8).copy(alpha = 0.14f))
            drawPath(quadPath, color = Color(0xFF38BDF8), style = Stroke(width = 6f))

            // Draw 4 Independent Corner Handles (Outer ring + inner dot)
            listOf(tl, tr, br, bl).forEach { pt ->
                drawCircle(color = Color.White, radius = 24f, center = pt)
                drawCircle(color = Color(0xFF0284C7), radius = 15f, center = pt)
            }
        }

        // Top Instruction Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.76f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "1. Kameradan Ekran Seçimi (4 Bağımsız Köşe)",
                    color = Color(0xFF38BDF8),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "4 köşenin her birini bağımsız sürükleyerek monitörün/TV'nin tam 4 köşesine oturtun. Blur modu tüm bu çerçeveyi kullanır.",
                    color = Color(0xFFCFD8DC),
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            OutlinedButton(onClick = { onUpdateQuad(ScreenQuad()) }) {
                Text("Sıfırla", color = Color.White, fontSize = 12.sp)
            }
        }

        // Bottom Confirmation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.82f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onSaveAndBackToHub,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E676),
                    contentColor = Color.Black
                )
            ) {
                Text(
                    text = "Ekran Köşelerini Kaydet & Alt Alanları Seçmeye Geç ✓",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * STAGE 2: Sub-Zone Selection INSIDE the Proportional / Perspective-Rectified Monitor Screen!
 *
 * After the user selects the 4 corners of the monitor (`ScreenQuad`), the camera feed is
 * perspective-warped (`RoiImageProcessor.warpScreenQuadToFlatBitmap`) so the monitor fills the
 * calibration view proportionally. The user then adjusts the Subtitle, Quest, or Dialogue box
 * (from all 4 corners and 4 edges) directly on this flattened screen!
 */
@Composable
fun SingleZoneCameraCalibrationScreen(
    zone: RoiZoneType,
    screenQuad: ScreenQuad,
    rect: NormalizedRoiRect,
    onUpdateRect: (NormalizedRoiRect) -> Unit,
    onBackToSetupHub: () -> Unit,
    onStartGameNow: () -> Unit
) {
    var rectifiedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Capture live camera frames and perspective-warp the 4-corner ScreenQuad into a proportional flat image
    CameraXControllerEffect(
        showPreview = false,
        enableFrameAnalysis = true,
        previewView = null,
        onFrameBitmap = { rawFrame ->
            val warped = RoiImageProcessor.warpScreenQuadToFlatBitmap(rawFrame, screenQuad, 960, 540)
            rawFrame.recycle()
            val prev = rectifiedBitmap
            rectifiedBitmap = warped
            prev?.recycle()
        }
    )

    val latestRect by rememberUpdatedState(rect)
    var canvasWidthPx by remember { mutableStateOf(1f) }
    var canvasHeightPx by remember { mutableStateOf(1f) }
    var activeDragHandle by remember { mutableStateOf(DragHandleMode.NONE) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0B1120))) {
        // Proportional Flattened Monitor Screen Feed
        val currentBmp = rectifiedBitmap
        if (currentBmp != null && !currentBmp.isRecycled) {
            Image(
                bitmap = currentBmp.asImageBitmap(),
                contentDescription = "Orantılı Düzleştirilmiş Monitör Ekranı",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Seçilen 4 köşe çerçevesi ekrana orantılı şekilde yerleştiriliyor...",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
            }
        }

        // 8-Way Interactive Frame Canvas over the Proportional Rectified Screen
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(zone) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            if (canvasWidthPx <= 1f || canvasHeightPx <= 1f) return@detectDragGestures
                            val r = latestRect
                            val tx = (startOffset.x / canvasWidthPx).coerceIn(0f, 1f)
                            val ty = (startOffset.y / canvasHeightPx).coerceIn(0f, 1f)
                            val hitTol = 0.065f

                            val nearLeft = abs(tx - r.left) < hitTol
                            val nearRight = abs(tx - r.right) < hitTol
                            val nearTop = abs(ty - r.top) < hitTol
                            val nearBottom = abs(ty - r.bottom) < hitTol
                            val withinX = tx in (r.left - hitTol)..(r.right + hitTol)
                            val withinY = ty in (r.top - hitTol)..(r.bottom + hitTol)

                            activeDragHandle = when {
                                nearLeft && nearTop -> DragHandleMode.CORNER_TOP_LEFT
                                nearRight && nearTop -> DragHandleMode.CORNER_TOP_RIGHT
                                nearLeft && nearBottom -> DragHandleMode.CORNER_BOTTOM_LEFT
                                nearRight && nearBottom -> DragHandleMode.CORNER_BOTTOM_RIGHT
                                nearTop && withinX -> DragHandleMode.EDGE_TOP
                                nearBottom && withinX -> DragHandleMode.EDGE_BOTTOM
                                nearLeft && withinY -> DragHandleMode.EDGE_LEFT
                                nearRight && withinY -> DragHandleMode.EDGE_RIGHT
                                else -> DragHandleMode.MOVE_CENTER
                            }
                        },
                        onDragEnd = { activeDragHandle = DragHandleMode.NONE },
                        onDragCancel = { activeDragHandle = DragHandleMode.NONE },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (canvasWidthPx <= 1f || canvasHeightPx <= 1f) return@detectDragGestures
                            val r = latestRect
                            val dx = dragAmount.x / canvasWidthPx
                            val dy = dragAmount.y / canvasHeightPx

                            val updated = when (activeDragHandle) {
                                DragHandleMode.CORNER_TOP_LEFT -> r.copy(
                                    left = (r.left + dx).coerceAtMost(r.right - 0.08f),
                                    top = (r.top + dy).coerceAtMost(r.bottom - 0.06f)
                                )
                                DragHandleMode.CORNER_TOP_RIGHT -> r.copy(
                                    right = (r.right + dx).coerceAtLeast(r.left + 0.08f),
                                    top = (r.top + dy).coerceAtMost(r.bottom - 0.06f)
                                )
                                DragHandleMode.CORNER_BOTTOM_LEFT -> r.copy(
                                    left = (r.left + dx).coerceAtMost(r.right - 0.08f),
                                    bottom = (r.bottom + dy).coerceAtLeast(r.top + 0.06f)
                                )
                                DragHandleMode.CORNER_BOTTOM_RIGHT -> r.copy(
                                    right = (r.right + dx).coerceAtLeast(r.left + 0.08f),
                                    bottom = (r.bottom + dy).coerceAtLeast(r.top + 0.06f)
                                )
                                DragHandleMode.EDGE_TOP -> r.copy(
                                    top = (r.top + dy).coerceAtMost(r.bottom - 0.06f)
                                )
                                DragHandleMode.EDGE_BOTTOM -> r.copy(
                                    bottom = (r.bottom + dy).coerceAtLeast(r.top + 0.06f)
                                )
                                DragHandleMode.EDGE_LEFT -> r.copy(
                                    left = (r.left + dx).coerceAtMost(r.right - 0.08f)
                                )
                                DragHandleMode.EDGE_RIGHT -> r.copy(
                                    right = (r.right + dx).coerceAtLeast(r.left + 0.08f)
                                )
                                else -> {
                                    val w = r.width
                                    val h = r.height
                                    val nl = (r.left + dx).coerceIn(0.01f, 0.99f - w)
                                    val nt = (r.top + dy).coerceIn(0.01f, 0.99f - h)
                                    NormalizedRoiRect(nl, nt, nl + w, nt + h)
                                }
                            }.clamp()

                            onUpdateRect(updated)
                        }
                    )
                }
        ) {
            canvasWidthPx = size.width
            canvasHeightPx = size.height

            val leftPx = rect.left * size.width
            val topPx = rect.top * size.height
            val rightPx = rect.right * size.width
            val bottomPx = rect.bottom * size.height
            val widthPx = rightPx - leftPx
            val heightPx = bottomPx - topPx
            val midX = leftPx + widthPx / 2f
            val midY = topPx + heightPx / 2f

            val outerPath = Path().apply {
                addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
            }
            val innerPath = Path().apply {
                addRect(androidx.compose.ui.geometry.Rect(leftPx, topPx, rightPx, bottomPx))
            }
            val maskPath = Path.combine(PathOperation.Difference, outerPath, innerPath)
            drawPath(path = maskPath, color = Color.Black.copy(alpha = 0.50f))

            drawRect(
                color = zone.badgeColor.copy(alpha = 0.14f),
                topLeft = Offset(leftPx, topPx),
                size = ComposeSize(widthPx, heightPx)
            )

            drawRect(
                color = zone.badgeColor,
                topLeft = Offset(leftPx, topPx),
                size = ComposeSize(widthPx, heightPx),
                style = Stroke(width = 6f)
            )

            val cornerRadius = 18f
            drawCircle(zone.badgeColor, cornerRadius, Offset(leftPx, topPx))
            drawCircle(zone.badgeColor, cornerRadius, Offset(rightPx, topPx))
            drawCircle(zone.badgeColor, cornerRadius, Offset(leftPx, bottomPx))
            drawCircle(zone.badgeColor, cornerRadius, Offset(rightPx, bottomPx))

            val barLength = 44f
            val barThickness = 12f
            drawRect(
                color = Color.White,
                topLeft = Offset(midX - barLength / 2f, topPx - barThickness / 2f),
                size = ComposeSize(barLength, barThickness)
            )
            drawRect(
                color = Color.White,
                topLeft = Offset(midX - barLength / 2f, bottomPx - barThickness / 2f),
                size = ComposeSize(barLength, barThickness)
            )
            drawRect(
                color = Color.White,
                topLeft = Offset(leftPx - barThickness / 2f, midY - barLength / 2f),
                size = ComposeSize(barThickness, barLength)
            )
            drawRect(
                color = Color.White,
                topLeft = Offset(rightPx - barThickness / 2f, midY - barLength / 2f),
                size = ComposeSize(barThickness, barLength)
            )
        }

        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.74f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${zone.titleTr} (Seçilen Ekran Üzerinde Orantılı)",
                    color = zone.badgeColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "4 bağımsız köşeyle seçtiğiniz monitör ekrana tam oturtuldu. Şimdi bu ekran içinde alanı köşe ve yanlarından ayarlayın.",
                    color = Color(0xFFCFD8DC),
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            OutlinedButton(
                onClick = { onUpdateRect(NormalizedRoiRect.defaultFor(zone)) }
            ) {
                Text("Sıfırla", color = Color.White, fontSize = 12.sp)
            }
        }

        // Bottom Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.78f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBackToSetupHub,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("← Ekran Ayarı Menüsüne Dön", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = onStartGameNow,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = zone.badgeColor,
                    contentColor = Color.Black
                )
            ) {
                Text("Kaydet & Oyuna Başla ▶", fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}
