package com.autoscroll.app.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import com.autoscroll.app.data.model.CustomTimeUnit
import com.autoscroll.app.data.model.IntervalPreset
import com.autoscroll.app.data.model.ScrollConfig
import com.autoscroll.app.data.model.ScrollDirection
import com.autoscroll.app.data.model.ScrollRuntimeState
import com.autoscroll.app.data.model.ScrollSpeed
import com.autoscroll.app.data.model.TargetApp
import com.autoscroll.app.data.repository.ScrollEngineStateHolder
import com.autoscroll.app.util.PermissionHelper
import com.autoscroll.app.util.PermissionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val config: StateFlow<ScrollConfig> = ScrollEngineStateHolder.config
    val runtimeState: StateFlow<ScrollRuntimeState> = ScrollEngineStateHolder.runtimeState

    private val _permissions = MutableStateFlow(PermissionStatus())
    val permissions: StateFlow<PermissionStatus> = _permissions.asStateFlow()

    private val _targetApps = MutableStateFlow<List<TargetApp>>(emptyList())
    val targetApps: StateFlow<List<TargetApp>> = _targetApps.asStateFlow()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    init {
        ScrollEngineStateHolder.init(application)
        refreshSystemState()
    }

    fun refreshSystemState() {
        val ctx = getApplication<Application>()
        _permissions.value = PermissionHelper.checkPermissions(ctx)
        _targetApps.value = PermissionHelper.getTargetAppCatalog(ctx)
    }

    fun toggleStartStop(onRequirePermissions: () -> Unit) {
        refreshSystemState()
        val currentPerms = _permissions.value
        val isRunning = runtimeState.value.isEnabledByUser

        if (isRunning) {
            ScrollEngineStateHolder.stopAutoScroll()
            return
        }

        if (!currentPerms.allGranted) {
            _uiMessage.value = "Aktifkan izin Aksesibilitas dan Floating Overlay terlebih dahulu."
            onRequirePermissions()
            return
        }

        ScrollEngineStateHolder.startAutoScroll()
    }

    fun pauseOrResume() {
        if (runtimeState.value.isPaused) {
            ScrollEngineStateHolder.resumeAutoScroll()
        } else {
            ScrollEngineStateHolder.pauseAutoScroll()
        }
    }

    fun resetScrollCounter() {
        ScrollEngineStateHolder.resetCounter()
    }

    fun selectIntervalPreset(preset: IntervalPreset) {
        ScrollEngineStateHolder.updateConfig(getApplication()) {
            it.copy(intervalPreset = preset)
        }
    }

    fun updateCustomInterval(value: Int, unit: CustomTimeUnit) {
        val clamped = value.coerceIn(1, 3600)
        ScrollEngineStateHolder.updateConfig(getApplication()) {
            it.copy(
                intervalPreset = IntervalPreset.CUSTOM,
                customIntervalValue = clamped,
                customTimeUnit = unit
            )
        }
    }

    fun selectDirection(direction: ScrollDirection) {
        ScrollEngineStateHolder.updateConfig(getApplication()) {
            it.copy(direction = direction)
        }
    }

    fun selectSpeed(speed: ScrollSpeed) {
        ScrollEngineStateHolder.updateConfig(getApplication()) {
            it.copy(speed = speed)
        }
    }

    fun updateDurationMs(durationMs: Long) {
        ScrollEngineStateHolder.updateConfig(getApplication()) {
            it.copy(durationMs = durationMs.coerceIn(200L, 1500L))
        }
    }

    fun selectTargetApp(app: TargetApp) {
        ScrollEngineStateHolder.updateConfig(getApplication()) {
            it.copy(
                targetPackageName = app.packageName,
                targetAppName = app.displayName
            )
        }
    }

    fun openTargetApp() {
        val ctx = getApplication<Application>()
        val targetPkg = config.value.targetPackageName
        val launchIntent = ctx.packageManager.getLaunchIntentForPackage(targetPkg)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { ctx.startActivity(launchIntent) }
        } else {
            _uiMessage.value = "${config.value.targetAppName} belum terpasang di perangkat ini."
        }
    }

    fun openAccessibilitySettings() {
        PermissionHelper.openAccessibilitySettings(getApplication())
    }

    fun openOverlaySettings() {
        PermissionHelper.openOverlaySettings(getApplication())
    }

    fun clearMessage() {
        _uiMessage.value = null
    }
}
