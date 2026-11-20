package com.autoscroll.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.autoscroll.app.data.model.FloatingControlMode
import com.autoscroll.app.data.model.MacroAction
import com.autoscroll.app.data.model.MacroActionType
import com.autoscroll.app.data.model.PlaybackStatus
import com.autoscroll.app.data.model.RecordingCaptureMethod
import com.autoscroll.app.data.model.ScrollConfig
import com.autoscroll.app.data.repository.ScrollEngineStateHolder
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
    private var recordingTimerJob: Job? = null
    private var macroPlaybackJob: Job? = null
    private var stateObserverJob: Job? = null

    private var floatingOverlay: FloatingControlOverlay? = null
    private var touchCaptureOverlay: TouchCaptureOverlay? = null

    private val ignoredSystemPackages = setOf(
        "com.android.systemui",
        "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        ScrollEngineStateHolder.init(applicationContext)
        ScrollEngineStateHolder.setServiceConnected(true)

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
            onStopRecording = {
                ScrollEngineStateHolder.stopRecordingSession()
                bringAppToFrontAfterRecording()
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
                    FloatingControlMode.RECORDING -> ScrollEngineStateHolder.stopRecordingSession()
                    FloatingControlMode.MACRO_PLAYBACK -> ScrollEngineStateHolder.hideFloatingPlaybackControl()
                }
            }
        )

        touchCaptureOverlay = TouchCaptureOverlay(
            context = this,
            getNextOrderIndex = { ScrollEngineStateHolder.recordingState.value.capturedActions.size },
            getPreviousAction = { ScrollEngineStateHolder.recordingState.value.capturedActions.lastOrNull() },
            onActionCapturedAndForward = { action, onForwardDone ->
                ScrollEngineStateHolder.appendRecordedAction(action)
                serviceScope.launch {
                    val forwarded = dispatchMacroAction(action)
                    if (!forwarded) {
                        ScrollEngineStateHolder.reportRecordingNotice(
                            "Sebagian gesture tidak diteruskan oleh sistem pada layar saat ini."
                        )
                    }
                    onForwardDone()
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

            // Opsional: fallback observasi event aksesibilitas saat mode ACCESSIBILITY_EVENTS dipilih
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val recState = ScrollEngineStateHolder.recordingState.value
                if (recState.isRecording && recState.captureMethod == RecordingCaptureMethod.ACCESSIBILITY_EVENTS) {
                    handleAccessibilityEventCapture(event)
                }
            }
        }
    }

    override fun onInterrupt() {
        autoScrollJob?.cancel()
        recordingTimerJob?.cancel()
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
        recordingTimerJob?.cancel()
        macroPlaybackJob?.cancel()
        stateObserverJob?.cancel()
        touchCaptureOverlay?.hide()
        touchCaptureOverlay = null
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
                ScrollEngineStateHolder.recordingState,
                ScrollEngineStateHolder.playbackState
            ) { mode, isVisible, autoState, recState, playState ->
                CombinedServiceSnapshot(mode, isVisible, autoState, recState, playState)
            }.collect { snap ->
                val canOverlay = PermissionHelper.canDrawOverlays(applicationContext)

                // Pastikan TouchCaptureOverlay dipasang SEBELUM FloatingControlOverlay agar tombol Stop selalu berada di lapisan teratas
                val shouldCaptureTouch = snap.mode == FloatingControlMode.RECORDING &&
                    snap.recording.isRecording &&
                    snap.recording.captureMethod == RecordingCaptureMethod.OVERLAY_PASSTHROUGH &&
                    canOverlay

                if (shouldCaptureTouch) {
                    touchCaptureOverlay?.show()
                } else {
                    touchCaptureOverlay?.hide()
                }

                val shouldShowFloating = canOverlay && (
                    snap.isVisible ||
                        snap.autoScroll.isEnabledByUser ||
                        snap.recording.isRecording ||
                        snap.playback.status == PlaybackStatus.PLAYING ||
                        snap.playback.status == PlaybackStatus.PAUSED
                    )

                if (shouldShowFloating) {
                    floatingOverlay?.showOrUpdate(
                        mode = snap.mode,
                        autoScrollConfig = ScrollEngineStateHolder.config.value,
                        autoScrollState = snap.autoScroll,
                        recordingState = snap.recording,
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

                // 2. Kelola Recording timer
                if (snap.mode == FloatingControlMode.RECORDING && snap.recording.isRecording) {
                    ensureRecordingTimerRunning()
                } else {
                    recordingTimerJob?.cancel()
                    recordingTimerJob = null
                }

                // 3. Kelola Macro Playback runner
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

    private fun ensureRecordingTimerRunning() {
        if (recordingTimerJob?.isActive == true) return
        recordingTimerJob = serviceScope.launch {
            while (isActive && ScrollEngineStateHolder.recordingState.value.isRecording) {
                delay(1000L)
                if (ScrollEngineStateHolder.recordingState.value.isRecording) {
                    ScrollEngineStateHolder.tickRecordingElapsed()
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

                    if (action.type != MacroActionType.DELAY) {
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
        if (action.type == MacroActionType.DELAY) return true
        if (action.type == MacroActionType.DOUBLE_TAP) {
            val first = dispatchSingleStroke(
                startX = action.startX,
                startY = action.startY,
                endX = action.startX,
                endY = action.startY,
                durationMs = 55L
            )
            if (!first) return false
            delay(95L)
            return dispatchSingleStroke(
                startX = action.startX,
                startY = action.startY,
                endX = action.startX,
                endY = action.startY,
                durationMs = 55L
            )
        }

        val duration = when (action.type) {
            MacroActionType.TAP -> action.durationMs.coerceIn(40L, 140L)
            MacroActionType.LONG_PRESS -> action.durationMs.coerceIn(450L, 4000L)
            else -> action.durationMs.coerceIn(100L, 4000L)
        }
        return dispatchSingleStroke(
            startX = action.startX,
            startY = action.startY,
            endX = action.endX,
            endY = action.endY,
            durationMs = duration
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

    private fun handleAccessibilityEventCapture(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString().orEmpty()
        if (pkg == packageName || pkg in ignoredSystemPackages) return

        val node = event.source
        if (node == null) {
            ScrollEngineStateHolder.reportRecordingNotice(
                "Event (${pkg}) tidak menyediakan koordinat layar (dibatasi Android)."
            )
            return
        }

        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.isEmpty) {
            ScrollEngineStateHolder.reportRecordingNotice(
                "Bounds elemen tidak tersedia pada aplikasi target ($pkg)."
            )
            return
        }

        val cx = rect.exactCenterX()
        val cy = rect.exactCenterY()
        val nextIndex = ScrollEngineStateHolder.recordingState.value.capturedActions.size

        val action = when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> MacroAction(
                orderIndex = nextIndex,
                type = MacroActionType.TAP,
                startX = cx,
                startY = cy,
                endX = cx,
                endY = cy,
                durationMs = 70L,
                delayAfterMs = 700L,
                note = "Aksesibilitas Tap (${cx.toInt()}, ${cy.toInt()})"
            )
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> MacroAction(
                orderIndex = nextIndex,
                type = MacroActionType.LONG_PRESS,
                startX = cx,
                startY = cy,
                endX = cx,
                endY = cy,
                durationMs = 600L,
                delayAfterMs = 700L,
                note = "Aksesibilitas Long Press (${cx.toInt()}, ${cy.toInt()})"
            )
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val scrollDy = (rect.height() * 0.45f).coerceAtLeast(180f)
                MacroAction(
                    orderIndex = nextIndex,
                    type = MacroActionType.SCROLL,
                    startX = cx,
                    startY = cy + scrollDy * 0.5f,
                    endX = cx,
                    endY = (cy - scrollDy * 0.5f).coerceAtLeast(100f),
                    durationMs = 450L,
                    delayAfterMs = 800L,
                    note = "Aksesibilitas Scroll (${pkg})"
                )
            }
            else -> null
        }

        if (action != null) {
            ScrollEngineStateHolder.appendRecordedAction(action)
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

    private fun bringAppToFrontAfterRecording() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        if (launchIntent != null) {
            runCatching { startActivity(launchIntent) }
        }
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
        val autoScroll: com.autoscroll.app.data.model.ScrollRuntimeState,
        val recording: com.autoscroll.app.data.model.RecordingState,
        val playback: com.autoscroll.app.data.model.MacroPlaybackState
    )
}
