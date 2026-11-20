package com.autoscroll.app.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.autoscroll.app.data.model.MacroAction
import com.autoscroll.app.util.GestureClassifier

/**
 * Overlay transparan layar penuh (Capture-and-Forward) untuk merekam sentuhan nyata
 * di atas aplikasi lain seperti Shopee pada perangkat non-root.
 *
 * Saat pengguna menyentuh layar:
 * 1. Koordinat & durasi direkam secara presisi.
 * 2. Overlay sementara disetel FLAG_NOT_TOUCHABLE dan gesture diteruskan ke aplikasi
 *    di bawahnya via AccessibilityService.dispatchGesture().
 * 3. Setelah dispatchGesture selesai, capture overlay diaktifkan kembali.
 */
class TouchCaptureOverlay(
    private val context: Context,
    private val getNextOrderIndex: () -> Int,
    private val getPreviousAction: () -> MacroAction?,
    private val onActionCapturedAndForward: (action: MacroAction, onForwardDone: () -> Unit) -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var captureView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isAttached = false

    private var downX = 0f
    private var downY = 0f
    private var downTimeMs = 0L
    private var lastUpTimeMs = 0L

    fun show() {
        if (isAttached) return
        attachCaptureLayer()
    }

    fun hide() {
        if (!isAttached) return
        val view = captureView ?: return
        runCatching { windowManager.removeView(view) }
        captureView = null
        layoutParams = null
        isAttached = false
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachCaptureLayer() {
        val density = context.resources.displayMetrics.density
        val borderStrokePx = (2 * density).toInt().coerceAtLeast(2)

        val view = View(context).apply {
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(borderStrokePx, Color.parseColor("#88EE4D2D"))
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    downTimeMs = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val upX = event.rawX
                    val upY = event.rawY
                    val upTimeMs = System.currentTimeMillis()
                    val durationMs = (upTimeMs - downTimeMs).coerceAtLeast(40L)
                    val gapSincePrev = if (lastUpTimeMs > 0L) (downTimeMs - lastUpTimeMs) else Long.MAX_VALUE
                    lastUpTimeMs = upTimeMs

                    val action = GestureClassifier.classifyTouch(
                        startX = downX,
                        startY = downY,
                        endX = upX,
                        endY = upY,
                        durationMs = durationMs,
                        delaySincePreviousUpMs = gapSincePrev,
                        previousAction = getPreviousAction(),
                        nextOrderIndex = getNextOrderIndex()
                    )

                    setPassThrough(true)
                    onActionCapturedAndForward(action) {
                        setPassThrough(false)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }

        runCatching {
            windowManager.addView(view, params)
            captureView = view
            layoutParams = params
            isAttached = true
        }
    }

    private fun setPassThrough(passThrough: Boolean) {
        val view = captureView ?: return
        val params = layoutParams ?: return
        if (!isAttached) return
        params.flags = if (passThrough) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }
        runCatching { windowManager.updateViewLayout(view, params) }
    }
}
