package com.autoscroll.app.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.autoscroll.app.data.model.ScrollRuntimeState
import kotlin.math.abs

class FloatingControlOverlay(
    private val context: Context,
    private val onPauseResume: () -> Unit,
    private val onStop: () -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var rootView: LinearLayout? = null
    private var statusText: TextView? = null
    private var pauseResumeButton: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isAttached = false

    fun showOrUpdate(state: ScrollRuntimeState, targetName: String) {
        if (!isAttached) {
            attachOverlay()
        }
        updateUi(state, targetName)
    }

    fun hide() {
        if (!isAttached) return
        val view = rootView ?: return
        runCatching {
            windowManager.removeView(view)
        }
        isAttached = false
        rootView = null
        statusText = null
        pauseResumeButton = null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachOverlay() {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(10), dp(8))
            elevation = dp(8).toFloat()
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), Color.parseColor("#E2E8F0"))
            }
        }

        val dragHandle = TextView(context).apply {
            text = "⋮⋮"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, 0, dp(8), 0)
        }

        val infoLabel = TextView(context).apply {
            setTextColor(Color.parseColor("#0F172A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, 0, dp(10), 0)
        }
        statusText = infoLabel

        val pauseBtn = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EE4D2D"))
                cornerRadius = dp(14).toFloat()
            }
            setOnClickListener { onPauseResume() }
        }
        pauseResumeButton = pauseBtn

        val stopBtn = TextView(context).apply {
            text = "Stop"
            setTextColor(Color.parseColor("#DC2626"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FEF2F2"))
                cornerRadius = dp(14).toFloat()
            }
            setOnClickListener { onStop() }
        }

        val spacer = LinearLayout.LayoutParams(dp(6), 1)
        container.addView(dragHandle)
        container.addView(infoLabel)
        container.addView(pauseBtn)
        val stopWrapper = LinearLayout(context).apply {
            setPadding(dp(6), 0, 0, 0)
            addView(stopBtn)
        }
        container.addView(stopWrapper)
        spacer.width = 0

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(16)
            y = dp(120)
        }

        var initialX = 0
        var initialY = 0
        var touchStartX = 0f
        var touchStartY = 0f
        var dragging = false

        container.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    dragging = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (touchStartX - event.rawX).toInt()
                    val dy = (event.rawY - touchStartY).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) {
                        dragging = true
                        params.x = initialX + dx
                        params.y = initialY + dy
                        runCatching { windowManager.updateViewLayout(container, params) }
                    }
                    dragging
                }
                else -> dragging
            }
        }

        runCatching {
            windowManager.addView(container, params)
            rootView = container
            layoutParams = params
            isAttached = true
        }
    }

    private fun updateUi(state: ScrollRuntimeState, targetName: String) {
        val statusPrefix = when {
            state.isPaused -> "Jeda"
            !state.isTargetAppInForeground -> "Tunggu $targetName"
            else -> "${state.remainingSeconds}s"
        }
        statusText?.text = "$statusPrefix · ${state.scrollCount}x"

        pauseResumeButton?.apply {
            text = if (state.isPaused) "Resume" else "Pause"
            val bgColor = if (state.isPaused) "#16A34A" else "#EE4D2D"
            (background as? GradientDrawable)?.setColor(Color.parseColor(bgColor))
        }
    }
}
