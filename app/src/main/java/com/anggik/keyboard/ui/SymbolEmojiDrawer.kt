package com.anggik.keyboard.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.anggik.keyboard.engine.HapticEngine
import com.anggik.keyboard.engine.HapticProfile
import java.io.File
import kotlin.math.ceil

@SuppressLint("ViewConstructor")
class SymbolEmojiDrawer(
    context: Context,
    private val palette: SettingsPalette,
    private val onPickToken: (String) -> Unit
) : LinearLayout(context) {

    private val dpContext = context.createDeviceProtectedStorageContext()
    private val recentFile = File(dpContext.filesDir, "recent_symbols.txt")
    private var activeTab = 0

    private val tabNames = listOf("Terbaru", "Ekspresi", "Teknis", "Kaomoji")

    private val emojiList = listOf(
        "😀", "😂", "🥹", "😎", "🤔", "🫡", "🔥", "⚡",
        "🚀", "✅", "❌", "⚠️", "📌", "🛠️", "💻", "📱",
        "🔒", "🔑", "📦", "🧠", "🎯", "💡", "📊", "🧩",
        "👍", "👏", "🙏", "🤝", "💯", "✨", "🎉", "⏱️"
    )

    private val techSymbols = listOf(
        "→", "←", "↑", "↓", "⇄", "⇒", "⇔", "✓",
        "✕", "⚠", "⚡", "⌘", "⌥", "⌃", "⇧", "⌫",
        "•", "◆", "▪", "▸", "λ", "Δ", "Σ", "Ω",
        "≠", "≈", "≤", "≥", "±", "∞", "√", "÷"
    )

    private val kaomojiList = listOf(
        "¯\\_(ツ)_/¯",
        "(╯°□°)╯︵ ┻━┻",
        "┬─┬ノ( º _ ºノ)",
        "(ง'̀-'́)ง",
        "(づ｡◕‿‿◕｡)づ",
        "(•_•) ( •_•)>⌐■-■",
        "ಠ_ಠ",
        "(☞ﾟヮﾟ)☞"
    )

    private val tabRow = LinearLayout(context)
    private val gridScroll = ScrollView(context)
    private val matrixCanvas = SymbolGridCanvas(context)

    init {
        orientation = VERTICAL
        setBackgroundColor(palette.bgCanvas)
        setPadding(
            UiKit.dp(context, 8f),
            UiKit.dp(context, 2f),
            UiKit.dp(context, 8f),
            UiKit.dp(context, 6f)
        )

        tabRow.orientation = HORIZONTAL
        tabRow.setPadding(0, 0, 0, UiKit.dp(context, 5f))

        gridScroll.isVerticalScrollBarEnabled = false
        gridScroll.addView(
            matrixCanvas,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        )

        addView(tabRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(gridScroll, LayoutParams(LayoutParams.MATCH_PARENT, UiKit.dp(context, 134f)))

        if (loadRecents().isEmpty()) {
            activeTab = 1
        }
        render()
    }

    private fun render() {
        tabRow.removeAllViews()
        tabNames.forEachIndexed { idx, label ->
            val selected = idx == activeTab
            val tabBtn = TextView(context).apply {
                text = label
                textSize = 11f
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-medium", if (selected) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (selected) palette.textOnAccent else palette.textSecondary)
                background = UiKit.roundedBox(
                    fillColor = if (selected) palette.accentPrimary else palette.bgPillTrack,
                    radiusPx = UiKit.dp(context, 8f).toFloat()
                )
                setPadding(
                    UiKit.dp(context, 8f),
                    UiKit.dp(context, 4f),
                    UiKit.dp(context, 8f),
                    UiKit.dp(context, 4f)
                )
                setOnClickListener {
                    HapticEngine.fire(this, HapticProfile.UI_TAP)
                    activeTab = idx
                    gridScroll.scrollTo(0, 0)
                    render()
                }
            }
            tabRow.addView(
                tabBtn,
                LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (idx < tabNames.lastIndex) marginEnd = UiKit.dp(context, 4f)
                }
            )
        }

        val items = when (activeTab) {
            0 -> loadRecents().ifEmpty { emojiList.take(16) }
            1 -> emojiList
            2 -> techSymbols
            else -> kaomojiList
        }
        val cols = if (activeTab == 3) 2 else 8
        val textSp = when (activeTab) {
            3 -> 12.5f
            2 -> 16f
            else -> 18f
        }
        matrixCanvas.bindData(items, cols, textSp)
    }

    private fun loadRecents(): List<String> {
        if (!recentFile.exists()) return emptyList()
        return try {
            recentFile.readLines().filter { it.isNotBlank() }.take(24)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun recordRecent(token: String) {
        try {
            val list = loadRecents().toMutableList()
            list.remove(token)
            list.add(0, token)
            recentFile.writeText(list.take(24).joinToString("\n"))
        } catch (_: Exception) {
            // Abaikan error I/O minor
        }
    }

    private inner class SymbolGridCanvas(ctx: Context) : View(ctx) {
        private val density = resources.displayMetrics.density
        private val gapPx = 4f * density
        private val rowHeightPx = 38f * density
        private val cornerPx = 8f * density

        private var items: List<String> = emptyList()
        private var cols = 8
        private var pressedIndex = -1

        private val cellFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = palette.bgCard
        }
        private val cellPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = palette.accentSoft
        }
        private val cellStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f * density
            color = palette.borderCard
        }
        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textPrimary
            textAlign = Paint.Align.CENTER
        }
        private val cellRect = RectF()

        fun bindData(newItems: List<String>, newCols: Int, textSp: Float) {
            items = newItems
            cols = newCols.coerceAtLeast(1)
            pressedIndex = -1
            labelPaint.textSize = textSp * density
            requestLayout()
            invalidate()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = MeasureSpec.getSize(widthMeasureSpec)
            val rows = if (items.isEmpty()) 1 else ceil(items.size.toDouble() / cols).toInt()
            val h = ((rows * rowHeightPx) + ((rows - 1).coerceAtLeast(0) * gapPx)).toInt()
            setMeasuredDimension(w, h)
        }

        override fun onDraw(canvas: Canvas) {
            if (items.isEmpty() || width <= 0) return
            val cellW = (width - ((cols - 1) * gapPx)) / cols.toFloat()
            val fm = labelPaint.fontMetrics
            val textOffset = -(fm.ascent + fm.descent) / 2f

            for (i in items.indices) {
                val r = i / cols
                val c = i % cols
                val left = c * (cellW + gapPx)
                val top = r * (rowHeightPx + gapPx)
                cellRect.set(left, top, left + cellW, top + rowHeightPx)

                val fill = if (i == pressedIndex) cellPressedPaint else cellFillPaint
                canvas.drawRoundRect(cellRect, cornerPx, cornerPx, fill)
                canvas.drawRoundRect(cellRect, cornerPx, cornerPx, cellStrokePaint)
                canvas.drawText(
                    items[i],
                    cellRect.centerX(),
                    cellRect.centerY() + textOffset,
                    labelPaint
                )
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pressedIndex = hitTest(event.x, event.y)
                    invalidate()
                    return pressedIndex >= 0
                }
                MotionEvent.ACTION_MOVE -> {
                    val idx = hitTest(event.x, event.y)
                    if (idx != pressedIndex) {
                        pressedIndex = idx
                        invalidate()
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val idx = hitTest(event.x, event.y)
                    if (idx >= 0 && idx == pressedIndex) {
                        val token = items[idx]
                        HapticEngine.fire(this, HapticProfile.UI_TAP)
                        recordRecent(token)
                        onPickToken(token)
                    }
                    pressedIndex = -1
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    pressedIndex = -1
                    invalidate()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        private fun hitTest(x: Float, y: Float): Int {
            if (items.isEmpty() || width <= 0 || x < 0f || y < 0f) return -1
            val cellW = (width - ((cols - 1) * gapPx)) / cols.toFloat()
            val strideX = cellW + gapPx
            val strideY = rowHeightPx + gapPx
            val col = (x / strideX).toInt()
            val row = (y / strideY).toInt()
            if (col < 0 || col >= cols || row < 0) return -1
            if ((x - col * strideX) > cellW || (y - row * strideY) > rowHeightPx) return -1
            val idx = row * cols + col
            return if (idx in items.indices) idx else -1
        }
    }
}
