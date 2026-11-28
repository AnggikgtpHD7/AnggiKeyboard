package com.anggik.keyboard.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.anggik.keyboard.engine.ClipboardVault
import com.anggik.keyboard.engine.HapticEngine
import com.anggik.keyboard.engine.HapticProfile
import com.anggik.keyboard.engine.LiveActionChip
import com.anggik.keyboard.engine.SmartMacroMathEngine
import com.anggik.keyboard.engine.SnippetEngine
import kotlin.math.abs

@SuppressLint("ViewConstructor")
class CommandStripView(
    context: Context,
    private var palette: SettingsPalette,
    private val clipboardVault: ClipboardVault,
    private val onCommitText: (String) -> Unit,
    private val onReplaceBeforeCursor: (Int, String) -> Unit,
    private val onSendDpad: (Int, Boolean) -> Unit,
    private val onDeleteWord: () -> Unit,
    private val onOpenSettings: () -> Unit
) : LinearLayout(context) {

    private enum class StripMode {
        TOOLBAR,
        CLIPBOARD,
        MACROS,
        SYMBOLS,
        TRACKPAD
    }

    private var mode = StripMode.TOOLBAR
    private var selectLock = false
    private var liveChip: LiveActionChip? = null

    private val topBar = LinearLayout(context)
    private val drawerHost = LinearLayout(context)

    init {
        orientation = VERTICAL
        setBackgroundColor(palette.bgCanvas)

        topBar.orientation = HORIZONTAL
        topBar.gravity = Gravity.CENTER_VERTICAL
        topBar.setPadding(
            UiKit.dp(context, 6f),
            UiKit.dp(context, 4f),
            UiKit.dp(context, 6f),
            UiKit.dp(context, 4f)
        )

        drawerHost.orientation = VERTICAL
        drawerHost.visibility = View.GONE

        addView(
            topBar,
            LayoutParams(LayoutParams.MATCH_PARENT, UiKit.dp(context, 38f))
        )
        addView(
            drawerHost,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        )

        clipboardVault.onVaultUpdated = {
            if (mode == StripMode.CLIPBOARD || mode == StripMode.TOOLBAR) {
                render()
            }
        }

        render()
    }

    fun applyTheme(newPalette: SettingsPalette) {
        if (palette == newPalette) return
        palette = newPalette
        setBackgroundColor(palette.bgCanvas)
        render()
    }

    fun resetTransientState() {
        if (mode != StripMode.TOOLBAR || selectLock) {
            mode = StripMode.TOOLBAR
            selectLock = false
            render()
        }
    }

    fun updateLiveSuggestion(chip: LiveActionChip?) {
        if (liveChip == chip) return
        liveChip = chip
        if (mode == StripMode.TOOLBAR) {
            renderTopBar()
        }
    }

    private fun render() {
        renderTopBar()
        renderDrawer()
    }

    private fun renderTopBar() {
        topBar.removeAllViews()

        if (mode == StripMode.TRACKPAD) {
            renderTrackpadBar()
            return
        }

        val btnClip = makeToolPill("Klip", active = mode == StripMode.CLIPBOARD) {
            mode = if (mode == StripMode.CLIPBOARD) StripMode.TOOLBAR else StripMode.CLIPBOARD
            render()
        }

        val btnMacro = makeToolPill("Makro", active = mode == StripMode.MACROS) {
            mode = if (mode == StripMode.MACROS) StripMode.TOOLBAR else StripMode.MACROS
            render()
        }

        val btnCursor = makeToolPill("Kursor", active = false) {
            mode = StripMode.TRACKPAD
            selectLock = false
            render()
        }

        val btnSymbol = makeToolPill("☺", active = mode == StripMode.SYMBOLS) {
            mode = if (mode == StripMode.SYMBOLS) StripMode.TOOLBAR else StripMode.SYMBOLS
            render()
        }

        topBar.addView(btnClip, pillParams(endMarginDp = 4f))
        topBar.addView(btnMacro, pillParams(endMarginDp = 4f))
        topBar.addView(btnCursor, pillParams(endMarginDp = 4f))
        topBar.addView(btnSymbol, pillParams(endMarginDp = 6f))

        // Area Tengah Dinamis: Live Math / Macro Suggestion atau Quick-Paste Clipboard
        val centerHolder = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val activeSuggestion = liveChip
        val quickClip = clipboardVault.latestQuickChip()

        if (activeSuggestion != null) {
            val suggestBtn = TextView(context).apply {
                text = activeSuggestion.badgeLabel
                textSize = 11.5f
                maxLines = 1
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                setTextColor(palette.textOnAccent)
                background = UiKit.ripplePill(
                    palette.accentPrimary,
                    0x33FFFFFF,
                    UiKit.dp(context, 9f).toFloat()
                )
                setPadding(
                    UiKit.dp(context, 10f),
                    UiKit.dp(context, 4f),
                    UiKit.dp(context, 10f),
                    UiKit.dp(context, 4f)
                )
                setOnClickListener {
                    performTickHaptic()
                    onReplaceBeforeCursor(activeSuggestion.triggerLength, activeSuggestion.replacementText)
                    liveChip = null
                    renderTopBar()
                }
            }
            centerHolder.addView(suggestBtn)
        } else if (quickClip != null) {
            val clipChip = TextView(context).apply {
                text = "⎘ ${quickClip.displayPreview()}"
                textSize = 11f
                maxLines = 1
                setTextColor(palette.textSecondary)
                background = UiKit.ripplePill(
                    palette.bgPillTrack,
                    palette.accentPrimary,
                    UiKit.dp(context, 9f).toFloat(),
                    palette.borderCard,
                    UiKit.dp(context, 1f)
                )
                setPadding(
                    UiKit.dp(context, 9f),
                    UiKit.dp(context, 4f),
                    UiKit.dp(context, 9f),
                    UiKit.dp(context, 4f)
                )
                setOnClickListener {
                    performTickHaptic()
                    onCommitText(quickClip.text)
                }
            }
            centerHolder.addView(clipChip)
        }

        topBar.addView(centerHolder, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

        val btnSettings = makeToolPill("⚙", active = false) {
            onOpenSettings()
        }
        topBar.addView(btnSettings, pillParams(startMarginDp = 4f))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun renderTrackpadBar() {
        val selBtn = makeToolPill(if (selectLock) "SEL: ON" else "SEL", active = selectLock) {
            selectLock = !selectLock
            renderTopBar()
        }
        topBar.addView(selBtn, pillParams(endMarginDp = 5f))

        val rail = TextView(context).apply {
            text = "◀  Geser Kursor Presisi  ▶"
            textSize = 11.5f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(palette.accentPrimary)
            background = UiKit.roundedBox(
                palette.accentSoft,
                UiKit.dp(context, 9f).toFloat(),
                palette.accentPrimary,
                UiKit.dp(context, 1f)
            )
            setPadding(UiKit.dp(context, 8f), UiKit.dp(context, 4f), UiKit.dp(context, 8f), UiKit.dp(context, 4f))
        }

        var lastX = 0f
        var accumDx = 0f
        val stepPx = UiKit.dp(context, 13f).toFloat()

        rail.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x
                    accumDx = 0f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - lastX
                    lastX = event.x
                    accumDx += dx
                    while (abs(accumDx) >= stepPx) {
                        if (accumDx > 0f) {
                            onSendDpad(KeyEvent.KEYCODE_DPAD_RIGHT, selectLock)
                            accumDx -= stepPx
                        } else {
                            onSendDpad(KeyEvent.KEYCODE_DPAD_LEFT, selectLock)
                            accumDx += stepPx
                        }
                        performTickHaptic()
                    }
                    true
                }
                else -> true
            }
        }

        topBar.addView(
            rail,
            LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                marginEnd = UiKit.dp(context, 5f)
            }
        )

        val delWordBtn = makeToolPill("⌫ Kata", active = false) {
            onDeleteWord()
        }
        topBar.addView(delWordBtn, pillParams(endMarginDp = 4f))

        val closeBtn = makeToolPill("✕", active = false) {
            mode = StripMode.TOOLBAR
            selectLock = false
            render()
        }
        topBar.addView(closeBtn, pillParams())
    }

    private fun renderDrawer() {
        drawerHost.removeAllViews()
        when (mode) {
            StripMode.TOOLBAR, StripMode.TRACKPAD -> {
                drawerHost.visibility = View.GONE
            }
            StripMode.SYMBOLS -> {
                drawerHost.visibility = View.VISIBLE
                val symbolDrawer = SymbolEmojiDrawer(context, palette) { token ->
                    onCommitText(token)
                }
                drawerHost.addView(
                    symbolDrawer,
                    LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                )
            }
            StripMode.MACROS -> {
                drawerHost.visibility = View.VISIBLE
                val hScroll = HorizontalScrollView(context).apply {
                    isHorizontalScrollBarEnabled = false
                    setPadding(
                        UiKit.dp(context, 6f),
                        UiKit.dp(context, 2f),
                        UiKit.dp(context, 6f),
                        UiKit.dp(context, 6f)
                    )
                }
                val row = LinearLayout(context).apply { orientation = HORIZONTAL }
                SnippetEngine.getAll(context).forEach { snip ->
                    val snipChip = TextView(context).apply {
                        text = "${snip.trigger} → ${snip.expansion.take(16)}"
                        textSize = 11.5f
                        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                        setTextColor(palette.accentPrimary)
                        background = UiKit.ripplePill(
                            palette.accentSoft,
                            palette.accentPrimary,
                            UiKit.dp(context, 10f).toFloat(),
                            palette.accentPrimary,
                            UiKit.dp(context, 1f)
                        )
                        setPadding(
                            UiKit.dp(context, 10f),
                            UiKit.dp(context, 6f),
                            UiKit.dp(context, 10f),
                            UiKit.dp(context, 6f)
                        )
                        setOnClickListener {
                            performTickHaptic()
                            onCommitText(snip.expansion)
                            mode = StripMode.TOOLBAR
                            render()
                        }
                    }
                    row.addView(
                        snipChip,
                        LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                            marginEnd = UiKit.dp(context, 6f)
                        }
                    )
                }
                SmartMacroMathEngine.presets.forEach { preset ->
                    val chip = TextView(context).apply {
                        text = "${preset.trigger} (${preset.label})"
                        textSize = 11.5f
                        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                        setTextColor(palette.textPrimary)
                        background = UiKit.ripplePill(
                            palette.bgCard,
                            palette.accentPrimary,
                            UiKit.dp(context, 10f).toFloat(),
                            palette.borderCard,
                            UiKit.dp(context, 1f)
                        )
                        setPadding(
                            UiKit.dp(context, 10f),
                            UiKit.dp(context, 6f),
                            UiKit.dp(context, 10f),
                            UiKit.dp(context, 6f)
                        )
                        setOnClickListener {
                            performTickHaptic()
                            onCommitText(preset.resolver())
                            mode = StripMode.TOOLBAR
                            render()
                        }
                    }
                    row.addView(
                        chip,
                        LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                            marginEnd = UiKit.dp(context, 6f)
                        }
                    )
                }
                hScroll.addView(row)
                drawerHost.addView(hScroll)
            }
            StripMode.CLIPBOARD -> {
                drawerHost.visibility = View.VISIBLE
                val scroll = ScrollView(context).apply {
                    isVerticalScrollBarEnabled = false
                    setPadding(
                        UiKit.dp(context, 8f),
                        UiKit.dp(context, 2f),
                        UiKit.dp(context, 8f),
                        UiKit.dp(context, 6f)
                    )
                }
                val listCol = LinearLayout(context).apply { orientation = VERTICAL }
                val items = clipboardVault.getAll()

                if (items.isEmpty()) {
                    val emptyTv = TextView(context).apply {
                        text = "Papan klip kosong • Salin teks untuk menyimpannya di sini"
                        textSize = 11.5f
                        gravity = Gravity.CENTER
                        setTextColor(palette.textSecondary)
                        setPadding(0, UiKit.dp(context, 14f), 0, UiKit.dp(context, 14f))
                    }
                    listCol.addView(emptyTv)
                } else {
                    items.take(8).forEach { item ->
                        val card = LinearLayout(context).apply {
                            orientation = HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            background = UiKit.roundedBox(
                                palette.bgCard,
                                UiKit.dp(context, 10f).toFloat(),
                                if (item.pinned) palette.accentPrimary else palette.borderCard,
                                UiKit.dp(context, 1f)
                            )
                            setPadding(
                                UiKit.dp(context, 10f),
                                UiKit.dp(context, 6f),
                                UiKit.dp(context, 8f),
                                UiKit.dp(context, 6f)
                            )
                        }

                        val previewTv = TextView(context).apply {
                            text = (if (item.pinned) "📌 " else "") + item.displayPreview()
                            textSize = 12f
                            maxLines = 1
                            setTextColor(if (item.sensitive) palette.statusWarnText else palette.textPrimary)
                            setOnClickListener {
                                performTickHaptic()
                                onCommitText(item.text)
                                mode = StripMode.TOOLBAR
                                render()
                            }
                        }

                        card.addView(previewTv, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

                        if (!item.sensitive) {
                            val pinBtn = makeToolPill(if (item.pinned) "Unpin" else "Pin", active = item.pinned) {
                                clipboardVault.togglePin(item.id)
                            }
                            card.addView(pinBtn, pillParams(startMarginDp = 4f))
                        }

                        val delBtn = makeToolPill("✕", active = false) {
                            clipboardVault.remove(item.id)
                        }
                        card.addView(delBtn, pillParams(startMarginDp = 4f))

                        listCol.addView(
                            card,
                            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                                bottomMargin = UiKit.dp(context, 5f)
                            }
                        )
                    }
                }

                scroll.addView(listCol)
                drawerHost.addView(
                    scroll,
                    LayoutParams(LayoutParams.MATCH_PARENT, UiKit.dp(context, 146f))
                )
            }
        }
    }

    private fun makeToolPill(label: String, active: Boolean, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            textSize = 11.5f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(if (active) palette.textOnAccent else palette.textPrimary)
            background = UiKit.ripplePill(
                fillColor = if (active) palette.accentPrimary else palette.bgPillTrack,
                rippleColor = palette.accentPrimary,
                radiusPx = UiKit.dp(context, 9f).toFloat(),
                strokeColor = if (active) palette.accentPrimary else palette.borderCard,
                strokePx = UiKit.dp(context, 1f)
            )
            setPadding(
                UiKit.dp(context, 9f),
                UiKit.dp(context, 4f),
                UiKit.dp(context, 9f),
                UiKit.dp(context, 4f)
            )
            setOnClickListener {
                performTickHaptic()
                onClick()
            }
        }

    private fun pillParams(startMarginDp: Float = 0f, endMarginDp: Float = 0f): LayoutParams =
        LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = UiKit.dp(context, startMarginDp)
            marginEnd = UiKit.dp(context, endMarginDp)
        }

    private fun performTickHaptic() {
        val profile = if (mode == StripMode.TRACKPAD) HapticProfile.TRACKPAD_TICK else HapticProfile.UI_TAP
        HapticEngine.fire(this, profile)
    }
}
