package com.gamessub.caption.ui

import android.graphics.Bitmap
import android.os.Build
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.gamessub.caption.BuildConfig
import com.gamessub.caption.camera.CameraXControllerEffect
import com.gamessub.caption.model.GamerHudTheme
import com.gamessub.caption.model.NormalizedRoiRect
import com.gamessub.caption.model.OcrScriptType
import com.gamessub.caption.model.RoiZoneType
import com.gamessub.caption.model.SavedScreenProfile
import com.gamessub.caption.model.TranslationEngineType
import com.gamessub.caption.model.TtsVoiceMode
import java.util.Locale

/**
 * 1. ENTRY MAIN MENU (Giriş Ana Menüsü)
 * Presents 3 primary cards:
 * - Oyuna Başla (Start Game Tracking)
 * - Ekran Ayarı (4 Submenus for Camera Zone Selection)
 * - Ayarlar (Themes, Languages, TTS Speed & Voices, API Keys)
 */
@Composable
fun HomeMainMenuScreen(
    state: MainUiState,
    onStartGame: () -> Unit,
    onOpenScreenSetupHub: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val theme = state.hudTheme

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.backgroundColor)
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Banner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "GamesSub Caption",
                        color = theme.primaryTextColor,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Oyun Altyazı, Görev & Canlı Sesli Dublaj Sistemi",
                        color = theme.accentColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Box(
                    modifier = Modifier
                        .background(theme.cardSurfaceColor, RoundedCornerShape(10.dp))
                        .border(1.dp, theme.accentColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = state.proStatusBadge,
                        color = theme.speakerBadgeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3 Primary Dashboard Cards
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Card 1: OYUNA BAŞLA
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onStartGame() }
                        .border(2.dp, Color(0xFF00E676), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = theme.cardSurfaceColor),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "▶  OYUNA BAŞLA",
                                color = Color(0xFF00E676),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Seçili alan (${state.activeZone.shortTitleTr}) ve ${state.hudTheme.title} temasıyla tam ekran çeviri ve canlı seslendirmeyi başlat.",
                                color = theme.primaryTextColor.copy(alpha = 0.85f),
                                fontSize = 13.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Card 2: EKRAN AYARI (Alan Seçimi Alt Menüleri)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenScreenSetupHub() }
                        .border(1.dp, theme.accentColor.copy(alpha = 0.55f), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = theme.cardSurfaceColor),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "📐  EKRAN AYARI (Alan Seçimi)",
                                color = theme.accentColor,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Ana Altyazı, Görev Alanı, Kullanıcı Seçimi ve Canlı Ekran Üstü (Blur) Çeviri çerçevelerini tam ekran kamerada ayarlayın.",
                                color = theme.primaryTextColor.copy(alpha = 0.80f),
                                fontSize = 13.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.CropFree,
                            contentDescription = null,
                            tint = theme.accentColor,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                // Card 3: AYARLAR (Tema, Ses, Dil, API)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSettings() }
                        .border(1.dp, theme.speakerBadgeColor.copy(alpha = 0.55f), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = theme.cardSurfaceColor),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "⚙️  AYARLAR (Tema, Ses, Dil & API)",
                                color = theme.speakerBadgeColor,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Arka plan ve yazı temaları, çoklu karakter seslendirme, ses hızı (${String.format(Locale.US, "%.2fx", state.ttsBaseSpeed)}), dil çifti (${state.sourceLanguageCode.uppercase()}→${state.targetLanguageCode.uppercase()}) ve BYOK API anahtarları.",
                                color = theme.primaryTextColor.copy(alpha = 0.80f),
                                fontSize = 13.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = theme.speakerBadgeColor,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Aktif Motor: ${state.translationEngine.title} • Ses Modu: ${state.ttsVoiceMode.title}",
                color = theme.primaryTextColor.copy(alpha = 0.55f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 2. SCREEN SETUP HUB (Ekran Ayarı Alt Menüsü)
 * Two-Stage Calibration Flow:
 * - Stage 1 Card: "1. Kameradan Ekran Seçimi (4 Bağımsız Köşe)" -> Free 4-corner polygon for the TV/Monitor.
 *   (Also notes that Blur Mode automatically uses this entire selected screen frame!)
 * - Stage 2 Cards: "2. Ana Altyazı Seçimi", "3. Görev Alanı Seçimi", "4. Kullanıcı Seçimi" ->
 *   Selected proportionally INSIDE the flattened/rectified monitor screen.
 */
@Composable
fun ScreenSetupHubScreen(
    state: MainUiState,
    onOpenScreenQuadCalibration: () -> Unit,
    onSelectZoneToCalibrate: (RoiZoneType) -> Unit,
    onCycleZoom: () -> Unit,
    onSaveProfile: (String) -> Unit,
    onLoadProfile: (SavedScreenProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onBackToMainMenu: () -> Unit,
    onStartGame: () -> Unit
) {
    val theme = state.hudTheme
    val q = state.screenQuad
    var showSaveProfileDialog by remember { mutableStateOf(false) }
    var newProfileNameInput by remember { mutableStateOf("") }

    if (showSaveProfileDialog) {
        AlertDialog(
            onDismissRequest = { showSaveProfileDialog = false },
            title = { Text("📌 Sabit Tripod / Ekran Profilini Kaydet", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Telefonu veya tripodu sabit koyduğunuz bu konumun 4 köşe monitör çerçevesi, altyazı/görev kutuları ve kamera yakınlaştırma (${String.format(Locale.US, "%.1fx", state.cameraZoomRatio)}) ayarı tek pakette kaydedilir.",
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = newProfileNameInput,
                        onValueChange = { newProfileNameInput = it },
                        label = { Text("Profil Adı (Örn: Masaüstü 27\" Tripod, Salon TV)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveProfile(newProfileNameInput)
                        newProfileNameInput = ""
                        showSaveProfileDialog = false
                    }
                ) {
                    Text("Kaydet")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveProfileDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.backgroundColor)
            .padding(18.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onBackToMainMenu) {
                    Text("← Ana Menü", color = theme.primaryTextColor)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Ekran Ayarı — 1080p Keskin Odak & Kayıtlı Tripod Profilleri",
                        color = theme.primaryTextColor,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Aktif Profil: ${state.activeProfileName} • Kamera: Full HD 1080p + Otomatik Odak (${String.format(Locale.US, "%.1fx", state.cameraZoomRatio)})",
                        color = theme.accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Button(
                onClick = onStartGame,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E676),
                    contentColor = Color.Black
                )
            ) {
                Text("Oyuna Başla ▶", fontWeight = FontWeight.ExtraBold)
            }
        }

        // SAVED TRIPOD / MONITOR PROFILES CARD ("Kayıtlı Ekranlar")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, Color(0xFFFFD54F).copy(alpha = 0.75f), RoundedCornerShape(14.dp)),
            colors = CardDefaults.cardColors(containerColor = theme.cardSurfaceColor),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "📌 Kayıtlı Ekranlar (Sabit Tripod / Monitör Profilleri)",
                            color = Color(0xFFFFD54F),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Tripodu sabit yere koyduğunuzda hazır kamera, 4 köşe ve altyazı ayarlarını tek dokunuşla yükleyin.",
                            color = theme.primaryTextColor.copy(alpha = 0.78f),
                            fontSize = 11.sp
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onCycleZoom) {
                            Text(
                                text = "🔍 Zoom: ${String.format(Locale.US, "%.1fx", state.cameraZoomRatio)}",
                                color = Color(0xFF38BDF8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Button(
                            onClick = { showSaveProfileDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFFD54F),
                                contentColor = Color.Black
                            )
                        ) {
                            Text("+ Mevcut Ayarı Kaydet", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        }
                    }
                }

                // Saved profile chips / quick-load cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.savedProfiles.forEach { profile ->
                        val isCurrent = (state.activeProfileName == profile.name)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isCurrent) Color(0xFFFFD54F).copy(alpha = 0.18f)
                                    else Color.Black.copy(alpha = 0.32f)
                                )
                                .border(
                                    width = if (isCurrent) 1.5.dp else 1.dp,
                                    color = if (isCurrent) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { onLoadProfile(profile) }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isCurrent) "✓ ${profile.name}" else profile.name,
                                        color = if (isCurrent) Color(0xFFFFD54F) else theme.primaryTextColor,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "Zoom ${String.format(Locale.US, "%.1fx", profile.cameraZoomRatio)} • 4 Köşe + 3 Alan Hazır",
                                        color = theme.primaryTextColor.copy(alpha = 0.60f),
                                        fontSize = 10.sp,
                                        maxLines = 1
                                    )
                                }
                                if (state.savedProfiles.size > 1) {
                                    Text(
                                        text = "✕",
                                        color = Color(0xFFEF5350),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clickable { onDeleteProfile(profile.id) }
                                            .padding(start = 6.dp, top = 2.dp, bottom = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // STAGE 1 CARD: 4 Independent Corners Monitor Selection
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenScreenQuadCalibration() }
                .border(2.dp, Color(0xFF38BDF8), RoundedCornerShape(14.dp)),
            colors = CardDefaults.cardColors(containerColor = theme.cardSurfaceColor),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
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
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Kamerada her köşesi bağımsız hareket edebilen 4 nokta ile monitörün tam yerini seçin. Seçilen bu çerçeve ekrana orantılı yerleştirilir ve Blur Modu için de tüm bu çerçeve kullanılır.",
                        color = theme.primaryTextColor.copy(alpha = 0.88f),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Sol-Üst(${(q.topLeft.x * 100).toInt()}%,${(q.topLeft.y * 100).toInt()}%) • Sağ-Üst(${(q.topRight.x * 100).toInt()}%,${(q.topRight.y * 100).toInt()}%) • Sağ-Alt(${(q.bottomRight.x * 100).toInt()}%,${(q.bottomRight.y * 100).toInt()}%) • Sol-Alt(${(q.bottomLeft.x * 100).toInt()}%,${(q.bottomLeft.y * 100).toInt()}%)",
                        color = theme.primaryTextColor.copy(alpha = 0.55f),
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = { onOpenScreenQuadCalibration() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF38BDF8),
                        contentColor = Color.Black
                    )
                ) {
                    Text("4 Köşeyi Seç", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
            }
        }

        // STAGE 2 CARDS: Only the 3 Sub-Zones (Subtitle, Quest, Dialogue) selected inside the proportional screen
        RoiZoneType.CALIBRATABLE_SUB_ZONES.forEachIndexed { idx, zone ->
            val rect = state.roiMap[zone] ?: NormalizedRoiRect.defaultFor(zone)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectZoneToCalibrate(zone) }
                    .border(1.5.dp, zone.badgeColor.copy(alpha = 0.7f), RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = theme.cardSurfaceColor),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${idx + 2}. ${zone.titleTr} (Seçilen Ekran İçinde)",
                            color = zone.badgeColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = zone.subtitleTr,
                            color = theme.primaryTextColor.copy(alpha = 0.85f),
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Orantılı Ekran İçi Konum: X(${(rect.left * 100).toInt()}% - ${(rect.right * 100).toInt()}%) • Y(${(rect.top * 100).toInt()}% - ${(rect.bottom * 100).toInt()}%)",
                            color = theme.primaryTextColor.copy(alpha = 0.55f),
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = { onSelectZoneToCalibrate(zone) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = zone.badgeColor,
                            contentColor = Color.Black
                        )
                    ) {
                        Text("Ekranda Ayarla", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/**
 * 3. ULTRA-MINIMAL TEXT-FOCUSED GAME TRACKING SCREEN + 4TH OPTION (LIVE AR BLUR OVERLAY)
 * - No bulky top or bottom bars!
 * - Over 90% of the screen is dedicated purely to the translated text (or Live AR Camera + Blurred Translation Box when Zone 4 is selected).
 * - Top bar is a very thin, semi-transparent strip with Back, Mute/Unmute, Speed preset (`1.05x`), and Simulate button.
 * - Bottom bar is a very narrow, semi-transparent pill switcher for the 4 zones.
 */
@Composable
fun GameTrackingHudScreen(
    state: MainUiState,
    onSelectZone: (RoiZoneType) -> Unit,
    onTriggerQuestRescan: () -> Unit,
    onSimulateSample: () -> Unit,
    onToggleTts: () -> Unit,
    onCycleTtsSpeed: () -> Unit,
    onBackToMainMenu: () -> Unit,
    onFrameBitmap: (Bitmap) -> Unit
) {
    val isLiveBlurMode = state.activeZone.isLiveArBlurMode
    val theme = state.hudTheme

    // Previewless CameraX runs continuously for all zones (including Blur Mode) with zero UI freezes!
    CameraXControllerEffect(
        showPreview = false,
        enableFrameAnalysis = true,
        previewView = null,
        cameraZoomRatio = state.cameraZoomRatio,
        exposureCompensation = state.exposureCompensation,
        focusCenterNormX = state.screenQuad.centerX(),
        focusCenterNormY = state.screenQuad.centerY(),
        onFrameBitmap = onFrameBitmap
    )

    val contentAlpha = if (state.isScreenDimmed) 0.42f else 1.0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.backgroundColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(if (state.showBottomBannerAd) 0.82f else 1.0f)
                .alpha(contentAlpha)
        ) {
            if (isLiveBlurMode) {
                // 4TH OPTION: Live Flattened Monitor Feed + Blurred Frosted Glass Overlay at Exact ROI Coordinates
                LiveArBlurTranslationOverlay(state = state)
            } else {
                // Standard Text-Focused Fullscreen Reader (90%+ area dedicated to subtitle text)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 38.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (!state.activeSpeakerName.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = theme.speakerBadgeColor.copy(alpha = 0.14f),
                                        shape = RoundedCornerShape(50)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = theme.speakerBadgeColor.copy(alpha = 0.55f),
                                        shape = RoundedCornerShape(50)
                                    )
                                    .padding(horizontal = 14.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "${state.activeSpeakerName}  •  ${state.activeTtsProfileStatus}",
                                    color = theme.speakerBadgeColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        // Primary Focus: Large, crisp translated text
                        Text(
                            text = state.translatedText,
                            color = theme.primaryTextColor,
                            fontSize = state.fontSizeSp.sp,
                            lineHeight = (state.fontSizeSp * 1.32f).sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            fontFamily = if (theme == GamerHudTheme.CYBER_MIDNIGHT) {
                                FontFamily.Monospace
                            } else {
                                FontFamily.SansSerif
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (state.rawDetectedText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = state.rawDetectedText,
                                color = theme.primaryTextColor.copy(alpha = 0.38f),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        if (state.activeZone == RoiZoneType.QUEST) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(theme.cardSurfaceColor.copy(alpha = 0.55f))
                                    .border(1.dp, theme.accentColor.copy(alpha = 0.4f), RoundedCornerShape(50))
                                    .clickable { onTriggerQuestRescan() }
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = theme.accentColor,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Görev Alanı • Anlık Canlı Çeviri Aktif",
                                        color = theme.accentColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ULTRA-SLIM TRANSPARENT TOP BAR (Geri, Durum, Ses Hızı, Ses Aç/Kapa)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Transparent Back Button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .clickable { onBackToMainMenu() }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Ana Menü",
                        tint = Color.White.copy(alpha = 0.80f),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Menü",
                        color = Color.White.copy(alpha = 0.80f),
                        fontSize = 11.sp
                    )
                }

                // Subtle Status Pill
                Text(
                    text = "${state.sourceLanguageCode.uppercase()}→${state.targetLanguageCode.uppercase()} • ${state.activeZone.shortTitleTr}",
                    color = Color.White.copy(alpha = 0.50f),
                    fontSize = 11.sp
                )

                // Right Transparent Controls: Simulate, TTS Speed Pill, Mute/Unmute
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Simulate Test Line
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.35f))
                            .clickable { onSimulateSample() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "▶ Test",
                            color = Color(0xFF69F0AE).copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Tap to cycle TTS Speech Speed (0.9x -> 1.0x -> 1.15x -> 1.3x -> 1.5x)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.35f))
                            .clickable { onCycleTtsSpeed() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Hız: ${String.format(Locale.US, "%.2fx", state.ttsBaseSpeed)}",
                            color = theme.speakerBadgeColor.copy(alpha = 0.90f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Transparent Mute/Unmute Button
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.35f))
                            .clickable { onToggleTts() }
                            .padding(5.dp)
                    ) {
                        Icon(
                            imageVector = if (state.ttsEnabled) {
                                Icons.AutoMirrored.Filled.VolumeUp
                            } else {
                                Icons.AutoMirrored.Filled.VolumeOff
                            },
                            contentDescription = "Ses Aç/Kapa",
                            tint = if (state.ttsEnabled) theme.accentColor else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // ULTRA-SLIM TRANSPARENT BOTTOM ZONE SWITCHER BAR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.60f))
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoiZoneType.entries.forEach { zone ->
                    val isSelected = (state.activeZone == zone)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (isSelected) {
                                    zone.badgeColor.copy(alpha = 0.22f)
                                } else {
                                    Color.White.copy(alpha = 0.06f)
                                }
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) {
                                    zone.badgeColor.copy(alpha = 0.70f)
                                } else {
                                    Color.White.copy(alpha = 0.12f)
                                },
                                shape = RoundedCornerShape(50)
                            )
                            .clickable { onSelectZone(zone) }
                            .padding(vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = zone.shortTitleTr,
                            color = if (isSelected) zone.badgeColor else Color.White.copy(alpha = 0.60f),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Fixed Bottom 18% AdMob Adaptive Banner Slot (ONLY shown in Free tier after 7-day trial)
        if (state.showBottomBannerAd) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.18f)
                    .background(Color(0xFF0D0D0D))
                    .border(width = 1.dp, color = Color(0xFF263238)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "REKLAM ALANI (Sabit Alt %18 AdMob Adaptive Banner)",
                        color = Color(0xFF90A4AE),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Çeviri metni üst %82'lik alanda korunur, reklam asla yazıyı kapatmaz.",
                        color = Color(0xFF546E7A),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

/**
 * 4TH SCREEN OPTION: LOCALIZED MULTI-REGION BLUR OVERLAY (Google Lens / In-Place AR Style!)
 *
 * - Displays the selected monitor screen (`state.rectifiedScreenImage` or live warped frame).
 * - Does NOT blur the entire screen!
 * - Instead, for EVERY text block detected across the screen (`state.localizedBlurBlocks` — whether at the top-right quest log,
 *   center-left hint box, or bottom subtitle), it blurs ONLY the exact bounding box (`bounds`) of that text
 *   and renders the translated text inside that box scaled to match the original text's height (`estimatedLineHeightRatio`)!
 */
@Composable
private fun LiveArBlurTranslationOverlay(
    state: MainUiState
) {
    val theme = state.hudTheme
    val rectifiedImg = state.rectifiedScreenImage ?: state.rectifiedScreenPreview?.asImageBitmap()

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val screenW = maxWidth
        val screenH = maxHeight

        // Base Layer: Proportional Rectified Monitor Screen
        if (rectifiedImg != null) {
            Image(
                bitmap = rectifiedImg,
                contentDescription = "Seçili Monitör Ekranı",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF090D16)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Monitör taranıyor ve düzleştiriliyor...",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp
                )
            }
        }

        // Top Status Badge in Blur Mode
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 40.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.60f))
                .border(1.dp, Color(0xFFE040FB).copy(alpha = 0.50f), RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (state.localizedBlurBlocks.isEmpty()) {
                    "✨ Canlı Blur Modu • Ekrandaki yazılar anlık taranıyor..."
                } else {
                    "✨ Canlı Blur Modu • ${state.localizedBlurBlocks.size} metin bloğu anlık çevriliyor"
                },
                color = Color(0xFFF06292),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Localized Blur & Translation Boxes at the Exact Coordinates and Size of Each Detected Text Block!
        state.localizedBlurBlocks.forEach { block ->
            val b = block.bounds
            val padX = 0.012f
            val padY = 0.008f
            val leftNorm = (b.left - padX).coerceIn(0f, 0.95f)
            val topNorm = (b.top - padY).coerceIn(0f, 0.95f)
            val widthNorm = (b.width + padX * 2f).coerceIn(0.05f, 1f - leftNorm)
            val heightNorm = (b.height + padY * 2f).coerceIn(0.035f, 1f - topNorm)

            val boxLeft = screenW * leftNorm
            val boxTop = screenH * topNorm
            val boxWidth = screenW * widthNorm
            val boxHeight = screenH * heightNorm

            // Scale font size dynamically to match the height of the original text on the monitor
            val dynamicFontSp = (screenH.value * block.estimatedLineHeightRatio * 0.78f).coerceIn(11f, 30f)

            Box(
                modifier = Modifier
                    .offset(x = boxLeft, y = boxTop)
                    .size(width = boxWidth, height = boxHeight)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                // Localized Frosted Blur Patch covering ONLY this text's area
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                Modifier.blur(16.dp)
                            } else {
                                Modifier
                            }
                        )
                        .background(
                            color = theme.backgroundColor.copy(alpha = 0.84f)
                        )
                )

                // Translated Text rendered in-place at the exact size of the original text
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            width = 1.dp,
                            color = theme.accentColor.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = block.translatedText,
                        color = theme.primaryTextColor,
                        fontSize = dynamicFontSp.sp,
                        lineHeight = (dynamicFontSp * 1.18f).sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * 4. SETTINGS SCREEN (Ayarlar Menüsü)
 * Includes:
 * - Theme Selector (changes BOTH Background Color and Text Color)
 * - TTS Speed Slider (`0.75x - 1.75x`) & Character Voice Studio (change voice per detected character)
 * - Language & OCR Script Settings
 * - BYOK API Keys (DeepL & Gemini AI) + Secret 7-Tap Master Key
 */
