package com.autoscroll.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AutoScrollAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var schedulerJob: Job? = null
    private var stateObserverJob: Job? = null
    private var floatingOverlay: FloatingControlOverlay? = null

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
            onPauseResume = {
                val state = ScrollEngineStateHolder.runtimeState.value
                if (state.isPaused) {
                    ScrollEngineStateHolder.resumeAutoScroll()
                } else {
                    ScrollEngineStateHolder.pauseAutoScroll()
                }
            },
            onStop = {
                ScrollEngineStateHolder.stopAutoScroll()
            }
        )

        observeRuntimeState()
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
        schedulerJob?.cancel()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        cleanupService()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanupService()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun cleanupService() {
        schedulerJob?.cancel()
        stateObserverJob?.cancel()
        floatingOverlay?.hide()
        floatingOverlay = null
        ScrollEngineStateHolder.setServiceConnected(false)
    }

    private fun observeRuntimeState() {
        stateObserverJob?.cancel()
        stateObserverJob = serviceScope.launch {
            ScrollEngineStateHolder.runtimeState.collectLatest { state ->
                val canOverlay = PermissionHelper.canDrawOverlays(applicationContext)
                if (state.isEnabledByUser && canOverlay) {
                    floatingOverlay?.showOrUpdate(
                        state = state,
                        targetName = ScrollEngineStateHolder.config.value.targetAppName
                    )
                } else {
                    floatingOverlay?.hide()
                }

                if (state.isEnabledByUser && !state.isPaused && state.isTargetAppInForeground) {
                    ensureSchedulerRunning()
                } else {
                    schedulerJob?.cancel()
                    schedulerJob = null
                }
            }
        }
    }

    private fun ensureSchedulerRunning() {
        if (schedulerJob?.isActive == true) return
        schedulerJob = serviceScope.launch {
            while (isActive) {
                val state = ScrollEngineStateHolder.runtimeState.value
                if (!state.isEnabledByUser || state.isPaused || !state.isTargetAppInForeground) {
                    break
                }

                val remaining = state.remainingSeconds
                if (remaining <= 1L) {
                    val config = ScrollEngineStateHolder.config.value
                    performAutoScrollGesture(config)
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

    private fun performAutoScrollGesture(config: ScrollConfig) {
        val (widthPx, heightPx) = getScreenDimensions()
        val stroke = GestureCalculator.calculateStroke(widthPx, heightPx, config)

        val path = Path().apply {
            moveTo(stroke.x, stroke.startY)
            lineTo(stroke.x, stroke.endY)
        }

        val strokeDescription = GestureDescription.StrokeDescription(
            path,
            0L,
            stroke.durationMs
        )
        val gesture = GestureDescription.Builder()
            .addStroke(strokeDescription)
            .build()

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
        // Abaikan event internal overlay milik aplikasi sendiri agar status Shopee tetap stabil
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
}
