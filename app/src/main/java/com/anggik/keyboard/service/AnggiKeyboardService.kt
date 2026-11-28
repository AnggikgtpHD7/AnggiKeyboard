package com.anggik.keyboard.service
import com.anggik.keyboard.ui.SettingsPalette
import com.anggik.keyboard.ui.DrawerBackDispatcher
import com.anggik.keyboard.ui.StylusHandwritingOverlay

import android.content.ClipboardManager
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.anggik.keyboard.engine.FastInputDispatcher
import com.anggik.keyboard.engine.InlineAutofillHelper
import com.anggik.keyboard.engine.LocalWordPredictor
import com.anggik.keyboard.ui.AcceleratedKeyboardSurface

class AnggiKeyboardService : InputMethodService(), AcceleratedKeyboardSurface.Listener {

    private val dispatcher = FastInputDispatcher()
    private val predictor = LocalWordPredictor()
    private val suggestionBuffer = ArrayList<String>(3)
    private val clipboardRing = ArrayList<String>(5)

    private var rootContainer: FrameLayout? = null
    private var keyboardSurface: AcceleratedKeyboardSurface? = null
    private var inlineScrollBar: HorizontalScrollView? = null
    private var inlineChipContainer: LinearLayout? = null

    private var isIndonesian = false
    private var isSensitiveInput = false
    private var lastTrackedWord = ""

    private val dpContext by lazy {
        createDeviceProtectedStorageContext()
    }

