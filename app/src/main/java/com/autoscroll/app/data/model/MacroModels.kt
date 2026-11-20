package com.autoscroll.app.data.model

enum class FloatingControlMode {
    AUTO_SCROLL,
    MACRO_BUILDER,
    MACRO_PLAYBACK
}

enum class MacroActionType(val label: String, val usesEndPoint: Boolean) {
    TAP("Tap", false),
    DOUBLE_TAP("Double Tap", false),
    LONG_PRESS("Long Press", false),
    SWIPE("Swipe", true),
    SCROLL_UP("Scroll Up", false),
    SCROLL_DOWN("Scroll Down", false),
    WAIT("Wait", false)
}

enum class PlaybackStatus(val label: String) {
    IDLE("Siap"),
    PLAYING("Berjalan"),
    PAUSED("Dijeda"),
    COMPLETED("Completed"),
    ERROR("Gagal")
}

enum class BuilderOverlayStep {
    TOOLBAR,
    PICKING_COORDINATE,
    CONFIGURING_ACTION,
    ACTION_LIST
}

enum class CoordinatePickMode {
    SINGLE_POINT,
    SWIPE_START_END,
    EDIT_START_ONLY,
    EDIT_END_ONLY
}

/**
 * Koordinat disimpan sebagai rasio persentase layar (0.0f .. 1.0f)
 * sehingga tetap akurat di berbagai resolusi & orientasi perangkat.
 */
data class MacroAction(
    val id: Long = 0L,
    val orderIndex: Int = 0,
    val type: MacroActionType = MacroActionType.TAP,
    val startXPercent: Float = 0.50f,
    val startYPercent: Float = 0.55f,
    val endXPercent: Float = 0.50f,
    val endYPercent: Float = 0.25f,
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

data class MacroBuilderState(
    val isActive: Boolean = false,
    val step: BuilderOverlayStep = BuilderOverlayStep.TOOLBAR,
    val pickMode: CoordinatePickMode = CoordinatePickMode.SINGLE_POINT,
    val editingActionIndex: Int? = null,
    val draftAction: MacroAction = MacroAction(),
    val actions: List<MacroAction> = emptyList(),
    val hasPendingSaveDraft: Boolean = false
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
