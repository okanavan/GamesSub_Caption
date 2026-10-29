package com.gamessub.caption.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamessub.caption.BuildConfig
import com.gamessub.caption.camera.CameraXControllerEffect
import com.gamessub.caption.model.GamerHudTheme
import com.gamessub.caption.model.OcrScriptType
import com.gamessub.caption.model.RoiZoneType
import com.gamessub.caption.model.SupportedLanguages
import com.gamessub.caption.model.TranslationEngineType
import com.gamessub.caption.model.TtsVoiceMode

/**
 * Fullscreen #000000 AMOLED "Gamer HUD" Reading Screen:
 * - Camera preview is completely disabled (`Previewless CameraX` runs in background).
 * - Only text pixels light up on OLED screens.
 * - One-tap switching between Zone 1 (Subtitle), Zone 2 (Quest), and Zone 3 (Dialogue),
 *   plus Physical Volume Up/Down key support.
 * - Free tier post-7-day layout reserves top 82% strictly for subtitles and bottom 18% for a
 *   fixed AdMob Adaptive Banner strip that NEVER overlaps translation text.
 */
@Composable
fun OledFullscreenHudScreen(
    state: MainUiState,
    onSelectZone: (RoiZoneType) -> Unit,
    onTriggerQuestRescan: () -> Unit,
    onSimulateSample: () -> Unit,
    onToggleTts: () -> Unit,
    onOpenCalibration: () -> Unit,
    onOpenSettings: () -> Unit,
    onFrameBitmap: (Bitmap) -> Unit
) {
    // Bind Previewless CameraX (showPreview = false -> zero GPU preview surface!)
    CameraXControllerEffect(
        showPreview = false,
        previewView = null,
        onFrameBitmap = onFrameBitmap
    )

    val theme = state.hudTheme
    val contentAlpha = if (state.isScreenDimmed) 0.42f else 1.0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black) // Pure #000000 AMOLED Black
    ) {
        // Top/Center 82% (or 100% in PRO/VIP) strictly dedicated to Gamer HUD & Subtitles
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(if (state.showBottomBannerAd) 0.82f else 1.0f)
                .alpha(contentAlpha)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Stealth Top Status & Control Strip
            HudTopBar(
                state = state,
                onToggleTts = onToggleTts,
                onSimulateSample = onSimulateSample,
                onOpenCalibration = onOpenCalibration,
                onOpenSettings = onOpenSettings
            )

            // 2. Center Fullscreen Translation & Speaker Badge Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Speaker Badge (e.g., [GERALT] or [YENNEFER]) - parsed & NOT spoken aloud by TTS
                    if (!state.activeSpeakerName.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = theme.speakerBadgeColor.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = theme.speakerBadgeColor.copy(alpha = 0.65f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${state.activeSpeakerName}  •  ${state.activeTtsProfileStatus}",
                                color = theme.speakerBadgeColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // Main Translated Text (High-Contrast OLED Typography)
                    Text(
                        text = state.translatedText,
                        color = theme.primaryTextColor,
                        fontSize = state.fontSizeSp.sp,
                        lineHeight = (state.fontSizeSp * 1.32f).sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        fontFamily = if (theme == GamerHudTheme.CYBER_HUD_NEON) {
                            FontFamily.Monospace
                        } else {
                            FontFamily.SansSerif
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Subtle original OCR source line below
                    if (state.rawDetectedText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = state.rawDetectedText,
                            color = Color(0xFF78909C),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Quest Single-Shot Sleep Indicator + Rescan Button
                    if (state.activeZone == RoiZoneType.QUEST && state.isQuestSingleShotSleeping) {
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = onTriggerQuestRescan,
                            border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = theme.accentColor
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Görev Okundu (Kamera Uyku Modunda) • Yeniden Tara",
                                color = theme.accentColor,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // 3. Bottom One-Tap Zone Switcher (Altyazı <-> Görev <-> Diyalog)
            HudZoneSwitcherBar(
                state = state,
                onSelectZone = onSelectZone
            )
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

@Composable
private fun HudTopBar(
    state: MainUiState,
    onToggleTts: () -> Unit,
    onSimulateSample: () -> Unit,
    onOpenCalibration: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0A0A), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF1F1F1F), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "OLED Previewless Aktif",
                    color = Color(0xFF00E676),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "• ${state.sourceLanguageCode.uppercase()} → ${state.targetLanguageCode.uppercase()}",
                    color = Color(0xFFB0BEC5),
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (state.isIdlePowerSave) "• Uyku (1000ms)" else "• Tarama (${state.currentScanIntervalMs}ms)",
                    color = Color(0xFF78909C),
                    fontSize = 11.sp
                )
            }
            Text(
                text = state.proStatusBadge,
                color = Color(0xFFFFD54F),
                fontSize = 10.sp
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Simulate sample subtitle/quest button for instant testing
            IconButton(onClick = onSimulateSample) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Test Altyazı Simülasyonu",
                    tint = Color(0xFF69F0AE)
                )
            }
            // Mute / Unmute Live Dubbing TTS
            IconButton(onClick = onToggleTts) {
                Icon(
                    imageVector = if (state.ttsEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    contentDescription = "Sesli Okuma (TTS)",
                    tint = if (state.ttsEnabled) Color(0xFF00E5FF) else Color.Gray
                )
            }
            // Re-open ROI Camera Calibration
            IconButton(onClick = onOpenCalibration) {
                Icon(
                    imageVector = Icons.Default.CropFree,
                    contentDescription = "Alan Seçimi (ROI)",
                    tint = Color(0xFFFFD600)
                )
            }
            // Open Settings & BYOK API Keys
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Ayarlar",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun HudZoneSwitcherBar(
    state: MainUiState,
    onSelectZone: (RoiZoneType) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RoiZoneType.entries.forEach { zone ->
                val isSelected = (state.activeZone == zone)
                Button(
                    onClick = { onSelectZone(zone) },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) {
                            zone.badgeColor.copy(alpha = 0.22f)
                        } else {
                            Color(0xFF121212)
                        },
                        contentColor = if (isSelected) zone.badgeColor else Color(0xFF90A4AE)
                    )
                ) {
                    Text(
                        text = zone.titleTr,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Fiziksel Ses Açma / Kısma (Volume Up/Down) tuşlarıyla ekrana bakmadan alan değiştirebilirsiniz.",
            color = Color(0xFF546E7A),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Settings, Cross-Language Selector, BYOK API Keys (DeepL / Gemini AI), Multi-Voice TTS,
 * Gamer HUD Themes, and 7-Tap Secret Master Key Screen.
 */
@Composable
fun SettingsAndByokScreen(
    state: MainUiState,
    onBackToHud: () -> Unit,
    onSelectLanguages: (String, String) -> Unit,
    onSelectOcrScript: (OcrScriptType) -> Unit,
    onSelectTranslationEngine: (TranslationEngineType) -> Unit,
    onSelectTtsMode: (TtsVoiceMode) -> Unit,
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
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
                    text = "Ayarlar, Çeviri Motorları & Canlı Dublaj",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = state.proStatusBadge,
                    color = Color(0xFF00E676),
                    fontSize = 12.sp
                )
            }
            Button(onClick = onBackToHud) {
                Text("HUD'a Dön")
            }
        }

        // 1. Cross-Language & OCR Script Selection
        SettingsSectionCard(title = "1. Çapraz Dil & OCR Alfabesi (Any-to-Any)") {
            Text("OCR Alfabesi (ML Kit v2):", color = Color(0xFFB0BEC5), fontSize = 12.sp)
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
            Text("Kaynak Oyun Dili → Hedef Çeviri Dili:", color = Color(0xFFB0BEC5), fontSize = 12.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("en" to "tr", "ja" to "tr", "ko" to "tr", "ru" to "tr", "en" to "es").forEach { (src, tgt) ->
                    val selected = state.sourceLanguageCode == src && state.targetLanguageCode == tgt
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectLanguages(src, tgt) },
                        label = { Text("${src.uppercase()} → ${tgt.uppercase()}", fontSize = 12.sp) }
                    )
                }
            }
        }

        // 2. Hybrid Translation Engine (Offline ML Kit + BYOK DeepL + BYOK Gemini AI)
        SettingsSectionCard(title = "2. Çeviri Motoru (Offline + Kendi API Anahtarın / BYOK)") {
            TranslationEngineType.entries.forEach { engine ->
                val selected = (state.translationEngine == engine)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectTranslationEngine(engine) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectTranslationEngine(engine) },
                        label = { Text(engine.title, fontWeight = FontWeight.Bold) }
                    )
                }
                Text(engine.description, color = Color(0xFF90A4AE), fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
            }

            HorizontalDivider(color = Color(0xFF263238), modifier = Modifier.padding(vertical = 8.dp))

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
                    Text("Ücretsiz API Anahtarı Nasıl Alınır? (3 Adım)", color = Color(0xFF00E5FF))
                }
                Button(onClick = { onSaveApiKeys(deepLInput, geminiInput) }) {
                    Text("Anahtarları Kaydet")
                }
            }
        }

        // 3. Multi-Character TTS Dubbing vs. Single Narrator
        SettingsSectionCard(title = "3. Canlı Dublaj (TTS - Tek Ses & Çoklu Karakter Sesi)") {
            TtsVoiceMode.entries.forEach { mode ->
                FilterChip(
                    selected = (state.ttsVoiceMode == mode),
                    onClick = { onSelectTtsMode(mode) },
                    label = { Text(mode.title) }
                )
                Text(mode.subtitle, color = Color(0xFF90A4AE), fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        // 4. Gamer HUD Themes & Anti-Flicker Filter
        SettingsSectionCard(title = "4. Gamer HUD Temaları & Görüntü Filtresi") {
            GamerHudTheme.entries.forEach { theme ->
                FilterChip(
                    selected = (state.hudTheme == theme),
                    onClick = { onSelectTheme(theme) },
                    label = { Text(theme.title, color = theme.primaryTextColor) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color(0xFF141414),
                        selectedContainerColor = Color(0xFF263238)
                    )
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Altyazı Punto Boyutu: ${state.fontSizeSp.toInt()} sp", color = Color.White, fontSize = 13.sp)
            Slider(
                value = state.fontSizeSp,
                onValueChange = onFontSizeChange,
                valueRange = 18f..42f
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Hareketli Arka Plan Kontrast Filtresi", color = Color.White, fontSize = 13.sp)
                Switch(checked = state.highContrastFilterEnabled, onCheckedChange = onToggleContrast)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Fiziksel Ses Tuşlarıyla Alan Geçişi", color = Color.White, fontSize = 13.sp)
                Switch(checked = state.volumeKeysSwitchZones, onCheckedChange = onToggleVolumeKeys)
            }
        }

        // 5. Version Row with Secret 7-Tap Master Key Trigger
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onVersionTapped() }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Sürüm: ${BuildConfig.VERSION_NAME} (${BuildConfig.EDITION_NAME})\n[VIP Master Şifre için sürüme 7 kez dokunun]",
                color = Color(0xFF607D8B),
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
                        "3. Bu ekrandaki kutuya yapıştırıp 'Anahtarları Kaydet' butonuna basın. Anahtarınız yalnızca kendi telefonunuzda saklanır."
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
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101010)),
        shape = RoundedCornerShape(12.dp)
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