    private val devicePrefs by lazy {
        dpContext.getSharedPreferences("anggi_kb_state", Context.MODE_PRIVATE)
    }

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (isSensitiveInput) return@OnPrimaryClipChangedListener
        capturePrimaryClip()
    }

    private var drawerBackDispatcher: DrawerBackDispatcher? = null
    private var handwritingOverlay: StylusHandwritingOverlay? = null

    override fun onCreate() {
        super.onCreate()
        isIndonesian = devicePrefs.getBoolean("lang_id", false)
        predictor.initStorage(dpContext.filesDir)
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.removePrimaryClipChangedListener(clipListener)
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val amoled = devicePrefs.getBoolean("amoled_dark", false)
        val scale = devicePrefs.getFloat("height_scale", 1.0f)
        val haptic = devicePrefs.getBoolean("haptic_enabled", true)
        val hand = devicePrefs.getInt("hand_mode", 0)
        val toolbarPx = (42f * resources.displayMetrics.density).toInt()

        val container = FrameLayout(this).apply {
            setBackgroundColor(if (amoled) 0xFF000000.toInt() else 0xFFEFF2F0.toInt())
        }

        val surface = AcceleratedKeyboardSurface(this).apply {
            listener = this@AnggiKeyboardService
            setLanguageName(if (isIndonesian) "Indonesia" else "English")
            applyVisualConfig(amoled, scale, haptic, hand)
        }

        val chipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val autofillScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            visibility = View.GONE
            setBackgroundColor(if (amoled) 0xFF000000.toInt() else 0xFFEFF2F0.toInt())
            addView(
                chipRow,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }

        container.addView(
            surface,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
        container.addView(
            autofillScroll,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                toolbarPx,
                Gravity.TOP
            )
        )

        applyUniversalInsets(container)
        rootContainer = container
        keyboardSurface = surface
        inlineScrollBar = autofillScroll
        inlineChipContainer = chipRow
        return container
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        hideInlineAutofillBar()

        val amoled = devicePrefs.getBoolean("amoled_dark", false)
        val scale = devicePrefs.getFloat("height_scale", 1.0f)
        val haptic = devicePrefs.getBoolean("haptic_enabled", true)
        val hand = devicePrefs.getInt("hand_mode", 0)
        val bg = if (amoled) 0xFF000000.toInt() else 0xFFEFF2F0.toInt()
        rootContainer?.setBackgroundColor(bg)
        inlineScrollBar?.setBackgroundColor(bg)
        keyboardSurface?.applyVisualConfig(amoled, scale, haptic, hand)

        val inputType = info?.inputType ?: 0
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val incognito = info != null && (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0

        isSensitiveInput = incognito ||
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD

        if (!isSensitiveInput) {
            capturePrimaryClip()
        }

        val useNumpad = inputClass == InputType.TYPE_CLASS_NUMBER || inputClass == InputType.TYPE_CLASS_PHONE
        val isTermux = info?.packageName == "com.termux"
        val action = (info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION

        keyboardSurface?.setContextProfile(useNumpad, isTermux, action)
        refreshSuggestionsAndCaps(info)
    }

    override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val toolbarPx = (42f * resources.displayMetrics.density).toInt()
        return InlineAutofillHelper.buildRequest(toolbarPx)
    }

    override fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val container = inlineChipContainer ?: return false
        val toolbarPx = (42f * resources.displayMetrics.density).toInt()
        InlineAutofillHelper.populateSuggestions(this, response, toolbarPx, container) { visible ->
            inlineScrollBar?.visibility = if (visible) View.VISIBLE else View.GONE
        }
        return true
    }

    private fun hideInlineAutofillBar() {
        inlineScrollBar?.visibility = View.GONE
        inlineChipContainer?.removeAllViews()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        refreshSuggestionsAndCaps(currentInputEditorInfo)
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    private fun refreshSuggestionsAndCaps(info: EditorInfo?) {
        val ic = currentInputConnection ?: return
        val type = info?.inputType ?: 0
        val caps = ic.getCursorCapsMode(type)
        keyboardSurface?.setAutoShift(caps != 0)

        if (isSensitiveInput) {
            keyboardSurface?.setSuggestions(emptyList())
            return
        }

        val prefix = dispatcher.extractActiveWordPrefix(ic)
        if (prefix.isNotEmpty()) {
            lastTrackedWord = prefix
            predictor.suggest(prefix, isIndonesian, suggestionBuffer)
            keyboardSurface?.setSuggestions(suggestionBuffer)
        } else {
            if (lastTrackedWord.isNotEmpty()) {
                predictor.learnWord(lastTrackedWord, !isSensitiveInput)
                lastTrackedWord = ""
            }
            val prevWord = dispatcher.extractPreviousWord(ic)
            if (prevWord.isNotEmpty()) {
                predictor.suggestNextWord(prevWord, isIndonesian, suggestionBuffer)
                keyboardSurface?.setSuggestions(suggestionBuffer)
            } else {
                keyboardSurface?.setSuggestions(emptyList())
            }
        }
    }

    private fun capturePrimaryClip() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = cm.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(this)?.toString()?.trim().orEmpty()
        if (text.isEmpty() || text.length > 256) return
        clipboardRing.remove(text)
        clipboardRing.add(0, text)
        if (clipboardRing.size > 5) {
            clipboardRing.removeAt(clipboardRing.lastIndex)
        }
    }

    private fun applyUniversalInsets(target: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            target.setOnApplyWindowInsetsListener { view, insets ->
                val sysBars = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                )
                view.setPadding(sysBars.left, 0, sysBars.right, sysBars.bottom)
                insets
            }
        } else {
            target.setPadding(0, 0, 0, 0)
        }
    }

    override fun onCommitText(text: CharSequence) {
        hideInlineAutofillBar()
        dispatcher.commitText(text, currentInputConnection)
    }

    override fun onGlideTrajectory(
        xs: FloatArray,
        ys: FloatArray,
        count: Int,
        startChar: Char,
        endChar: Char,
        isShifted: Boolean
    ) {
        hideInlineAutofillBar()
        val surface = keyboardSurface ?: return
        val matched = surface.glideEngine.decodeTrajectory(
            xs, ys, count, startChar, endChar, predictor, isIndonesian, suggestionBuffer
        ) ?: return
        val formatted = if (isShifted) {
            matched.replaceFirstChar { it.uppercaseChar() }
        } else {
            matched
        }
        predictor.learnWord(matched, !isSensitiveInput)
        dispatcher.commitText("$formatted ", currentInputConnection)
        surface.setSuggestions(suggestionBuffer)
    }

    override fun onCtrlChar(ch: Char) {
        dispatcher.sendCtrlChar(ch, currentInputConnection)
    }

    override fun onDoubleSpace(): Boolean {
        val converted = dispatcher.tryConvertDoubleSpaceToPeriod(currentInputConnection)
        if (converted) {
            refreshSuggestionsAndCaps(currentInputEditorInfo)
        }
        return converted
    }

    override fun onSuggestionPicked(word: String) {
        predictor.learnWord(word, !isSensitiveInput)
        lastTrackedWord = ""
        dispatcher.replaceActiveWordWithSuggestion(word, currentInputConnection)
        refreshSuggestionsAndCaps(currentInputEditorInfo)
    }

    override fun onBackspace() {
        hideInlineAutofillBar()
        dispatcher.sendBackspace(currentInputConnection)
    }

    override fun onDeleteWord() {
        dispatcher.deleteWordBeforeCursor(currentInputConnection)
    }

    override fun onEnter() {
        dispatcher.performEditorAction(currentInputEditorInfo, currentInputConnection)
    }

    override fun onCursorMove(keyCode: Int) {
        dispatcher.sendKeyCode(keyCode, currentInputConnection)
    }

    override fun onSwitchLanguage() {
        isIndonesian = !isIndonesian
        devicePrefs.edit().putBoolean("lang_id", isIndonesian).apply()
        keyboardSurface?.setLanguageName(if (isIndonesian) "Indonesia" else "English")
        refreshSuggestionsAndCaps(currentInputEditorInfo)
    }

    override fun onVoiceInput() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            switchToNextInputMethod(false)
        } else {
            @Suppress("DEPRECATION")
            imm.switchToNextInputMethod(window?.window?.attributes?.token, false)
        }
    }

    override fun onSelectAll() {
        currentInputConnection?.performContextMenuAction(android.R.id.selectAll)
    }

    override fun onCopy() {
        currentInputConnection?.performContextMenuAction(android.R.id.copy)
    }

    override fun onPasteText(text: String) {
        dispatcher.commitText(text, currentInputConnection)
    }

    override fun getClipboardHistory(): List<String> {
        if (isSensitiveInput) return emptyList()
        return clipboardRing.toList()
    }

    override fun onHandModeChanged(mode: Int) {
        devicePrefs.edit().putInt("hand_mode", mode.coerceIn(0, 2)).apply()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        stopHandwritingOverlay()
        drawerBackDispatcher?.detach()
        super.onFinishInputView(finishingInput)
    }

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        val anchor = inputViewContainer ?: touchSurface ?: return
        if (anchor.height > 0) {
            val top = anchor.top.coerceAtLeast(0)
            val bottom = (anchor.top + anchor.height).coerceAtLeast(top)
            outInsets.contentTopInsets = top
            outInsets.visibleTopInsets = top
            outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
            outInsets.touchableRegion.set(0, top, anchor.width.coerceAtLeast(0), bottom)
        }
    }

    override fun onStartStylusHandwriting(): Boolean {
        val host = inputViewContainer as? android.view.ViewGroup ?: return false
        if (handwritingOverlay == null) {
            val amoled = devicePrefs.getBoolean("amoled_dark", false)
            val palette = SettingsPalette.fromContext(this, amoled)
            val overlay = StylusHandwritingOverlay(
                context = this,
                palette = palette,
                onCommitText = { txt -> currentInputConnection?.commitText(txt, 1) },
                onBackspaceWord = {
                    val ic = currentInputConnection ?: return@StylusHandwritingOverlay
                    val before = ic.getTextBeforeCursor(64, 0)?.toString() ?: ""
                    val trimmed = before.trimEnd()
                    val lastSpace = trimmed.lastIndexOf(" ")
                    val delCount = if (lastSpace >= 0) before.length - (lastSpace + 1) else before.length
                    if (delCount > 0) ic.deleteSurroundingText(delCount, 0)
                },
                onDismiss = { stopHandwritingOverlay() }
            )
            host.addView(overlay, 0)
            handwritingOverlay = overlay
        }
        handwritingOverlay?.visibility = View.VISIBLE
        return true
    }

    private fun stopHandwritingOverlay() {
        val overlay = handwritingOverlay ?: return
        overlay.visibility = View.GONE
        (overlay.parent as? android.view.ViewGroup)?.removeView(overlay)
        handwritingOverlay = null
    }
}
