package com.autoscroll.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autoscroll.app.ui.screen.DashboardScreen
import com.autoscroll.app.ui.screen.MyMacrosScreen
import com.autoscroll.app.ui.screen.PermissionsScreen
import com.autoscroll.app.ui.screen.RecorderScreen
import com.autoscroll.app.ui.theme.AutoScrollTheme
import com.autoscroll.app.ui.theme.ShopeeOrange
import com.autoscroll.app.ui.theme.ShopeeOrangeSoft
import com.autoscroll.app.ui.theme.SurfaceWhite
import com.autoscroll.app.ui.theme.TextSecondary
import com.autoscroll.app.ui.viewmodel.MainViewModel

private enum class AppTab(val label: String) {
    AUTO_SCROLL("Auto Scroll"),
    RECORDER("Rekam"),
    MY_MACROS("My Macros")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AutoScrollTheme {
                val viewModel: MainViewModel = viewModel()
                val config by viewModel.config.collectAsState()
                val runtimeState by viewModel.runtimeState.collectAsState()
                val recordingState by viewModel.recordingState.collectAsState()
                val playbackState by viewModel.playbackState.collectAsState()
                val savedMacros by viewModel.savedMacros.collectAsState()
                val permissions by viewModel.permissions.collectAsState()
                val targetApps by viewModel.targetApps.collectAsState()
                val uiMessage by viewModel.uiMessage.collectAsState()

                var showPermissions by rememberSaveable { mutableStateOf(false) }
                var selectedTab by rememberSaveable { mutableStateOf(AppTab.AUTO_SCROLL) }

                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            viewModel.refreshSystemState()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                if (showPermissions) {
                    PermissionsScreen(
                        permissions = permissions,
                        onBack = { showPermissions = false },
                        onOpenAccessibility = viewModel::openAccessibilitySettings,
                        onOpenOverlay = viewModel::openOverlaySettings
                    )
                } else {
                    Scaffold(
                        bottomBar = {
                            NavigationBar(containerColor = SurfaceWhite) {
                                AppTab.entries.forEach { tab ->
                                    val selected = selectedTab == tab
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { selectedTab = tab },
                                        icon = {
                                            Icon(
                                                imageVector = when (tab) {
                                                    AppTab.AUTO_SCROLL -> Icons.Rounded.SwapVert
                                                    AppTab.RECORDER -> Icons.Rounded.FiberManualRecord
                                                    AppTab.MY_MACROS -> Icons.Rounded.Bookmark
                                                },
                                                contentDescription = tab.label
                                            )
                                        },
                                        label = { Text(tab.label) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = ShopeeOrange,
                                            selectedTextColor = ShopeeOrange,
                                            indicatorColor = ShopeeOrangeSoft,
                                            unselectedIconColor = TextSecondary,
                                            unselectedTextColor = TextSecondary
                                        )
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding)) {
                            when (selectedTab) {
                                AppTab.AUTO_SCROLL -> DashboardScreen(
                                    config = config,
                                    runtimeState = runtimeState,
                                    permissions = permissions,
                                    targetApps = targetApps,
                                    uiMessage = uiMessage,
                                    onClearMessage = viewModel::clearMessage,
                                    onToggleStartStop = {
                                        viewModel.toggleStartStop(
                                            onRequirePermissions = { showPermissions = true }
                                        )
                                    },
                                    onResetCounter = viewModel::resetScrollCounter,
                                    onSelectPreset = viewModel::selectIntervalPreset,
                                    onApplyCustom = viewModel::updateCustomInterval,
                                    onSelectDirection = viewModel::selectDirection,
                                    onSelectSpeed = viewModel::selectSpeed,
                                    onDurationChange = viewModel::updateDurationMs,
                                    onSelectTargetApp = viewModel::selectTargetApp,
                                    onOpenTargetApp = viewModel::openTargetApp,
                                    onOpenPermissions = { showPermissions = true }
                                )

                                AppTab.RECORDER -> RecorderScreen(
                                    config = config,
                                    recordingState = recordingState,
                                    onSelectCaptureMethod = viewModel::setCaptureMethod,
                                    onStartRecording = {
                                        viewModel.startRecording(
                                            onRequirePermissions = { showPermissions = true }
                                        )
                                    },
                                    onStopRecording = viewModel::stopRecording,
                                    onOpenTargetApp = viewModel::openTargetApp,
                                    onSaveMacro = { name, repeat ->
                                        viewModel.saveRecordedMacro(name, repeat)
                                        selectedTab = AppTab.MY_MACROS
                                    },
                                    onDiscardDraft = viewModel::discardRecordingDraft,
                                    onOpenPermissions = { showPermissions = true }
                                )

                                AppTab.MY_MACROS -> MyMacrosScreen(
                                    macros = savedMacros,
                                    playbackState = playbackState,
                                    onUseMacro = { macro ->
                                        viewModel.useMacro(
                                            macro = macro,
                                            onRequirePermissions = { showPermissions = true }
                                        )
                                    },
                                    onRenameMacro = viewModel::renameMacro,
                                    onUpdateRepeat = viewModel::updateMacroRepeat,
                                    onSaveEditedActions = viewModel::saveEditedActions,
                                    onDeleteMacro = viewModel::deleteMacro,
                                    onGoToRecorder = { selectedTab = AppTab.RECORDER },
                                    onOpenPermissions = { showPermissions = true }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
