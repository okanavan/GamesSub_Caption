package com.gamessub.caption.camera

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
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
import java.util.concurrent.TimeUnit
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

/**
 * Full HD (1920x1080) CameraX Controller with:
 * - High-Resolution `1920x1080` ImageAnalysis capture so small game subtitles stay razor-sharp.
 * - Automatic Center-of-Monitor Auto-Focus & Anti-Glare Metering (`FocusMeteringAction`).
 * - Optical/Digital Zoom Ratio (`cameraZoomRatio`) and Anti-Blowout Exposure Compensation (`exposureCompensation`).
 */
@Composable
fun CameraXControllerEffect(
    showPreview: Boolean,
    enableFrameAnalysis: Boolean,
    previewView: PreviewView?,
    cameraZoomRatio: Float = 1.0f,
    exposureCompensation: Int = -2,
    focusCenterNormX: Float = 0.5f,
    focusCenterNormY: Float = 0.5f,
    onFrameBitmap: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnFrame by rememberUpdatedState(onFrameBitmap)
    val currentAnalyzeFlag by rememberUpdatedState(enableFrameAnalysis)
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }

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
                // Upgraded from 1280x720 to Full HD 1920x1080 for crisp text readability!
                .setTargetResolution(Size(1920, 1080))
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
                val camera = if (showPreview && previewView != null) {
                    @Suppress("DEPRECATION")
                    val preview = Preview.Builder()
                        .setTargetResolution(Size(1920, 1080))
                        .build()
                        .also {
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
                boundCamera = camera
                applyCameraFocusZoomAndExposure(
                    camera = camera,
                    zoomRatio = cameraZoomRatio,
                    exposureCompensation = exposureCompensation,
                    normX = focusCenterNormX,
                    normY = focusCenterNormY
                )
            } catch (_: Exception) {
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Dynamically update Zoom, Exposure & Auto-Focus whenever user adjusts zoom or monitor quad center
    LaunchedEffect(boundCamera, cameraZoomRatio, exposureCompensation, focusCenterNormX, focusCenterNormY) {
        boundCamera?.let { cam ->
            applyCameraFocusZoomAndExposure(
                camera = cam,
                zoomRatio = cameraZoomRatio,
                exposureCompensation = exposureCompensation,
                normX = focusCenterNormX,
                normY = focusCenterNormY
            )
        }
    }
}

private fun applyCameraFocusZoomAndExposure(
    camera: Camera,
    zoomRatio: Float,
    exposureCompensation: Int,
    normX: Float,
    normY: Float
) {
    try {
        val control = camera.cameraControl
        val info = camera.cameraInfo

        // 1. Apply Optical/Digital Zoom within hardware limits
        val zoomState = info.zoomState.value
        val minZ = zoomState?.minZoomRatio ?: 1.0f
        val maxZ = zoomState?.maxZoomRatio ?: 4.0f
        control.setZoomRatio(zoomRatio.coerceIn(minZ, maxZ))

        // 2. Apply Anti-Glare Exposure Compensation so bright white TV text doesn't bloom in dark rooms
        val expState = info.exposureState
        if (expState.isExposureCompensationSupported) {
            val range = expState.exposureCompensationRange
            val clampedExp = exposureCompensation.coerceIn(range.lower, range.upper)
            control.setExposureCompensationIndex(clampedExp)
        }

        // 3. Auto-Focus & Spot-Meter directly on the selected monitor center
        val factory = SurfaceOrientedMeteringPointFactory(1f, 1f)
        val centerPoint = factory.createPoint(
            normX.coerceIn(0.1f, 0.9f),
            normY.coerceIn(0.1f, 0.9f)
        )
        val action = FocusMeteringAction.Builder(
            centerPoint,
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
        )
            .setAutoCancelDuration(4, TimeUnit.SECONDS)
            .build()
        control.startFocusAndMetering(action)
    } catch (_: Exception) {
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
 * - Full HD 1080p Camera Preview + Automatic Focus & Anti-Glare on Monitor Center.
 * - Double-tap anywhere on the screen to force instant laser re-focus on that spot!
 * - Tiny, semi-transparent Top-Left `← Geri` button + Tiny Top-Right `1.0x / 1.5x / 2.0x` Zoom pill.
 */
@Composable
fun ScreenQuadCameraCalibrationScreen(
    screenQuad: ScreenQuad,
    cameraZoomRatio: Float,
    exposureCompensation: Int,
    onUpdateQuad: (ScreenQuad) -> Unit,
    onCycleZoom: () -> Unit,
    onSaveAndBackToHub: () -> Unit,
    onFrameBitmap: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    var focusPointX by remember { mutableStateOf(screenQuad.centerX()) }
    var focusPointY by remember { mutableStateOf(screenQuad.centerY()) }

    CameraXControllerEffect(
        showPreview = true,
        enableFrameAnalysis = false,
        previewView = previewView,
        cameraZoomRatio = cameraZoomRatio,
        exposureCompensation = exposureCompensation,
        focusCenterNormX = focusPointX,
        focusCenterNormY = focusPointY,
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

        // Fullscreen Unobstructed 4-Corner Polygon Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { tapOffset ->
                            // Double-tap anywhere to trigger instant AF/AE focus on that monitor spot!
                            if (canvasWidthPx > 1f && canvasHeightPx > 1f) {
                                focusPointX = (tapOffset.x / canvasWidthPx).coerceIn(0.05f, 0.95f)
                                focusPointY = (tapOffset.y / canvasHeightPx).coerceIn(0.05f, 0.95f)
                            }
                        }
                    )
                }
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

                            activeCorner = if (minD < 0.14f) {
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
                        onDragEnd = {
                            activeCorner = QuadCornerHandle.NONE
                            focusPointX = latestQuad.centerX()
                            focusPointY = latestQuad.centerY()
                        },
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
            drawPath(outsideMask, color = Color.Black.copy(alpha = 0.55f))

            drawPath(quadPath, color = Color(0xFF38BDF8).copy(alpha = 0.12f))
            drawPath(quadPath, color = Color(0xFF38BDF8), style = Stroke(width = 6f))

            listOf(tl, tr, br, bl).forEach { pt ->
                drawCircle(color = Color.White, radius = 24f, center = pt)
                drawCircle(color = Color(0xFF0284C7), radius = 15f, center = pt)
            }
        }

        // Small Semi-Transparent Top-Left Back Button (Auto-saves)
        SmallTransparentBackButton(
            label = "← Geri",
            accentColor = Color(0xFF38BDF8),
            onClick = onSaveAndBackToHub,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
        )

        // Small Semi-Transparent Top-Right Zoom & Auto-Focus Pill
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.42f))
                    .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.55f), RoundedCornerShape(50))
                    .clickable { onCycleZoom() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "🔍 ${String.format(java.util.Locale.US, "%.1fx", cameraZoomRatio)} HD",
                    color = Color.White.copy(alpha = 0.90f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * STAGE 2: Sub-Zone Selection INSIDE the Full HD (1920x1080) Proportional Monitor Screen!
 *
 * - Warps at Full HD `1920x1080` with anti-alias + contrast clarity filter so text is crystal clear!
 * - Zero top/bottom obstructing bars; only a small top-left transparent `← Geri` button.
 */
@Composable
fun SingleZoneCameraCalibrationScreen(
    zone: RoiZoneType,
    screenQuad: ScreenQuad,
    rect: NormalizedRoiRect,
    cameraZoomRatio: Float,
    exposureCompensation: Int,
    onUpdateRect: (NormalizedRoiRect) -> Unit,
    onBackToSetupHub: () -> Unit
) {
    val context = LocalContext.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    var rectifiedImageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val isWarpingFrame = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val latestQuad by rememberUpdatedState(screenQuad)

    CameraXControllerEffect(
        showPreview = false,
        enableFrameAnalysis = true,
        previewView = null,
        cameraZoomRatio = cameraZoomRatio,
        exposureCompensation = exposureCompensation,
        focusCenterNormX = screenQuad.centerX(),
        focusCenterNormY = screenQuad.centerY(),
        onFrameBitmap = { rawFrame ->
            if (!isWarpingFrame.compareAndSet(false, true)) {
                rawFrame.recycle()
                return@CameraXControllerEffect
            }
            try {
                // Upgraded to Full HD 1920x1080 so subtitle text on the monitor is super sharp!
                val warped = RoiImageProcessor.warpScreenQuadToFlatBitmap(
                    fullFrame = rawFrame,
                    screenQuad = latestQuad,
                    targetWidth = 1920,
                    targetHeight = 1080
                )
                rawFrame.recycle()
                val composeImg = warped.asImageBitmap()
                mainExecutor.execute {
                    rectifiedImageBitmap = composeImg
                    isWarpingFrame.set(false)
                }
            } catch (_: Exception) {
                rawFrame.recycle()
                isWarpingFrame.set(false)
            }
        }
    )

    val latestRect by rememberUpdatedState(rect)
    var canvasWidthPx by remember { mutableStateOf(1f) }
    var canvasHeightPx by remember { mutableStateOf(1f) }
    var activeDragHandle by remember { mutableStateOf(DragHandleMode.NONE) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0B1120))) {
        val currentImg = rectifiedImageBitmap
        if (currentImg != null) {
            Image(
                bitmap = currentImg,
                contentDescription = "Full HD Orantılı Düzleştirilmiş Monitör Ekranı",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Fullscreen Unobstructed 8-Way Frame Canvas
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
                            val hitTol = 0.07f

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

        // ONLY a small, semi-transparent Top-Left Back Button (Auto-saves and returns)
        SmallTransparentBackButton(
            label = "← Geri",
            accentColor = zone.badgeColor,
            onClick = onBackToSetupHub,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
        )
    }
}

@Composable
private fun SmallTransparentBackButton(
    label: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.42f))
            .border(1.dp, accentColor.copy(alpha = 0.55f), RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = label,
            tint = Color.White.copy(alpha = 0.90f),
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "Geri",
            color = Color.White.copy(alpha = 0.90f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
