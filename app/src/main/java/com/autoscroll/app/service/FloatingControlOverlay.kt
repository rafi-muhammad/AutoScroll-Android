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
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.autoscroll.app.data.model.FloatingControlMode
import com.autoscroll.app.data.model.MacroPlaybackState
import com.autoscroll.app.data.model.PlaybackStatus
import com.autoscroll.app.data.model.RecordingState
import com.autoscroll.app.data.model.ScrollConfig
import com.autoscroll.app.data.model.ScrollRuntimeState
import kotlin.math.abs

/**
 * Satu-satunya Floating Control Window (SYSTEM_ALERT_WINDOW) yang mengganti
 * isi dan tombolnya secara dinamis berdasarkan mode aktif:
 * - AUTO_SCROLL
 * - RECORDING
 * - MACRO_PLAYBACK
 */
class FloatingControlOverlay(
    private val context: Context,
    private val onAutoScrollStart: () -> Unit,
    private val onAutoScrollPauseResume: () -> Unit,
    private val onAutoScrollStop: () -> Unit,
    private val onStopRecording: () -> Unit,
    private val onPlaybackPlay: () -> Unit,
    private val onPlaybackPauseResume: () -> Unit,
    private val onPlaybackStop: () -> Unit,
    private val onCloseOverlay: () -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var rootView: LinearLayout? = null
    private var contentRow: LinearLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isAttached = false

    private var lastRenderedSignature: String = ""
    private var titleView: TextView? = null
    private var subtitleView: TextView? = null
    private var primaryActionBtn: TextView? = null
    private var secondaryActionBtn: TextView? = null
    private var closeBtn: TextView? = null
    private var recBadgeView: TextView? = null

    fun showOrUpdate(
        mode: FloatingControlMode,
        autoScrollConfig: ScrollConfig,
        autoScrollState: ScrollRuntimeState,
        recordingState: RecordingState,
        playbackState: MacroPlaybackState
    ) {
        if (!isAttached) {
            attachOverlay()
        }
        renderDynamicMode(
            mode = mode,
            autoScrollConfig = autoScrollConfig,
            autoScrollState = autoScrollState,
            recordingState = recordingState,
            playbackState = playbackState
        )
    }

    fun hide() {
        if (!isAttached) return
        val view = rootView ?: return
        runCatching { windowManager.removeView(view) }
        isAttached = false
        rootView = null
        contentRow = null
        titleView = null
        subtitleView = null
        primaryActionBtn = null
        secondaryActionBtn = null
        closeBtn = null
        recBadgeView = null
        lastRenderedSignature = ""
    }

    private fun dp(value: Int): Int {
        val density = context.resources.displayMetrics.density
        return (value * density).toInt()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachOverlay() {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(10), dp(8))
            elevation = dp(10).toFloat()
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

        val recBadge = TextView(context).apply {
            text = "REC"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(7), dp(3), dp(7), dp(3))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#DC2626"))
                cornerRadius = dp(10).toFloat()
            }
            visibility = View.GONE
        }
        recBadgeView = recBadge

        val textStack = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), 0, dp(10), 0)
        }
        val title = TextView(context).apply {
            setTextColor(Color.parseColor("#0F172A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            maxLines = 1
        }
        val subtitle = TextView(context).apply {
            setTextColor(Color.parseColor("#64748B"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            maxLines = 1
        }
        textStack.addView(title)
        textStack.addView(subtitle)
        titleView = title
        subtitleView = subtitle

        val primaryBtn = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EE4D2D"))
                cornerRadius = dp(14).toFloat()
            }
        }
        primaryActionBtn = primaryBtn

        val secondaryBtn = TextView(context).apply {
            setTextColor(Color.parseColor("#DC2626"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FEF2F2"))
                cornerRadius = dp(14).toFloat()
            }
        }
        secondaryActionBtn = secondaryBtn

        val closeAction = TextView(context).apply {
            text = "✕"
            setTextColor(Color.parseColor("#64748B"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(8), dp(6), dp(6), dp(6))
            setOnClickListener { onCloseOverlay() }
        }
        closeBtn = closeAction

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(dragHandle)
            addView(recBadge)
            addView(textStack)
            addView(primaryBtn)
            val secWrap = LinearLayout(context).apply {
                setPadding(dp(6), 0, 0, 0)
                addView(secondaryBtn)
            }
            addView(secWrap)
            addView(closeAction)
        }
        contentRow = row
        container.addView(row)

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

    private fun renderDynamicMode(
        mode: FloatingControlMode,
        autoScrollConfig: ScrollConfig,
        autoScrollState: ScrollRuntimeState,
        recordingState: RecordingState,
        playbackState: MacroPlaybackState
    ) {
        when (mode) {
            FloatingControlMode.AUTO_SCROLL -> {
                recBadgeView?.visibility = View.GONE
                val stateLabel = when {
                    !autoScrollState.isEnabledByUser -> "Siap · ${autoScrollConfig.formattedInterval()}"
                    autoScrollState.isPaused -> "Dijeda · ${autoScrollState.scrollCount}x"
                    !autoScrollState.isTargetAppInForeground -> "Tunggu ${autoScrollConfig.targetAppName}"
                    else -> "Aktif · ${autoScrollState.remainingSeconds}d (${autoScrollState.scrollCount}x)"
                }
                titleView?.text = "Auto Scroll"
                subtitleView?.text = stateLabel

                if (!autoScrollState.isEnabledByUser) {
                    styleButton(primaryActionBtn, "Start", "#EE4D2D", Color.WHITE) {
                        onAutoScrollStart()
                    }
                    secondaryActionBtn?.visibility = View.GONE
                    closeBtn?.visibility = View.VISIBLE
                } else {
                    val pauseText = if (autoScrollState.isPaused) "Resume" else "Pause"
                    val pauseBg = if (autoScrollState.isPaused) "#16A34A" else "#EE4D2D"
                    styleButton(primaryActionBtn, pauseText, pauseBg, Color.WHITE) {
                        onAutoScrollPauseResume()
                    }
                    styleButton(secondaryActionBtn, "Stop", "#FEF2F2", Color.parseColor("#DC2626")) {
                        onAutoScrollStop()
                    }
                    closeBtn?.visibility = View.GONE
                }
            }

            FloatingControlMode.RECORDING -> {
                recBadgeView?.visibility = View.VISIBLE
                val mins = recordingState.elapsedSeconds / 60L
                val secs = recordingState.elapsedSeconds % 60L
                val formattedTime = "%02d:%02d".format(mins, secs)

                titleView?.text = "$formattedTime · ${recordingState.capturedActions.size} Gesture"
                subtitleView?.text = "Merekam sentuhan layar"

                styleButton(primaryActionBtn, "Stop Recording", "#DC2626", Color.WHITE) {
                    onStopRecording()
                }
                secondaryActionBtn?.visibility = View.GONE
                closeBtn?.visibility = View.GONE
            }

            FloatingControlMode.MACRO_PLAYBACK -> {
                recBadgeView?.visibility = View.GONE
                val macroName = playbackState.activeMacro?.name ?: "Macro"
                val total = playbackState.totalActions
                val currentStep = playbackState.currentActionIndex.coerceAtMost(total)
                val repeatSuffix = if ((playbackState.activeMacro?.repeatCount ?: 1) > 1) {
                    " · Ulang ${playbackState.currentRepeat}/${playbackState.activeMacro?.repeatCount}"
                } else {
                    ""
                }

                titleView?.text = macroName.take(18)
                subtitleView?.text = "${playbackState.status.label} · $currentStep/$total Aksi$repeatSuffix"

                when (playbackState.status) {
                    PlaybackStatus.IDLE, PlaybackStatus.COMPLETED, PlaybackStatus.ERROR -> {
                        val playLabel = if (playbackState.status == PlaybackStatus.COMPLETED) "Play Ulang" else "Play"
                        styleButton(primaryActionBtn, playLabel, "#EE4D2D", Color.WHITE) {
                            onPlaybackPlay()
                        }
                        secondaryActionBtn?.visibility = View.GONE
                        closeBtn?.visibility = View.VISIBLE
                    }
                    PlaybackStatus.PLAYING -> {
                        styleButton(primaryActionBtn, "Pause", "#EE4D2D", Color.WHITE) {
                            onPlaybackPauseResume()
                        }
                        styleButton(secondaryActionBtn, "Stop", "#FEF2F2", Color.parseColor("#DC2626")) {
                            onPlaybackStop()
                        }
                        closeBtn?.visibility = View.GONE
                    }
                    PlaybackStatus.PAUSED -> {
                        styleButton(primaryActionBtn, "Resume", "#16A34A", Color.WHITE) {
                            onPlaybackPauseResume()
                        }
                        styleButton(secondaryActionBtn, "Stop", "#FEF2F2", Color.parseColor("#DC2626")) {
                            onPlaybackStop()
                        }
                        closeBtn?.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun styleButton(
        button: TextView?,
        label: String,
        bgHex: String,
        textColor: Int,
        onClick: () -> Unit
    ) {
        button ?: return
        button.visibility = View.VISIBLE
        button.text = label
        button.setTextColor(textColor)
        (button.background as? GradientDrawable)?.setColor(Color.parseColor(bgHex))
        button.setOnClickListener { onClick() }
    }
}
