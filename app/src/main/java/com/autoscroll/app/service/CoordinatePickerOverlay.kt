package com.autoscroll.app.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.autoscroll.app.data.model.CoordinatePickMode
import com.autoscroll.app.util.CoordinateUtils
import kotlin.math.hypot

/**
 * Overlay transparan dengan crosshair/reticle yang dapat digeser bebas di atas aplikasi target
 * untuk memilih koordinat X dan Y dalam persentase layar secara presisi tanpa memicu klik ke aplikasi target.
 *
 * Overlay ini HANYA dipasang saat builder berada pada langkah PICKING_COORDINATE, sehingga tidak
 * menghalangi interaksi aplikasi target di luar mode pemilihan koordinat.
 */
class CoordinatePickerOverlay(
    private val context: Context,
    private val onConfirmLocation: (
        startXPercent: Float,
        startYPercent: Float,
        endXPercent: Float?,
        endYPercent: Float?,
        forceSwipeType: Boolean
    ) -> Unit,
    private val onCancel: () -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var rootContainer: FrameLayout? = null
    private var canvasView: CrosshairCanvasView? = null
    private var coordReadoutText: TextView? = null
    private var modeToggleBtn: TextView? = null
    private var isAttached = false

    private var twoPointMode = false
    private var activePickMode = CoordinatePickMode.SINGLE_POINT
    private var startXPercent = 0.50f
    private var startYPercent = 0.55f
    private var endXPercent = 0.50f
    private var endYPercent = 0.25f

    private enum class DragTarget { NONE, START, END }

    fun show(
        pickMode: CoordinatePickMode,
        initialStartXPercent: Float,
        initialStartYPercent: Float,
        initialEndXPercent: Float,
        initialEndYPercent: Float
    ) {
        activePickMode = pickMode
        twoPointMode = pickMode == CoordinatePickMode.SWIPE_START_END
        startXPercent = initialStartXPercent.coerceIn(0.05f, 0.95f)
        startYPercent = initialStartYPercent.coerceIn(0.08f, 0.92f)
        endXPercent = initialEndXPercent.coerceIn(0.05f, 0.95f)
        endYPercent = initialEndYPercent.coerceIn(0.08f, 0.92f)

        if (!isAttached) {
            attachOverlay()
        }
        syncHudText()
        canvasView?.invalidate()
    }

    fun hide() {
        if (!isAttached) return
        val root = rootContainer ?: return
        runCatching { windowManager.removeView(root) }
        rootContainer = null
        canvasView = null
        coordReadoutText = null
        modeToggleBtn = null
        isAttached = false
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }

    private fun attachOverlay() {
        val root = FrameLayout(context)

        val reticleView = CrosshairCanvasView(context)
        canvasView = reticleView
        root.addView(
            reticleView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // HUD Card di bagian atas untuk menampilkan koordinat real-time, mode titik, Cancel, dan Confirm Location
        val hudCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            elevation = dp(12).toFloat()
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.parseColor("#E2E8F0"))
            }
        }

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleText = TextView(context).apply {
            text = "Coordinate Picker"
            setTextColor(Color.parseColor("#0F172A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val toggleBtn = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(10), dp(5), dp(10), dp(5))
            setTextColor(Color.parseColor("#EE4D2D"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FFF3EF"))
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.parseColor("#EE4D2D"))
            }
            setOnClickListener {
                twoPointMode = !twoPointMode
                syncHudText()
                canvasView?.invalidate()
            }
        }
        modeToggleBtn = toggleBtn

        topRow.addView(titleText)
        topRow.addView(toggleBtn)

        val readout = TextView(context).apply {
            setTextColor(Color.parseColor("#334155"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.MONOSPACE
            setPadding(0, dp(6), 0, dp(10))
        }
        coordReadoutText = readout

        val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val cancelBtn = TextView(context).apply {
            text = "Cancel"
            setTextColor(Color.parseColor("#475569"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F1F5F9"))
                cornerRadius = dp(12).toFloat()
            }
            setOnClickListener { onCancel() }
        }

        val confirmBtn = TextView(context).apply {
            text = "Confirm Location"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EE4D2D"))
                cornerRadius = dp(12).toFloat()
            }
            setOnClickListener {
                if (twoPointMode) {
                    onConfirmLocation(startXPercent, startYPercent, endXPercent, endYPercent, true)
                } else {
                    val (pickedX, pickedY) = singlePointCoordinates()
                    onConfirmLocation(pickedX, pickedY, null, null, false)
                }
            }
        }

        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(8), 1)
        }
        actionRow.addView(cancelBtn)
        actionRow.addView(spacer)
        actionRow.addView(confirmBtn)

        hudCard.addView(topRow)
        hudCard.addView(readout)
        hudCard.addView(actionRow)

        val hudParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ).apply {
            setMargins(dp(14), dp(42), dp(14), 0)
        }
        root.addView(hudCard, hudParams)

        val windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        runCatching {
            windowManager.addView(root, windowParams)
            rootContainer = root
            isAttached = true
        }
    }

    private fun syncHudText() {
        val w = (canvasView?.width ?: context.resources.displayMetrics.widthPixels).coerceAtLeast(1)
        val h = (canvasView?.height ?: context.resources.displayMetrics.heightPixels).coerceAtLeast(1)

        val (singleXPercent, singleYPercent) = singlePointCoordinates()
        val sxPx = CoordinateUtils.percentToPixel(singleXPercent, w).toInt()
        val syPx = CoordinateUtils.percentToPixel(singleYPercent, h).toInt()

        modeToggleBtn?.visibility = if (
            activePickMode == CoordinatePickMode.EDIT_START_ONLY ||
            activePickMode == CoordinatePickMode.EDIT_END_ONLY
        ) {
            View.GONE
        } else {
            View.VISIBLE
        }
        modeToggleBtn?.text = if (twoPointMode) "Mode: 2 Titik (Swipe)" else "Mode: 1 Titik"

        coordReadoutText?.text = if (twoPointMode) {
            val exPx = CoordinateUtils.percentToPixel(endXPercent, w).toInt()
            val eyPx = CoordinateUtils.percentToPixel(endYPercent, h).toInt()
            "Start: ${CoordinateUtils.formatPercent(startXPercent)}, ${CoordinateUtils.formatPercent(startYPercent)} (${sxPx}px,${syPx}px)\n" +
                "End  : ${CoordinateUtils.formatPercent(endXPercent)}, ${CoordinateUtils.formatPercent(endYPercent)} (${exPx}px,${eyPx}px)"
        } else {
            val label = if (activePickMode == CoordinatePickMode.EDIT_END_ONLY) "End" else "Posisi"
            "$label: X ${CoordinateUtils.formatPercent(singleXPercent)} (${sxPx}px) · Y ${CoordinateUtils.formatPercent(singleYPercent)} (${syPx}px)"
        }
    }

    private fun singlePointCoordinates(): Pair<Float, Float> {
        return if (activePickMode == CoordinatePickMode.EDIT_END_ONLY) {
            endXPercent to endYPercent
        } else {
            startXPercent to startYPercent
        }
    }

    @SuppressLint("ViewConstructor")
    private inner class CrosshairCanvasView(ctx: Context) : View(ctx) {

        private var activeTarget = DragTarget.START

        private val scrimPaint = Paint().apply {
            color = Color.parseColor("#380F172A")
            style = Paint.Style.FILL
        }

        private val guideLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#88FFFFFF")
            strokeWidth = dp(1).toFloat()
            pathEffect = DashPathEffect(floatArrayOf(12f, 10f), 0f)
        }

        private val swipePathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EE4D2D")
            strokeWidth = dp(3).toFloat()
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(16f, 10f), 0f)
        }

        private val startRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EE4D2D")
            strokeWidth = dp(3).toFloat()
            style = Paint.Style.STROKE
        }

        private val startFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#44EE4D2D")
            style = Paint.Style.FILL
        }

        private val endRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#16A34A")
            strokeWidth = dp(3).toFloat()
            style = Paint.Style.STROKE
        }

        private val endFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4416A34A")
            style = Paint.Style.FILL
        }

        private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CC0F172A")
            style = Paint.Style.FILL
        }

        private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = dp(11).toFloat()
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)

            if (twoPointMode) {
                val sx = CoordinateUtils.percentToPixel(startXPercent, width)
                val sy = CoordinateUtils.percentToPixel(startYPercent, height)
                val ex = CoordinateUtils.percentToPixel(endXPercent, width)
                val ey = CoordinateUtils.percentToPixel(endYPercent, height)
                canvas.drawLine(sx, sy, ex, ey, swipePathPaint)
                drawCrosshair(canvas, sx, sy, startRingPaint, startFillPaint, "START")
                drawCrosshair(canvas, ex, ey, endRingPaint, endFillPaint, "END")
            } else {
                val (singleXPercent, singleYPercent) = singlePointCoordinates()
                val sx = CoordinateUtils.percentToPixel(singleXPercent, width)
                val sy = CoordinateUtils.percentToPixel(singleYPercent, height)
                val tag = if (activePickMode == CoordinatePickMode.EDIT_END_ONLY) "END" else "TARGET"
                val ring = if (activePickMode == CoordinatePickMode.EDIT_END_ONLY) endRingPaint else startRingPaint
                val fill = if (activePickMode == CoordinatePickMode.EDIT_END_ONLY) endFillPaint else startFillPaint
                drawCrosshair(canvas, sx, sy, ring, fill, tag)
            }
        }

        private fun drawCrosshair(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            ringPaint: Paint,
            fillPaint: Paint,
            tag: String
        ) {
            val radius = dp(24).toFloat()
            canvas.drawLine(0f, cy, width.toFloat(), cy, guideLinePaint)
            canvas.drawLine(cx, 0f, cx, height.toFloat(), guideLinePaint)
            canvas.drawCircle(cx, cy, radius, fillPaint)
            canvas.drawCircle(cx, cy, radius, ringPaint)
            canvas.drawCircle(cx, cy, dp(4).toFloat(), ringPaint)

            val pillHalfW = dp(32).toFloat()
            val pillTop = (cy - radius - dp(26)).coerceAtLeast(dp(120).toFloat())
            canvas.drawRoundRect(
                cx - pillHalfW,
                pillTop,
                cx + pillHalfW,
                pillTop + dp(20),
                dp(8).toFloat(),
                dp(8).toFloat(),
                labelBgPaint
            )
            canvas.drawText(tag, cx, pillTop + dp(14), labelTextPaint)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (width <= 0 || height <= 0) return true
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    activeTarget = if (!twoPointMode) {
                        DragTarget.START
                    } else {
                        val sx = CoordinateUtils.percentToPixel(startXPercent, width)
                        val sy = CoordinateUtils.percentToPixel(startYPercent, height)
                        val ex = CoordinateUtils.percentToPixel(endXPercent, width)
                        val ey = CoordinateUtils.percentToPixel(endYPercent, height)
                        val distStart = hypot((event.x - sx).toDouble(), (event.y - sy).toDouble())
                        val distEnd = hypot((event.x - ex).toDouble(), (event.y - ey).toDouble())
                        if (distEnd < distStart) DragTarget.END else DragTarget.START
                    }
                    updateActiveCoordinate(event.x, event.y)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    updateActiveCoordinate(event.x, event.y)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    updateActiveCoordinate(event.x, event.y)
                    return true
                }
            }
            return true
        }

        private fun updateActiveCoordinate(rawX: Float, rawY: Float) {
            val pctX = CoordinateUtils.pixelToPercent(rawX, width)
            val pctY = CoordinateUtils.pixelToPercent(rawY, height)
            if ((activeTarget == DragTarget.END && twoPointMode) ||
                activePickMode == CoordinatePickMode.EDIT_END_ONLY
            ) {
                endXPercent = pctX
                endYPercent = pctY
            } else {
                startXPercent = pctX
                startYPercent = pctY
            }
            syncHudText()
            invalidate()
        }
    }
}
