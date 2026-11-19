package com.autoscroll.app

import com.autoscroll.app.data.model.CustomTimeUnit
import com.autoscroll.app.data.model.IntervalPreset
import com.autoscroll.app.data.model.ScrollConfig
import com.autoscroll.app.data.model.ScrollDirection
import com.autoscroll.app.data.model.ScrollSpeed
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
}
