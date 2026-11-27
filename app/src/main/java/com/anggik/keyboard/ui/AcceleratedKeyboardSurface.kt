package com.anggik.keyboard.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RenderNode
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import com.anggik.keyboard.model.KeySpec
import com.anggik.keyboard.model.KeyType
import com.anggik.keyboard.model.KeyboardLayer
import com.anggik.keyboard.model.KeyboardLayouts
import com.anggik.keyboard.model.ShiftState
import java.util.Locale
import kotlin.math.abs

class AcceleratedKeyboardSurface(context: Context) : View(context) {

    interface Listener {
        fun onCommitText(text: CharSequence)
        fun onBackspace()
        fun onDeleteWord()
        fun onEnter()
        fun onCursorMove(keyCode: Int)
        fun onSwitchLanguage()
        fun onVoiceInput()
        fun onPasteText(text: String)
        fun onSelectAll()
        fun onCopy()
        fun onPasteClipboard()
        fun getClipboardPreview(): String?
    }

    var listener: Listener? = null

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    // Exact colors extracted from reference UI (3608.jpg)
    private val colorBg = 0xFFEFF2F0.toInt()
    private val colorKeyWhite = 0xFFFFFFFF.toInt()
    private val colorKeyMint = 0xFFD1E4D4.toInt()
    private val colorKeyEnter = 0xFF75C18A.toInt()
    private val colorKeyPressed = 0xFFC2D6C6.toInt()
    private val colorKeyShadow = 0xFFD2D7D4.toInt()
    private val colorDivider = 0xFFDCE1DE.toInt()
    private val colorTextPrimary = 0xFF121413.toInt()
    private val colorTextHint = 0xFF4B504D.toInt()
    private val colorTextMuted = 0xFF9AA09D.toInt()
    private val colorMicDisabled = 0xFFB0B5B2.toInt()

