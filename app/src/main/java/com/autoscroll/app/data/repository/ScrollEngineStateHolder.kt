package com.autoscroll.app.data.repository

import android.content.Context
import com.autoscroll.app.data.model.CustomTimeUnit
import com.autoscroll.app.data.model.FloatingControlMode
import com.autoscroll.app.data.model.IntervalPreset
import com.autoscroll.app.data.model.MacroAction
import com.autoscroll.app.data.model.MacroActionType
import com.autoscroll.app.data.model.MacroPlaybackState
import com.autoscroll.app.data.model.MacroRecord
import com.autoscroll.app.data.model.PlaybackStatus
import com.autoscroll.app.data.model.RecordingCaptureMethod
import com.autoscroll.app.data.model.RecordingState
import com.autoscroll.app.data.model.ScrollConfig
import com.autoscroll.app.data.model.ScrollDirection
import com.autoscroll.app.data.model.ScrollRuntimeState
import com.autoscroll.app.data.model.ScrollSpeed
import com.autoscroll.app.data.model.TargetApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Centralized state hub coordinating:
 * - Auto Scroll (existing behavior preserved)
 * - Gesture Recording (Capture-and-Forward + Accessibility Event fallback)
 * - Macro Playback (Room-persisted active macro)
 * - Single Dynamic Floating Control Mode
 */
object ScrollEngineStateHolder {

    private const val PREFS_NAME = "auto_scroll_prefs"
    private const val KEY_PRESET = "interval_preset"
    private const val KEY_CUSTOM_VALUE = "custom_value"
    private const val KEY_CUSTOM_UNIT = "custom_unit"
    private const val KEY_DIRECTION = "scroll_direction"
    private const val KEY_SPEED = "scroll_speed"
    private const val KEY_DURATION_MS = "scroll_duration_ms"
    private const val KEY_TARGET_PKG = "target_package"
    private const val KEY_TARGET_NAME = "target_name"

    private val holderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _config = MutableStateFlow(ScrollConfig())
    val config: StateFlow<ScrollConfig> = _config.asStateFlow()

    private val _runtimeState = MutableStateFlow(ScrollRuntimeState())
    val runtimeState: StateFlow<ScrollRuntimeState> = _runtimeState.asStateFlow()

    private val _floatingMode = MutableStateFlow(FloatingControlMode.AUTO_SCROLL)
    val floatingMode: StateFlow<FloatingControlMode> = _floatingMode.asStateFlow()

    private val _isFloatingVisible = MutableStateFlow(false)
    val isFloatingVisible: StateFlow<Boolean> = _isFloatingVisible.asStateFlow()

    private val _recordingState = MutableStateFlow(RecordingState())
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _playbackState = MutableStateFlow(MacroPlaybackState())
    val playbackState: StateFlow<MacroPlaybackState> = _playbackState.asStateFlow()

    @Volatile
    private var initialized = false
    private var macroRepository: MacroRepository? = null
    private var lastActionTimestampMs: Long = 0L

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val appCtx = context.applicationContext
            val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val preset = runCatching {
                IntervalPreset.valueOf(prefs.getString(KEY_PRESET, IntervalPreset.SEC_30.name) ?: IntervalPreset.SEC_30.name)
            }.getOrDefault(IntervalPreset.SEC_30)

            val customValue = prefs.getInt(KEY_CUSTOM_VALUE, 45).coerceIn(1, 3600)
            val customUnit = runCatching {
                CustomTimeUnit.valueOf(prefs.getString(KEY_CUSTOM_UNIT, CustomTimeUnit.SECONDS.name) ?: CustomTimeUnit.SECONDS.name)
            }.getOrDefault(CustomTimeUnit.SECONDS)

            val direction = runCatching {
                ScrollDirection.valueOf(prefs.getString(KEY_DIRECTION, ScrollDirection.DOWN.name) ?: ScrollDirection.DOWN.name)
            }.getOrDefault(ScrollDirection.DOWN)

            val speed = runCatching {
                ScrollSpeed.valueOf(prefs.getString(KEY_SPEED, ScrollSpeed.MEDIUM.name) ?: ScrollSpeed.MEDIUM.name)
            }.getOrDefault(ScrollSpeed.MEDIUM)

