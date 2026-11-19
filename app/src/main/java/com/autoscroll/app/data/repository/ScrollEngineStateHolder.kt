package com.autoscroll.app.data.repository

import android.content.Context
import com.autoscroll.app.data.model.CustomTimeUnit
import com.autoscroll.app.data.model.IntervalPreset
import com.autoscroll.app.data.model.ScrollConfig
import com.autoscroll.app.data.model.ScrollDirection
import com.autoscroll.app.data.model.ScrollRuntimeState
import com.autoscroll.app.data.model.ScrollSpeed
import com.autoscroll.app.data.model.TargetApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Singleton state holder & SharedPreferences repository yang menyinkronkan
 * Compose UI, ViewModel, AccessibilityService, dan FloatingControlOverlay.
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

    private val _config = MutableStateFlow(ScrollConfig())
    val config: StateFlow<ScrollConfig> = _config.asStateFlow()

    private val _runtimeState = MutableStateFlow(ScrollRuntimeState())
    val runtimeState: StateFlow<ScrollRuntimeState> = _runtimeState.asStateFlow()

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
    }

    fun startAutoScroll() {
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
}
