package com.anggik.keyboard.ui

import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.InputDevice
import android.view.MotionEvent
import android.view.HapticFeedbackConstants
import android.view.Surface
import android.view.Window
import com.anggik.keyboard.engine.HapticEngine
import com.anggik.keyboard.engine.HapticProfile
import android.widget.LinearLayout

/**
 * Root container yang memaksa pengiriman sampel digitizer tanpa antrean VSYNC
 * (unbuffered dispatch) pada setiap gestur sentuh dari API 26 hingga API 36.
 */
class UnbufferedInputRootLayout(context: Context) : LinearLayout(context) {

    init {
        orientation = VERTICAL
        isMotionEventSplittingEnabled = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requestUnbufferedDispatch(InputDevice.SOURCE_TOUCHSCREEN)
        }
    }

    override fun performHapticFeedback(feedbackConstant: Int, flags: Int): Boolean {
        val profile = if (feedbackConstant == HapticFeedbackConstants.CLOCK_TICK ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && feedbackConstant == HapticFeedbackConstants.TEXT_HANDLE_MOVE)
        ) {
            HapticProfile.TRACKPAD_TICK
        } else {
            HapticProfile.KEY_CHAR
        }
        return HapticEngine.fire(context, profile)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            requestUnbufferedDispatch(ev)
        }
        return super.dispatchTouchEvent(ev)
    }
}

object TouchPipelineOptimizer {

    @Volatile
    private var glyphsWarmed = false

    /**
     * Mengunci jendela IME pada mode refresh rate fisik tertinggi (mis. 144Hz / 120Hz)
     * tanpa mengubah resolusi layar agar mencegah penurunan refresh rate saat mengetik.
     */
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
            if (attrs.preferredDisplayModeId != bestMode.modeId) {
                attrs.preferredDisplayModeId = bestMode.modeId
                win.attributes = attrs
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val surface = win.decorView?.viewRootImpl?.surface
                if (surface != null && surface.isValid) {
                    surface.setFrameRate(
                        bestMode.refreshRate,
                        Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                        Surface.CHANGE_FRAME_RATE_ALWAYS
                    )
                }
            }
        } catch (_: Exception) {
            // Pertahankan mode default jika OEM membatasi preferredDisplayModeId
        }
    }

    /**
     * Memanaskan atlas glif FreeType/Minikin di memori native sebelum sentuhan pertama
     * agar frame pertama bebas dari latensi rasterisasi huruf & simbol.
     */
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
