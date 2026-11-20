package com.autoscroll.app

import com.autoscroll.app.data.model.CustomTimeUnit
import com.autoscroll.app.data.model.IntervalPreset
import com.autoscroll.app.data.model.MacroAction
import com.autoscroll.app.data.model.MacroActionType
import com.autoscroll.app.data.model.ScrollConfig
import com.autoscroll.app.data.model.ScrollDirection
import com.autoscroll.app.data.model.ScrollSpeed
import com.autoscroll.app.util.GestureCalculator
import com.autoscroll.app.util.GestureClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollConfigTest {

    @Test
    fun `effectiveIntervalSeconds returns preset seconds accurately`() {
        assertEquals(30L, ScrollConfig(intervalPreset = IntervalPreset.SEC_30).effectiveIntervalSeconds())
        assertEquals(60L, ScrollConfig(intervalPreset = IntervalPreset.MIN_1).effectiveIntervalSeconds())
        assertEquals(120L, ScrollConfig(intervalPreset = IntervalPreset.MIN_2).effectiveIntervalSeconds())
        assertEquals(180L, ScrollConfig(intervalPreset = IntervalPreset.MIN_3).effectiveIntervalSeconds())
        assertEquals(300L, ScrollConfig(intervalPreset = IntervalPreset.MIN_5).effectiveIntervalSeconds())
    }

    @Test
    fun `effectiveIntervalSeconds supports custom seconds and minutes`() {
        val customSec = ScrollConfig(
            intervalPreset = IntervalPreset.CUSTOM,
            customIntervalValue = 15,
            customTimeUnit = CustomTimeUnit.SECONDS
        )
        assertEquals(15L, customSec.effectiveIntervalSeconds())

        val customMin = ScrollConfig(
            intervalPreset = IntervalPreset.CUSTOM,
            customIntervalValue = 4,
            customTimeUnit = CustomTimeUnit.MINUTES
        )
        assertEquals(240L, customMin.effectiveIntervalSeconds())
    }

    @Test
    fun `gesture stroke swipes upward for DOWN scroll and downward for UP scroll`() {
        val downStroke = GestureCalculator.calculateStroke(
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            config = ScrollConfig(direction = ScrollDirection.DOWN, speed = ScrollSpeed.MEDIUM, durationMs = 500L)
        )
        assertTrue(downStroke.startY > downStroke.endY)
        assertEquals(540f, downStroke.x, 0.1f)
        assertEquals(500L, downStroke.durationMs)

        val upStroke = GestureCalculator.calculateStroke(
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            config = ScrollConfig(direction = ScrollDirection.UP, speed = ScrollSpeed.FAST, durationMs = 600L)
        )
        assertTrue(upStroke.startY < upStroke.endY)
        assertEquals(600L, upStroke.durationMs)
    }

    @Test
    fun `gesture classifier differentiates tap, double tap, long press, and vertical scroll`() {
        val firstTap = GestureClassifier.classifyTouch(
            startX = 500f,
            startY = 1100f,
            endX = 504f,
            endY = 1103f,
            durationMs = 70L,
            delaySincePreviousUpMs = 2000L,
            previousAction = null,
            nextOrderIndex = 0
        )
        assertEquals(MacroActionType.TAP, firstTap.type)

        val doubleTap = GestureClassifier.classifyTouch(
            startX = 505f,
            startY = 1105f,
            endX = 506f,
            endY = 1104f,
            durationMs = 60L,
            delaySincePreviousUpMs = 150L,
            previousAction = firstTap,
            nextOrderIndex = 1
        )
        assertEquals(MacroActionType.DOUBLE_TAP, doubleTap.type)

        val longPress = GestureClassifier.classifyTouch(
            startX = 300f,
            startY = 900f,
            endX = 302f,
            endY = 901f,
            durationMs = 720L,
            delaySincePreviousUpMs = 1000L,
            previousAction = firstTap,
            nextOrderIndex = 2
        )
        assertEquals(MacroActionType.LONG_PRESS, longPress.type)

        val scroll = GestureClassifier.classifyTouch(
            startX = 540f,
            startY = 1700f,
            endX = 542f,
            endY = 750f,
            durationMs = 450L,
            delaySincePreviousUpMs = 1000L,
            previousAction = longPress,
            nextOrderIndex = 3
        )
        assertEquals(MacroActionType.SCROLL, scroll.type)
    }

    @Test
    fun `reorderAction moves action and updates orderIndex cleanly`() {
        val list = listOf(
            MacroAction(orderIndex = 0, type = MacroActionType.TAP, note = "A"),
            MacroAction(orderIndex = 1, type = MacroActionType.SCROLL, note = "B"),
            MacroAction(orderIndex = 2, type = MacroActionType.SWIPE, note = "C")
        )
        val reordered = GestureClassifier.reorderAction(list, 2, 0)
        assertEquals(listOf("C", "A", "B"), reordered.map { it.note })
        assertEquals(listOf(0, 1, 2), reordered.map { it.orderIndex })
    }
}
