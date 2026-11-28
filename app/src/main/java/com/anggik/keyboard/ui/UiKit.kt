package com.anggik.keyboard.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

data class SettingsPalette(
    val bgCanvas: Int,
    val bgCard: Int,
    val bgPillTrack: Int,
    val bgPillActive: Int,
    val borderCard: Int,
    val accentPrimary: Int,
    val accentSoft: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val textOnAccent: Int,
    val statusOkBg: Int,
    val statusOkText: Int,
    val statusWarnBg: Int,
    val statusWarnText: Int
) {
    companion object {
        fun fromContext(ctx: Context, amoledDark: Boolean): SettingsPalette {
            val dpCtx = ctx.applicationContext.createDeviceProtectedStorageContext()
            val accentId = dpCtx.getSharedPreferences("anggi_kb_state", Context.MODE_PRIVATE)
                .getInt("accent_id", 0)
            return fromMode(amoledDark, accentId, ctx)
        }

        fun fromMode(amoledDark: Boolean): SettingsPalette = fromMode(amoledDark, 0, null)

        fun fromMode(amoledDark: Boolean, accentId: Int, ctx: Context?): SettingsPalette {
            val base = basePalette(amoledDark)
            val (primary, soft) = resolveAccentPair(amoledDark, accentId, ctx)
            val onAccent = if (isColorBright(primary)) 0xFF08100C.toInt() else 0xFFFFFFFF.toInt()
            return base.copy(
                accentPrimary = primary,
                accentSoft = soft,
                textOnAccent = onAccent
            )
        }

        private fun resolveAccentPair(amoledDark: Boolean, accentId: Int, ctx: Context?): Pair<Int, Int> {
            if (accentId == 4 && ctx != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return try {
                    val dyn = ctx.getColor(
                        if (amoledDark) android.R.color.system_accent1_200
                        else android.R.color.system_accent1_600
                    )
                    val soft = blendOver(if (amoledDark) 0xFF111413.toInt() else 0xFFFFFFFF.toInt(), dyn, 0.18f)
                    dyn to soft
                } catch (_: Exception) {
                    defaultEmerald(amoledDark)
                }
            }
            return when (accentId) {
                1 -> if (amoledDark) (0xFF26D0CE.toInt() to 0xFF102B2D.toInt()) else (0xFF007791.toInt() to 0xFFD6EEF2.toInt())
                2 -> if (amoledDark) (0xFFF59E0B.toInt() to 0xFF2F210A.toInt()) else (0xFFB45309.toInt() to 0xFFFEF3C7.toInt())
                3 -> if (amoledDark) (0xFFFB7185.toInt() to 0xFF311219.toInt()) else (0xFFBE123C.toInt() to 0xFFFFE4E6.toInt())
                else -> defaultEmerald(amoledDark)
            }
        }

        private fun defaultEmerald(amoledDark: Boolean): Pair<Int, Int> =
            if (amoledDark) (0xFF49C474.toInt() to 0xFF162C1E.toInt())
            else (0xFF2E8B57.toInt() to 0xFFD9ECE0.toInt())

        private fun isColorBright(color: Int): Boolean {
            val lum = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0
            return lum > 0.62
        }

        private fun blendOver(base: Int, overlay: Int, ratio: Float): Int {
            val inv = 1f - ratio
            val r = (Color.red(base) * inv + Color.red(overlay) * ratio).toInt()
            val g = (Color.green(base) * inv + Color.green(overlay) * ratio).toInt()
            val b = (Color.blue(base) * inv + Color.blue(overlay) * ratio).toInt()
            return Color.rgb(r, g, b)
        }

        private fun basePalette(amoledDark: Boolean): SettingsPalette = if (amoledDark) {
            SettingsPalette(
                bgCanvas = 0xFF000000.toInt(),
                bgCard = 0xFF111413.toInt(),
                bgPillTrack = 0xFF1B201E.toInt(),
                bgPillActive = 0xFF28382E.toInt(),
                borderCard = 0xFF202623.toInt(),
                accentPrimary = 0xFF49C474.toInt(),
                accentSoft = 0xFF162C1E.toInt(),
                textPrimary = 0xFFF2F6F3.toInt(),
                textSecondary = 0xFF93A199.toInt(),
                textOnAccent = 0xFF06140B.toInt(),
                statusOkBg = 0xFF132E1D.toInt(),
                statusOkText = 0xFF5CE089.toInt(),
                statusWarnBg = 0xFF33240E.toInt(),
                statusWarnText = 0xFFF6B951.toInt()
            )
        } else {
            SettingsPalette(
                bgCanvas = 0xFFF2F5F3.toInt(),
                bgCard = 0xFFFFFFFF.toInt(),
                bgPillTrack = 0xFFE6ECE8.toInt(),
                bgPillActive = 0xFFFFFFFF.toInt(),
                borderCard = 0xFFDCE3DF.toInt(),
                accentPrimary = 0xFF2E8B57.toInt(),
                accentSoft = 0xFFD9ECE0.toInt(),
                textPrimary = 0xFF121513.toInt(),
                textSecondary = 0xFF55605A.toInt(),
                textOnAccent = 0xFFFFFFFF.toInt(),
                statusOkBg = 0xFFDDF3E5.toInt(),
                statusOkText = 0xFF1B6B3E.toInt(),
                statusWarnBg = 0xFFFEF0D6.toInt(),
                statusWarnText = 0xFF9A5B00.toInt()
            )
        }
    }
}

