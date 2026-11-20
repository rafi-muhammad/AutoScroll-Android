package com.autoscroll.app

import com.autoscroll.app.data.model.CustomTimeUnit
import com.autoscroll.app.data.model.CoordinatePickMode
import com.autoscroll.app.data.model.IntervalPreset
import com.autoscroll.app.data.model.MacroAction
import com.autoscroll.app.data.model.MacroActionType
import com.autoscroll.app.data.model.ScrollConfig
import com.autoscroll.app.data.model.ScrollDirection
import com.autoscroll.app.data.model.ScrollSpeed
import com.autoscroll.app.util.CoordinateUtils
import com.autoscroll.app.util.GestureCalculator
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
    fun `coordinate utils converts percentage coordinates accurately across resolutions`() {
        val pctX = CoordinateUtils.pixelToPercent(540f, 1080)
        val pctY = CoordinateUtils.pixelToPercent(1200f, 2400)
        assertEquals(0.5f, pctX, 0.01f)
        assertEquals(0.5f, pctY, 0.01f)

        // Adaptive playback on 1440x3200 screen
        val tapStroke = CoordinateUtils.resolvePixelStroke(
            action = MacroAction(
                type = MacroActionType.TAP,
                startXPercent = pctX,
                startYPercent = pctY,
                durationMs = 80L
            ),
            screenWidthPx = 1440,
            screenHeightPx = 3200
        )
        assertEquals(720f, tapStroke.startX, 1f)
        assertEquals(1600f, tapStroke.startY, 1f)

        // Scroll Down swipes upward (startY > endY), Scroll Up swipes downward (startY < endY)
        val scrollDown = CoordinateUtils.resolvePixelStroke(
            action = MacroAction(type = MacroActionType.SCROLL_DOWN, startXPercent = 0.5f, startYPercent = 0.72f),
            screenWidthPx = 1080,
            screenHeightPx = 2400
        )
        assertTrue(scrollDown.startY > scrollDown.endY)

        val scrollUp = CoordinateUtils.resolvePixelStroke(
            action = MacroAction(type = MacroActionType.SCROLL_UP, startXPercent = 0.5f, startYPercent = 0.28f),
            screenWidthPx = 1080,
            screenHeightPx = 2400
        )
        assertTrue(scrollUp.startY < scrollUp.endY)
    }

    @Test
    fun `coordinate picker applies swipe points and edits the end point independently`() {
        val swipe = CoordinateUtils.applyPickedCoordinates(
            action = MacroAction(type = MacroActionType.TAP),
            pickMode = CoordinatePickMode.SINGLE_POINT,
            startXPercent = 0.2f,
            startYPercent = 0.3f,
            endXPercent = 0.8f,
            endYPercent = 0.7f,
            forceSwipeType = true
        )
        assertEquals(MacroActionType.SWIPE, swipe.type)
        assertEquals(0.2f, swipe.startXPercent, 0.001f)
        assertEquals(0.3f, swipe.startYPercent, 0.001f)
        assertEquals(0.8f, swipe.endXPercent, 0.001f)
        assertEquals(0.7f, swipe.endYPercent, 0.001f)

        val editedEnd = CoordinateUtils.applyPickedCoordinates(
            action = swipe,
            pickMode = CoordinatePickMode.EDIT_END_ONLY,
            startXPercent = 0.4f,
            startYPercent = 0.6f,
            endXPercent = null,
            endYPercent = null,
            forceSwipeType = false
        )
        assertEquals(0.2f, editedEnd.startXPercent, 0.001f)
        assertEquals(0.3f, editedEnd.startYPercent, 0.001f)
        assertEquals(0.4f, editedEnd.endXPercent, 0.001f)
        assertEquals(0.6f, editedEnd.endYPercent, 0.001f)
    }

    @Test
    fun `reorderAction moves action and updates orderIndex cleanly`() {
        val list = listOf(
            MacroAction(orderIndex = 0, type = MacroActionType.TAP, note = "A"),
            MacroAction(orderIndex = 1, type = MacroActionType.SCROLL_DOWN, note = "B"),
            MacroAction(orderIndex = 2, type = MacroActionType.SWIPE, note = "C")
        )
        val reordered = CoordinateUtils.reorderAction(list, 2, 0)
        assertEquals(listOf("C", "A", "B"), reordered.map { it.note })
        assertEquals(listOf(0, 1, 2), reordered.map { it.orderIndex })
    }
}
