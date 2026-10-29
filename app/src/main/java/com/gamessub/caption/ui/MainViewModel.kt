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
import com.gamessub.caption.model.ScreenQuad
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
    HOME_MAIN_MENU,
    SCREEN_SETUP_HUB,
    SCREEN_QUAD_CALIBRATION, // Stage 1: 4 Independent Corners Monitor Selection
    ZONE_CAMERA_CALIBRATION, // Stage 2: Sub-ROI Selection inside the Proportional Rectified Screen
    GAME_TRACKING_HUD,
    SETTINGS_SCREEN
}

data class MainUiState(
    val screenMode: ScreenMode = ScreenMode.HOME_MAIN_MENU,
    val activeZone: RoiZoneType = RoiZoneType.SUBTITLE,
    val calibratingZone: RoiZoneType = RoiZoneType.SUBTITLE,
    val screenQuad: ScreenQuad = ScreenQuad(),
    val roiMap: Map<RoiZoneType, NormalizedRoiRect> = mapOf(
        RoiZoneType.SUBTITLE to NormalizedRoiRect.defaultFor(RoiZoneType.SUBTITLE),
        RoiZoneType.QUEST to NormalizedRoiRect.defaultFor(RoiZoneType.QUEST),
        RoiZoneType.DIALOGUE to NormalizedRoiRect.defaultFor(RoiZoneType.DIALOGUE),
        RoiZoneType.LIVE_AR_BLUR to NormalizedRoiRect.defaultFor(RoiZoneType.LIVE_AR_BLUR)
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
    val hudTheme: GamerHudTheme = GamerHudTheme.STUDIO_SLATE,
    val fontSizeSp: Float = 28f,

    val rawDetectedText: String = "",
    val translatedText: String = "Altyazı bekleniyor... (Test için üst şeffaf bardaki ▶ Test butonuna dokunabilirsiniz)",
    val activeSpeakerName: String? = null,
    val detectedSubtitleColor: DominantSubtitleColor = DominantSubtitleColor.WHITE,
    val activeTtsProfileStatus: String = "Hazır",
    val assignedCharacterVoices: Map<String, String> = emptyMap(),

    val currentScanIntervalMs: Long = 380L,
    val isIdlePowerSave: Boolean = false,
    val isQuestSingleShotSleeping: Boolean = false,
    val isScreenDimmed: Boolean = false,
    val lastTextDetectedTimestampMs: Long = System.currentTimeMillis(),

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
                    GamerHudTheme.STUDIO_SLATE
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

    fun openScreenQuadCalibration() {
        _uiState.update {
            it.copy(screenMode = ScreenMode.SCREEN_QUAD_CALIBRATION)
        }
    }

    fun updateScreenQuad(newQuad: ScreenQuad) {
        _uiState.update {
            it.copy(screenQuad = newQuad.clamp())
        }
    }

    fun openZoneCalibrationCamera(zone: RoiZoneType) {
        val state = _uiState.value
        if (!state.isProUnlocked && zone != RoiZoneType.SUBTITLE) {
            _uiState.update {
                it.copy(statusToastMessage = "${zone.titleTr} PRO sürüm özelliğidir.")
            }
            return
        }
        _uiState.update {
            it.copy(
                calibratingZone = zone,
                activeZone = zone,
                screenMode = ScreenMode.ZONE_CAMERA_CALIBRATION
            )
        }
    }

    fun selectActiveZone(zone: RoiZoneType) {
        val state = _uiState.value
        if (!state.isProUnlocked && zone != RoiZoneType.SUBTITLE) {
            _uiState.update {
                it.copy(statusToastMessage = "${zone.shortTitleTr} alanı PRO sürüm özelliğidir.")
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

    fun updateRoiForZone(zone: RoiZoneType, newRect: NormalizedRoiRect) {
        _uiState.update { state ->
            val updated = state.roiMap.toMutableMap()
            updated[zone] = newRect.clamp()
            state.copy(roiMap = updated)
        }
    }

    fun triggerManualQuestRescan() {
        lastStableOcrByZone.remove(RoiZoneType.QUEST)
        _uiState.update {
            it.copy(
                isQuestSingleShotSleeping = false,
                isScreenDimmed = false,
                statusToastMessage = "Görev (Quest) alanı yeniden taranıyor..."
            )
        }
    }

    fun setTtsSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.75f, 1.75f)
        _uiState.update { it.copy(ttsBaseSpeed = clamped) }
    }

    fun cycleTtsSpeedPreset() {
        val presets = listOf(0.90f, 1.00f, 1.15f, 1.30f, 1.50f)
        val current = _uiState.value.ttsBaseSpeed
        val idx = presets.indexOfFirst { kotlin.math.abs(it - current) < 0.06f }
        val nextSpeed = presets[(idx + 1) % presets.size]
        setTtsSpeed(nextSpeed)
    }

    fun cycleVoiceForCharacter(speakerName: String) {
        val newProfile = ttsManager.cycleCharacterVoiceProfile(speakerName)
        syncCharacterVoiceMap()
        _uiState.update {
            it.copy(statusToastMessage = "$speakerName -> ${newProfile.label}")
        }
    }

    private fun syncCharacterVoiceMap() {
        val mapped = ttsManager.getAssignedCharacters().mapValues { it.value.label }
        _uiState.update { it.copy(assignedCharacterVoices = mapped) }
    }

    fun onCameraFrameAvailable(fullFrameBitmap: Bitmap) {
        val state = _uiState.value

        if (state.screenMode != ScreenMode.GAME_TRACKING_HUD) {
            fullFrameBitmap.recycle()
            return
        }

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
        val quad = state.screenQuad
        // For Blur Mode, use the ENTIRE selected ScreenQuad (0..1); for other zones, use their sub-box inside the quad
        val roiRect = if (activeZone.isLiveArBlurMode) {
            NormalizedRoiRect(0.0f, 0.0f, 1.0f, 1.0f)
        } else {
            state.roiMap[activeZone] ?: NormalizedRoiRect.defaultFor(activeZone)
        }

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val processed = RoiImageProcessor.cropAndProcess(
                    fullFrame = fullFrameBitmap,
                    screenQuad = quad,
                    roiInsideScreen = roiRect,
                    applyHighContrastFilter = state.highContrastFilterEnabled
                )
                fullFrameBitmap.recycle()

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
            val idleDurationMs = nowMs - state.lastTextDetectedTimestampMs
            val shouldIdle = idleDurationMs >= 10_000L
            val shouldDim = idleDurationMs >= 25_000L && !zone.isLiveArBlurMode
            _uiState.update {
                it.copy(
                    isIdlePowerSave = shouldIdle,
                    currentScanIntervalMs = if (shouldIdle) 1000L else 380L,
                    isScreenDimmed = shouldDim
                )
            }
            return
        }

        val previousForZone = lastStableOcrByZone[zone].orEmpty()
        if (TextStabilityFilter.isDuplicateOrFlicker(previousForZone, cleaned, threshold = 0.85f)) {
            _uiState.update {
                it.copy(
                    lastTextDetectedTimestampMs = nowMs,
                    isIdlePowerSave = false,
                    isScreenDimmed = false,
                    currentScanIntervalMs = 380L
                )
            }
            return
        }

        lastStableOcrByZone[zone] = cleaned

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
        syncCharacterVoiceMap()

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

    fun simulateSampleGameLine() {
        val state = _uiState.value
        val samplesByZone = when (state.activeZone) {
            RoiZoneType.SUBTITLE, RoiZoneType.LIVE_AR_BLUR -> listOf(
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
        syncCharacterVoiceMap()

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
        _uiState.update { it.copy(fontSizeSp = sizeSp.coerceIn(18f, 44f)) }
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
