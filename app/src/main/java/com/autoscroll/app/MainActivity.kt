package com.autoscroll.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autoscroll.app.ui.screen.DashboardScreen
import com.autoscroll.app.ui.screen.PermissionsScreen
import com.autoscroll.app.ui.theme.AutoScrollTheme
import com.autoscroll.app.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AutoScrollTheme {
                val viewModel: MainViewModel = viewModel()
                val config by viewModel.config.collectAsState()
                val runtimeState by viewModel.runtimeState.collectAsState()
                val permissions by viewModel.permissions.collectAsState()
                val targetApps by viewModel.targetApps.collectAsState()
                val uiMessage by viewModel.uiMessage.collectAsState()

                var showPermissions by rememberSaveable { mutableStateOf(false) }

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
                    DashboardScreen(
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
                }
            }
        }
    }
}