    private val toolbarHeightPx = 42f * density
    private val keyHeightPx = 47f * density
    private val rowGapPx = 7f * density
    private val colGapPx = 5.5f * density
    private val outerPadXPx = 4f * density
    private val outerPadTopPx = 6f * density
    private val outerPadBottomPx = 6f * density
    private val keyCornerPx = 6.5f * density
    private val shadowOffsetPx = 1.5f * density

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorBg }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorDivider
        strokeWidth = 1f * density
    }
    private val keyFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorKeyShadow }

    private val mainTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorTextPrimary
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    private val actionLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorTextPrimary
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val hintTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorTextHint
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val spaceTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorTextPrimary
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorTextPrimary
        style = Paint.Style.STROKE
        strokeWidth = 1.85f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val iconFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorTextPrimary
        style = Paint.Style.FILL
    }

    private var currentLayer = KeyboardLayer.QWERTY
    private var shiftState = ShiftState.OFF
    private var languageLabel = "English"
    private var imeAction = EditorInfo.IME_ACTION_UNSPECIFIED

    private enum class UtilityPanel { NONE, CLIPBOARD, CURSOR }
    private var activeUtilityPanel = UtilityPanel.NONE

    private data class KeyBound(val row: Int, val col: Int, val spec: KeySpec, val rect: RectF)
    private val keyBounds = ArrayList<KeyBound>(44)
    private val toolbarBounds = Array(4) { RectF() }
    private val utilityChipBounds = Array(6) { RectF() }
    private val tempRect = RectF()
    private val vectorPath = Path()

    private var staticNode: Any? = null
    private var fallbackBitmap: Bitmap? = null
    private var isStaticDirty = true

    // Multi-touch rollover tracking (up to 5 pointers, zero heap allocation)
    private val pointerIds = IntArray(5) { MotionEvent.INVALID_POINTER_ID }
    private val pointerKeys = Array<KeyBound?>(5) { null }
    private val pointerDownX = FloatArray(5)
    private val pointerLastDragX = FloatArray(5)
    private val pointerLongTriggered = BooleanArray(5)

    private val handler = Handler(Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null
    private var longPressRunnable: Runnable? = null

    init {
        isHapticFeedbackEnabled = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val totalHeight = (toolbarHeightPx + outerPadTopPx + (4 * keyHeightPx) + (3 * rowGapPx) + outerPadBottomPx).toInt()
        setMeasuredDimension(w, totalHeight)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildGeometry(w.toFloat())
        invalidateStaticLayer()
    }

    fun setLanguageName(name: String) {
        if (languageLabel != name) {
            languageLabel = name
            invalidateStaticLayer()
        }
    }

    fun setEditorAction(action: Int) {
        if (imeAction != action) {
            imeAction = action
            invalidateStaticLayer()
        }
    }

    fun setAutoShift(shouldShift: Boolean) {
        if (shiftState == ShiftState.CAPS_LOCK) return
        val target = if (shouldShift) ShiftState.SHIFTED else ShiftState.OFF
        if (shiftState != target) {
            shiftState = target
            invalidateStaticLayer()
        }
    }

    private fun invalidateStaticLayer() {
        isStaticDirty = true
        invalidate()
    }

    private fun activeRows(): List<List<KeySpec>> = when (currentLayer) {
        KeyboardLayer.QWERTY -> KeyboardLayouts.qwertyRows
        KeyboardLayer.SYMBOLS -> KeyboardLayouts.symbolsRows
        KeyboardLayer.SYMBOLS_ALT -> KeyboardLayouts.symbolsAltRows
        KeyboardLayer.EMOJI -> KeyboardLayouts.emojiRows
    }

    private fun rebuildGeometry(totalWidth: Float) {
        if (totalWidth <= 0f) return
        keyBounds.clear()

        // Toolbar 4 slots: [0]=Grid, [1]=Clipboard, [2]=Mic, [3]=Search/Cursor
        val slotW = totalWidth / 6f
        toolbarBounds[0].set(0f, 0f, slotW, toolbarHeightPx)
        toolbarBounds[1].set(slotW, 0f, slotW * 2f, toolbarHeightPx)
        toolbarBounds[2].set(slotW * 2f, 0f, slotW * 3f, toolbarHeightPx)
        toolbarBounds[3].set(totalWidth - slotW, 0f, totalWidth, toolbarHeightPx)

        // Utility panel chips (6 slots across toolbar width)
        val chipW = (totalWidth - (7 * colGapPx)) / 6f
        for (i in 0 until 6) {
            val left = colGapPx + i * (chipW + colGapPx)
            utilityChipBounds[i].set(left, 5f * density, left + chipW, toolbarHeightPx - 5f * density)
        }

        val rows = activeRows()
        val usableWidth = totalWidth - (2f * outerPadXPx)
        var y = toolbarHeightPx + outerPadTopPx

        for (r in rows.indices) {
            val rowKeys = rows[r]
            val isRow2Qwerty = (currentLayer == KeyboardLayer.QWERTY && r == 1)
            val totalWeight = rowKeys.sumOf { it.weight.toDouble() }.toFloat() + if (isRow2Qwerty) 1.0f else 0f
            val totalGaps = (rowKeys.size - 1) * colGapPx
            val unitWidth = (usableWidth - totalGaps) / totalWeight
            var x = outerPadXPx + if (isRow2Qwerty) unitWidth * 0.5f else 0f

            for (c in rowKeys.indices) {
                val spec = rowKeys[c]
                val kw = unitWidth * spec.weight
                keyBounds.add(KeyBound(r, c, spec, RectF(x, y, x + kw, y + keyHeightPx)))
                x += kw + colGapPx
            }
            y += keyHeightPx + rowGapPx
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && canvas.isHardwareAccelerated) {
            val node = (staticNode as? RenderNode)
                ?: RenderNode("AnggiStaticSurface").also { staticNode = it }
            if (isStaticDirty || !node.hasDisplayList()) {
                node.setPosition(0, 0, width, height)
                val rec = node.beginRecording(width, height)
                drawStaticScene(rec)
                node.endRecording()
                isStaticDirty = false
            }
            canvas.drawRenderNode(node)
        } else {
            if (isStaticDirty || fallbackBitmap == null ||
                fallbackBitmap?.width != width || fallbackBitmap?.height != height
            ) {
                fallbackBitmap?.recycle()
                fallbackBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                    drawStaticScene(Canvas(it))
                }
                isStaticDirty = false
            }
            fallbackBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        }

        drawActivePressOverlay(canvas)
    }

    private fun drawStaticScene(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        drawToolbar(canvas)

        mainTextPaint.textSize = 23f * density
        actionLabelPaint.textSize = 15.5f * density
        hintTextPaint.textSize = 10.5f * density
        spaceTextPaint.textSize = 14f * density

        for (kb in keyBounds) {
            drawKeyCap(canvas, kb, isPressed = false)
        }
    }

    private fun drawActivePressOverlay(canvas: Canvas) {
        for (i in pointerKeys.indices) {
            val kb = pointerKeys[i] ?: continue
            drawKeyCap(canvas, kb, isPressed = true)
        }
    }

    private fun drawToolbar(canvas: Canvas) {
        canvas.drawLine(0f, 0.5f * density, width.toFloat(), 0.5f * density, dividerPaint)
        canvas.drawLine(0f, toolbarHeightPx, width.toFloat(), toolbarHeightPx, dividerPaint)

        if (activeUtilityPanel == UtilityPanel.CURSOR) {
            val labels = arrayOf("◄", "▲", "▼", "►", "Copy", "✕")
            for (i in 0 until 6) {
                val r = utilityChipBounds[i]
                keyFillPaint.color = if (i == 5) colorKeyMint else colorKeyWhite
                canvas.drawRoundRect(r, 5f * density, 5f * density, keyFillPaint)
                val yOffset = (actionLabelPaint.descent() + actionLabelPaint.ascent()) / 2f
                canvas.drawText(labels[i], r.centerX(), r.centerY() - yOffset, actionLabelPaint)
            }
            return
        }

        if (activeUtilityPanel == UtilityPanel.CLIPBOARD) {
            val clip = listener?.getClipboardPreview()?.replace('\n', ' ')?.take(28) ?: "Clipboard kosong"
            val pasteRect = RectF(colGapPx, 5f * density, width - 54f * density, toolbarHeightPx - 5f * density)
            val closeRect = RectF(width - 48f * density, 5f * density, width - colGapPx, toolbarHeightPx - 5f * density)
            keyFillPaint.color = colorKeyWhite
            canvas.drawRoundRect(pasteRect, 5f * density, 5f * density, keyFillPaint)
            keyFillPaint.color = colorKeyMint
            canvas.drawRoundRect(closeRect, 5f * density, 5f * density, keyFillPaint)
            val yOff = (spaceTextPaint.descent() + spaceTextPaint.ascent()) / 2f
            canvas.drawText(clip, pasteRect.centerX(), pasteRect.centerY() - yOff, spaceTextPaint)
            canvas.drawText("✕", closeRect.centerX(), closeRect.centerY() - yOff, actionLabelPaint)
            return
        }

        // 1. 4-square dashboard grid icon
        run {
            val cx = toolbarBounds[0].centerX()
            val cy = toolbarBounds[0].centerY()
            val s = 6.5f * density
            val gap = 2.5f * density
            iconPaint.color = colorTextPrimary
            canvas.drawRoundRect(cx - s - gap / 2, cy - s - gap / 2, cx - gap / 2, cy - gap / 2, 1.5f * density, 1.5f * density, iconPaint)
            canvas.drawRoundRect(cx + gap / 2, cy - s - gap / 2, cx + s + gap / 2, cy - gap / 2, 1.5f * density, 1.5f * density, iconPaint)
            canvas.drawRoundRect(cx - s - gap / 2, cy + gap / 2, cx - gap / 2, cy + s + gap / 2, 1.5f * density, 1.5f * density, iconPaint)
            canvas.drawRoundRect(cx + gap / 2, cy + gap / 2, cx + s + gap / 2, cy + s + gap / 2, 1.5f * density, 1.5f * density, iconPaint)
        }

        // 2. Clipboard icon
        run {
            val cx = toolbarBounds[1].centerX()
            val cy = toolbarBounds[1].centerY()
            val w = 7f * density
            val h = 8.5f * density
            iconPaint.color = colorTextPrimary
            canvas.drawRoundRect(cx - w, cy - h + 1.5f * density, cx + w, cy + h, 2f * density, 2f * density, iconPaint)
            canvas.drawRoundRect(cx - 3.5f * density, cy - h - 1f * density, cx + 3.5f * density, cy - h + 2.5f * density, 1f * density, 1f * density, iconPaint)
            canvas.drawLine(cx - 3.5f * density, cy, cx + 3.5f * density, cy, iconPaint)
            canvas.drawLine(cx - 3.5f * density, cy + 3.5f * density, cx + 1.5f * density, cy + 3.5f * density, iconPaint)
        }

        // 3. Mic icon
        run {
            val cx = toolbarBounds[2].centerX()
            val cy = toolbarBounds[2].centerY()
            iconPaint.color = colorMicDisabled
            canvas.drawRoundRect(cx - 3.2f * density, cy - 8f * density, cx + 3.2f * density, cy + 1f * density, 3.2f * density, 3.2f * density, iconPaint)
            canvas.drawArc(cx - 6.5f * density, cy - 4f * density, cx + 6.5f * density, cy + 5.5f * density, 0f, 180f, false, iconPaint)
            canvas.drawLine(cx, cy + 5.5f * density, cx, cy + 8.5f * density, iconPaint)
        }

        // 4. Right Search/Cursor magnifier icon
        run {
            val cx = toolbarBounds[3].centerX() - 1.5f * density
            val cy = toolbarBounds[3].centerY() - 1.5f * density
            val r = 6.5f * density
            iconPaint.color = colorTextPrimary
            canvas.drawCircle(cx, cy, r, iconPaint)
            canvas.drawLine(cx + r * 0.72f, cy + r * 0.72f, cx + r + 4.5f * density, cy + r + 4.5f * density, iconPaint)
        }
    }

    private fun drawKeyCap(canvas: Canvas, kb: KeyBound, isPressed: Boolean) {
        val rect = kb.rect
        val spec = kb.spec

        // Shadow base
        tempRect.set(rect.left, rect.top + shadowOffsetPx, rect.right, rect.bottom + shadowOffsetPx)
        canvas.drawRoundRect(tempRect, keyCornerPx, keyCornerPx, keyShadowPaint)

        // Keycap fill
        keyFillPaint.color = when {
            isPressed -> colorKeyPressed
            spec.type == KeyType.ENTER -> colorKeyEnter
            spec.type == KeyType.SHIFT ||
                spec.type == KeyType.BACKSPACE ||
                spec.type == KeyType.MODE_SYMBOLS ||
                spec.type == KeyType.MODE_SYMBOLS_ALT ||
                spec.type == KeyType.MODE_EMOJI -> colorKeyMint
            else -> colorKeyWhite
        }
        canvas.drawRoundRect(rect, keyCornerPx, keyCornerPx, keyFillPaint)

        // Top-right secondary hint
        if (spec.hint.isNotEmpty()) {
            hintTextPaint.color = colorTextHint
            canvas.drawText(
                spec.hint,
                rect.right - 4.5f * density,
                rect.top + 12.5f * density,
                hintTextPaint
            )
        }

        val cx = rect.centerX()
        val cy = rect.centerY()

        when (spec.type) {
            KeyType.CHAR -> {
                val label = if (currentLayer == KeyboardLayer.QWERTY && shiftState != ShiftState.OFF) {
                    spec.label.uppercase(Locale.ROOT)
                } else {
                    spec.label
                }
                val yOff = (mainTextPaint.descent() + mainTextPaint.ascent()) / 2f
                canvas.drawText(label, cx, cy - yOff, mainTextPaint)
            }
            KeyType.COMMA -> {
                val yOff = (mainTextPaint.descent() + mainTextPaint.ascent()) / 2f
                canvas.drawText(",", cx, cy - yOff + 2f * density, mainTextPaint)
                // Mini mic icon at top-right of comma
                val mx = rect.right - 7f * density
                val my = rect.top + 8f * density
                iconPaint.color = colorTextHint
                iconPaint.strokeWidth = 1.25f * density
                canvas.drawRoundRect(mx - 1.8f * density, my - 4.5f * density, mx + 1.8f * density, my + 0.5f * density, 1.8f * density, 1.8f * density, iconPaint)
                canvas.drawArc(mx - 3.6f * density, my - 2f * density, mx + 3.6f * density, my + 3f * density, 0f, 180f, false, iconPaint)
                iconPaint.strokeWidth = 1.85f * density
            }
            KeyType.PERIOD -> {
                val yOff = (mainTextPaint.descent() + mainTextPaint.ascent()) / 2f
                canvas.drawText(".", cx, cy - yOff + 2f * density, mainTextPaint)
            }
            KeyType.MODE_SYMBOLS, KeyType.MODE_SYMBOLS_ALT -> {
                val yOff = (actionLabelPaint.descent() + actionLabelPaint.ascent()) / 2f
                canvas.drawText(spec.label, cx, cy - yOff, actionLabelPaint)
            }
            KeyType.MODE_EMOJI -> {
                if (spec.label == "ABC") {
                    val yOff = (actionLabelPaint.descent() + actionLabelPaint.ascent()) / 2f
                    canvas.drawText("ABC", cx, cy - yOff, actionLabelPaint)
                } else {
                    iconPaint.color = colorTextPrimary
                    val r = 8.5f * density
                    canvas.drawCircle(cx, cy, r, iconPaint)
                    iconFillPaint.color = colorTextPrimary
                    canvas.drawCircle(cx - 2.8f * density, cy - 2.2f * density, 1.2f * density, iconFillPaint)
                    canvas.drawCircle(cx + 2.8f * density, cy - 2.2f * density, 1.2f * density, iconFillPaint)
                    canvas.drawArc(cx - 4.5f * density, cy - 2.5f * density, cx + 4.5f * density, cy + 4.8f * density, 15f, 150f, false, iconPaint)
                }
            }
            KeyType.SHIFT -> {
                iconPaint.color = colorTextPrimary
                vectorPath.reset()
                val w = 8.5f * density
                val h = 9f * density
                vectorPath.moveTo(cx, cy - h)
                vectorPath.lineTo(cx + w, cy + 0.5f * density)
                vectorPath.lineTo(cx + 3.8f * density, cy + 0.5f * density)
                vectorPath.lineTo(cx + 3.8f * density, cy + h)
                vectorPath.lineTo(cx - 3.8f * density, cy + h)
                vectorPath.lineTo(cx - 3.8f * density, cy + 0.5f * density)
                vectorPath.lineTo(cx - w, cy + 0.5f * density)
                vectorPath.close()
                if (shiftState == ShiftState.OFF) {
                    canvas.drawPath(vectorPath, iconPaint)
                } else {
                    iconFillPaint.color = colorTextPrimary
                    canvas.drawPath(vectorPath, iconFillPaint)
                    if (shiftState == ShiftState.CAPS_LOCK) {
                        canvas.drawLine(cx - 5f * density, cy + h + 3f * density, cx + 5f * density, cy + h + 3f * density, iconPaint)
                    }
                }
            }
            KeyType.BACKSPACE -> {
                iconPaint.color = colorTextPrimary
                vectorPath.reset()
                val left = cx - 9.5f * density
                val right = cx + 8.5f * density
                val top = cy - 6.5f * density
                val bot = cy + 6.5f * density
                vectorPath.moveTo(left, cy)
                vectorPath.lineTo(left + 5.5f * density, top)
                vectorPath.lineTo(right, top)
                vectorPath.lineTo(right, bot)
                vectorPath.lineTo(left + 5.5f * density, bot)
                vectorPath.close()
                canvas.drawPath(vectorPath, iconPaint)
                val xOff = 2.2f * density
                val xSize = 2.6f * density
                canvas.drawLine(cx + xOff - xSize, cy - xSize, cx + xOff + xSize, cy + xSize, iconPaint)
                canvas.drawLine(cx + xOff + xSize, cy - xSize, cx + xOff - xSize, cy + xSize, iconPaint)
            }
            KeyType.SPACE -> {
                val yOff = (spaceTextPaint.descent() + spaceTextPaint.ascent()) / 2f
                spaceTextPaint.color = colorTextPrimary
                canvas.drawText(languageLabel, cx, cy - yOff, spaceTextPaint)

                val textHalf = spaceTextPaint.measureText(languageLabel) / 2f
                iconFillPaint.color = colorTextMuted
                // Left chevron
                vectorPath.reset()
                val lx = cx - textHalf - 11f * density
                vectorPath.moveTo(lx - 3.5f * density, cy)
                vectorPath.lineTo(lx + 1.5f * density, cy - 3.5f * density)
                vectorPath.lineTo(lx + 1.5f * density, cy + 3.5f * density)
                vectorPath.close()
                canvas.drawPath(vectorPath, iconFillPaint)

                // Right chevron
                vectorPath.reset()
                val rx = cx + textHalf + 11f * density
                vectorPath.moveTo(rx + 3.5f * density, cy)
                vectorPath.lineTo(rx - 1.5f * density, cy - 3.5f * density)
                vectorPath.lineTo(rx - 1.5f * density, cy + 3.5f * density)
                vectorPath.close()
                canvas.drawPath(vectorPath, iconFillPaint)

                // Globe icon at top-right of Spacebar
                val gx = rect.right - 9f * density
                val gy = rect.top + 8.5f * density
                val gr = 4.8f * density
                iconPaint.color = colorTextMuted
                iconPaint.strokeWidth = 1.15f * density
                canvas.drawCircle(gx, gy, gr, iconPaint)
                canvas.drawOval(gx - gr * 0.45f, gy - gr, gx + gr * 0.45f, gy + gr, iconPaint)
                canvas.drawLine(gx - gr, gy, gx + gr, gy, iconPaint)
                iconPaint.strokeWidth = 1.85f * density
            }
            KeyType.ENTER -> {
                iconPaint.color = colorTextPrimary
                when (imeAction) {
                    EditorInfo.IME_ACTION_SEARCH -> {
                        val r = 6.2f * density
                        val sx = cx - 1.5f * density
                        val sy = cy - 1.5f * density
                        canvas.drawCircle(sx, sy, r, iconPaint)
                        canvas.drawLine(sx + r * 0.72f, sy + r * 0.72f, sx + r + 4.2f * density, sy + r + 4.2f * density, iconPaint)
                    }
                    EditorInfo.IME_ACTION_DONE -> {
                        vectorPath.reset()
                        vectorPath.moveTo(cx - 7f * density, cy)
                        vectorPath.lineTo(cx - 2f * density, cy + 5.5f * density)
                        vectorPath.lineTo(cx + 8f * density, cy - 5.5f * density)
                        canvas.drawPath(vectorPath, iconPaint)
                    }
                    EditorInfo.IME_ACTION_NEXT, EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_SEND -> {
                        canvas.drawLine(cx - 8f * density, cy, cx + 8f * density, cy, iconPaint)
                        canvas.drawLine(cx + 2.5f * density, cy - 5.5f * density, cx + 8f * density, cy, iconPaint)
                        canvas.drawLine(cx + 2.5f * density, cy + 5.5f * density, cx + 8f * density, cy, iconPaint)
                    }
                    else -> {
                        // Return arrow
                        vectorPath.reset()
                        vectorPath.moveTo(cx + 7f * density, cy - 5f * density)
                        vectorPath.lineTo(cx + 7f * density, cy + 2f * density)
                        vectorPath.lineTo(cx - 7f * density, cy + 2f * density)
                        canvas.drawPath(vectorPath, iconPaint)
                        canvas.drawLine(cx - 2.5f * density, cy - 2.5f * density, cx - 7f * density, cy + 2f * density, iconPaint)
                        canvas.drawLine(cx - 2.5f * density, cy + 6.5f * density, cx - 7f * density, cy + 2f * density, iconPaint)
                    }
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val idx = event.actionIndex
        val pid = event.getPointerId(idx)
        val x = event.getX(idx)
        val y = event.getY(idx)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (y < toolbarHeightPx) {
                    handleToolbarTap(x)
                    return true
                }
                // Rollover flush: commit earlier pressed character key immediately
                flushRolloverPointers(exceptPid = pid)
                val hit = findKeyAt(x, y)
                val slot = allocPointerSlot(pid)
                if (slot != -1 && hit != null) {
                    pointerKeys[slot] = hit
                    pointerDownX[slot] = x
                    pointerLastDragX[slot] = x
                    pointerLongTriggered[slot] = false
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    scheduleLongOrRepeat(slot, hit)
                    invalidate()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (p in 0 until event.pointerCount) {
                    val mPid = event.getPointerId(p)
                    val slot = findSlotByPid(mPid)
                    if (slot == -1) continue
                    val kb = pointerKeys[slot] ?: continue
                    val curX = event.getX(p)
                    if (kb.spec.type == KeyType.SPACE) {
                        val delta = curX - pointerLastDragX[slot]
                        val stepPx = 11f * density
                        if (abs(delta) >= stepPx) {
                            val steps = (delta / stepPx).toInt()
                            val code = if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
                            repeat(abs(steps)) { listener?.onCursorMove(code) }
                            pointerLastDragX[slot] += steps * stepPx
                            pointerLongTriggered[slot] = true
                        }
                    } else if (kb.spec.type == KeyType.BACKSPACE) {
                        if (pointerDownX[slot] - curX > touchSlop * 2.5f && !pointerLongTriggered[slot]) {
                            cancelTimers()
                            pointerLongTriggered[slot] = true
                            listener?.onDeleteWord()
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                cancelTimers()
                val slot = findSlotByPid(pid)
                if (slot != -1) {
                    val kb = pointerKeys[slot]
                    val wasLong = pointerLongTriggered[slot]
                    releaseSlot(slot)
                    if (kb != null && !wasLong) {
                        executeKeyAction(kb.spec)
                    }
                    invalidate()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelTimers()
                for (i in pointerIds.indices) releaseSlot(i)
                invalidate()
            }
        }
        return true
    }

    private fun handleToolbarTap(x: Float) {
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (activeUtilityPanel == UtilityPanel.CURSOR) {
            for (i in 0 until 6) {
                if (utilityChipBounds[i].contains(x, toolbarHeightPx / 2f)) {
                    when (i) {
                        0 -> listener?.onCursorMove(KeyEvent.KEYCODE_DPAD_LEFT)
                        1 -> listener?.onCursorMove(KeyEvent.KEYCODE_DPAD_UP)
                        2 -> listener?.onCursorMove(KeyEvent.KEYCODE_DPAD_DOWN)
                        3 -> listener?.onCursorMove(KeyEvent.KEYCODE_DPAD_RIGHT)
                        4 -> listener?.onCopy()
                        5 -> {
                            activeUtilityPanel = UtilityPanel.NONE
                            invalidateStaticLayer()
                        }
                    }
                    return
                }
            }
            return
        }

        if (activeUtilityPanel == UtilityPanel.CLIPBOARD) {
            if (x > width - 50f * density) {
                activeUtilityPanel = UtilityPanel.NONE
            } else {
                listener?.onPasteClipboard()
                activeUtilityPanel = UtilityPanel.NONE
            }
            invalidateStaticLayer()
            return
        }

        when {
            toolbarBounds[0].contains(x, toolbarHeightPx / 2f) -> {
                currentLayer = if (currentLayer == KeyboardLayer.QWERTY) KeyboardLayer.SYMBOLS else KeyboardLayer.QWERTY
                rebuildGeometry(width.toFloat())
                invalidateStaticLayer()
            }
            toolbarBounds[1].contains(x, toolbarHeightPx / 2f) -> {
                activeUtilityPanel = UtilityPanel.CLIPBOARD
                invalidateStaticLayer()
            }
            toolbarBounds[2].contains(x, toolbarHeightPx / 2f) -> {
                listener?.onVoiceInput()
            }
            toolbarBounds[3].contains(x, toolbarHeightPx / 2f) -> {
                activeUtilityPanel = UtilityPanel.CURSOR
                invalidateStaticLayer()
            }
        }
    }

    private fun executeKeyAction(spec: KeySpec) {
        when (spec.type) {
            KeyType.CHAR -> {
                val out = if (currentLayer == KeyboardLayer.QWERTY && shiftState != ShiftState.OFF) {
                    spec.label.uppercase(Locale.ROOT)
                } else {
                    spec.label
                }
                listener?.onCommitText(out)
                if (shiftState == ShiftState.SHIFTED) {
                    shiftState = ShiftState.OFF
                    invalidateStaticLayer()
                }
            }
            KeyType.COMMA -> listener?.onCommitText(",")
            KeyType.PERIOD -> listener?.onCommitText(".")
            KeyType.SPACE -> listener?.onCommitText(" ")
            KeyType.BACKSPACE -> listener?.onBackspace()
            KeyType.ENTER -> listener?.onEnter()
            KeyType.SHIFT -> {
                shiftState = when (shiftState) {
                    ShiftState.OFF -> ShiftState.SHIFTED
                    ShiftState.SHIFTED -> ShiftState.CAPS_LOCK
                    ShiftState.CAPS_LOCK -> ShiftState.OFF
                }
                invalidateStaticLayer()
            }
            KeyType.MODE_SYMBOLS -> {
                currentLayer = if (currentLayer == KeyboardLayer.QWERTY) KeyboardLayer.SYMBOLS else KeyboardLayer.QWERTY
                rebuildGeometry(width.toFloat())
                invalidateStaticLayer()
            }
            KeyType.MODE_SYMBOLS_ALT -> {
                currentLayer = KeyboardLayer.SYMBOLS_ALT
                rebuildGeometry(width.toFloat())
                invalidateStaticLayer()
            }
            KeyType.MODE_EMOJI -> {
                currentLayer = if (currentLayer == KeyboardLayer.EMOJI) KeyboardLayer.QWERTY else KeyboardLayer.EMOJI
                rebuildGeometry(width.toFloat())
                invalidateStaticLayer()
            }
        }
    }

    private fun scheduleLongOrRepeat(slot: Int, kb: KeyBound) {
        cancelTimers()
        if (kb.spec.type == KeyType.BACKSPACE) {
            val r = object : Runnable {
                override fun run() {
                    if (pointerKeys[slot] == kb) {
                        pointerLongTriggered[slot] = true
                        listener?.onBackspace()
                        handler.postDelayed(this, 55L)
                    }
                }
            }
            repeatRunnable = r
            handler.postDelayed(r, 340L)
        } else if (kb.spec.type == KeyType.SPACE) {
            val lp = Runnable {
                if (pointerKeys[slot] == kb && !pointerLongTriggered[slot]) {
                    pointerLongTriggered[slot] = true
                    listener?.onSwitchLanguage()
                }
            }
            longPressRunnable = lp
            handler.postDelayed(lp, 380L)
        } else if (!kb.spec.longPressText.isNullOrEmpty()) {
            val lp = Runnable {
                if (pointerKeys[slot] == kb && !pointerLongTriggered[slot]) {
                    pointerLongTriggered[slot] = true
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    listener?.onCommitText(kb.spec.longPressText)
                }
            }
            longPressRunnable = lp
            handler.postDelayed(lp, 320L)
        }
    }

    private fun cancelTimers() {
        repeatRunnable?.let { handler.removeCallbacks(it) }
        longPressRunnable?.let { handler.removeCallbacks(it) }
        repeatRunnable = null
        longPressRunnable = null
    }

    private fun flushRolloverPointers(exceptPid: Int) {
        for (i in pointerIds.indices) {
            val pid = pointerIds[i]
            if (pid != MotionEvent.INVALID_POINTER_ID && pid != exceptPid) {
                val kb = pointerKeys[i]
                if (kb != null && kb.spec.type == KeyType.CHAR && !pointerLongTriggered[i]) {
                    executeKeyAction(kb.spec)
                }
                releaseSlot(i)
            }
        }
    }

    private fun findKeyAt(x: Float, y: Float): KeyBound? {
        for (i in keyBounds.indices) {
            val kb = keyBounds[i]
            if (x >= kb.rect.left - colGapPx / 2f && x <= kb.rect.right + colGapPx / 2f &&
                y >= kb.rect.top - rowGapPx / 2f && y <= kb.rect.bottom + rowGapPx / 2f
            ) {
                return kb
            }
        }
        return null
    }

    private fun allocPointerSlot(pid: Int): Int {
        for (i in pointerIds.indices) {
            if (pointerIds[i] == MotionEvent.INVALID_POINTER_ID) {
                pointerIds[i] = pid
                return i
            }
        }
        return -1
    }

    private fun findSlotByPid(pid: Int): Int {
        for (i in pointerIds.indices) {
            if (pointerIds[i] == pid) return i
        }
        return -1
    }

    private fun releaseSlot(slot: Int) {
        pointerIds[slot] = MotionEvent.INVALID_POINTER_ID
        pointerKeys[slot] = null
        pointerLongTriggered[slot] = false
    }
}
