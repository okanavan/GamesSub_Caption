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
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.gamessub.caption.camera.ScreenQuadCameraCalibrationScreen
import com.gamessub.caption.camera.SingleZoneCameraCalibrationScreen
import com.gamessub.caption.model.NormalizedRoiRect
import com.gamessub.caption.ui.GameTrackingHudScreen
import com.gamessub.caption.ui.HomeMainMenuScreen
import com.gamessub.caption.ui.MainViewModel
import com.gamessub.caption.ui.ScreenMode
import com.gamessub.caption.ui.ScreenSetupHubScreen
import com.gamessub.caption.ui.SettingsAndByokScreen

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val theme = uiState.hudTheme

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

            LaunchedEffect(uiState.isScreenDimmed) {
                val params = window.attributes
                params.screenBrightness = if (uiState.isScreenDimmed) {
                    0.08f
                } else {
                    WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
                window.attributes = params
            }

            LaunchedEffect(uiState.statusToastMessage) {
                uiState.statusToastMessage?.let { msg ->
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                    viewModel.clearToastMessage()
                }
            }

            val dynamicColorScheme = darkColorScheme(
                background = theme.backgroundColor,
                surface = theme.cardSurfaceColor,
                primary = theme.accentColor,
                secondary = theme.speakerBadgeColor
            )

            MaterialTheme(colorScheme = dynamicColorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = theme.backgroundColor
                ) {
                    when (uiState.screenMode) {
                        ScreenMode.HOME_MAIN_MENU -> {
                            HomeMainMenuScreen(
                                state = uiState,
                                onStartGame = {
                                    viewModel.setScreenMode(ScreenMode.GAME_TRACKING_HUD)
                                },
                                onOpenScreenSetupHub = {
                                    viewModel.setScreenMode(ScreenMode.SCREEN_SETUP_HUB)
                                },
                                onOpenSettings = {
                                    viewModel.setScreenMode(ScreenMode.SETTINGS_SCREEN)
                                }
                            )
                        }

                        ScreenMode.SCREEN_SETUP_HUB -> {
                            ScreenSetupHubScreen(
                                state = uiState,
                                onOpenScreenQuadCalibration = viewModel::openScreenQuadCalibration,
                                onSelectZoneToCalibrate = viewModel::openZoneCalibrationCamera,
                                onBackToMainMenu = {
                                    viewModel.setScreenMode(ScreenMode.HOME_MAIN_MENU)
                                },
                                onStartGame = {
                                    viewModel.setScreenMode(ScreenMode.GAME_TRACKING_HUD)
                                }
                            )
                        }

                        ScreenMode.SCREEN_QUAD_CALIBRATION -> {
                            ScreenQuadCameraCalibrationScreen(
                                screenQuad = uiState.screenQuad,
                                onUpdateQuad = viewModel::updateScreenQuad,
                                onSaveAndBackToHub = {
                                    viewModel.setScreenMode(ScreenMode.SCREEN_SETUP_HUB)
                                },
                                onFrameBitmap = viewModel::onCameraFrameAvailable
                            )
                        }

                        ScreenMode.ZONE_CAMERA_CALIBRATION -> {
                            val zone = uiState.calibratingZone
                            val rect = uiState.roiMap[zone] ?: NormalizedRoiRect.defaultFor(zone)
                            SingleZoneCameraCalibrationScreen(
                                zone = zone,
                                screenQuad = uiState.screenQuad,
                                rect = rect,
                                onUpdateRect = { updatedRect ->
                                    viewModel.updateRoiForZone(zone, updatedRect)
                                },
                                onBackToSetupHub = {
                                    viewModel.setScreenMode(ScreenMode.SCREEN_SETUP_HUB)
                                }
                            )
                        }

                        ScreenMode.GAME_TRACKING_HUD -> {
                            GameTrackingHudScreen(
                                state = uiState,
                                onSelectZone = viewModel::selectActiveZone,
                                onTriggerQuestRescan = viewModel::triggerManualQuestRescan,
                                onSimulateSample = viewModel::simulateSampleGameLine,
                                onToggleTts = viewModel::toggleTtsEnabled,
                                onCycleTtsSpeed = viewModel::cycleTtsSpeedPreset,
                                onBackToMainMenu = {
                                    viewModel.setScreenMode(ScreenMode.HOME_MAIN_MENU)
                                },
                                onFrameBitmap = viewModel::onCameraFrameAvailable
                            )
                        }

                        ScreenMode.SETTINGS_SCREEN -> {
                            SettingsAndByokScreen(
                                state = uiState,
                                onBackToMainMenu = {
                                    viewModel.setScreenMode(ScreenMode.HOME_MAIN_MENU)
                                },
                                onSelectLanguages = viewModel::setLanguages,
                                onSelectOcrScript = viewModel::setOcrScript,
                                onSelectTranslationEngine = viewModel::setTranslationEngine,
                                onSelectTtsMode = viewModel::setTtsVoiceMode,
                                onTtsSpeedChange = viewModel::setTtsSpeed,
                                onCycleCharacterVoice = viewModel::cycleVoiceForCharacter,
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