            val durationMs = prefs.getLong(KEY_DURATION_MS, 550L).coerceIn(200L, 1500L)
            val targetPkg = prefs.getString(KEY_TARGET_PKG, TargetApp.SHOPEE_ID.packageName)
                ?: TargetApp.SHOPEE_ID.packageName
            val targetName = prefs.getString(KEY_TARGET_NAME, TargetApp.SHOPEE_ID.displayName)
                ?: TargetApp.SHOPEE_ID.displayName

            val loaded = ScrollConfig(
                intervalPreset = preset,
                customIntervalValue = customValue,
                customTimeUnit = customUnit,
                direction = direction,
                speed = speed,
                durationMs = durationMs,
                targetPackageName = targetPkg,
                targetAppName = targetName
            )
            _config.value = loaded
            _runtimeState.update { it.copy(remainingSeconds = loaded.effectiveIntervalSeconds()) }

            val repo = MacroRepository(appCtx)
            macroRepository = repo
            val persistedMacroId = repo.getPersistedActiveMacroId()
            if (persistedMacroId != MacroRepository.NO_ACTIVE_MACRO) {
                holderScope.launch {
                    val macro = repo.getMacroById(persistedMacroId)
                    if (macro != null) {
                        _playbackState.update { it.copy(activeMacro = macro, status = PlaybackStatus.IDLE) }
                    }
                }
            }
            initialized = true
        }
    }

    fun updateConfig(context: Context, transform: (ScrollConfig) -> ScrollConfig) {
        init(context)
        val updated = transform(_config.value)
        _config.value = updated
        persistConfig(context, updated)
        _runtimeState.update { current ->
            val maxSec = updated.effectiveIntervalSeconds()
            current.copy(
                remainingSeconds = if (current.isEnabledByUser) {
                    current.remainingSeconds.coerceAtMost(maxSec)
                } else {
                    maxSec
                }
            )
        }
    }

    private fun persistConfig(context: Context, cfg: ScrollConfig) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_PRESET, cfg.intervalPreset.name)
            .putInt(KEY_CUSTOM_VALUE, cfg.customIntervalValue)
            .putString(KEY_CUSTOM_UNIT, cfg.customTimeUnit.name)
            .putString(KEY_DIRECTION, cfg.direction.name)
            .putString(KEY_SPEED, cfg.speed.name)
            .putLong(KEY_DURATION_MS, cfg.durationMs)
            .putString(KEY_TARGET_PKG, cfg.targetPackageName)
            .putString(KEY_TARGET_NAME, cfg.targetAppName)
            .apply()
    }

    fun setServiceConnected(connected: Boolean) {
        _runtimeState.update {
            it.copy(
                isServiceConnected = connected,
                isEnabledByUser = if (connected) it.isEnabledByUser else false,
                isPaused = if (connected) it.isPaused else false
            )
        }
        if (!connected) {
            _recordingState.update { it.copy(isRecording = false) }
            _playbackState.update {
                if (it.status == PlaybackStatus.PLAYING || it.status == PlaybackStatus.PAUSED) {
                    it.copy(status = PlaybackStatus.IDLE, currentActionIndex = 0)
                } else {
                    it
                }
            }
            _isFloatingVisible.value = false
        }
    }

    // --- Mode A: Auto Scroll ---

    fun selectAutoScrollMode(showFloating: Boolean = false) {
        stopRecordingInternal(keepDraft = false)
        stopMacroPlaybackInternal()
        _floatingMode.value = FloatingControlMode.AUTO_SCROLL
        if (showFloating) {
            _isFloatingVisible.value = true
        }
    }

    fun startAutoScroll() {
        stopRecordingInternal(keepDraft = false)
        stopMacroPlaybackInternal()
        _floatingMode.value = FloatingControlMode.AUTO_SCROLL
        _isFloatingVisible.value = true
        val intervalSec = _config.value.effectiveIntervalSeconds()
        _runtimeState.update { state ->
            state.copy(
                isEnabledByUser = true,
                isPaused = false,
                remainingSeconds = intervalSec,
                lastErrorMessage = null
            )
        }
    }

    fun stopAutoScroll() {
        val intervalSec = _config.value.effectiveIntervalSeconds()
        _runtimeState.update { state ->
            state.copy(
                isEnabledByUser = false,
                isPaused = false,
                remainingSeconds = intervalSec
            )
        }
        if (_floatingMode.value == FloatingControlMode.AUTO_SCROLL) {
            _isFloatingVisible.value = false
        }
    }

    fun pauseAutoScroll() {
        _runtimeState.update { state ->
            if (!state.isEnabledByUser) state else state.copy(isPaused = true)
        }
    }

    fun resumeAutoScroll() {
        _runtimeState.update { state ->
            if (!state.isEnabledByUser) state else state.copy(isPaused = false)
        }
    }

    fun resetCounter() {
        _runtimeState.update { it.copy(scrollCount = 0) }
    }

    fun updateForegroundPackage(packageName: String) {
        val target = _config.value.targetPackageName
        _runtimeState.update { state ->
            state.copy(
                currentForegroundPackage = packageName,
                isTargetAppInForeground = isPackageMatchingTarget(packageName, target)
            )
        }
    }

    fun tickCountdown(nextRemainingSeconds: Long) {
        _runtimeState.update { it.copy(remainingSeconds = nextRemainingSeconds) }
    }

    fun onGestureSucceeded() {
        val intervalSec = _config.value.effectiveIntervalSeconds()
        _runtimeState.update { state ->
            state.copy(
                scrollCount = state.scrollCount + 1,
                remainingSeconds = intervalSec,
                lastErrorMessage = null
            )
        }
    }

    fun onGestureFailed(reason: String) {
        val intervalSec = _config.value.effectiveIntervalSeconds()
        _runtimeState.update { state ->
            state.copy(
                remainingSeconds = intervalSec,
                lastErrorMessage = reason
            )
        }
    }

    fun isPackageMatchingTarget(foregroundPkg: String, targetPkg: String): Boolean {
        if (foregroundPkg.isBlank() || targetPkg.isBlank()) return false
        return foregroundPkg.equals(targetPkg, ignoreCase = true)
    }

    // --- Mode B: Gesture Recording ---

    fun setRecordingCaptureMethod(method: RecordingCaptureMethod) {
        _recordingState.update { it.copy(captureMethod = method) }
    }

    fun startRecordingSession() {
        stopAutoScrollInternal()
        stopMacroPlaybackInternal()
        _floatingMode.value = FloatingControlMode.RECORDING
        _isFloatingVisible.value = true
        lastActionTimestampMs = System.currentTimeMillis()
        _recordingState.update {
            it.copy(
                isRecording = true,
                elapsedSeconds = 0L,
                capturedActions = emptyList(),
                hasPendingDraft = false,
                lastCaptureNotice = null
            )
        }
    }

    fun tickRecordingElapsed() {
        _recordingState.update { state ->
            if (!state.isRecording) state else state.copy(elapsedSeconds = state.elapsedSeconds + 1L)
        }
    }

    fun appendRecordedAction(action: MacroAction) {
        val now = System.currentTimeMillis()
        val gapMs = if (lastActionTimestampMs > 0L) (now - lastActionTimestampMs).coerceIn(100L, 30_000L) else 600L
        lastActionTimestampMs = now

        _recordingState.update { state ->
            if (!state.isRecording) return@update state
            val updatedList = state.capturedActions.toMutableList()
            if (updatedList.isNotEmpty()) {
                val lastIdx = updatedList.lastIndex
                // Jika double tap menggantikan tap sebelumnya dalam < 320ms, gabungkan menjadi 1 DOUBLE_TAP
                if (action.type == MacroActionType.DOUBLE_TAP && updatedList[lastIdx].type == MacroActionType.TAP) {
                    updatedList[lastIdx] = action.copy(orderIndex = lastIdx)
                    return@update state.copy(capturedActions = updatedList, lastCaptureNotice = null)
                }
                updatedList[lastIdx] = updatedList[lastIdx].copy(delayAfterMs = gapMs)
            }
            updatedList.add(action.copy(orderIndex = updatedList.size))
            state.copy(capturedActions = updatedList, lastCaptureNotice = null)
        }
    }

    fun reportRecordingNotice(message: String) {
        _recordingState.update { it.copy(lastCaptureNotice = message) }
    }

    fun stopRecordingSession() {
        val hasActions = _recordingState.value.capturedActions.isNotEmpty()
        _recordingState.update { state ->
            state.copy(
                isRecording = false,
                hasPendingDraft = hasActions
            )
        }
        _isFloatingVisible.value = false
        _floatingMode.value = FloatingControlMode.AUTO_SCROLL
    }

    fun discardRecordingDraft() {
        _recordingState.update {
            it.copy(
                isRecording = false,
                elapsedSeconds = 0L,
                capturedActions = emptyList(),
                hasPendingDraft = false,
                lastCaptureNotice = null
            )
        }
    }

    private fun stopRecordingInternal(keepDraft: Boolean) {
        _recordingState.update { state ->
            state.copy(
                isRecording = false,
                hasPendingDraft = if (keepDraft) state.hasPendingDraft else false
            )
        }
    }

    private fun stopAutoScrollInternal() {
        val intervalSec = _config.value.effectiveIntervalSeconds()
        _runtimeState.update {
            it.copy(isEnabledByUser = false, isPaused = false, remainingSeconds = intervalSec)
        }
    }

    // --- Mode C: Macro Playback ---

    fun selectActiveMacro(context: Context, macro: MacroRecord) {
        init(context)
        stopAutoScrollInternal()
        stopRecordingInternal(keepDraft = false)
        macroRepository?.setPersistedActiveMacroId(macro.id)
        _floatingMode.value = FloatingControlMode.MACRO_PLAYBACK
        _isFloatingVisible.value = true
        _playbackState.value = MacroPlaybackState(
            activeMacro = macro,
            status = PlaybackStatus.IDLE,
            currentActionIndex = 0,
            currentRepeat = 1,
            lastError = null
        )
    }

    fun syncUpdatedMacroIfActive(macro: MacroRecord) {
        val currentActive = _playbackState.value.activeMacro ?: return
        if (currentActive.id == macro.id) {
            _playbackState.update { it.copy(activeMacro = macro) }
        }
    }

    fun clearActiveMacroIfDeleted(macroId: Long) {
        if (_playbackState.value.activeMacro?.id == macroId) {
            _playbackState.value = MacroPlaybackState()
            if (_floatingMode.value == FloatingControlMode.MACRO_PLAYBACK) {
                _isFloatingVisible.value = false
                _floatingMode.value = FloatingControlMode.AUTO_SCROLL
            }
        }
    }

    fun startMacroPlayback() {
        val active = _playbackState.value.activeMacro ?: return
        if (active.actions.isEmpty()) {
            _playbackState.update {
                it.copy(status = PlaybackStatus.ERROR, lastError = "Macro belum memiliki gerakan")
            }
            return
        }
        stopAutoScrollInternal()
        stopRecordingInternal(keepDraft = false)
        _floatingMode.value = FloatingControlMode.MACRO_PLAYBACK
        _isFloatingVisible.value = true
        _playbackState.update {
            it.copy(
                status = PlaybackStatus.PLAYING,
                currentActionIndex = 0,
                currentRepeat = 1,
                lastError = null
            )
        }
    }

    fun pauseMacroPlayback() {
        _playbackState.update { state ->
            if (state.status == PlaybackStatus.PLAYING) state.copy(status = PlaybackStatus.PAUSED) else state
        }
    }

    fun resumeMacroPlayback() {
        _playbackState.update { state ->
            if (state.status == PlaybackStatus.PAUSED) state.copy(status = PlaybackStatus.PLAYING) else state
        }
    }

    fun stopMacroPlayback() {
        _playbackState.update { state ->
            state.copy(
                status = PlaybackStatus.IDLE,
                currentActionIndex = 0,
                currentRepeat = 1
            )
        }
    }

    fun hideFloatingPlaybackControl() {
        stopMacroPlaybackInternal()
        _isFloatingVisible.value = false
    }

    fun updatePlaybackProgress(actionIndex: Int, repeatIteration: Int) {
        _playbackState.update { state ->
            state.copy(
                currentActionIndex = actionIndex,
                currentRepeat = repeatIteration
            )
        }
    }

    fun markPlaybackCompleted() {
        _playbackState.update { state ->
            state.copy(
                status = PlaybackStatus.COMPLETED,
                currentActionIndex = state.totalActions
            )
        }
    }

    fun markPlaybackError(message: String) {
        _playbackState.update { state ->
            state.copy(
                status = PlaybackStatus.ERROR,
                lastError = message
            )
        }
    }

    private fun stopMacroPlaybackInternal() {
        _playbackState.update { state ->
            state.copy(
                status = PlaybackStatus.IDLE,
                currentActionIndex = 0,
                currentRepeat = 1
            )
        }
    }
}
