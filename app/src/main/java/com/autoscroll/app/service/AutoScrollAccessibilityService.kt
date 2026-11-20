package com.autoscroll.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.autoscroll.app.MainActivity
import com.autoscroll.app.data.model.BuilderOverlayStep
import com.autoscroll.app.data.model.FloatingControlMode
import com.autoscroll.app.data.model.MacroAction
import com.autoscroll.app.data.model.MacroActionType
import com.autoscroll.app.data.model.MacroBuilderState
import com.autoscroll.app.data.model.MacroPlaybackState
import com.autoscroll.app.data.model.PlaybackStatus
import com.autoscroll.app.data.model.ScrollConfig
import com.autoscroll.app.data.model.ScrollRuntimeState
import com.autoscroll.app.data.repository.ScrollEngineStateHolder
import com.autoscroll.app.util.CoordinateUtils
import com.autoscroll.app.util.GestureCalculator
import com.autoscroll.app.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AutoScrollAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var autoScrollJob: Job? = null
    private var macroPlaybackJob: Job? = null
    private var stateObserverJob: Job? = null

    private var floatingOverlay: FloatingControlOverlay? = null
    private var coordinatePickerOverlay: CoordinatePickerOverlay? = null

    private val ignoredSystemPackages = setOf(
        "com.android.systemui",
        "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        ScrollEngineStateHolder.init(applicationContext)
        ScrollEngineStateHolder.setServiceConnected(true)

        coordinatePickerOverlay = CoordinatePickerOverlay(
            context = this,
            onConfirmLocation = { sxPct, syPct, exPct, eyPct, forceSwipe ->
                ScrollEngineStateHolder.confirmPickedCoordinates(
                    startXPercent = sxPct,
                    startYPercent = syPct,
                    endXPercent = exPct,
                    endYPercent = eyPct,
                    forceSwipeType = forceSwipe
                )
            },
            onCancel = {
                ScrollEngineStateHolder.cancelCoordinatePicker()
            }
        )

        floatingOverlay = FloatingControlOverlay(
            context = this,
            onAutoScrollStart = { ScrollEngineStateHolder.startAutoScroll() },
            onAutoScrollPauseResume = {
                if (ScrollEngineStateHolder.runtimeState.value.isPaused) {
                    ScrollEngineStateHolder.resumeAutoScroll()
                } else {
                    ScrollEngineStateHolder.pauseAutoScroll()
                }
            },
            onAutoScrollStop = { ScrollEngineStateHolder.stopAutoScroll() },
            onBuilderAddAction = { ScrollEngineStateHolder.beginAddNewActionCoordinatePicker() },
            onBuilderRepickCoordinate = { mode -> ScrollEngineStateHolder.beginRepickCoordinate(mode) },
            onBuilderSelectActionType = { type -> ScrollEngineStateHolder.updateDraftActionType(type) },
            onBuilderAdjustDuration = { delta, exact -> ScrollEngineStateHolder.adjustDraftDuration(delta, exact) },
            onBuilderAdjustDelay = { delta, exact -> ScrollEngineStateHolder.adjustDraftDelay(delta, exact) },
            onBuilderSaveAction = { ScrollEngineStateHolder.saveDraftActionToBuilderList() },
            onBuilderCancelConfig = { ScrollEngineStateHolder.cancelActionConfiguration() },
            onBuilderOpenActionList = { ScrollEngineStateHolder.openBuilderActionList() },
            onBuilderCloseActionList = { ScrollEngineStateHolder.closeBuilderActionList() },
            onBuilderEditAction = { index -> ScrollEngineStateHolder.editBuilderAction(index) },
            onBuilderMoveAction = { from, to -> ScrollEngineStateHolder.moveBuilderAction(from, to) },
            onBuilderDeleteAction = { index -> ScrollEngineStateHolder.deleteBuilderAction(index) },
            onBuilderStopAndSave = {
                ScrollEngineStateHolder.stopAndRequestSaveMacro()
                bringAppToFrontForSaveMacro()
            },
            onPlaybackPlay = { ScrollEngineStateHolder.startMacroPlayback() },
            onPlaybackPauseResume = {
                if (ScrollEngineStateHolder.playbackState.value.status == PlaybackStatus.PAUSED) {
                    ScrollEngineStateHolder.resumeMacroPlayback()
                } else {
                    ScrollEngineStateHolder.pauseMacroPlayback()
                }
            },
            onPlaybackStop = { ScrollEngineStateHolder.stopMacroPlayback() },
            onCloseOverlay = {
                when (ScrollEngineStateHolder.floatingMode.value) {
                    FloatingControlMode.AUTO_SCROLL -> ScrollEngineStateHolder.stopAutoScroll()
                    FloatingControlMode.MACRO_BUILDER -> ScrollEngineStateHolder.stopAndRequestSaveMacro()
                    FloatingControlMode.MACRO_PLAYBACK -> ScrollEngineStateHolder.hideFloatingPlaybackControl()
                }
            }
        )

        observeCentralState()
        refreshForegroundPackage()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                val candidate = resolveActiveAppPackage(event)
                if (!candidate.isNullOrBlank()) {
                    ScrollEngineStateHolder.updateForegroundPackage(candidate)
                }
            }
        }
    }

    override fun onInterrupt() {
        autoScrollJob?.cancel()
        macroPlaybackJob?.cancel()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        cleanupService()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanupService()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun cleanupService() {
        autoScrollJob?.cancel()
        macroPlaybackJob?.cancel()
        stateObserverJob?.cancel()
        coordinatePickerOverlay?.hide()
        coordinatePickerOverlay = null
        floatingOverlay?.hide()
        floatingOverlay = null
        ScrollEngineStateHolder.setServiceConnected(false)
    }

    private fun observeCentralState() {
        stateObserverJob?.cancel()
        stateObserverJob = serviceScope.launch {
            combine(
                ScrollEngineStateHolder.floatingMode,
                ScrollEngineStateHolder.isFloatingVisible,
                ScrollEngineStateHolder.runtimeState,
                ScrollEngineStateHolder.builderState,
                ScrollEngineStateHolder.playbackState
            ) { mode, isVisible, autoState, builderState, playState ->
                CombinedServiceSnapshot(mode, isVisible, autoState, builderState, playState)
            }.collect { snap ->
                val canOverlay = PermissionHelper.canDrawOverlays(applicationContext)

                // CoordinatePickerOverlay HANYA aktif ketika pengguna sedang memilih titik koordinat.
                // Di luar mode tersebut, overlay pemilih dihapus total agar tidak menghalangi aplikasi target.
                val shouldShowPicker = canOverlay &&
                    snap.mode == FloatingControlMode.MACRO_BUILDER &&
                    snap.builder.isActive &&
                    snap.builder.step == BuilderOverlayStep.PICKING_COORDINATE

                if (shouldShowPicker) {
                    floatingOverlay?.hide()
                    coordinatePickerOverlay?.show(
                        pickMode = snap.builder.pickMode,
                        initialStartXPercent = snap.builder.draftAction.startXPercent,
                        initialStartYPercent = snap.builder.draftAction.startYPercent,
                        initialEndXPercent = snap.builder.draftAction.endXPercent,
                        initialEndYPercent = snap.builder.draftAction.endYPercent
                    )
                } else {
                    coordinatePickerOverlay?.hide()
                }

                val shouldShowFloating = canOverlay && !shouldShowPicker && (
                    snap.isVisible ||
                        snap.autoScroll.isEnabledByUser ||
                        snap.builder.isActive ||
                        snap.playback.status == PlaybackStatus.PLAYING ||
                        snap.playback.status == PlaybackStatus.PAUSED
                    )

                if (shouldShowFloating) {
                    floatingOverlay?.showOrUpdate(
                        mode = snap.mode,
                        autoScrollConfig = ScrollEngineStateHolder.config.value,
                        autoScrollState = snap.autoScroll,
                        builderState = snap.builder,
                        playbackState = snap.playback
                    )
                } else {
                    floatingOverlay?.hide()
                }

                // 1. Kelola Auto Scroll scheduler
                if (snap.mode == FloatingControlMode.AUTO_SCROLL &&
                    snap.autoScroll.isEnabledByUser &&
                    !snap.autoScroll.isPaused &&
                    snap.autoScroll.isTargetAppInForeground
                ) {
                    ensureAutoScrollRunning()
                } else {
                    autoScrollJob?.cancel()
                    autoScrollJob = null
                }

                // 2. Kelola Macro Playback runner
                if (snap.mode == FloatingControlMode.MACRO_PLAYBACK &&
                    snap.playback.status == PlaybackStatus.PLAYING
                ) {
                    ensureMacroPlaybackRunning()
                } else if (snap.playback.status != PlaybackStatus.PAUSED) {
                    macroPlaybackJob?.cancel()
                    macroPlaybackJob = null
                }
            }
        }
    }

    private fun ensureAutoScrollRunning() {
        if (autoScrollJob?.isActive == true) return
        autoScrollJob = serviceScope.launch {
            while (isActive) {
                val state = ScrollEngineStateHolder.runtimeState.value
                if (!state.isEnabledByUser || state.isPaused || !state.isTargetAppInForeground) break

                if (state.remainingSeconds <= 1L) {
                    performAutoScrollGesture(ScrollEngineStateHolder.config.value)
                    delay(1000L)
                } else {
                    delay(1000L)
                    val latest = ScrollEngineStateHolder.runtimeState.value
                    if (latest.isEnabledByUser && !latest.isPaused && latest.isTargetAppInForeground) {
                        ScrollEngineStateHolder.tickCountdown((latest.remainingSeconds - 1L).coerceAtLeast(0L))
                    }
                }
            }
        }
    }

    private fun ensureMacroPlaybackRunning() {
        if (macroPlaybackJob?.isActive == true) return
        macroPlaybackJob = serviceScope.launch {
            val macro = ScrollEngineStateHolder.playbackState.value.activeMacro ?: run {
                ScrollEngineStateHolder.markPlaybackError("Tidak ada macro yang dipilih")
                return@launch
            }
            val actions = macro.actions
            val maxRepeat = macro.repeatCount.coerceAtLeast(1)

            for (repeatIter in 1..maxRepeat) {
                for (index in actions.indices) {
                    waitIfPlaybackPaused()
                    if (!isActive || ScrollEngineStateHolder.playbackState.value.status != PlaybackStatus.PLAYING) {
                        return@launch
                    }

                    val action = actions[index]
                    ScrollEngineStateHolder.updatePlaybackProgress(index + 1, repeatIter)

                    if (action.type != MacroActionType.WAIT) {
                        val ok = dispatchMacroAction(action)
                        if (!ok) {
                            ScrollEngineStateHolder.markPlaybackError(
                                "Gesture #${index + 1} (${action.type.label}) dibatalkan sistem"
                            )
                            return@launch
                        }
                    }

                    if (!delayPausable(action.delayAfterMs)) {
                        return@launch
                    }
                }
            }

            ScrollEngineStateHolder.markPlaybackCompleted()
        }
    }

    private suspend fun waitIfPlaybackPaused() {
        while (ScrollEngineStateHolder.playbackState.value.status == PlaybackStatus.PAUSED) {
            delay(100L)
        }
    }

    private suspend fun delayPausable(totalMs: Long): Boolean {
        var remaining = totalMs.coerceAtLeast(0L)
        while (remaining > 0L) {
            waitIfPlaybackPaused()
            if (ScrollEngineStateHolder.playbackState.value.status != PlaybackStatus.PLAYING) {
                return false
            }
            val step = remaining.coerceAtMost(80L)
            delay(step)
            remaining -= step
        }
        return true
    }

    private suspend fun dispatchMacroAction(action: MacroAction): Boolean {
        if (action.type == MacroActionType.WAIT) return true

        val (widthPx, heightPx) = getScreenDimensions()
        val stroke = CoordinateUtils.resolvePixelStroke(action, widthPx, heightPx)

        if (action.type == MacroActionType.DOUBLE_TAP) {
            val first = dispatchSingleStroke(
                startX = stroke.startX,
                startY = stroke.startY,
                endX = stroke.startX,
                endY = stroke.startY,
                durationMs = 55L
            )
            if (!first) return false
            delay(95L)
            return dispatchSingleStroke(
                startX = stroke.startX,
                startY = stroke.startY,
                endX = stroke.startX,
                endY = stroke.startY,
                durationMs = 55L
            )
        }

        return dispatchSingleStroke(
            startX = stroke.startX,
            startY = stroke.startY,
            endX = stroke.endX,
            endY = stroke.endY,
            durationMs = stroke.durationMs
        )
    }

    private suspend fun dispatchSingleStroke(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long
    ): Boolean = suspendCancellableCoroutine { cont ->
        val path = Path().apply {
            moveTo(startX, startY)
            if (startX != endX || startY != endY) {
                lineTo(endX, endY)
            } else {
                lineTo(startX + 0.5f, startY + 0.5f)
            }
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs.coerceAtLeast(25L))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val accepted = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(false)
                }
            },
            null
        )

        if (!accepted && cont.isActive) {
            cont.resume(false)
        }
    }

    private fun performAutoScrollGesture(config: ScrollConfig) {
        val (widthPx, heightPx) = getScreenDimensions()
        val stroke = GestureCalculator.calculateStroke(widthPx, heightPx, config)

        val path = Path().apply {
            moveTo(stroke.x, stroke.startY)
            lineTo(stroke.x, stroke.endY)
        }

        val strokeDescription = GestureDescription.StrokeDescription(path, 0L, stroke.durationMs)
        val gesture = GestureDescription.Builder().addStroke(strokeDescription).build()

        val dispatched = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    ScrollEngineStateHolder.onGestureSucceeded()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    ScrollEngineStateHolder.onGestureFailed("Gesture dibatalkan oleh sistem")
                }
            },
            null
        )

        if (!dispatched) {
            ScrollEngineStateHolder.onGestureFailed("Gagal mengirim gesture ke layar aktif")
        }
    }

    private fun bringAppToFrontForSaveMacro() {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_OPEN_SAVE_MACRO, true)
        }
        runCatching { startActivity(launchIntent) }
    }

    private fun resolveActiveAppPackage(event: AccessibilityEvent): String? {
        val activeWindowPkg = windows
            ?.firstOrNull { window ->
                window.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                    window.isActive &&
                    window.root?.packageName != null
            }
            ?.root
            ?.packageName
            ?.toString()

        val candidate = activeWindowPkg
            ?: rootInActiveWindow?.packageName?.toString()
            ?: event.packageName?.toString()

        if (candidate.isNullOrBlank()) return null
        if (candidate in ignoredSystemPackages) return null
        if (candidate == packageName && event.className?.toString()?.contains("LinearLayout") == true) {
            return null
        }
        return candidate
    }

    private fun refreshForegroundPackage() {
        val currentPkg = rootInActiveWindow?.packageName?.toString() ?: return
        if (currentPkg !in ignoredSystemPackages) {
            ScrollEngineStateHolder.updateForegroundPackage(currentPkg)
        }
    }

    private fun getScreenDimensions(): Pair<Int, Int> {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        }
    }

    private data class CombinedServiceSnapshot(
        val mode: FloatingControlMode,
        val isVisible: Boolean,
        val autoScroll: ScrollRuntimeState,
        val builder: MacroBuilderState,
        val playback: MacroPlaybackState
    )
}
