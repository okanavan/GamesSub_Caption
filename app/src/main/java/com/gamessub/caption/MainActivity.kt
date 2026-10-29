package com.gamessub.caption

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.gamessub.caption.camera.RoiCalibrationScreen
import com.gamessub.caption.ui.MainViewModel
import com.gamessub.caption.ui.OledFullscreenHudScreen
import com.gamessub.caption.ui.ScreenMode
import com.gamessub.caption.ui.SettingsAndByokScreen

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on while translating game subtitles & hide system bars for pure OLED HUD
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            var hasCameraPermission by remember {
                mutableStateOf(
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                )
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { granted ->
                hasCameraPermission = granted
            }

            LaunchedEffect(Unit) {
                if (!hasCameraPermission) {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }

            // Automatically dim physical window brightness when idle for a long time on OLED
            LaunchedEffect(uiState.isScreenDimmed) {
                val params = window.attributes
                params.screenBrightness = if (uiState.isScreenDimmed) 0.08f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = params
            }

            // Show non-blocking Toast notifications when needed
            LaunchedEffect(uiState.statusToastMessage) {
                uiState.statusToastMessage?.let { msg ->
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                    viewModel.clearToastMessage()
                }
            }

            val oledColorScheme = darkColorScheme(
                background = Color.Black,
                surface = Color.Black,
                primary = Color(0xFFFFD600),
                secondary = Color(0xFF00E5FF)
            )

            MaterialTheme(colorScheme = oledColorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    when (uiState.screenMode) {
                        ScreenMode.ROI_CALIBRATION_CAMERA -> {
                            RoiCalibrationScreen(
                                activeZone = uiState.activeZone,
                                roiMap = uiState.roiMap,
                                isProUnlocked = uiState.isProUnlocked,
                                onSelectZone = viewModel::selectActiveZone,
                                onUpdateActiveRoi = viewModel::updateRoiForActiveZone,
                                onFinishCalibration = {
                                    viewModel.setScreenMode(ScreenMode.OLED_FULLSCREEN_HUD)
                                },
                                onFrameBitmap = viewModel::onCameraFrameAvailable
                            )
                        }

                        ScreenMode.OLED_FULLSCREEN_HUD -> {
                            OledFullscreenHudScreen(
                                state = uiState,
                                onSelectZone = viewModel::selectActiveZone,
                                onTriggerQuestRescan = viewModel::triggerManualQuestRescan,
                                onSimulateSample = viewModel::simulateSampleGameLine,
                                onToggleTts = viewModel::toggleTtsEnabled,
                                onOpenCalibration = {
                                    viewModel.setScreenMode(ScreenMode.ROI_CALIBRATION_CAMERA)
                                },
                                onOpenSettings = {
                                    viewModel.setScreenMode(ScreenMode.SETTINGS_AND_BYOK)
                                },
                                onFrameBitmap = viewModel::onCameraFrameAvailable
                            )
                        }

                        ScreenMode.SETTINGS_AND_BYOK -> {
                            SettingsAndByokScreen(
                                state = uiState,
                                onBackToHud = {
                                    viewModel.setScreenMode(ScreenMode.OLED_FULLSCREEN_HUD)
                                },
                                onSelectLanguages = viewModel::setLanguages,
                                onSelectOcrScript = viewModel::setOcrScript,
                                onSelectTranslationEngine = viewModel::setTranslationEngine,
                                onSelectTtsMode = viewModel::setTtsVoiceMode,
                                onSelectTheme = viewModel::setHudTheme,
                                onFontSizeChange = viewModel::setFontSize,
                                onToggleContrast = viewModel::toggleHighContrastFilter,
                                onToggleVolumeKeys = viewModel::toggleVolumeKeyShortcut,
                                onSaveApiKeys = viewModel::saveApiKeys,
                                onVersionTapped = viewModel::onVersionNumberTapped,
                                onSubmitMasterKey = viewModel::submitMasterKey,
                                onDismissMasterDialog = viewModel::dismissMasterKeyDialog
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Physical Volume Up / Volume Down keys let the gamer switch between
     * Subtitle (Zone 1) <-> Quest (Zone 2) <-> Dialogue (Zone 3) without looking at the phone.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (viewModel.cycleZoneByVolumeKey(forward = true)) true
                else super.onKeyDown(keyCode, event)
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (viewModel.cycleZoneByVolumeKey(forward = false)) true
                else super.onKeyDown(keyCode, event)
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

