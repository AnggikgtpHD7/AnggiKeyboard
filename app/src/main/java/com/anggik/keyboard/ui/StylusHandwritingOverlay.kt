package com.anggik.keyboard.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import com.anggik.keyboard.engine.HapticEngine
import com.anggik.keyboard.engine.HapticProfile
import kotlin.math.abs

@SuppressLint("ClickableViewAccessibility")
class StylusHandwritingOverlay(
    context: Context,
    palette: SettingsPalette,
    private val onCommitText: (String) -> Unit,
    private val onBackspaceWord: () -> Unit,
    private val onDismiss: () -> Unit
) : View(context) {

    private val density = resources.displayMetrics.density
    private val strokePath = Path()
    private var startX = 0f
    private var startY = 0f
    private var minX = Float.MAX_VALUE
    private var maxX = Float.MIN_VALUE
    private var minY = Float.MAX_VALUE
    private var maxY = Float.MIN_VALUE
    private var hasStroke = false

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = (palette.bgCard and 0x00FFFFFF) or 0xEC000000.toInt()
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = palette.accentPrimary
    }
    private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 4.5f * density
        color = palette.accentPrimary
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.textSecondary
        textSize = 11.5f * density
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (148f * density).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRoundRect(
            6f * density,
            4f * density,
            w - 6f * density,
            h - 4f * density,
            18f * density,
            18f * density,
            bgPaint
        )
        canvas.drawRoundRect(
            6f * density,
            4f * density,
            w - 6f * density,
            h - 4f * density,
            18f * density,
            18f * density,
            borderPaint
        )
        canvas.drawText(
            "Handwriting: Gores horizontal = Hapus Kata • Ketuk = Spasi • Geser atas = Tutup",
            18f * density,
            24f * density,
            hintPaint
        )
        if (hasStroke) {
            canvas.drawPath(strokePath, inkPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                startX = event.x
                startY = event.y
                minX = event.x
                maxX = event.x
                minY = event.y
                maxY = event.y
                strokePath.reset()
                strokePath.moveTo(event.x, event.y)
                hasStroke = true
                HapticEngine.fire(this, HapticProfile.UI_TAP)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val historySize = event.historySize
                for (h in 0 until historySize) {
                    val hx = event.getHistoricalX(h)
                    val hy = event.getHistoricalY(h)
                    strokePath.lineTo(hx, hy)
                    updateBounds(hx, hy)
                }
                strokePath.lineTo(event.x, event.y)
                updateBounds(event.x, event.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                val dx = event.x - startX
                val dy = event.y - startY
                val spanX = maxX - minX
                val spanY = maxY - minY

                when {
                    dy < -42f * density && abs(dy) > abs(dx) -> {
                        onDismiss()
                    }
                    spanX > 65f * density && spanY < 26f * density && abs(dx) > 45f * density -> {
                        HapticEngine.fire(this, HapticProfile.KEY_ACTION)
                        onBackspaceWord()
                    }
                    spanX < 14f * density && spanY < 14f * density -> {
                        onCommitText(" ")
                    }
                }

                strokePath.reset()
                hasStroke = false
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateBounds(x: Float, y: Float) {
        if (x < minX) minX = x
        if (x > maxX) maxX = x
        if (y < minY) minY = y
        if (y > maxY) maxY = y
    }
}