@Composable
fun SettingsAndByokScreen(
    state: MainUiState,
    onBackToMainMenu: () -> Unit,
    onSelectLanguages: (String, String) -> Unit,
    onSelectOcrScript: (OcrScriptType) -> Unit,
    onSelectTranslationEngine: (TranslationEngineType) -> Unit,
    onSelectTtsMode: (TtsVoiceMode) -> Unit,
    onTtsSpeedChange: (Float) -> Unit,
    onCycleCharacterVoice: (String) -> Unit,
    onSelectTheme: (GamerHudTheme) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onToggleContrast: (Boolean) -> Unit,
    onToggleVolumeKeys: (Boolean) -> Unit,
    onSaveApiKeys: (String, String) -> Unit,
    onVersionTapped: () -> Unit,
    onSubmitMasterKey: (String) -> Unit,
    onDismissMasterDialog: () -> Unit
) {
    var deepLInput by remember(state.deepLApiKey) { mutableStateOf(state.deepLApiKey) }
    var geminiInput by remember(state.geminiApiKey) { mutableStateOf(state.geminiApiKey) }
    var showApiKeyGuideDialog by remember { mutableStateOf(false) }
    val theme = state.hudTheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.backgroundColor)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Ayarlar (Tema, Ses, Dil & API)",
                    color = theme.primaryTextColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = state.proStatusBadge,
                    color = Color(0xFF00E676),
                    fontSize = 12.sp
                )
            }
            Button(onClick = onBackToMainMenu) {
                Text("← Ana Menüye Dön")
            }
        }

        // 1. Themes (Background Color + Text Color)
        SettingsSectionCard(
            title = "1. Tema Ayarları (Arka Plan Rengi + Yazı Rengi)",
            surfaceColor = theme.cardSurfaceColor
        ) {
            GamerHudTheme.entries.forEach { item ->
                val isSelected = (state.hudTheme == item)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(item.backgroundColor)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) item.accentColor else Color.White.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelectTheme(item) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            color = item.primaryTextColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = item.subtitle,
                            color = item.primaryTextColor.copy(alpha = 0.70f),
                            fontSize = 11.sp
                        )
                    }
                    if (isSelected) {
                        Text("✓ Seçili", color = item.accentColor, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Altyazı Yazı Boyutu: ${state.fontSizeSp.toInt()} sp",
                color = theme.primaryTextColor,
                fontSize = 13.sp
            )
            Slider(
                value = state.fontSizeSp,
                onValueChange = onFontSizeChange,
                valueRange = 18f..44f
            )
        }

        // 2. Live Dubbing (TTS), Speech Rate & Multi-Character Voice Studio
        SettingsSectionCard(
            title = "2. Seslendirme (TTS), Ses Hızı & Karakter Ses Stüdyosu",
            surfaceColor = theme.cardSurfaceColor
        ) {
            TtsVoiceMode.entries.forEach { mode ->
                FilterChip(
                    selected = (state.ttsVoiceMode == mode),
                    onClick = { onSelectTtsMode(mode) },
                    label = { Text(mode.title) }
                )
                Text(mode.subtitle, color = theme.primaryTextColor.copy(alpha = 0.7f), fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.12f), modifier = Modifier.padding(vertical = 6.dp))

            Text(
                text = "Seslendirme Okuma Hızı: ${String.format(Locale.US, "%.2fx", state.ttsBaseSpeed)}",
                color = theme.speakerBadgeColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Slider(
                value = state.ttsBaseSpeed,
                onValueChange = onTtsSpeedChange,
                valueRange = 0.75f..1.75f
            )

            if (state.assignedCharacterVoices.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Yakalanan Oyun Karakterleri (Sesi değiştirmek için karaktere dokunun):",
                    color = theme.accentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                state.assignedCharacterVoices.forEach { (charName, voiceLabel) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.35f))
                            .clickable { onCycleCharacterVoice(charName) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = charName.replaceFirstChar { it.uppercase() },
                            color = theme.primaryTextColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "$voiceLabel (Değiştir ↻)",
                            color = theme.speakerBadgeColor,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // 3. Language & OCR Settings
        SettingsSectionCard(
            title = "3. Dil Çifti & OCR Alfabesi",
            surfaceColor = theme.cardSurfaceColor
        ) {
            Text("OCR Alfabesi (ML Kit v2):", color = theme.primaryTextColor.copy(alpha = 0.8f), fontSize = 12.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OcrScriptType.entries.forEach { script ->
                    FilterChip(
                        selected = (state.ocrScript == script),
                        onClick = { onSelectOcrScript(script) },
                        label = { Text(script.name, fontSize = 11.sp) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Oyun Dili → Çeviri Dili:", color = theme.primaryTextColor.copy(alpha = 0.8f), fontSize = 12.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("en" to "tr", "ja" to "tr", "ko" to "tr", "ru" to "tr", "de" to "tr").forEach { (src, tgt) ->
                    val selected = state.sourceLanguageCode == src && state.targetLanguageCode == tgt
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectLanguages(src, tgt) },
                        label = { Text("${src.uppercase()} → ${tgt.uppercase()}", fontSize = 12.sp) }
                    )
                }
            }
        }

        // 4. API & Translation Engine Settings
        SettingsSectionCard(
            title = "4. Çeviri Motoru & API Ayarları (Offline + BYOK)",
            surfaceColor = theme.cardSurfaceColor
        ) {
            TranslationEngineType.entries.forEach { engine ->
                val selected = (state.translationEngine == engine)
                FilterChip(
                    selected = selected,
                    onClick = { onSelectTranslationEngine(engine) },
                    label = { Text(engine.title, fontWeight = FontWeight.Bold) }
                )
                Text(engine.description, color = theme.primaryTextColor.copy(alpha = 0.7f), fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.12f), modifier = Modifier.padding(vertical = 8.dp))

            OutlinedTextField(
                value = deepLInput,
                onValueChange = { deepLInput = it },
                label = { Text("DeepL API Anahtarı (BYOK - İsteğe Bağlı)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = geminiInput,
                onValueChange = { geminiInput = it },
                label = { Text("Gemini AI API Anahtarı (Bağlam Duyarlı - İsteğe Bağlı)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = { showApiKeyGuideDialog = true }) {
                    Text("Ücretsiz API Anahtarı Nasıl Alınır?", color = theme.accentColor)
                }
                Button(onClick = { onSaveApiKeys(deepLInput, geminiInput) }) {
                    Text("Anahtarları Kaydet")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Hareketli Arka Plan Kontrast Filtresi", color = theme.primaryTextColor, fontSize = 13.sp)
                Switch(checked = state.highContrastFilterEnabled, onCheckedChange = onToggleContrast)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Fiziksel Ses Tuşlarıyla Alan Geçişi", color = theme.primaryTextColor, fontSize = 13.sp)
                Switch(checked = state.volumeKeysSwitchZones, onCheckedChange = onToggleVolumeKeys)
            }
        }

        // Version Row with Secret 7-Tap Master Key Trigger
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onVersionTapped() }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Sürüm: ${BuildConfig.VERSION_NAME} (${BuildConfig.EDITION_NAME})\n[VIP Master Şifre için sürüme 7 kez dokunun]",
                color = theme.primaryTextColor.copy(alpha = 0.45f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }

    if (showApiKeyGuideDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyGuideDialog = false },
            title = { Text("3 Adımda Ücretsiz API Anahtarı Alma Rehberi") },
            text = {
                Text(
                    "1. Google AI Studio (aistudio.google.com) veya DeepL Developer sayfasına girin.\n\n" +
                        "2. 'Get API Key / Create API Key' butonuna tıklayarak ücretsiz anahtarınızı kopyalayın.\n\n" +
                        "3. Bu ekrandaki kutuya yapıştırıp 'Anahtarları Kaydet' butonuna basın."
                )
            },
            confirmButton = {
                TextButton(onClick = { showApiKeyGuideDialog = false }) {
                    Text("Anladım")
                }
            }
        )
    }

    if (state.showMasterKeyDialog) {
        var masterInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = onDismissMasterDialog,
            title = { Text("Gizli VIP Master Şifre") },
            text = {
                Column {
                    Text("Ömür boyu %100 sınırsız ve reklamsız PRO sürümü açmak için Master Şifreyi girin:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = masterInput,
                        onValueChange = { masterInput = it },
                        label = { Text("Master Şifre") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = { onSubmitMasterKey(masterInput) }) {
                    Text("VIP PRO Aktif Et")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissMasterDialog) {
                    Text("İptal")
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    surfaceColor: Color,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                color = Color(0xFFFFD600),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}
