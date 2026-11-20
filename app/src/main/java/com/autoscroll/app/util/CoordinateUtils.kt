package com.autoscroll.app.util

import com.autoscroll.app.data.model.MacroAction
import com.autoscroll.app.data.model.MacroActionType
import com.autoscroll.app.data.model.CoordinatePickMode
import kotlin.math.roundToInt

data class PixelStroke(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val durationMs: Long
)

object CoordinateUtils {

    fun applyPickedCoordinates(
        action: MacroAction,
        pickMode: CoordinatePickMode,
        startXPercent: Float,
        startYPercent: Float,
        endXPercent: Float?,
        endYPercent: Float?,
        forceSwipeType: Boolean
    ): MacroAction {
        return when (pickMode) {
            CoordinatePickMode.EDIT_END_ONLY -> action.copy(
                endXPercent = (endXPercent ?: startXPercent).coerceIn(0.02f, 0.98f),
                endYPercent = (endYPercent ?: startYPercent).coerceIn(0.02f, 0.98f)
            )
            CoordinatePickMode.SWIPE_START_END -> {
                val becomesSwipe = forceSwipeType && action.type != MacroActionType.SWIPE
                action.copy(
                    type = if (forceSwipeType) MacroActionType.SWIPE else action.type,
                    startXPercent = startXPercent.coerceIn(0.02f, 0.98f),
                    startYPercent = startYPercent.coerceIn(0.02f, 0.98f),
                    endXPercent = (endXPercent ?: 0.5f).coerceIn(0.02f, 0.98f),
                    endYPercent = (endYPercent ?: 0.25f).coerceIn(0.02f, 0.98f),
                    durationMs = if (becomesSwipe) defaultDurationFor(MacroActionType.SWIPE) else action.durationMs
                )
            }
            CoordinatePickMode.EDIT_START_ONLY,
            CoordinatePickMode.SINGLE_POINT -> {
                val becomesSwipe = pickMode == CoordinatePickMode.SINGLE_POINT &&
                    forceSwipeType && action.type != MacroActionType.SWIPE
                action.copy(
                    type = if (forceSwipeType && pickMode == CoordinatePickMode.SINGLE_POINT) {
                        MacroActionType.SWIPE
                    } else {
                        action.type
                    },
                    startXPercent = startXPercent.coerceIn(0.02f, 0.98f),
                    startYPercent = startYPercent.coerceIn(0.02f, 0.98f),
                    endXPercent = if (becomesSwipe) {
                        (endXPercent ?: action.endXPercent).coerceIn(0.02f, 0.98f)
                    } else {
                        action.endXPercent
                    },
                    endYPercent = if (becomesSwipe) {
                        (endYPercent ?: action.endYPercent).coerceIn(0.02f, 0.98f)
                    } else {
                        action.endYPercent
                    },
                    durationMs = if (becomesSwipe) defaultDurationFor(MacroActionType.SWIPE) else action.durationMs
                )
            }
        }
    }

    fun pixelToPercent(pixel: Float, dimensionPx: Int): Float {
        if (dimensionPx <= 0) return 0.5f
        return (pixel / dimensionPx.toFloat()).coerceIn(0.02f, 0.98f)
    }

    fun percentToPixel(percent: Float, dimensionPx: Int): Float {
        val safeDim = dimensionPx.coerceAtLeast(100)
        return (percent.coerceIn(0.01f, 0.99f) * safeDim).coerceIn(1f, (safeDim - 1).toFloat())
    }

    fun formatPercent(percent: Float): String {
        val tenths = (percent.coerceIn(0f, 1f) * 1000f).roundToInt() / 10.0
        return "${tenths}%"
    }

    fun formatCoordinatePair(xPercent: Float, yPercent: Float): String {
        return "${formatPercent(xPercent)}, ${formatPercent(yPercent)}"
    }

    fun defaultDurationFor(type: MacroActionType): Long {
        return when (type) {
            MacroActionType.TAP -> 80L
            MacroActionType.DOUBLE_TAP -> 65L
            MacroActionType.LONG_PRESS -> 650L
            MacroActionType.SWIPE -> 420L
            MacroActionType.SCROLL_UP -> 480L
            MacroActionType.SCROLL_DOWN -> 480L
            MacroActionType.WAIT -> 0L
        }
    }

