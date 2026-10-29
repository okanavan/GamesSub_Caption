package com.gamessub.caption.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gamessub.caption.billing.ProAccessManager
import com.gamessub.caption.model.DominantSubtitleColor
import com.gamessub.caption.model.GamerHudTheme
import com.gamessub.caption.model.NormalizedRoiRect
import com.gamessub.caption.model.OcrScriptType
import com.gamessub.caption.model.RoiZoneType
import com.gamessub.caption.model.TranslationEngineType
import com.gamessub.caption.model.TtsVoiceMode
import com.gamessub.caption.ocr.MlKitOcrEngine
import com.gamessub.caption.ocr.RoiImageProcessor
import com.gamessub.caption.translation.HybridTranslationManager
import com.gamessub.caption.tts.MultiVoiceTtsManager
import com.gamessub.caption.util.SpeakerParser
import com.gamessub.caption.util.TextStabilityFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

enum class ScreenMode {
    ROI_CALIBRATION_CAMERA, // Step 1: Select 3 ROI boxes on monitor
    OLED_FULLSCREEN_HUD,    // Step 2: Previewless CameraX + Fullscreen #000000 OLED Reader
    SETTINGS_AND_BYOK       // Step 3: Engines, API Keys, 7-tap Master Key, Themes
}

data class MainUiState(
    val screenMode: ScreenMode = ScreenMode.OLED_FULLSCREEN_HUD,
    val activeZone: RoiZoneType = RoiZoneType.SUBTITLE,
    val roiMap: Map<RoiZoneType, NormalizedRoiRect> = mapOf(
        RoiZoneType.SUBTITLE to NormalizedRoiRect.defaultFor(RoiZoneType.SUBTITLE),
        RoiZoneType.QUEST to NormalizedRoiRect.defaultFor(RoiZoneType.QUEST),
        RoiZoneType.DIALOGUE to NormalizedRoiRect.defaultFor(RoiZoneType.DIALOGUE)
    ),
    val ocrScript: OcrScriptType = OcrScriptType.LATIN,
    val sourceLanguageCode: String = "en",
    val targetLanguageCode: String = "tr",
    val translationEngine: TranslationEngineType = TranslationEngineType.OFFLINE_MLKIT,
    val ttsVoiceMode: TtsVoiceMode = TtsVoiceMode.MULTI_CHARACTER,
    val ttsEnabled: Boolean = true,
    val ttsBaseSpeed: Float = 1.05f,
    val autoCatchUpTts: Boolean = true,
    val highContrastFilterEnabled: Boolean = true,
    val volumeKeysSwitchZones: Boolean = true,
    val hudTheme: GamerHudTheme = GamerHudTheme.OLED_STEALTH,
    val fontSizeSp: Float = 26f,

    // Live OCR & Translation Output
    val rawDetectedText: String = "",
    val translatedText: String = "Oyun ekranında Altyazı veya Görev (Quest) alanını seçin ya da Hızlı Simülasyon ile test edin.",
    val activeSpeakerName: String? = null,
    val detectedSubtitleColor: DominantSubtitleColor = DominantSubtitleColor.WHITE,
    val activeTtsProfileStatus: String = "Hazır",

    // Dynamic Scanning & Power Saving Telemetry
    val currentScanIntervalMs: Long = 400L,
    val isIdlePowerSave: Boolean = false,
    val isQuestSingleShotSleeping: Boolean = false,
    val isScreenDimmed: Boolean = false,
    val lastTextDetectedTimestampMs: Long = System.currentTimeMillis(),

    // PRO & Monetization State
    val isProUnlocked: Boolean = true,
    val showBottomBannerAd: Boolean = false,
    val proStatusBadge: String = "",
    val deepLApiKey: String = "",
    val geminiApiKey: String = "",
    val versionTapCount: Int = 0,
    val showMasterKeyDialog: Boolean = false,
    val statusToastMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val proAccessManager = ProAccessManager(application)
    private val ocrEngine = MlKitOcrEngine()
    private val translationManager = HybridTranslationManager()
    private val ttsManager = MultiVoiceTtsManager(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val isProcessingFrame = AtomicBoolean(false)
    private var lastFrameProcessedAtMs: Long = 0L
    private var lastStableOcrByZone = mutableMapOf<RoiZoneType, String>()

    init {
        refreshProAndKeysState()
        ttsManager.setTargetLanguage(_uiState.value.targetLanguageCode)
    }

    private fun refreshProAndKeysState() {
        val hasPro = proAccessManager.hasProAccess()
        _uiState.update { state ->
            state.copy(
                isProUnlocked = hasPro,
                showBottomBannerAd = proAccessManager.shouldShowBottomBannerAd(),
                proStatusBadge = proAccessManager.getStatusBadgeText(),
                deepLApiKey = proAccessManager.deepLApiKey,
                geminiApiKey = proAccessManager.geminiApiKey,
                // Enforce Free tier rules if trial expired & not unlocked
                activeZone = if (!hasPro) RoiZoneType.SUBTITLE else state.activeZone,
                translationEngine = if (!hasPro && state.translationEngine.requiresPro) {
                    TranslationEngineType.OFFLINE_MLKIT
                } else {
                    state.translationEngine
                },
                ttsVoiceMode = if (!hasPro && state.ttsVoiceMode.requiresPro) {
                    TtsVoiceMode.SINGLE_NARRATOR
                } else {
                    state.ttsVoiceMode
                },
                hudTheme = if (!hasPro && state.hudTheme.requiresPro) {
                    GamerHudTheme.OLED_STEALTH
                } else {
                    state.hudTheme
                }
            )
        }
    }

    fun setScreenMode(mode: ScreenMode) {
        _uiState.update {
            it.copy(
                screenMode = mode,
                isQuestSingleShotSleeping = false,
                isScreenDimmed = false
            )
        }
    }

    fun selectActiveZone(zone: RoiZoneType) {
        val state = _uiState.value
        if (!state.isProUnlocked && zone != RoiZoneType.SUBTITLE) {
            _uiState.update {
                it.copy(statusToastMessage = "Görev (Quest) ve Diyalog alanları PRO sürüm özelliğidir.")
            }
            return
        }
        _uiState.update {
            it.copy(
                activeZone = zone,
                isQuestSingleShotSleeping = false,
                isIdlePowerSave = false,
                isScreenDimmed = false,
                currentScanIntervalMs = 380L
            )
        }
    }

    /**
     * Triggered by Physical Volume Up / Volume Down buttons so the gamer can switch
     * between Subtitle, Quest, and Dialogue zones without looking at the phone screen.
     */
    fun cycleZoneByVolumeKey(forward: Boolean): Boolean {
        val state = _uiState.value
        if (!state.volumeKeysSwitchZones) return false
        if (!state.isProUnlocked) {
            _uiState.update {
                it.copy(statusToastMessage = "Ses tuşlarıyla çoklu alan geçişi PRO özelliğidir.")
            }
            return true
        }
        val zones = RoiZoneType.entries
        val currentIndex = zones.indexOf(state.activeZone)
        val nextIndex = if (forward) {
            (currentIndex + 1) % zones.size
        } else {
            (currentIndex - 1 + zones.size) % zones.size
        }
        selectActiveZone(zones[nextIndex])
        return true
    }

    fun updateRoiForActiveZone(newRect: NormalizedRoiRect) {
        val currentZone = _uiState.value.activeZone
        _uiState.update { state ->
            val updated = state.roiMap.toMutableMap()
            updated[currentZone] = newRect.clamp()
            state.copy(roiMap = updated)
        }
    }

    /**
     * Re-triggers single-shot Quest OCR scan when the user is on Quest Zone 2.
     */
    fun triggerManualQuestRescan() {
        lastStableOcrByZone.remove(RoiZoneType.QUEST)
        _uiState.update {
            it.copy(
                isQuestSingleShotSleeping = false,
                isScreenDimmed = false,
                statusToastMessage = "Görev (Quest) penceresi yeniden taranıyor..."
            )
        }
    }

    /**
     * Core Previewless CameraX Frame Analyzer Entry Point:
     * - Respects dynamic scan interval (350-450ms active, 1000ms after 10s idle).
     * - Pauses automatically after 1 clean read in Quest Single-Shot mode.
     * - Crops ONLY the active ROI rectangle.
     * - Applies 85% Levenshtein Anti-Flicker filter before calling Translation & TTS.
     */
    fun onCameraFrameAvailable(fullFrameBitmap: Bitmap) {
        val state = _uiState.value

        // Only run background OCR when in OLED Fullscreen HUD mode
        if (state.screenMode != ScreenMode.OLED_FULLSCREEN_HUD) {
            fullFrameBitmap.recycle()
            return
        }

        // If Quest Zone is in Single-Shot Sleep mode, skip processing to save 100% CPU/battery
        if (state.activeZone == RoiZoneType.QUEST && state.isQuestSingleShotSleeping) {
            fullFrameBitmap.recycle()
            return
        }

        val now = System.currentTimeMillis()
        val elapsedSinceLastScan = now - lastFrameProcessedAtMs
        if (elapsedSinceLastScan < state.currentScanIntervalMs) {
            fullFrameBitmap.recycle()
            return
        }

        if (!isProcessingFrame.compareAndSet(false, true)) {
            fullFrameBitmap.recycle()
            return
        }
        lastFrameProcessedAtMs = now

        val activeZone = state.activeZone
        val roiRect = state.roiMap[activeZone] ?: NormalizedRoiRect.defaultFor(activeZone)

        viewModelScope.launch(Dispatchers.Default) {
            try {
                // Step 1 & 2: Crop ONLY active ROI + detect dominant text color + contrast filter
                val processed = RoiImageProcessor.cropAndProcess(
                    fullFrame = fullFrameBitmap,
                    roi = roiRect,
                    applyHighContrastFilter = state.highContrastFilterEnabled
                )
                fullFrameBitmap.recycle()

                // Step 3: Multi-script ML Kit OCR v2
                val rawOcr = ocrEngine.recognizeText(processed.croppedBitmap, state.ocrScript)
                processed.croppedBitmap.recycle()

                handleRecognizedText(
                    rawOcrText = rawOcr,
                    dominantColor = processed.dominantTextColor,
                    zone = activeZone,
                    nowMs = now
                )
            } catch (_: Exception) {
                fullFrameBitmap.recycle()
            } finally {
                isProcessingFrame.set(false)
            }
        }
    }

    private suspend fun handleRecognizedText(
        rawOcrText: String,
        dominantColor: DominantSubtitleColor,
        zone: RoiZoneType,
        nowMs: Long
    ) {
        val state = _uiState.value
        val cleaned = rawOcrText.trim()

        if (cleaned.length < 2) {
            // Check 10-second idle threshold -> slow scan to 1000ms & dim OLED after 25s
            val idleDurationMs = nowMs - state.lastTextDetectedTimestampMs
            val shouldIdle = idleDurationMs >= 10_000L
            val shouldDim = idleDurationMs >= 25_000L
            _uiState.update {
                it.copy(
                    isIdlePowerSave = shouldIdle,
                    currentScanIntervalMs = if (shouldIdle) 1000L else 400L,
                    isScreenDimmed = shouldDim
                )
            }
            return
        }

        // Step 3: 85% Levenshtein Anti-Flicker Filter
        val previousForZone = lastStableOcrByZone[zone].orEmpty()
        if (TextStabilityFilter.isDuplicateOrFlicker(previousForZone, cleaned, threshold = 0.85f)) {
            _uiState.update {
                it.copy(
                    lastTextDetectedTimestampMs = nowMs,
                    isIdlePowerSave = false,
                    isScreenDimmed = false,
                    currentScanIntervalMs = 400L
                )
            }
            return
        }

        // New stable subtitle or quest text confirmed!
        lastStableOcrByZone[zone] = cleaned

        // Parse speaker name BEFORE/AFTER translation so speaker name is never read aloud by TTS
        val sourceParsed = SpeakerParser.parse(cleaned, dominantColor)

        val translationResult = translationManager.translate(
            text = sourceParsed.cleanSpokenText,
            sourceLangCode = state.sourceLanguageCode,
            targetLangCode = state.targetLanguageCode,
            engine = state.translationEngine,
            deepLApiKey = state.deepLApiKey,
            geminiApiKey = state.geminiApiKey
        )

        val translatedBody = translationResult.getOrDefault(sourceParsed.cleanSpokenText)
        val finalParsedForTts = sourceParsed.copy(cleanSpokenText = translatedBody)

        val ttsStatus = ttsManager.speakDialogue(
            parsedLine = finalParsedForTts,
            voiceMode = state.ttsVoiceMode,
            enabled = state.ttsEnabled,
            userBaseSpeed = state.ttsBaseSpeed,
            autoCatchUpEnabled = state.autoCatchUpTts
        )

        val enterQuestSleep = (zone == RoiZoneType.QUEST && zone.isSingleShotDefault)

        _uiState.update {
            it.copy(
                rawDetectedText = cleaned,
                translatedText = translatedBody,
                activeSpeakerName = sourceParsed.speakerName,
                detectedSubtitleColor = dominantColor,
                activeTtsProfileStatus = ttsStatus,
                lastTextDetectedTimestampMs = nowMs,
                isIdlePowerSave = false,
                isScreenDimmed = false,
                currentScanIntervalMs = 380L,
                isQuestSingleShotSleeping = enterQuestSleep
            )
        }
    }

    /**
     * Simulates live game subtitles & quest logs (e.g., Witcher / Cyberpunk / RPG quests)
     * so the user can test Multi-Voice TTS, Speaker Extraction, and HUD themes immediately.
     */
    fun simulateSampleGameLine() {
        val state = _uiState.value
        val samplesByZone = when (state.activeZone) {
            RoiZoneType.SUBTITLE -> listOf(
                Triple(
                    "Geralt: Wind's howling... Looks like a storm is coming from the northern hills.",
                    "Rüzgar uğulduyor... Kuzey tepelerinden fırtına geliyor gibi görünüyor.",
                    DominantSubtitleColor.WHITE
                ),
                Triple(
                    "[Yennefer] Hold on! Don't touch that portal monolith without the ward!",
                    "Bekle! Koruma tılsımı olmadan o portal dikilitaşına sakın dokunma!",
                    DominantSubtitleColor.CYAN_BLUE
                ),
                Triple(
                    "Johnny Silverhand: Wake the hell up, Samurai. We have a city to burn.",
                    "Uyan artık Samuray. Yakacak bir şehrimiz var.",
                    DominantSubtitleColor.YELLOW
                )
            )
            RoiZoneType.QUEST -> listOf(
                Triple(
                    "MAIN QUEST: Find the ancient sunstone in the sunken ruins (0/1)\nOptional: Speak with the blacksmith in Novigrad.",
                    "ANA GÖREV: Batık harabelerdeki kadim güneş taşını bul (0/1)\nİsteğe Bağlı: Novigrad'daki demirciyle konuş.",
                    DominantSubtitleColor.WHITE
                )
            )
            RoiZoneType.DIALOGUE -> listOf(
                Triple(
                    "1. [Persuade] Hand over the relic peacefully.\n2. [Attack] Draw your silver sword.\n3. Ask about the missing witcher.",
                    "1. [İkna Et] Yadigârı barışçıl şekilde teslim et.\n2. [Saldır] Gümüş kılıcını çek.\n3. Kayıp efsungeri sor.",
                    DominantSubtitleColor.GREEN
                )
            )
        }

        val chosen = samplesByZone.random()
        val parsed = SpeakerParser.parse(chosen.first, chosen.third)
        val displayParsed = parsed.copy(cleanSpokenText = chosen.second)
        val ttsStatus = ttsManager.speakDialogue(
            parsedLine = displayParsed,
            voiceMode = state.ttsVoiceMode,
            enabled = state.ttsEnabled,
            userBaseSpeed = state.ttsBaseSpeed,
            autoCatchUpEnabled = state.autoCatchUpTts
        )

        _uiState.update {
            it.copy(
                rawDetectedText = chosen.first,
                translatedText = chosen.second,
                activeSpeakerName = parsed.speakerName,
                detectedSubtitleColor = chosen.third,
                activeTtsProfileStatus = ttsStatus,
                isScreenDimmed = false,
                isQuestSingleShotSleeping = (state.activeZone == RoiZoneType.QUEST)
            )
        }
    }

    fun setLanguages(sourceCode: String, targetCode: String) {
        ttsManager.setTargetLanguage(targetCode)
        _uiState.update {
            it.copy(sourceLanguageCode = sourceCode, targetLanguageCode = targetCode)
        }
    }

    fun setOcrScript(script: OcrScriptType) {
        _uiState.update { it.copy(ocrScript = script) }
    }

    fun setTranslationEngine(engine: TranslationEngineType) {
        if (engine.requiresPro && !_uiState.value.isProUnlocked) {
            _uiState.update {
                it.copy(statusToastMessage = "${engine.title} sadece PRO sürümde kullanılabilir.")
            }
            return
        }
        _uiState.update { it.copy(translationEngine = engine) }
    }

    fun setTtsVoiceMode(mode: TtsVoiceMode) {
        if (mode.requiresPro && !_uiState.value.isProUnlocked) {
            _uiState.update {
                it.copy(statusToastMessage = "Çoklu Karakter Seslendirme PRO sürüm özelliğidir.")
            }
            return
        }
        _uiState.update { it.copy(ttsVoiceMode = mode) }
    }

    fun toggleTtsEnabled() {
        val next = !_uiState.value.ttsEnabled
        if (!next) ttsManager.stop()
        _uiState.update { it.copy(ttsEnabled = next) }
    }

    fun setHudTheme(theme: GamerHudTheme) {
        if (theme.requiresPro && !_uiState.value.isProUnlocked) {
            _uiState.update {
                it.copy(statusToastMessage = "${theme.title} teması PRO sürüm özelliğidir.")
            }
            return
        }
        _uiState.update { it.copy(hudTheme = theme) }
    }

    fun setFontSize(sizeSp: Float) {
        _uiState.update { it.copy(fontSizeSp = sizeSp.coerceIn(18f, 42f)) }
    }

    fun toggleHighContrastFilter(enabled: Boolean) {
        _uiState.update { it.copy(highContrastFilterEnabled = enabled) }
    }

    fun toggleVolumeKeyShortcut(enabled: Boolean) {
        _uiState.update { it.copy(volumeKeysSwitchZones = enabled) }
    }

    fun saveApiKeys(deepLKey: String, geminiKey: String) {
        proAccessManager.deepLApiKey = deepLKey
        proAccessManager.geminiApiKey = geminiKey
        refreshProAndKeysState()
        _uiState.update {
            it.copy(statusToastMessage = "API anahtarları cihazınıza güvenli şekilde kaydedildi.")
        }
    }

    /**
     * 7-Tap Secret Master Key Trigger in Settings version row.
     */
    fun onVersionNumberTapped() {
        val nextCount = _uiState.value.versionTapCount + 1
        if (nextCount >= 7) {
            _uiState.update {
                it.copy(versionTapCount = 0, showMasterKeyDialog = true)
            }
        } else {
            val remaining = 7 - nextCount
            _uiState.update {
                it.copy(
                    versionTapCount = nextCount,
                    statusToastMessage = if (remaining <= 3) {
                        "VIP Master Şifre ekranı için $remaining dokunuş kaldı..."
                    } else {
                        null
                    }
                )
            }
        }
    }

    fun dismissMasterKeyDialog() {
        _uiState.update { it.copy(showMasterKeyDialog = false) }
    }

    fun submitMasterKey(code: String) {
        val unlocked = proAccessManager.verifyAndUnlockMasterKey(code)
        refreshProAndKeysState()
        _uiState.update {
            it.copy(
                showMasterKeyDialog = !unlocked,
                statusToastMessage = if (unlocked) {
                    "Tebrikler! Ömür boyu sınırsız VIP PRO yetkisi aktif edildi!"
                } else {
                    "Geçersiz Master Şifre."
                }
            )
        }
    }

    fun clearToastMessage() {
        _uiState.update { it.copy(statusToastMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        ocrEngine.close()
        translationManager.close()
        ttsManager.shutdown()
    }
}