object UiKit {

    fun dp(ctx: Context, value: Float): Int =
        (value * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun dp(view: View, value: Float): Int =
        dp(view.context, value)

    fun roundedBox(fillColor: Int, radiusPx: Float, strokeColor: Int = 0, strokePx: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(fillColor)
            if (strokePx > 0) setStroke(strokePx, strokeColor)
        }

    fun ripplePill(fillColor: Int, rippleColor: Int, radiusPx: Float, strokeColor: Int = 0, strokePx: Int = 0): RippleDrawable {
        val content = roundedBox(fillColor, radiusPx, strokeColor, strokePx)
        val mask = roundedBox(0xFFFFFFFF.toInt(), radiusPx)
        return RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask)
    }

    fun sectionLabel(ctx: Context, title: String, palette: SettingsPalette): TextView =
        TextView(ctx).apply {
            text = title.uppercase()
            textSize = 11f
            letterSpacing = 0.08f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(palette.textSecondary)
            setPadding(dp(ctx, 4f), dp(ctx, 10f), dp(ctx, 4f), dp(ctx, 5f))
        }

    fun bentoCard(ctx: Context, palette: SettingsPalette): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBox(
                fillColor = palette.bgCard,
                radiusPx = dp(ctx, 16f).toFloat(),
                strokeColor = palette.borderCard,
                strokePx = dp(ctx, 1f)
            )
            setPadding(dp(ctx, 14f), dp(ctx, 10f), dp(ctx, 14f), dp(ctx, 10f))
        }

    fun divider(ctx: Context, palette: SettingsPalette): View =
        View(ctx).apply {
            setBackgroundColor(palette.borderCard)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(ctx, 1f)
            ).apply {
                topMargin = dp(ctx, 8f)
                bottomMargin = dp(ctx, 8f)
            }
        }

    fun segmentedControl(
        ctx: Context,
        options: List<String>,
        selectedIndex: Int,
        palette: SettingsPalette,
        onSelect: (Int) -> Unit
    ): LinearLayout {
        val trackPad = dp(ctx, 3f)
        val radius = dp(ctx, 10f).toFloat()
        val track = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundedBox(palette.bgPillTrack, radius)
            setPadding(trackPad, trackPad, trackPad, trackPad)
        }

        val itemViews = ArrayList<TextView>(options.size)

        fun renderSelection(activeIdx: Int) {
            for (i in itemViews.indices) {
                val tv = itemViews[i]
                val active = i == activeIdx
                tv.background = if (active) {
                    roundedBox(palette.bgPillActive, dp(ctx, 8f).toFloat(), palette.borderCard, dp(ctx, 1f))
                } else {
                    null
                }
                tv.setTextColor(if (active) palette.accentPrimary else palette.textSecondary)
                tv.typeface = Typeface.create("sans-serif-medium", if (active) Typeface.BOLD else Typeface.NORMAL)
            }
        }

        options.forEachIndexed { idx, label ->
            val tab = TextView(ctx).apply {
                text = label
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(dp(ctx, 10f), dp(ctx, 6f), dp(ctx, 10f), dp(ctx, 6f))
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    renderSelection(idx)
                    onSelect(idx)
                }
            }
            itemViews.add(tab)
            track.addView(
                tab,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
        }

        renderSelection(selectedIndex.coerceIn(0, options.lastIndex))
        return track
    }
}

class ModernSwitchView(
    context: Context,
    initialChecked: Boolean,
    private val palette: SettingsPalette,
    private val onToggle: (Boolean) -> Unit
) : View(context) {

    var isChecked: Boolean = initialChecked
        private set

    private val density = resources.displayMetrics.density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isClickable = true
        isFocusable = true
        setOnClickListener {
            isChecked = !isChecked
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            invalidate()
            onToggle(isChecked)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension((46f * density).toInt(), (26f * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2f

        trackPaint.color = if (isChecked) palette.accentPrimary else palette.bgPillTrack
        canvas.drawRoundRect(0f, 0f, w, h, r, r, trackPaint)

        val thumbMargin = 3.2f * density
        val thumbRadius = r - thumbMargin
        val cx = if (isChecked) w - r else r
        thumbPaint.color = if (isChecked) palette.textOnAccent else palette.textSecondary
        canvas.drawCircle(cx, r, thumbRadius, thumbPaint)
    }
}
