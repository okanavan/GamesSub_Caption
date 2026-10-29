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
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.gamessub.caption.model.NormalizedRoiRect
import com.gamessub.caption.model.RoiZoneType
import java.util.concurrent.Executors

/**
 * Manages the CameraX lifecycle with Two Distinct Operating Modes:
 *
 * 1. `showPreview == true` (ROI Calibration Screen):
 *    Binds `Preview` + interactive drag/resize overlay for selecting:
 *    - Zone 1: Subtitle
 *    - Zone 2: Quest / Objective
 *    - Zone 3: Dialogue / Inventory
 *
 * 2. `showPreview == false` (OLED Fullscreen HUD Mode - Previewless CameraX):
 *    Unbinds `Preview` completely so GPU rendering load drops to 0% and screen stays 100% #000000 OLED black.
 *    Binds ONLY `ImageAnalysis` (STRATEGY_KEEP_ONLY_LATEST) to feed frames into the ROI cropper.
 */
@Composable
fun CameraXControllerEffect(
    showPreview: Boolean,
    previewView: PreviewView?,
    onFrameBitmap: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnFrame by rememberUpdatedState(onFrameBitmap)
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

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                if (!showPreview) {
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
                    // PREVIEWLESS CAMERAX: Zero GPU preview surface, background ImageAnalysis only!
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        imageAnalysis
                    )
                }
            } catch (_: Exception) {
                // Ignore if camera is unavailable on emulator without virtual scene
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
 * Interactive Multi-ROI Selection Screen where the user aligns the phone with the TV/Monitor,
 * draws/resizes Subtitle (Zone 1), Quest (Zone 2), and Dialogue (Zone 3) boxes, and then switches
 * to Fullscreen OLED Reading Mode (turning off the camera preview).
 */
@Composable
fun RoiCalibrationScreen(
    activeZone: RoiZoneType,
    roiMap: Map<RoiZoneType, NormalizedRoiRect>,
    isProUnlocked: Boolean,
    onSelectZone: (RoiZoneType) -> Unit,
    onUpdateActiveRoi: (NormalizedRoiRect) -> Unit,
    onFinishCalibration: () -> Unit,
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
        previewView = previewView,
        onFrameBitmap = onFrameBitmap
    )

    var canvasWidthPx by remember { mutableStateOf(1f) }
    var canvasHeightPx by remember { mutableStateOf(1f) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Interactive Drag & Resize Canvas for the 3 ROI boxes
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(activeZone, roiMap) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        if (canvasWidthPx <= 1f || canvasHeightPx <= 1f) return@detectDragGestures

                        val currentRect = roiMap[activeZone] ?: NormalizedRoiRect.defaultFor(activeZone)
                        val touchXNorm = (change.position.x / canvasWidthPx).coerceIn(0f, 1f)
                        val touchYNorm = (change.position.y / canvasHeightPx).coerceIn(0f, 1f)
                        val dxNorm = dragAmount.x / canvasWidthPx
                        val dyNorm = dragAmount.y / canvasHeightPx

                        // If dragging near bottom-right corner -> resize box; otherwise move box
                        val isNearBottomRight =
                            kotlin.math.abs(touchXNorm - currentRect.right) < 0.14f &&
                                kotlin.math.abs(touchYNorm - currentRect.bottom) < 0.14f

                        val updated = if (isNearBottomRight) {
                            currentRect.copy(
                                right = currentRect.right + dxNorm,
                                bottom = currentRect.bottom + dyNorm
                            ).clamp()
                        } else {
                            val w = currentRect.width
                            val h = currentRect.height
                            val newLeft = (currentRect.left + dxNorm).coerceIn(0.02f, 0.98f - w)
                            val newTop = (currentRect.top + dyNorm).coerceIn(0.02f, 0.98f - h)
                            NormalizedRoiRect(
                                left = newLeft,
                                top = newTop,
                                right = newLeft + w,
                                bottom = newTop + h
                            ).clamp()
                        }
                        onUpdateActiveRoi(updated)
                    }
                }
        ) {
            canvasWidthPx = size.width
            canvasHeightPx = size.height

            // Dim outside areas slightly
            drawRect(color = Color.Black.copy(alpha = 0.35f))

            RoiZoneType.entries.forEach { zone ->
                if (!isProUnlocked && zone != RoiZoneType.SUBTITLE) return@forEach
                val rect = roiMap[zone] ?: NormalizedRoiRect.defaultFor(zone)
                val isSelected = (zone == activeZone)

                val leftPx = rect.left * size.width
                val topPx = rect.top * size.height
                val widthPx = rect.width * size.width
                val heightPx = rect.height * size.height

                // Subtle fill for active ROI
                drawRect(
                    color = zone.badgeColor.copy(alpha = if (isSelected) 0.18f else 0.06f),
                    topLeft = Offset(leftPx, topPx),
                    size = ComposeSize(widthPx, heightPx)
                )

                // Crisp border
                drawRect(
                    color = if (isSelected) zone.badgeColor else zone.badgeColor.copy(alpha = 0.45f),
                    topLeft = Offset(leftPx, topPx),
                    size = ComposeSize(widthPx, heightPx),
                    style = Stroke(width = if (isSelected) 7f else 3f)
                )

                // Resize handle on bottom-right corner of the active ROI
                if (isSelected) {
                    drawCircle(
                        color = zone.badgeColor,
                        radius = 18f,
                        center = Offset(leftPx + widthPx, topPx + heightPx)
                    )
                }
            }
        }

        // Top Instruction & Zone Selector Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.80f))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = "Monitördeki Çeviri Alanlarını (ROI) Seçin",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Kutuyu taşımak için ortasından, boyutlandırmak için sağ alt köşesinden sürükleyin.",
                color = Color(0xFFB0BEC5),
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RoiZoneType.entries.forEach { zone ->
                    val selected = (zone == activeZone)
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectZone(zone) },
                        label = {
                            Text(
                                text = zone.titleTr,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = zone.badgeColor.copy(alpha = 0.25f),
                            selectedLabelColor = zone.badgeColor,
                            labelColor = Color.White
                        )
                    )
                }
            }
        }

        // Bottom Confirmation Bar -> Switches to Previewless OLED Fullscreen HUD
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = onFinishCalibration,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E676),
                    contentColor = Color.Black
                )
            ) {
                Text(
                    text = "Alanları Kaydet & Kamera Görüntüsünü Kapat (Tam Ekran OLED Modu)",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