    fun resolvePixelStroke(
        action: MacroAction,
        screenWidthPx: Int,
        screenHeightPx: Int
    ): PixelStroke {
        val xStart = percentToPixel(action.startXPercent, screenWidthPx)
        val yStart = percentToPixel(action.startYPercent, screenHeightPx)

        return when (action.type) {
            MacroActionType.TAP, MacroActionType.DOUBLE_TAP -> PixelStroke(
                startX = xStart,
                startY = yStart,
                endX = xStart,
                endY = yStart,
                durationMs = action.durationMs.coerceIn(40L, 180L)
            )
            MacroActionType.LONG_PRESS -> PixelStroke(
                startX = xStart,
                startY = yStart,
                endX = xStart,
                endY = yStart,
                durationMs = action.durationMs.coerceIn(450L, 5000L)
            )
            MacroActionType.SWIPE -> PixelStroke(
                startX = xStart,
                startY = yStart,
                endX = percentToPixel(action.endXPercent, screenWidthPx),
                endY = percentToPixel(action.endYPercent, screenHeightPx),
                durationMs = action.durationMs.coerceIn(120L, 5000L)
            )
            MacroActionType.SCROLL_DOWN -> {
                // Swipe jari dari bawah ke atas menggeser halaman ke bawah
                val fromY = percentToPixel(action.startYPercent.coerceIn(0.45f, 0.85f), screenHeightPx)
                val toY = percentToPixel((action.startYPercent - 0.36f).coerceIn(0.15f, 0.55f), screenHeightPx)
                PixelStroke(
                    startX = xStart,
                    startY = fromY,
                    endX = xStart,
                    endY = toY,
                    durationMs = action.durationMs.coerceIn(180L, 2500L)
                )
            }
            MacroActionType.SCROLL_UP -> {
                // Swipe jari dari atas ke bawah menggeser halaman ke atas
                val fromY = percentToPixel(action.startYPercent.coerceIn(0.18f, 0.55f), screenHeightPx)
                val toY = percentToPixel((action.startYPercent + 0.36f).coerceIn(0.45f, 0.86f), screenHeightPx)
                PixelStroke(
                    startX = xStart,
                    startY = fromY,
                    endX = xStart,
                    endY = toY,
                    durationMs = action.durationMs.coerceIn(180L, 2500L)
                )
            }
            MacroActionType.WAIT -> PixelStroke(
                startX = xStart,
                startY = yStart,
                endX = xStart,
                endY = yStart,
                durationMs = 0L
            )
        }
    }

    fun buildSummaryNote(action: MacroAction): String {
        return when (action.type) {
            MacroActionType.TAP,
            MacroActionType.DOUBLE_TAP,
            MacroActionType.LONG_PRESS -> "${action.type.label} (${formatCoordinatePair(action.startXPercent, action.startYPercent)})"
            MacroActionType.SWIPE -> "Swipe (${formatCoordinatePair(action.startXPercent, action.startYPercent)}) -> (${formatCoordinatePair(action.endXPercent, action.endYPercent)})"
            MacroActionType.SCROLL_UP -> "Scroll Up di X=${formatPercent(action.startXPercent)}"
            MacroActionType.SCROLL_DOWN -> "Scroll Down di X=${formatPercent(action.startXPercent)}"
            MacroActionType.WAIT -> "Wait ${action.delayAfterMs} ms"
        }
    }

    fun reorderAction(actions: List<MacroAction>, fromIndex: Int, toIndex: Int): List<MacroAction> {
        if (fromIndex !in actions.indices || toIndex !in actions.indices || fromIndex == toIndex) {
            return actions
        }
        val mutable = actions.toMutableList()
        val moved = mutable.removeAt(fromIndex)
        mutable.add(toIndex, moved)
        return mutable.mapIndexed { idx, action -> action.copy(orderIndex = idx) }
    }

    fun deleteActionAt(actions: List<MacroAction>, index: Int): List<MacroAction> {
        return actions.filterIndexed { i, _ -> i != index }
            .mapIndexed { idx, action -> action.copy(orderIndex = idx) }
    }
}
