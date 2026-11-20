package com.autoscroll.app.data.repository

import android.content.Context
import com.autoscroll.app.data.model.BuilderOverlayStep
import com.autoscroll.app.data.model.CoordinatePickMode
import com.autoscroll.app.data.model.CustomTimeUnit
import com.autoscroll.app.data.model.FloatingControlMode
import com.autoscroll.app.data.model.IntervalPreset
import com.autoscroll.app.data.model.MacroAction
import com.autoscroll.app.data.model.MacroActionType
import com.autoscroll.app.data.model.MacroBuilderState
import com.autoscroll.app.data.model.MacroPlaybackState
import com.autoscroll.app.data.model.MacroRecord
import com.autoscroll.app.data.model.PlaybackStatus
import com.autoscroll.app.data.model.ScrollConfig
import com.autoscroll.app.data.model.ScrollDirection
import com.autoscroll.app.data.model.ScrollRuntimeState
import com.autoscroll.app.data.model.ScrollSpeed
import com.autoscroll.app.data.model.TargetApp
import com.autoscroll.app.util.CoordinateUtils
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
 * - Auto Scroll (preserved)
 * - Floating Coordinate-Based Macro Builder (Picker, Action Settings, Action List, Stop & Save)
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

    private val _builderState = MutableStateFlow(MacroBuilderState())
    val builderState: StateFlow<MacroBuilderState> = _builderState.asStateFlow()

    private val _playbackState = MutableStateFlow(MacroPlaybackState())
    val playbackState: StateFlow<MacroPlaybackState> = _playbackState.asStateFlow()

    @Volatile
    private var initialized = false
    private var macroRepository: MacroRepository? = null

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
            _builderState.update { it.copy(isActive = false, step = BuilderOverlayStep.TOOLBAR) }
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

    fun startAutoScroll() {
        stopBuilderInternal(keepPendingSave = false)
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

    // --- Mode B: Coordinate-Based Macro Builder ---

    fun startMacroBuilderSession() {
        stopAutoScrollInternal()
        stopMacroPlaybackInternal()
        _floatingMode.value = FloatingControlMode.MACRO_BUILDER
        _isFloatingVisible.value = true
        _builderState.value = MacroBuilderState(
            isActive = true,
            step = BuilderOverlayStep.TOOLBAR,
            pickMode = CoordinatePickMode.SINGLE_POINT,
            editingActionIndex = null,
            draftAction = MacroAction(),
            actions = emptyList(),
            hasPendingSaveDraft = false
        )
    }

    fun beginAddNewActionCoordinatePicker() {
        _builderState.update { current ->
            current.copy(
                step = BuilderOverlayStep.PICKING_COORDINATE,
                pickMode = CoordinatePickMode.SINGLE_POINT,
                editingActionIndex = null,
                draftAction = MacroAction(
                    orderIndex = current.actions.size,
                    type = MacroActionType.TAP,
                    startXPercent = 0.50f,
                    startYPercent = 0.55f,
                    endXPercent = 0.50f,
                    endYPercent = 0.25f,
                    durationMs = CoordinateUtils.defaultDurationFor(MacroActionType.TAP),
                    delayAfterMs = 800L
                )
            )
        }
    }

    fun beginRepickCoordinate(pickMode: CoordinatePickMode) {
        _builderState.update { current ->
            current.copy(
                step = BuilderOverlayStep.PICKING_COORDINATE,
                pickMode = pickMode
            )
        }
    }

    fun confirmPickedCoordinates(
        startXPercent: Float,
        startYPercent: Float,
        endXPercent: Float?,
        endYPercent: Float?,
        forceSwipeType: Boolean
    ) {
        _builderState.update { current ->
            val updatedDraft = CoordinateUtils.applyPickedCoordinates(
                action = current.draftAction,
                pickMode = current.pickMode,
                startXPercent = startXPercent,
                startYPercent = startYPercent,
                endXPercent = endXPercent,
                endYPercent = endYPercent,
                forceSwipeType = forceSwipeType
            )
            val finalizedDraft = updatedDraft.copy(
                note = CoordinateUtils.buildSummaryNote(updatedDraft)
            )
            current.copy(
                step = BuilderOverlayStep.CONFIGURING_ACTION,
                draftAction = finalizedDraft
            )
        }
    }

    fun cancelCoordinatePicker() {
        _builderState.update { current ->
            val fallbackStep = if (current.editingActionIndex != null) {
                BuilderOverlayStep.CONFIGURING_ACTION
            } else {
                BuilderOverlayStep.TOOLBAR
            }
            current.copy(step = fallbackStep)
        }
    }

    fun updateDraftActionType(type: MacroActionType) {
        _builderState.update { current ->
            val updated = current.draftAction.copy(
                type = type,
                durationMs = CoordinateUtils.defaultDurationFor(type)
            )
            current.copy(
                draftAction = updated.copy(note = CoordinateUtils.buildSummaryNote(updated))
            )
        }
    }

    fun adjustDraftDuration(deltaMs: Long? = null, exactMs: Long? = null) {
        _builderState.update { current ->
            val base = exactMs ?: (current.draftAction.durationMs + (deltaMs ?: 0L))
            val clamped = if (current.draftAction.type == MacroActionType.WAIT) 0L else base.coerceIn(40L, 5000L)
            current.copy(
                draftAction = current.draftAction.copy(durationMs = clamped)
            )
        }
    }

    fun adjustDraftDelay(deltaMs: Long? = null, exactMs: Long? = null) {
        _builderState.update { current ->
            val base = exactMs ?: (current.draftAction.delayAfterMs + (deltaMs ?: 0L))
            val clamped = base.coerceIn(0L, 60_000L)
            val updated = current.draftAction.copy(delayAfterMs = clamped)
            current.copy(
                draftAction = updated.copy(note = CoordinateUtils.buildSummaryNote(updated))
            )
        }
    }

    fun saveDraftActionToBuilderList() {
        _builderState.update { current ->
            val list = current.actions.toMutableList()
            val editIdx = current.editingActionIndex
            val ready = current.draftAction.copy(
                note = CoordinateUtils.buildSummaryNote(current.draftAction)
            )
            if (editIdx != null && editIdx in list.indices) {
                list[editIdx] = ready.copy(orderIndex = editIdx)
            } else {
                list.add(ready.copy(orderIndex = list.size))
            }
            current.copy(
                step = BuilderOverlayStep.TOOLBAR,
                editingActionIndex = null,
                actions = list.mapIndexed { idx, act -> act.copy(orderIndex = idx) }
            )
        }
    }

    fun cancelActionConfiguration() {
        _builderState.update { current ->
            current.copy(
                step = if (current.editingActionIndex != null) BuilderOverlayStep.ACTION_LIST else BuilderOverlayStep.TOOLBAR,
                editingActionIndex = null
            )
        }
    }

    fun openBuilderActionList() {
        _builderState.update { it.copy(step = BuilderOverlayStep.ACTION_LIST) }
    }

    fun closeBuilderActionList() {
        _builderState.update { it.copy(step = BuilderOverlayStep.TOOLBAR) }
    }

    fun editBuilderAction(index: Int) {
        _builderState.update { current ->
            val target = current.actions.getOrNull(index) ?: return@update current
            current.copy(
                step = BuilderOverlayStep.CONFIGURING_ACTION,
                editingActionIndex = index,
                draftAction = target
            )
        }
    }

    fun moveBuilderAction(fromIndex: Int, toIndex: Int) {
        _builderState.update { current ->
            current.copy(
                actions = CoordinateUtils.reorderAction(current.actions, fromIndex, toIndex)
            )
        }
    }

    fun deleteBuilderAction(index: Int) {
        _builderState.update { current ->
            current.copy(
                actions = CoordinateUtils.deleteActionAt(current.actions, index)
            )
        }
    }

    fun stopAndRequestSaveMacro() {
        val hasActions = _builderState.value.actions.isNotEmpty()
        _builderState.update { current ->
            current.copy(
                isActive = false,
                step = BuilderOverlayStep.TOOLBAR,
                editingActionIndex = null,
                hasPendingSaveDraft = hasActions
            )
        }
        _isFloatingVisible.value = false
        _floatingMode.value = FloatingControlMode.AUTO_SCROLL
    }

    fun updatePendingSaveDraftActions(actions: List<MacroAction>) {
        _builderState.update { current ->
            current.copy(
                actions = actions.mapIndexed { idx, a ->
                    a.copy(orderIndex = idx, note = CoordinateUtils.buildSummaryNote(a))
                }
            )
        }
    }

    fun discardBuilderDraft() {
        _builderState.value = MacroBuilderState()
    }

    private fun stopBuilderInternal(keepPendingSave: Boolean) {
        _builderState.update { current ->
            current.copy(
                isActive = false,
                step = BuilderOverlayStep.TOOLBAR,
                editingActionIndex = null,
                hasPendingSaveDraft = if (keepPendingSave) current.hasPendingSaveDraft else false
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
        stopBuilderInternal(keepPendingSave = false)
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
        stopBuilderInternal(keepPendingSave = false)
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
