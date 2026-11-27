package com.anggik.keyboard.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator

class LaunchSplashOverlay(
    context: Context,
    private val palette: SettingsPalette
) : View(context) {

    private val density = resources.displayMetrics.density
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.bgCanvas }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val keyDarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1B4D32.toInt() }
    private val keyMidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2E8B57.toInt() }
    private val keyAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF49C474.toInt() }

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.textPrimary
        textAlign = Paint.Align.CENTER
        textSize = 21f * density
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.textSecondary
        textAlign = Paint.Align.CENTER
        textSize = 12f * density
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.bgPillTrack }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accentPrimary }

    private val rect = RectF()
    private var progress = 0.15f
    private var progressAnimator: ValueAnimator? = null

    init {
        isClickable = true
        isFocusable = true
        elevation = 24f * density
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val cx = w / 2f
        val cy = h * 0.43f
        val r = 38f * density
        badgePaint.shader = LinearGradient(
            cx - r, cy - r, cx + r, cy + r,
            0xFF2E8B57.toInt(),
            0xFF15472C.toInt(),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        canvas.drawRect(0f, 0f, w, h, bgPaint)

        val cx = w / 2f
        val cy = h * 0.43f
        val badgeSize = 76f * density
        val half = badgeSize / 2f

        // Squircle Badge
        rect.set(cx - half, cy - half, cx + half, cy + half)
        canvas.drawRoundRect(rect, 20f * density, 20f * density, badgePaint)

        // Inner White Keyboard Chassis
        val cw = 48f * density
        val ch = 32f * density
        rect.set(cx - cw / 2f, cy - ch / 2f, cx + cw / 2f, cy + ch / 2f)
        canvas.drawRoundRect(rect, 6f * density, 6f * density, cardPaint)

        // Row 1 Keycaps
        val kPad = 3.5f * density
        val kGap = 2.2f * density
        val r1Top = rect.top + kPad
        val kH = 6.2f * density
        val r1W = (cw - (2 * kPad) - (4 * kGap)) / 5f
        var kx = rect.left + kPad
        for (i in 0 until 5) {
            tempKeyRect.set(kx, r1Top, kx + r1W, r1Top + kH)
            canvas.drawRoundRect(tempKeyRect, 1.6f * density, 1.6f * density, keyDarkPaint)
            kx += r1W + kGap
        }

        // Row 2 Keycaps
        val r2Top = r1Top + kH + kGap
        val r2W = (cw - (2 * kPad) - (3 * kGap) - 3f * density) / 4f
        kx = rect.left + kPad + 1.5f * density
        for (i in 0 until 4) {
            tempKeyRect.set(kx, r2Top, kx + r2W, r2Top + kH)
            canvas.drawRoundRect(tempKeyRect, 1.6f * density, 1.6f * density, keyMidPaint)
            kx += r2W + kGap
        }

        // Row 3 Spacebar + Enter
        val r3Top = r2Top + kH + kGap
        val sideW = 7f * density
        val spaceW = cw - (2 * kPad) - (2 * sideW) - (2 * kGap)
        tempKeyRect.set(rect.left + kPad, r3Top, rect.left + kPad + sideW, r3Top + kH)
        canvas.drawRoundRect(tempKeyRect, 1.6f * density, 1.6f * density, keyDarkPaint)

        tempKeyRect.set(rect.left + kPad + sideW + kGap, r3Top, rect.left + kPad + sideW + kGap + spaceW, r3Top + kH)
        canvas.drawRoundRect(tempKeyRect, 1.6f * density, 1.6f * density, keyDarkPaint)

        tempKeyRect.set(rect.right - kPad - sideW, r3Top, rect.right - kPad, r3Top + kH)
        canvas.drawRoundRect(tempKeyRect, 1.6f * density, 1.6f * density, keyAccentPaint)

        // App Title & Runtime Telemetry Subtitle
        val titleY = cy + half + 32f * density
        canvas.drawText("AnggiKeyboard", cx, titleY, titlePaint)
        canvas.drawText("Canvas IME Engine • API ${Build.VERSION.SDK_INT}", cx, titleY + 19f * density, subPaint)

        // Compact Progress Pill
        val barW = 132f * density
        val barH = 4f * density
        val barTop = titleY + 34f * density
        rect.set(cx - barW / 2f, barTop, cx + barW / 2f, barTop + barH)
        canvas.drawRoundRect(rect, barH / 2f, barH / 2f, trackPaint)

        rect.set(cx - barW / 2f, barTop, cx - barW / 2f + (barW * progress), barTop + barH)
        canvas.drawRoundRect(rect, barH / 2f, barH / 2f, barPaint)
    }

    private val tempKeyRect = RectF()

    fun playAndDismiss(host: ViewGroup) {
        progressAnimator = ValueAnimator.ofFloat(0.18f, 1.0f).apply {
            duration = 210L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                progress = anim.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    animate()
                        .alpha(0f)
                        .translationY(-10f * density)
                        .setDuration(150L)
                        .setListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(a: Animator) {
                                host.removeView(this@LaunchSplashOverlay)
                            }
                        })
                        .start()
                }
            })
            start()
        }
    }

    override fun onDetachedFromWindow() {
        progressAnimator?.cancel()
        super.onDetachedFromWindow()
    }
}
