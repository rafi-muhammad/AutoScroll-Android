package com.autoscroll.app.data.model

enum class FloatingControlMode {
    AUTO_SCROLL,
    RECORDING,
    MACRO_PLAYBACK
}

enum class MacroActionType(val label: String) {
    TAP("Tap"),
    DOUBLE_TAP("Double Tap"),
    LONG_PRESS("Long Press"),
    SWIPE("Swipe"),
    SCROLL("Scroll"),
    DELAY("Jeda")
}

enum class PlaybackStatus(val label: String) {
    IDLE("Siap"),
    PLAYING("Berjalan"),
    PAUSED("Dijeda"),
    COMPLETED("Completed"),
    ERROR("Gagal")
}

enum class RecordingCaptureMethod(val label: String) {
    OVERLAY_PASSTHROUGH("Overlay Capture + Forward"),
    ACCESSIBILITY_EVENTS("Accessibility Node Events")
}

data class MacroAction(
    val id: Long = 0L,
    val orderIndex: Int = 0,
    val type: MacroActionType,
    val startX: Float = 540f,
    val startY: Float = 1200f,
    val endX: Float = 540f,
    val endY: Float = 1200f,
    val durationMs: Long = 80L,
    val delayAfterMs: Long = 800L,
    val note: String = ""
)

data class MacroRecord(
    val id: Long = 0L,
    val name: String,
    val targetPackageName: String = TargetApp.SHOPEE_ID.packageName,
    val targetAppName: String = TargetApp.SHOPEE_ID.displayName,
    val repeatCount: Int = 1,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val actions: List<MacroAction> = emptyList()
)

data class RecordingState(
    val isRecording: Boolean = false,
    val elapsedSeconds: Long = 0L,
    val captureMethod: RecordingCaptureMethod = RecordingCaptureMethod.OVERLAY_PASSTHROUGH,
    val capturedActions: List<MacroAction> = emptyList(),
    val hasPendingDraft: Boolean = false,
    val lastCaptureNotice: String? = null
)

data class MacroPlaybackState(
    val activeMacro: MacroRecord? = null,
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val currentActionIndex: Int = 0,
    val currentRepeat: Int = 1,
    val lastError: String? = null
) {
    val totalActions: Int
        get() = activeMacro?.actions?.size ?: 0
}
