package com.autoscroll.app.util

import com.autoscroll.app.data.model.MacroAction
import com.autoscroll.app.data.model.MacroActionType
import kotlin.math.abs
import kotlin.math.hypot

object GestureClassifier {

    private const val TAP_SLOP_PX = 28f
    private const val LONG_PRESS_THRESHOLD_MS = 450L
    private const val DOUBLE_TAP_GAP_MS = 320L
    private const val DOUBLE_TAP_SLOP_PX = 60f

    fun classifyTouch(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long,
        delaySincePreviousUpMs: Long,
        previousAction: MacroAction?,
        nextOrderIndex: Int
    ): MacroAction {
        val dx = endX - startX
        val dy = endY - startY
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val safeDuration = durationMs.coerceIn(30L, 5000L)

        if (distance <= TAP_SLOP_PX) {
            if (safeDuration >= LONG_PRESS_THRESHOLD_MS) {
                return MacroAction(
                    orderIndex = nextOrderIndex,
                    type = MacroActionType.LONG_PRESS,
                    startX = startX,
                    startY = startY,
                    endX = startX,
                    endY = startY,
                    durationMs = safeDuration,
                    delayAfterMs = 600L,
                    note = "Tekan lama (${startX.toInt()}, ${startY.toInt()})"
                )
            }

            if (previousAction != null &&
                previousAction.type == MacroActionType.TAP &&
                delaySincePreviousUpMs in 0L..DOUBLE_TAP_GAP_MS &&
                hypot((startX - previousAction.startX).toDouble(), (startY - previousAction.startY).toDouble()) <= DOUBLE_TAP_SLOP_PX
            ) {
                return MacroAction(
                    orderIndex = nextOrderIndex,
                    type = MacroActionType.DOUBLE_TAP,
                    startX = startX,
                    startY = startY,
                    endX = startX,
                    endY = startY,
                    durationMs = 65L,
                    delayAfterMs = 600L,
                    note = "Ketuk 2x (${startX.toInt()}, ${startY.toInt()})"
                )
            }

            return MacroAction(
                orderIndex = nextOrderIndex,
                type = MacroActionType.TAP,
                startX = startX,
                startY = startY,
                endX = startX,
                endY = startY,
                durationMs = safeDuration.coerceAtMost(140L),
                delayAfterMs = 600L,
                note = "Ketuk (${startX.toInt()}, ${startY.toInt()})"
            )
        }

        val isVerticalScroll = abs(dy) > abs(dx) * 1.35f && distance >= 120f
        val actionType = if (isVerticalScroll) MacroActionType.SCROLL else MacroActionType.SWIPE
        val directionLabel = when {
            isVerticalScroll && dy < 0 -> "Scroll ke Bawah"
            isVerticalScroll && dy > 0 -> "Scroll ke Atas"
            else -> "Swipe (${startX.toInt()},${startY.toInt()} -> ${endX.toInt()},${endY.toInt()})"
        }

        return MacroAction(
            orderIndex = nextOrderIndex,
            type = actionType,
            startX = startX,
            startY = startY,
            endX = endX,
            endY = endY,
            durationMs = safeDuration.coerceIn(120L, 2500L),
            delayAfterMs = 700L,
            note = directionLabel
        )
    }

    fun reorderAction(actions: List<MacroAction>, fromIndex: Int, toIndex: Int): List<MacroAction> {
        if (fromIndex !in actions.indices || toIndex !in actions.indices || fromIndex == toIndex) {
            return actions
        }
        val mutable = actions.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        return mutable.mapIndexed { idx, action -> action.copy(orderIndex = idx) }
    }
}
