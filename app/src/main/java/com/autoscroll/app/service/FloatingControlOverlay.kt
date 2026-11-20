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
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.autoscroll.app.data.model.BuilderOverlayStep
import com.autoscroll.app.data.model.CoordinatePickMode
import com.autoscroll.app.data.model.FloatingControlMode
import com.autoscroll.app.data.model.MacroAction
import com.autoscroll.app.data.model.MacroActionType
import com.autoscroll.app.data.model.MacroBuilderState
import com.autoscroll.app.data.model.MacroPlaybackState
import com.autoscroll.app.data.model.PlaybackStatus
import com.autoscroll.app.data.model.ScrollConfig
import com.autoscroll.app.data.model.ScrollRuntimeState
import com.autoscroll.app.util.CoordinateUtils
import kotlin.math.abs

/**
 * Satu-satunya Dynamic Floating Control Window (SYSTEM_ALERT_WINDOW) dengan 3 mode:
 * - AUTO_SCROLL
 * - MACRO_BUILDER (Toolbar, Action Settings, Action List, Stop & Save)
 * - MACRO_PLAYBACK (Play, Pause, Resume, Stop, Progress, Repeat)
 */
class FloatingControlOverlay(
    private val context: Context,
    private val onAutoScrollStart: () -> Unit,
    private val onAutoScrollPauseResume: () -> Unit,
    private val onAutoScrollStop: () -> Unit,
    private val onBuilderAddAction: () -> Unit,
    private val onBuilderRepickCoordinate: (CoordinatePickMode) -> Unit,
    private val onBuilderSelectActionType: (MacroActionType) -> Unit,
    private val onBuilderAdjustDuration: (deltaMs: Long?, exactMs: Long?) -> Unit,
    private val onBuilderAdjustDelay: (deltaMs: Long?, exactMs: Long?) -> Unit,
    private val onBuilderSaveAction: () -> Unit,
    private val onBuilderCancelConfig: () -> Unit,
    private val onBuilderOpenActionList: () -> Unit,
    private val onBuilderCloseActionList: () -> Unit,
    private val onBuilderEditAction: (Int) -> Unit,
    private val onBuilderMoveAction: (Int, Int) -> Unit,
    private val onBuilderDeleteAction: (Int) -> Unit,
    private val onBuilderStopAndSave: () -> Unit,
    private val onPlaybackPlay: () -> Unit,
    private val onPlaybackPauseResume: () -> Unit,
    private val onPlaybackStop: () -> Unit,
    private val onCloseOverlay: () -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var rootView: LinearLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isAttached = false
    private var lastSignature: String = ""

    fun showOrUpdate(
        mode: FloatingControlMode,
        autoScrollConfig: ScrollConfig,
        autoScrollState: ScrollRuntimeState,
        builderState: MacroBuilderState,
        playbackState: MacroPlaybackState
    ) {
        if (!isAttached) {
            attachOverlay()
        }
        val sig = buildSignature(mode, autoScrollConfig, autoScrollState, builderState, playbackState)
        if (sig == lastSignature) return
        lastSignature = sig

        renderDynamicMode(
            mode = mode,
            autoScrollConfig = autoScrollConfig,
            autoScrollState = autoScrollState,
            builderState = builderState,
            playbackState = playbackState
        )
    }

    fun hide() {
        if (!isAttached) return
        val view = rootView ?: return
        runCatching { windowManager.removeView(view) }
        isAttached = false
        rootView = null
        layoutParams = null
        lastSignature = ""
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachOverlay() {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            elevation = dp(12).toFloat()
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.parseColor("#E2E8F0"))
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
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

    private fun buildSignature(
        mode: FloatingControlMode,
        cfg: ScrollConfig,
        auto: ScrollRuntimeState,
        builder: MacroBuilderState,
        playback: MacroPlaybackState
    ): String {
        return when (mode) {
            FloatingControlMode.AUTO_SCROLL ->
                "AUTO:${auto.isEnabledByUser}:${auto.isPaused}:${auto.isTargetAppInForeground}:${auto.remainingSeconds}:${auto.scrollCount}:${cfg.formattedInterval()}"
            FloatingControlMode.MACRO_BUILDER ->
                "BUILDER:${builder.step}:${builder.pickMode}:${builder.editingActionIndex}:${builder.actions.hashCode()}:${builder.draftAction.hashCode()}"
            FloatingControlMode.MACRO_PLAYBACK ->
                "PLAY:${playback.activeMacro?.id}:${playback.activeMacro?.name}:${playback.status}:${playback.currentActionIndex}:${playback.currentRepeat}:${playback.activeMacro?.repeatCount}"
        }
    }

    private fun renderDynamicMode(
        mode: FloatingControlMode,
        autoScrollConfig: ScrollConfig,
        autoScrollState: ScrollRuntimeState,
        builderState: MacroBuilderState,
        playbackState: MacroPlaybackState
    ) {
        val root = rootView ?: return
        root.removeAllViews()

        when (mode) {
            FloatingControlMode.AUTO_SCROLL -> renderAutoScrollToolbar(root, autoScrollConfig, autoScrollState)
            FloatingControlMode.MACRO_BUILDER -> when (builderState.step) {
                BuilderOverlayStep.TOOLBAR,
                BuilderOverlayStep.PICKING_COORDINATE -> renderBuilderToolbar(root, builderState)
                BuilderOverlayStep.CONFIGURING_ACTION -> renderActionConfigurator(root, builderState)
                BuilderOverlayStep.ACTION_LIST -> renderBuilderActionList(root, builderState)
            }
            FloatingControlMode.MACRO_PLAYBACK -> renderPlaybackToolbar(root, playbackState)
        }
    }

    // --- MODE A: AUTO SCROLL ---

    private fun renderAutoScrollToolbar(
        root: LinearLayout,
        autoScrollConfig: ScrollConfig,
        autoScrollState: ScrollRuntimeState
    ) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(createDragHandle())

        val statusLabel = when {
            !autoScrollState.isEnabledByUser -> "Siap · ${autoScrollConfig.formattedInterval()}"
            autoScrollState.isPaused -> "Dijeda · ${autoScrollState.scrollCount}x"
            !autoScrollState.isTargetAppInForeground -> "Tunggu ${autoScrollConfig.targetAppName}"
            else -> "Aktif · ${autoScrollState.remainingSeconds}d (${autoScrollState.scrollCount}x)"
        }
        row.addView(createTitleStack("Auto Scroll", statusLabel))

        if (!autoScrollState.isEnabledByUser) {
            row.addView(createPillButton("Start", "#EE4D2D", Color.WHITE) { onAutoScrollStart() })
            row.addView(createCloseIcon { onCloseOverlay() })
        } else {
            val pauseText = if (autoScrollState.isPaused) "Resume" else "Pause"
            val pauseBg = if (autoScrollState.isPaused) "#16A34A" else "#EE4D2D"
            row.addView(createPillButton(pauseText, pauseBg, Color.WHITE) { onAutoScrollPauseResume() })
            row.addView(createSpace(dp(6)))
            row.addView(createPillButton("Stop", "#FEF2F2", Color.parseColor("#DC2626")) { onAutoScrollStop() })
        }

        root.addView(row)
    }

    // --- MODE B: MACRO BUILDER ---

    private fun renderBuilderToolbar(root: LinearLayout, builderState: MacroBuilderState) {
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(createDragHandle())
        header.addView(
            createTitleStack(
                "Macro Builder",
                "${builderState.actions.size} Action dibuat"
            )
        )
        header.addView(createCloseIcon { onCloseOverlay() })
        root.addView(header)

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        btnRow.addView(createPillButton("+ Tambah Gerakan", "#EE4D2D", Color.WHITE) { onBuilderAddAction() })
        btnRow.addView(createSpace(dp(6)))
        btnRow.addView(
            createPillButton(
                "Daftar (${builderState.actions.size})",
                "#F1F5F9",
                Color.parseColor("#0F172A")
            ) { onBuilderOpenActionList() }
        )
        btnRow.addView(createSpace(dp(6)))
        btnRow.addView(createPillButton("Stop & Save", "#0F172A", Color.WHITE) { onBuilderStopAndSave() })
        root.addView(btnRow)
    }

    private fun renderActionConfigurator(root: LinearLayout, builderState: MacroBuilderState) {
        val action = builderState.draftAction

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(createDragHandle())
        val heading = if (builderState.editingActionIndex != null) {
            "Edit Action #${builderState.editingActionIndex + 1}"
        } else {
            "Atur Action Baru"
        }
        header.addView(createTitleStack(heading, action.type.label))
        root.addView(header)

        // Action Type Selector
        root.addView(createSectionLabel("Jenis Action"))
        val chipScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
        }
        val chipRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        MacroActionType.entries.forEach { typeOpt ->
            val selected = action.type == typeOpt
            chipRow.addView(
                createPillButton(
                    label = typeOpt.label,
                    bgHex = if (selected) "#EE4D2D" else "#F1F5F9",
                    textColor = if (selected) Color.WHITE else Color.parseColor("#334155")
                ) { onBuilderSelectActionType(typeOpt) }
            )
            chipRow.addView(createSpace(dp(5)))
        }
        chipScroll.addView(chipRow)
        root.addView(chipScroll)

        // Coordinate Readout & Repick (hidden only if WAIT)
        if (action.type != MacroActionType.WAIT) {
            root.addView(createSectionLabel("Koordinat Layar (%)"))
            val coordRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val startTxt = TextView(context).apply {
                text = if (action.type == MacroActionType.SWIPE) {
                    "Start: ${CoordinateUtils.formatCoordinatePair(action.startXPercent, action.startYPercent)}"
                } else {
                    "Pos: ${CoordinateUtils.formatCoordinatePair(action.startXPercent, action.startYPercent)}"
                }
                setTextColor(Color.parseColor("#0F172A"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                typeface = Typeface.MONOSPACE
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            coordRow.addView(startTxt)
            coordRow.addView(
                createPillButton(
                    if (action.type == MacroActionType.SWIPE) "Ubah Start" else "Pilih Lokasi",
                    "#FFF3EF",
                    Color.parseColor("#EE4D2D")
                ) {
                    onBuilderRepickCoordinate(
                        if (action.type == MacroActionType.SWIPE) CoordinatePickMode.EDIT_START_ONLY
                        else CoordinatePickMode.SINGLE_POINT
                    )
                }
            )
            root.addView(coordRow)

            if (action.type == MacroActionType.SWIPE) {
                val endRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(5), 0, 0)
                }
                val endTxt = TextView(context).apply {
                    text = "End  : ${CoordinateUtils.formatCoordinatePair(action.endXPercent, action.endYPercent)}"
                    setTextColor(Color.parseColor("#0F172A"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    typeface = Typeface.MONOSPACE
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                endRow.addView(endTxt)
                endRow.addView(
                    createPillButton("Ubah End", "#ECFDF5", Color.parseColor("#16A34A")) {
                        onBuilderRepickCoordinate(CoordinatePickMode.EDIT_END_ONLY)
                    }
                )
                root.addView(endRow)
            }
        }

        // Gesture Duration Stepper
        if (action.type != MacroActionType.WAIT) {
            root.addView(createSectionLabel("Gesture Duration"))
            root.addView(
                createStepperRow(
                    valueLabel = "${action.durationMs} ms",
                    onMinus = { onBuilderAdjustDuration(-50L, null) },
                    onPlus = { onBuilderAdjustDuration(50L, null) },
                    presets = listOf(80L to "80ms", 350L to "350ms", 650L to "650ms"),
                    onSelectPreset = { ms -> onBuilderAdjustDuration(null, ms) }
                )
            )
        }

        // Delay Before Next Action Stepper
        root.addView(createSectionLabel("Delay Before Next Action"))
        root.addView(
            createStepperRow(
                valueLabel = "${action.delayAfterMs} ms",
                onMinus = { onBuilderAdjustDelay(-200L, null) },
                onPlus = { onBuilderAdjustDelay(200L, null) },
                presets = listOf(300L to "0.3s", 800L to "0.8s", 1500L to "1.5s"),
                onSelectPreset = { ms -> onBuilderAdjustDelay(null, ms) }
            )
        )

        val footer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(10), 0, 0)
        }
        footer.addView(createPillButton("Batal", "#F1F5F9", Color.parseColor("#475569")) {
            onBuilderCancelConfig()
        })
        footer.addView(createSpace(dp(8)))
        footer.addView(createPillButton("Simpan Action", "#EE4D2D", Color.WHITE) {
            onBuilderSaveAction()
        })
        root.addView(footer)
    }

    private fun renderBuilderActionList(root: LinearLayout, builderState: MacroBuilderState) {
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(createDragHandle())
        header.addView(
            createTitleStack(
                "Daftar Gerakan",
                "${builderState.actions.size} Action tersimpan"
            )
        )
        header.addView(createPillButton("Tutup", "#F1F5F9", Color.parseColor("#475569")) {
            onBuilderCloseActionList()
        })
        root.addView(header)

        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                dp(300),
                dp(210)
            ).apply {
                topMargin = dp(8)
            }
        }
        val listCol = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        if (builderState.actions.isEmpty()) {
            val emptyTxt = TextView(context).apply {
                text = "Belum ada gerakan. Tekan Tambah Gerakan untuk memilih titik di layar."
                setTextColor(Color.parseColor("#64748B"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding(dp(4), dp(12), dp(4), dp(12))
            }
            listCol.addView(emptyTxt)
        } else {
            builderState.actions.forEachIndexed { idx, act ->
                listCol.addView(createBuilderActionItem(idx, builderState.actions.lastIndex, act))
                listCol.addView(createVerticalSpace(dp(6)))
            }
        }
        scroll.addView(listCol)
        root.addView(scroll)

        val footer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        footer.addView(createPillButton("+ Tambah Gerakan", "#FFF3EF", Color.parseColor("#EE4D2D")) {
            onBuilderAddAction()
        })
        footer.addView(createSpace(dp(6)))
        footer.addView(createPillButton("Stop & Save", "#EE4D2D", Color.WHITE) {
            onBuilderStopAndSave()
        })
        root.addView(footer)
    }

    private fun createBuilderActionItem(index: Int, lastIdx: Int, action: MacroAction): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F8FAFC"))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.parseColor("#E2E8F0"))
            }

            addView(TextView(context).apply {
                text = "${index + 1}. ${action.note.ifBlank { CoordinateUtils.buildSummaryNote(action) }}"
                setTextColor(Color.parseColor("#0F172A"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            })
            addView(TextView(context).apply {
                text = "Durasi ${action.durationMs}ms · Delay ${action.delayAfterMs}ms"
                setTextColor(Color.parseColor("#64748B"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setPadding(0, dp(2), 0, dp(6))
            })

            val rowBtns = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            if (index > 0) {
                rowBtns.addView(createPillButton("↑", "#E2E8F0", Color.parseColor("#0F172A")) {
                    onBuilderMoveAction(index, index - 1)
                })
                rowBtns.addView(createSpace(dp(4)))
            }
            if (index < lastIdx) {
                rowBtns.addView(createPillButton("↓", "#E2E8F0", Color.parseColor("#0F172A")) {
                    onBuilderMoveAction(index, index + 1)
                })
                rowBtns.addView(createSpace(dp(4)))
            }
            rowBtns.addView(createPillButton("Edit", "#FFF3EF", Color.parseColor("#EE4D2D")) {
                onBuilderEditAction(index)
            })
            rowBtns.addView(createSpace(dp(4)))
            rowBtns.addView(createPillButton("Hapus", "#FEF2F2", Color.parseColor("#DC2626")) {
                onBuilderDeleteAction(index)
            })
            addView(rowBtns)
        }
    }

    // --- MODE C: MACRO PLAYBACK ---

    private fun renderPlaybackToolbar(root: LinearLayout, playbackState: MacroPlaybackState) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(createDragHandle())

        val macroName = playbackState.activeMacro?.name ?: "Macro"
        val total = playbackState.totalActions
        val currentStep = playbackState.currentActionIndex.coerceAtMost(total)
        val repeatTotal = playbackState.activeMacro?.repeatCount ?: 1
        val repeatSuffix = if (repeatTotal > 1) " · U${playbackState.currentRepeat}/$repeatTotal" else ""
        val subtitle = "${playbackState.status.label} · $currentStep/$total Aksi$repeatSuffix"

        row.addView(createTitleStack(macroName.take(18), subtitle))

        when (playbackState.status) {
            PlaybackStatus.IDLE, PlaybackStatus.COMPLETED, PlaybackStatus.ERROR -> {
                val playLabel = if (playbackState.status == PlaybackStatus.COMPLETED) "Play Ulang" else "Play"
                row.addView(createPillButton(playLabel, "#EE4D2D", Color.WHITE) { onPlaybackPlay() })
                row.addView(createCloseIcon { onCloseOverlay() })
            }
            PlaybackStatus.PLAYING -> {
                row.addView(createPillButton("Pause", "#EE4D2D", Color.WHITE) { onPlaybackPauseResume() })
                row.addView(createSpace(dp(6)))
                row.addView(createPillButton("Stop", "#FEF2F2", Color.parseColor("#DC2626")) { onPlaybackStop() })
            }
            PlaybackStatus.PAUSED -> {
                row.addView(createPillButton("Resume", "#16A34A", Color.WHITE) { onPlaybackPauseResume() })
                row.addView(createSpace(dp(6)))
                row.addView(createPillButton("Stop", "#FEF2F2", Color.parseColor("#DC2626")) { onPlaybackStop() })
            }
        }

        root.addView(row)
    }

    // --- UI Helper Views ---

    private fun createDragHandle(): TextView {
        return TextView(context).apply {
            text = "⋮⋮"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, 0, dp(8), 0)
        }
    }

    private fun createTitleStack(titleText: String, subtitleText: String): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, dp(10), 0)
            addView(TextView(context).apply {
                text = titleText
                setTextColor(Color.parseColor("#0F172A"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                maxLines = 1
            })
            addView(TextView(context).apply {
                text = subtitleText
                setTextColor(Color.parseColor("#64748B"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                maxLines = 1
            })
        }
    }

    private fun createSectionLabel(textStr: String): TextView {
        return TextView(context).apply {
            text = textStr
            setTextColor(Color.parseColor("#475569"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(4))
        }
    }

    private fun createStepperRow(
        valueLabel: String,
        onMinus: () -> Unit,
        onPlus: () -> Unit,
        presets: List<Pair<Long, String>>,
        onSelectPreset: (Long) -> Unit
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(createPillButton("-", "#F1F5F9", Color.parseColor("#0F172A"), onMinus))
            addView(TextView(context).apply {
                text = valueLabel
                setTextColor(Color.parseColor("#0F172A"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                typeface = Typeface.MONOSPACE
                setPadding(dp(8), 0, dp(8), 0)
            })
            addView(createPillButton("+", "#F1F5F9", Color.parseColor("#0F172A"), onPlus))
            addView(createSpace(dp(8)))
            presets.forEach { (ms, label) ->
                addView(createPillButton(label, "#FFF3EF", Color.parseColor("#EE4D2D")) {
                    onSelectPreset(ms)
                })
                addView(createSpace(dp(4)))
            }
        }
    }

    private fun createPillButton(
        label: String,
        bgHex: String,
        textColor: Int,
        onClick: () -> Unit
    ): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor(bgHex))
                cornerRadius = dp(12).toFloat()
            }
            setOnClickListener { onClick() }
        }
    }

    private fun createCloseIcon(onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = "✕"
            setTextColor(Color.parseColor("#64748B"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(8), dp(6), dp(4), dp(6))
            setOnClickListener { onClick() }
        }
    }

    private fun createSpace(widthPx: Int): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(widthPx, 1)
        }
    }

    private fun createVerticalSpace(heightPx: Int): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(1, heightPx)
        }
    }
}
