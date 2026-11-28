package com.anggik.keyboard.ui

import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.MotionEvent
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import com.anggik.keyboard.engine.HapticEngine
import com.anggik.keyboard.engine.HapticProfile

class UnbufferedInputRootLayout(context: Context) : LinearLayout(context) {

    init {
        orientation = VERTICAL
        isMotionEventSplittingEnabled = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                requestUnbufferedDispatch(InputDevice.SOURCE_TOUCHSCREEN)
            } catch (_: Throwable) {
                // Abaikan jika sumber tidak mendukung dispatch unbuffered langsung
            }
        }
    }

    override fun performHapticFeedback(feedbackConstant: Int, flags: Int): Boolean {
        val profile = if (
            feedbackConstant == HapticFeedbackConstants.CLOCK_TICK ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 &&
                feedbackConstant == HapticFeedbackConstants.TEXT_HANDLE_MOVE)
        ) {
            HapticProfile.TRACKPAD_TICK
        } else {
            HapticProfile.KEY_CHAR
        }
        return HapticEngine.fire(context, profile)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            try {
                requestUnbufferedDispatch(ev)
            } catch (_: Throwable) {
                // Pertahankan pengiriman standar jika event tidak memenuhi syarat
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}

object TouchPipelineOptimizer {

    @Volatile
    private var glyphsWarmed = false

    fun lockHighestRefreshRate(context: Context, window: Window?) {
        val win = window ?: return
        try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return
            val display = dm.getDisplay(Display.DEFAULT_DISPLAY) ?: return
            val currentMode = display.mode ?: return
            val bestMode = display.supportedModes
                .filter {
                    it.physicalWidth == currentMode.physicalWidth &&
                        it.physicalHeight == currentMode.physicalHeight
                }
                .maxByOrNull { it.refreshRate } ?: return

            val attrs = win.attributes
            var updated = false
            if (attrs.preferredDisplayModeId != bestMode.modeId) {
                attrs.preferredDisplayModeId = bestMode.modeId
                updated = true
            }
            if (Build.VERSION.SDK_INT >= 35) {
                if (attrs.frameRate != bestMode.refreshRate) {
                    attrs.frameRate = bestMode.refreshRate
                    updated = true
                }
            }
            if (updated) {
                win.attributes = attrs
            }
        } catch (_: Exception) {
            // Pertahankan mode default jika OEM membatasi atribut mode tampilan
        }
    }

    fun prewarmGlyphAtlas(context: Context) {
        if (glyphsWarmed) return
        glyphsWarmed = true

        val density = context.resources.displayMetrics.density
        val bounds = Rect()
        val sizesSp = floatArrayOf(12f, 15f, 19f, 22f)
        val typefaces = arrayOf(
            Typeface.DEFAULT,
            Typeface.create("sans-serif-medium", Typeface.BOLD),
            Typeface.MONOSPACE
        )

        val sb = StringBuilder(160)
        for (c in 32..126) sb.append(c.toChar())
        sb.append("→←↑↓⇄⇒⇔✓✕⚠⚡⌘⌥⌃⇧⌫•◆▪▸λΔΣΩ≠≈≤≥±∞√÷")
        val charset = sb.toString()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (tf in typefaces) {
            paint.typeface = tf
            for (sp in sizesSp) {
                paint.textSize = sp * density
                paint.measureText(charset)
                paint.getTextBounds(charset, 0, charset.length, bounds)
            }
        }
    }
}
