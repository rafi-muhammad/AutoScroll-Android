package com.autoscroll.app.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autoscroll.app.data.model.CustomTimeUnit
import com.autoscroll.app.data.model.FloatingControlMode
import com.autoscroll.app.data.model.IntervalPreset
import com.autoscroll.app.data.model.MacroAction
import com.autoscroll.app.data.model.MacroPlaybackState
import com.autoscroll.app.data.model.MacroRecord
import com.autoscroll.app.data.model.RecordingCaptureMethod
import com.autoscroll.app.data.model.RecordingState
import com.autoscroll.app.data.model.ScrollConfig
import com.autoscroll.app.data.model.ScrollDirection
import com.autoscroll.app.data.model.ScrollRuntimeState
import com.autoscroll.app.data.model.ScrollSpeed
import com.autoscroll.app.data.model.TargetApp
import com.autoscroll.app.data.repository.MacroRepository
import com.autoscroll.app.data.repository.ScrollEngineStateHolder
import com.autoscroll.app.util.PermissionHelper
import com.autoscroll.app.util.PermissionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val macroRepository = MacroRepository(application)

    val config: StateFlow<ScrollConfig> = ScrollEngineStateHolder.config
    val runtimeState: StateFlow<ScrollRuntimeState> = ScrollEngineStateHolder.runtimeState
    val floatingMode: StateFlow<FloatingControlMode> = ScrollEngineStateHolder.floatingMode
    val recordingState: StateFlow<RecordingState> = ScrollEngineStateHolder.recordingState
    val playbackState: StateFlow<MacroPlaybackState> = ScrollEngineStateHolder.playbackState

    val savedMacros: StateFlow<List<MacroRecord>> = macroRepository.allMacros
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

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

    // --- Auto Scroll ---

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

    // --- Recording ---

    fun setCaptureMethod(method: RecordingCaptureMethod) {
        ScrollEngineStateHolder.setRecordingCaptureMethod(method)
    }

    fun startRecording(onRequirePermissions: () -> Unit) {
        refreshSystemState()
        if (!_permissions.value.allGranted) {
            _uiMessage.value = "Izin Aksesibilitas dan Overlay diperlukan untuk merekam gesture."
            onRequirePermissions()
            return
        }
        ScrollEngineStateHolder.startRecordingSession()
        _uiMessage.value = "Mode REC aktif. Buka ${config.value.targetAppName} lalu lakukan gesture."
    }

    fun stopRecording() {
        ScrollEngineStateHolder.stopRecordingSession()
    }

    fun saveRecordedMacro(name: String, repeatCount: Int) {
        val actions = recordingState.value.capturedActions
        if (actions.isEmpty()) {
            _uiMessage.value = "Belum ada gesture yang terekam."
            return
        }
        val cfg = config.value
        viewModelScope.launch {
            val savedId = macroRepository.saveNewMacro(
                name = name,
                targetPackageName = cfg.targetPackageName,
                targetAppName = cfg.targetAppName,
                actions = actions,
                repeatCount = repeatCount
            )
            ScrollEngineStateHolder.discardRecordingDraft()
            val saved = macroRepository.getMacroById(savedId)
            if (saved != null) {
                ScrollEngineStateHolder.selectActiveMacro(getApplication(), saved)
            }
            _uiMessage.value = "Macro \"$name\" disimpan dan siap dijalankan."
        }
    }

    fun discardRecordingDraft() {
        ScrollEngineStateHolder.discardRecordingDraft()
    }

    // --- My Macros & Playback ---

    fun useMacro(macro: MacroRecord, onRequirePermissions: () -> Unit) {
        refreshSystemState()
        if (!_permissions.value.allGranted) {
            _uiMessage.value = "Aktifkan izin Aksesibilitas dan Overlay untuk menampilkan Floating Control."
            onRequirePermissions()
            return
        }
        ScrollEngineStateHolder.selectActiveMacro(getApplication(), macro)
        _uiMessage.value = "\"${macro.name}\" aktif di Floating Control. Buka ${macro.targetAppName} dan tekan Play."
    }

    fun renameMacro(macroId: Long, newName: String) {
        viewModelScope.launch {
            macroRepository.renameMacro(macroId, newName)
            macroRepository.getMacroById(macroId)?.let {
                ScrollEngineStateHolder.syncUpdatedMacroIfActive(it)
            }
            _uiMessage.value = "Nama macro diperbarui."
        }
    }

    fun updateMacroRepeat(macroId: Long, repeatCount: Int) {
        viewModelScope.launch {
            macroRepository.updateRepeatCount(macroId, repeatCount)
            macroRepository.getMacroById(macroId)?.let {
                ScrollEngineStateHolder.syncUpdatedMacroIfActive(it)
            }
        }
    }

    fun saveEditedActions(macroId: Long, actions: List<MacroAction>) {
        viewModelScope.launch {
            macroRepository.updateMacroActions(macroId, actions)
            macroRepository.getMacroById(macroId)?.let {
                ScrollEngineStateHolder.syncUpdatedMacroIfActive(it)
            }
            _uiMessage.value = "Urutan gerakan macro diperbarui."
        }
    }

    fun deleteMacro(macroId: Long) {
        viewModelScope.launch {
            macroRepository.deleteMacro(macroId)
            ScrollEngineStateHolder.clearActiveMacroIfDeleted(macroId)
            _uiMessage.value = "Macro dihapus."
        }
    }

    // --- Common ---

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
