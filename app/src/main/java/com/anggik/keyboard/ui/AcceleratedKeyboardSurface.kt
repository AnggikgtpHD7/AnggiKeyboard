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
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import com.anggik.keyboard.engine.GlideTypingEngine
import com.anggik.keyboard.model.KeySpec
import com.anggik.keyboard.model.KeyType
import com.anggik.keyboard.model.KeyboardLayer
import com.anggik.keyboard.model.KeyboardLayouts
import com.anggik.keyboard.model.ShiftState
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot

class AcceleratedKeyboardSurface(context: Context) : View(context) {

    interface Listener {
        fun onCommitText(text: CharSequence)
        fun onGlideTrajectory(xs: FloatArray, ys: FloatArray, count: Int, startChar: Char, endChar: Char, isShifted: Boolean)
        fun onCtrlChar(ch: Char)
        fun onDoubleSpace(): Boolean
        fun onSuggestionPicked(word: String)
        fun onBackspace()
        fun onDeleteWord()
        fun onEnter()
        fun onCursorMove(keyCode: Int)
        fun onSwitchLanguage()
        fun onVoiceInput()
        fun onSelectAll()
        fun onCopy()
        fun onPasteText(text: String)
        fun getClipboardHistory(): List<String>
    }

    var listener: Listener? = null
    val glideEngine = GlideTypingEngine()

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    // Dynamic palette (Mint Light default vs AMOLED True Black)
    private var colorBg = 0xFFEFF2F0.toInt()
    private var colorKeyWhite = 0xFFFFFFFF.toInt()
    private var colorKeyMint = 0xFFD1E4D4.toInt()
    private var colorKeyEnter = 0xFF75C18A.toInt()
    private var colorKeyPressed = 0xFFC2D6C6.toInt()
    private var colorKeyShadow = 0xFFD2D7D4.toInt()
    private var colorDivider = 0xFFDCE1DE.toInt()
    private var colorTextPrimary = 0xFF121413.toInt()
    private var colorTextHint = 0xFF4B504D.toInt()
    private var colorTextMuted = 0xFF9AA09D.toInt()
    private var colorMicDisabled = 0xFFB0B5B2.toInt()
    private var colorGlideTrail = 0xAA46A363.toInt()

    private var heightScale = 1.0f
    private var hapticEnabled = true
    private var isAmoledDark = false

    private val toolbarHeightPx = 42f * density
    private val baseKeyHeightPx = 47f * density
    private val rowGapPx = 7f * density
    private val colGapPx = 5.5f * density
    private val outerPadXPx = 4f * density
    private val outerPadTopPx = 6f * density
    private val outerPadBottomPx = 6f * density
    private val keyCornerPx = 6.5f * density
    private val shadowOffsetPx = 1.5f * density

