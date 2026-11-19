package com.autoscroll.app.util

import com.autoscroll.app.data.model.ScrollConfig
import com.autoscroll.app.data.model.ScrollDirection

data class ScrollStrokeSpec(
    val x: Float,
    val startY: Float,
    val endY: Float,
    val durationMs: Long
)

object GestureCalculator {

    fun calculateStroke(
        screenWidthPx: Int,
        screenHeightPx: Int,
        config: ScrollConfig
    ): ScrollStrokeSpec {
        val safeWidth = screenWidthPx.coerceAtLeast(320)
        val safeHeight = screenHeightPx.coerceAtLeast(480)

        val centerX = safeWidth * 0.5f
        val travelRatio = config.speed.distanceFraction.coerceIn(0.20f, 0.85f)
        val halfTravel = (safeHeight * travelRatio) * 0.5f
        val midY = safeHeight * 0.52f

        val topY = (midY - halfTravel).coerceAtLeast(safeHeight * 0.14f)
        val bottomY = (midY + halfTravel).coerceAtMost(safeHeight * 0.86f)

        // Scroll DOWN (membaca konten ke bawah) berarti jari menggeser layar dari bawah ke atas.
        // Scroll UP (kembali ke atas) berarti jari menggeser layar dari atas ke bawah.
        val (startY, endY) = when (config.direction) {
            ScrollDirection.DOWN -> bottomY to topY
            ScrollDirection.UP -> topY to bottomY
        }

        val safeDuration = config.durationMs.coerceIn(180L, 2000L)

        return ScrollStrokeSpec(
            x = centerX,
            startY = startY,
            endY = endY,
            durationMs = safeDuration
        )
    }
}