    private fun currentKeyHeight(): Float = baseKeyHeightPx * heightScale

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1f * density }
    private val keyFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubbleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
    }
    private val glideTrailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4.2f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val mainTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    private val bubbleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        textSize = 27f * density
    }
    private val actionLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val hintTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val spaceTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.85f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val iconFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var currentLayer = KeyboardLayer.QWERTY
    private var shiftState = ShiftState.OFF
    private var languageLabel = "English"
    private var imeAction = EditorInfo.IME_ACTION_UNSPECIFIED
    private var isTerminalContext = false
    private var isCtrlLatched = false

    private enum class UtilityPanel { NONE, CLIPBOARD, CURSOR }
    private var activeUtilityPanel = UtilityPanel.NONE

    private val activeSuggestions = ArrayList<String>(3)
    private var suppressSuggestionsForWord = false

    private data class KeyBound(val row: Int, val col: Int, val spec: KeySpec, val rect: RectF)
    private val keyBounds = ArrayList<KeyBound>(44)
    private val toolbarBounds = Array(4) { RectF() }
    private val utilityChipBounds = Array(7) { RectF() }
    private val suggestionChipBounds = Array(4) { RectF() }
    private val tempRect = RectF()
    private val bubbleRect = RectF()
    private val vectorPath = Path()
    private val glidePath = Path()

    private var staticNode: Any? = null
    private var fallbackBitmap: Bitmap? = null
    private var isStaticDirty = true

    // Multi-touch rollover tracking (5 pointers, zero heap allocation)
    private val pointerIds = IntArray(5) { MotionEvent.INVALID_POINTER_ID }
    private val pointerKeys = Array<KeyBound?>(5) { null }
    private val pointerDownX = FloatArray(5)
    private val pointerDownY = FloatArray(5)
    private val pointerLastDragX = FloatArray(5)
    private val pointerLongTriggered = BooleanArray(5)

    // Glide gesture buffer (64 points, zero allocation)
    private val glideXs = FloatArray(64)
    private val glideYs = FloatArray(64)
    private var glideCount = 0
    private var glideActivePid = MotionEvent.INVALID_POINTER_ID
    private var glideStartChar = '\u0000'
    private var isGliding = false

    private var lastSpaceUpTimeMs = 0L

    private val handler = Handler(Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null
    private var longPressRunnable: Runnable? = null

    init {
        isHapticFeedbackEnabled = true
        applyVisualConfig(amoledDark = false, scale = 1.0f, haptic = true)
    }

    fun applyVisualConfig(amoledDark: Boolean, scale: Float, haptic: Boolean) {
        val clampedScale = scale.coerceIn(0.85f, 1.20f)
        val scaleChanged = abs(heightScale - clampedScale) > 0.001f
        isAmoledDark = amoledDark
        heightScale = clampedScale
        hapticEnabled = haptic

        if (amoledDark) {
            colorBg = 0xFF000000.toInt()
            colorKeyWhite = 0xFF1A1E1C.toInt()
            colorKeyMint = 0xFF162C1E.toInt()
            colorKeyEnter = 0xFF2E8B57.toInt()
            colorKeyPressed = 0xFF2B3E32.toInt()
            colorKeyShadow = 0xFF0A0C0B.toInt()
            colorDivider = 0xFF1F2421.toInt()
            colorTextPrimary = 0xFFF1F5F2.toInt()
            colorTextHint = 0xFF95A39B.toInt()
            colorTextMuted = 0xFF6B7770.toInt()
            colorMicDisabled = 0xFF5A635E.toInt()
            colorGlideTrail = 0xCC49C474.toInt()
        } else {
            colorBg = 0xFFEFF2F0.toInt()
            colorKeyWhite = 0xFFFFFFFF.toInt()
            colorKeyMint = 0xFFD1E4D4.toInt()
            colorKeyEnter = 0xFF75C18A.toInt()
            colorKeyPressed = 0xFFC2D6C6.toInt()
            colorKeyShadow = 0xFFD2D7D4.toInt()
            colorDivider = 0xFFDCE1DE.toInt()
            colorTextPrimary = 0xFF121413.toInt()
            colorTextHint = 0xFF4B504D.toInt()
            colorTextMuted = 0xFF9AA09D.toInt()
            colorMicDisabled = 0xFFB0B5B2.toInt()
            colorGlideTrail = 0xAA46A363.toInt()
        }

        bgPaint.color = colorBg
        dividerPaint.color = colorDivider
        keyShadowPaint.color = colorKeyShadow
        bubbleBorderPaint.color = colorKeyShadow
        glideTrailPaint.color = colorGlideTrail
        mainTextPaint.color = colorTextPrimary
        bubbleTextPaint.color = colorTextPrimary
        actionLabelPaint.color = colorTextPrimary
        hintTextPaint.color = colorTextHint
        spaceTextPaint.color = colorTextPrimary

        if (scaleChanged) {
            requestLayout()
            if (width > 0) rebuildGeometry(width.toFloat())
        }
        invalidateStaticLayer()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val totalHeight = (toolbarHeightPx + outerPadTopPx + (4 * currentKeyHeight()) + (3 * rowGapPx) + outerPadBottomPx).toInt()
        setMeasuredDimension(w, totalHeight)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildGeometry(w.toFloat())
        invalidateStaticLayer()
    }

    fun setContextProfile(useNumpad: Boolean, isTermux: Boolean, action: Int) {
        var changed = false
        val targetLayer = if (useNumpad) KeyboardLayer.NUMPAD else KeyboardLayer.QWERTY
        if (currentLayer != targetLayer) {
            currentLayer = targetLayer
            changed = true
        }
        if (isTerminalContext != isTermux) {
            isTerminalContext = isTermux
            isCtrlLatched = false
            changed = true
        }
        if (imeAction != action) {
            imeAction = action
            changed = true
        }
        if (changed) {
            rebuildGeometry(width.toFloat())
            invalidateStaticLayer()
        }
    }

    fun setLanguageName(name: String) {
        if (languageLabel != name) {
            languageLabel = name
            invalidateStaticLayer()
        }
    }

    fun setSuggestions(words: List<String>) {
        if (words.isEmpty()) {
            suppressSuggestionsForWord = false
        }
        if (activeSuggestions != words) {
            activeSuggestions.clear()
            activeSuggestions.addAll(words.take(3))
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

    private fun triggerHaptic(type: Int) {
        if (hapticEnabled) {
            performHapticFeedback(type)
        }
    }

    private fun activeRows(): List<List<KeySpec>> = when (currentLayer) {
        KeyboardLayer.QWERTY -> KeyboardLayouts.qwertyRows
        KeyboardLayer.SYMBOLS -> KeyboardLayouts.symbolsRows
        KeyboardLayer.SYMBOLS_ALT -> KeyboardLayouts.symbolsAltRows
        KeyboardLayer.EMOJI -> KeyboardLayouts.emojiRows
        KeyboardLayer.NUMPAD -> KeyboardLayouts.numpadRows
    }

    private fun rebuildGeometry(totalWidth: Float) {
        if (totalWidth <= 0f) return
        keyBounds.clear()

        val slotW = totalWidth / 6f
        toolbarBounds[0].set(0f, 0f, slotW, toolbarHeightPx)
        toolbarBounds[1].set(slotW, 0f, slotW * 2f, toolbarHeightPx)
        toolbarBounds[2].set(slotW * 2f, 0f, slotW * 3f, toolbarHeightPx)
        toolbarBounds[3].set(totalWidth - slotW, 0f, totalWidth, toolbarHeightPx)

        val chipW = (totalWidth - (8 * colGapPx)) / 7f
        for (i in 0 until 7) {
            val left = colGapPx + i * (chipW + colGapPx)
            utilityChipBounds[i].set(left, 5f * density, left + chipW, toolbarHeightPx - 5f * density)
        }

        val dismissW = 34f * density
        val sugAreaW = totalWidth - dismissW - (5 * colGapPx)
        val sugW = sugAreaW / 3f
        for (i in 0 until 3) {
            val left = colGapPx + i * (sugW + colGapPx)
            suggestionChipBounds[i].set(left, 5f * density, left + sugW, toolbarHeightPx - 5f * density)
        }
        suggestionChipBounds[3].set(totalWidth - dismissW - colGapPx, 5f * density, totalWidth - colGapPx, toolbarHeightPx - 5f * density)

        val rows = activeRows()
        val usableWidth = totalWidth - (2f * outerPadXPx)
        val kh = currentKeyHeight()
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
                val rect = RectF(x, y, x + kw, y + kh)
                keyBounds.add(KeyBound(r, c, spec, rect))
                if (currentLayer == KeyboardLayer.QWERTY && spec.type == KeyType.CHAR && spec.label.length == 1) {
                    glideEngine.updateKeyGeometry(spec.label[0], rect.centerX(), rect.centerY(), unitWidth)
                }
                x += kw + colGapPx
            }
            y += kh + rowGapPx
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
        actionLabelPaint.textSize = 15f * density
        hintTextPaint.textSize = 10.5f * density
        spaceTextPaint.textSize = 14f * density

        for (kb in keyBounds) {
            drawKeyCap(canvas, kb, isPressed = false)
        }
    }

    private fun drawActivePressOverlay(canvas: Canvas) {
        if (isGliding && glideCount >= 2) {
            glidePath.reset()
            glidePath.moveTo(glideXs[0], glideYs[0])
            for (i in 1 until glideCount) {
                glidePath.lineTo(glideXs[i], glideYs[i])
            }
            canvas.drawPath(glidePath, glideTrailPaint)
            return
        }

        for (i in pointerKeys.indices) {
            val kb = pointerKeys[i] ?: continue
            drawKeyCap(canvas, kb, isPressed = true)
            if (kb.spec.type == KeyType.CHAR || kb.spec.type == KeyType.COMMA || kb.spec.type == KeyType.PERIOD) {
                drawKeyPreviewBubble(canvas, kb, pointerLongTriggered[i])
            }
        }
    }

    private fun drawKeyPreviewBubble(canvas: Canvas, kb: KeyBound, showAlt: Boolean) {
        val raw = if (showAlt && !kb.spec.longPressText.isNullOrEmpty()) {
            kb.spec.longPressText
        } else {
            kb.spec.label
        }
        val label = if (currentLayer == KeyboardLayer.QWERTY && shiftState != ShiftState.OFF && !showAlt) {
            raw.uppercase(Locale.ROOT)
        } else {
            raw
        }
        if (label.isEmpty()) return

        val bw = (kb.rect.width() * 1.32f).coerceAtLeast(38f * density)
        val bh = 44f * density
        val cx = kb.rect.centerX().coerceIn(bw / 2f + 2f * density, width - bw / 2f - 2f * density)
        val bottom = kb.rect.top - 4f * density
        val top = (bottom - bh).coerceAtLeast(2f * density)

        bubbleRect.set(cx - bw / 2f, top, cx + bw / 2f, top + bh)
        tempRect.set(bubbleRect.left, bubbleRect.top + 2f * density, bubbleRect.right, bubbleRect.bottom + 2f * density)
        canvas.drawRoundRect(tempRect, 8f * density, 8f * density, keyShadowPaint)

        keyFillPaint.color = colorKeyWhite
        canvas.drawRoundRect(bubbleRect, 8f * density, 8f * density, keyFillPaint)
        canvas.drawRoundRect(bubbleRect, 8f * density, 8f * density, bubbleBorderPaint)

        val yOff = (bubbleTextPaint.descent() + bubbleTextPaint.ascent()) / 2f
        canvas.drawText(label, bubbleRect.centerX(), bubbleRect.centerY() - yOff, bubbleTextPaint)
    }

    private fun drawToolbar(canvas: Canvas) {
        canvas.drawLine(0f, 0.5f * density, width.toFloat(), 0.5f * density, dividerPaint)
        canvas.drawLine(0f, toolbarHeightPx, width.toFloat(), toolbarHeightPx, dividerPaint)
        val yOff = (spaceTextPaint.descent() + spaceTextPaint.ascent()) / 2f

        if (activeUtilityPanel == UtilityPanel.CURSOR) {
            val labels = arrayOf("◄", "▲", "▼", "►", "All", "Copy", "✕")
            for (i in 0 until 7) {
                val r = utilityChipBounds[i]
                keyFillPaint.color = if (i == 6) colorKeyMint else colorKeyWhite
                canvas.drawRoundRect(r, 5f * density, 5f * density, keyFillPaint)
                canvas.drawText(labels[i], r.centerX(), r.centerY() - yOff, spaceTextPaint)
            }
            return
        }

        if (activeUtilityPanel == UtilityPanel.CLIPBOARD) {
            val history = listener?.getClipboardHistory().orEmpty()
            if (history.isEmpty()) {
                val emptyRect = RectF(colGapPx, 5f * density, width - 48f * density, toolbarHeightPx - 5f * density)
                val closeRect = RectF(width - 42f * density, 5f * density, width - colGapPx, toolbarHeightPx - 5f * density)
                keyFillPaint.color = colorKeyWhite
                canvas.drawRoundRect(emptyRect, 5f * density, 5f * density, keyFillPaint)
                keyFillPaint.color = colorKeyMint
                canvas.drawRoundRect(closeRect, 5f * density, 5f * density, keyFillPaint)
                canvas.drawText("Clipboard kosong", emptyRect.centerX(), emptyRect.centerY() - yOff, spaceTextPaint)
                canvas.drawText("✕", closeRect.centerX(), closeRect.centerY() - yOff, spaceTextPaint)
            } else {
                val count = history.size.coerceAtMost(3)
                for (i in 0 until count) {
                    val r = suggestionChipBounds[i]
                    keyFillPaint.color = colorKeyWhite
                    canvas.drawRoundRect(r, 5f * density, 5f * density, keyFillPaint)
                    val preview = history[i].replace('\n', ' ').take(12)
                    canvas.drawText(preview, r.centerX(), r.centerY() - yOff, spaceTextPaint)
                }
                val closeRect = suggestionChipBounds[3]
                keyFillPaint.color = colorKeyMint
                canvas.drawRoundRect(closeRect, 5f * density, 5f * density, keyFillPaint)
                canvas.drawText("✕", closeRect.centerX(), closeRect.centerY() - yOff, spaceTextPaint)
            }
            return
        }

        if (activeSuggestions.isNotEmpty() && !suppressSuggestionsForWord) {
            for (i in activeSuggestions.indices) {
                val r = suggestionChipBounds[i]
                keyFillPaint.color = if (i == 0) colorKeyMint else colorKeyWhite
                canvas.drawRoundRect(r, 5f * density, 5f * density, keyFillPaint)
                canvas.drawText(activeSuggestions[i], r.centerX(), r.centerY() - yOff, spaceTextPaint)
            }
            val dismissRect = suggestionChipBounds[3]
            keyFillPaint.color = colorKeyWhite
            canvas.drawRoundRect(dismissRect, 5f * density, 5f * density, keyFillPaint)
            canvas.drawText("▾", dismissRect.centerX(), dismissRect.centerY() - yOff, spaceTextPaint)
            return
        }

        if (isTerminalContext) {
            val termKeys = arrayOf("ESC", "TAB", "CTRL", "|", "/", "~", "-")
            for (i in 0 until 7) {
                val r = utilityChipBounds[i]
                keyFillPaint.color = if (i == 2 && isCtrlLatched) colorKeyEnter else colorKeyWhite
                canvas.drawRoundRect(r, 5f * density, 5f * density, keyFillPaint)
                canvas.drawText(termKeys[i], r.centerX(), r.centerY() - yOff, spaceTextPaint)
            }
            return
        }

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

        run {
            val cx = toolbarBounds[2].centerX()
            val cy = toolbarBounds[2].centerY()
            iconPaint.color = colorMicDisabled
            canvas.drawRoundRect(cx - 3.2f * density, cy - 8f * density, cx + 3.2f * density, cy + 1f * density, 3.2f * density, 3.2f * density, iconPaint)
            canvas.drawArc(cx - 6.5f * density, cy - 4f * density, cx + 6.5f * density, cy + 5.5f * density, 0f, 180f, false, iconPaint)
            canvas.drawLine(cx, cy + 5.5f * density, cx, cy + 8.5f * density, iconPaint)
        }

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

        tempRect.set(rect.left, rect.top + shadowOffsetPx, rect.right, rect.bottom + shadowOffsetPx)
        canvas.drawRoundRect(tempRect, keyCornerPx, keyCornerPx, keyShadowPaint)

        keyFillPaint.color = when {
            isPressed -> colorKeyPressed
            spec.type == KeyType.ENTER -> colorKeyEnter
            spec.type == KeyType.SHIFT ||
                spec.type == KeyType.BACKSPACE ||
                spec.type == KeyType.MODE_SYMBOLS ||
                spec.type == KeyType.MODE_SYMBOLS_ALT ||
                spec.type == KeyType.MODE_EMOJI ||
                spec.type == KeyType.MODE_NUMPAD -> colorKeyMint
            else -> colorKeyWhite
        }
        canvas.drawRoundRect(rect, keyCornerPx, keyCornerPx, keyFillPaint)

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
                if (currentLayer != KeyboardLayer.NUMPAD) {
                    val mx = rect.right - 7f * density
                    val my = rect.top + 8f * density
                    iconPaint.color = colorTextHint
                    iconPaint.strokeWidth = 1.25f * density
                    canvas.drawRoundRect(mx - 1.8f * density, my - 4.5f * density, mx + 1.8f * density, my + 0.5f * density, 1.8f * density, 1.8f * density, iconPaint)
                    canvas.drawArc(mx - 3.6f * density, my - 2f * density, mx + 3.6f * density, my + 3f * density, 0f, 180f, false, iconPaint)
                    iconPaint.strokeWidth = 1.85f * density
                }
            }
            KeyType.PERIOD -> {
                val yOff = (mainTextPaint.descent() + mainTextPaint.ascent()) / 2f
                canvas.drawText(".", cx, cy - yOff + 2f * density, mainTextPaint)
            }
            KeyType.MODE_SYMBOLS, KeyType.MODE_SYMBOLS_ALT, KeyType.MODE_NUMPAD -> {
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
                vectorPath.reset()
                val lx = cx - textHalf - 11f * density
                vectorPath.moveTo(lx - 3.5f * density, cy)
                vectorPath.lineTo(lx + 1.5f * density, cy - 3.5f * density)
                vectorPath.lineTo(lx + 1.5f * density, cy + 3.5f * density)
                vectorPath.close()
                canvas.drawPath(vectorPath, iconFillPaint)

                vectorPath.reset()
                val rx = cx + textHalf + 11f * density
                vectorPath.moveTo(rx + 3.5f * density, cy)
                vectorPath.lineTo(rx - 1.5f * density, cy - 3.5f * density)
                vectorPath.lineTo(rx - 1.5f * density, cy + 3.5f * density)
                vectorPath.close()
                canvas.drawPath(vectorPath, iconFillPaint)

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
                if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN && isGliding) {
                    resetGlideState()
                }
                flushRolloverPointers(exceptPid = pid)
                val hit = findKeyAt(x, y)
                val slot = allocPointerSlot(pid)
                if (slot != -1 && hit != null) {
                    pointerKeys[slot] = hit
                    pointerDownX[slot] = x
                    pointerDownY[slot] = y
                    pointerLastDragX[slot] = x
                    pointerLongTriggered[slot] = false
                    triggerHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                    scheduleLongOrRepeat(slot, hit)

                    if (event.actionMasked == MotionEvent.ACTION_DOWN &&
                        currentLayer == KeyboardLayer.QWERTY &&
                        hit.spec.type == KeyType.CHAR &&
                        hit.spec.label.length == 1
                    ) {
                        glideActivePid = pid
                        glideStartChar = hit.spec.label[0]
                        glideXs[0] = x
                        glideYs[0] = y
                        glideCount = 1
                        isGliding = false
                    }
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
                    val curY = event.getY(p)

                    if (mPid == glideActivePid && currentLayer == KeyboardLayer.QWERTY && kb.spec.type == KeyType.CHAR) {
                        val distFromOrigin = hypot(curX - pointerDownX[slot], curY - pointerDownY[slot])
                        if (!isGliding && distFromOrigin > touchSlop * 1.9f && !pointerLongTriggered[slot]) {
                            cancelTimers()
                            isGliding = true
                        }
                        if (isGliding && glideCount < glideXs.size) {
                            val prevX = glideXs[glideCount - 1]
                            val prevY = glideYs[glideCount - 1]
                            if (hypot(curX - prevX, curY - prevY) >= 6f * density) {
                                glideXs[glideCount] = curX
                                glideYs[glideCount] = curY
                                glideCount++
                                invalidate()
                            }
                        }
                    } else if (kb.spec.type == KeyType.SPACE) {
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

                    if (pid == glideActivePid && isGliding) {
                        val endHit = findKeyAt(x, y)
                        val endChar = if (endHit != null && endHit.spec.type == KeyType.CHAR && endHit.spec.label.length == 1) {
                            endHit.spec.label[0]
                        } else {
                            glideStartChar
                        }
                        val wasShifted = shiftState != ShiftState.OFF
                        listener?.onGlideTrajectory(glideXs, glideYs, glideCount, glideStartChar, endChar, wasShifted)
                        if (shiftState == ShiftState.SHIFTED) {
                            shiftState = ShiftState.OFF
                            invalidateStaticLayer()
                        }
                        resetGlideState()
                        invalidate()
                        return true
                    }

                    if (pid == glideActivePid) {
                        resetGlideState()
                    }
                    if (kb != null && !wasLong) {
                        executeKeyAction(kb.spec)
                    }
                    invalidate()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelTimers()
                resetGlideState()
                for (i in pointerIds.indices) releaseSlot(i)
                invalidate()
            }
        }
        return true
    }

    private fun resetGlideState() {
        glideActivePid = MotionEvent.INVALID_POINTER_ID
        glideCount = 0
        isGliding = false
    }

    private fun handleToolbarTap(x: Float) {
        triggerHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
        if (activeUtilityPanel == UtilityPanel.CURSOR) {
            for (i in 0 until 7) {
                if (utilityChipBounds[i].contains(x, toolbarHeightPx / 2f)) {
                    when (i) {
                        0 -> listener?.onCursorMove(KeyEvent.KEYCODE_DPAD_LEFT)
                        1 -> listener?.onCursorMove(KeyEvent.KEYCODE_DPAD_UP)
                        2 -> listener?.onCursorMove(KeyEvent.KEYCODE_DPAD_DOWN)
                        3 -> listener?.onCursorMove(KeyEvent.KEYCODE_DPAD_RIGHT)
                        4 -> listener?.onSelectAll()
                        5 -> listener?.onCopy()
                        6 -> {
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
            val history = listener?.getClipboardHistory().orEmpty()
            if (suggestionChipBounds[3].contains(x, toolbarHeightPx / 2f)) {
                activeUtilityPanel = UtilityPanel.NONE
                invalidateStaticLayer()
                return
            }
            val count = history.size.coerceAtMost(3)
            for (i in 0 until count) {
                if (suggestionChipBounds[i].contains(x, toolbarHeightPx / 2f)) {
                    listener?.onPasteText(history[i])
                    activeUtilityPanel = UtilityPanel.NONE
                    invalidateStaticLayer()
                    return
                }
            }
            return
        }

        if (activeSuggestions.isNotEmpty() && !suppressSuggestionsForWord) {
            if (suggestionChipBounds[3].contains(x, toolbarHeightPx / 2f)) {
                suppressSuggestionsForWord = true
                invalidateStaticLayer()
                return
            }
            for (i in activeSuggestions.indices) {
                if (suggestionChipBounds[i].contains(x, toolbarHeightPx / 2f)) {
                    listener?.onSuggestionPicked(activeSuggestions[i])
                    activeSuggestions.clear()
                    invalidateStaticLayer()
                    return
                }
            }
            return
        }

        if (isTerminalContext) {
            for (i in 0 until 7) {
                if (utilityChipBounds[i].contains(x, toolbarHeightPx / 2f)) {
                    when (i) {
                        0 -> listener?.onCursorMove(KeyEvent.KEYCODE_ESCAPE)
                        1 -> listener?.onCursorMove(KeyEvent.KEYCODE_TAB)
                        2 -> {
                            isCtrlLatched = !isCtrlLatched
                            invalidateStaticLayer()
                        }
                        3 -> listener?.onCommitText("|")
                        4 -> listener?.onCommitText("/")
                        5 -> listener?.onCommitText("~")
                        6 -> listener?.onCommitText("-")
                    }
                    return
                }
            }
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
                if (isCtrlLatched && spec.label.length == 1 && spec.label[0].isLetter()) {
                    isCtrlLatched = false
                    listener?.onCtrlChar(spec.label[0])
                    invalidateStaticLayer()
                    return
                }
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
            KeyType.SPACE -> {
                val now = SystemClock.uptimeMillis()
                if (now - lastSpaceUpTimeMs <= 320L && listener?.onDoubleSpace() == true) {
                    lastSpaceUpTimeMs = 0L
                } else {
                    lastSpaceUpTimeMs = now
                    listener?.onCommitText(" ")
                }
            }
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
            KeyType.MODE_SYMBOLS, KeyType.MODE_NUMPAD -> {
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
                    triggerHaptic(HapticFeedbackConstants.LONG_PRESS)
                    invalidate()
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
