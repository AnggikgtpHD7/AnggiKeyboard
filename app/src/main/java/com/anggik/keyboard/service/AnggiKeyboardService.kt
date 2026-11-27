package com.anggik.keyboard.service

import android.content.ClipboardManager
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.text.InputType
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import com.anggik.keyboard.engine.FastInputDispatcher
import com.anggik.keyboard.ui.AcceleratedKeyboardSurface

class AnggiKeyboardService : InputMethodService(), AcceleratedKeyboardSurface.Listener {

    private val dispatcher = FastInputDispatcher()
    private var rootContainer: FrameLayout? = null
    private var keyboardSurface: AcceleratedKeyboardSurface? = null
    private var isIndonesian = false
    private var isSensitiveInput = false

    private val devicePrefs by lazy {
        createDeviceProtectedStorageContext().getSharedPreferences("anggi_kb_state", Context.MODE_PRIVATE)
    }

    override fun onCreate() {
        super.onCreate()
        isIndonesian = devicePrefs.getBoolean("lang_id", false)
    }

    override fun onCreateInputView(): View {
        val container = FrameLayout(this).apply {
            setBackgroundColor(0xFFEFF2F0.toInt())
        }
        val surface = AcceleratedKeyboardSurface(this).apply {
            listener = this@AnggiKeyboardService
            setLanguageName(if (isIndonesian) "Indonesia" else "English")
        }
        container.addView(
            surface,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
        applyUniversalInsets(container)
        rootContainer = container
        keyboardSurface = surface
        return container
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val inputType = info?.inputType ?: 0
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val incognito = info != null && (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
        isSensitiveInput = incognito ||
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD

        val action = (info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        keyboardSurface?.setEditorAction(action)
        syncAutoCaps(info)
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
        syncAutoCaps(currentInputEditorInfo)
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    private fun syncAutoCaps(info: EditorInfo?) {
        val ic = currentInputConnection ?: return
        val type = info?.inputType ?: 0
        val caps = ic.getCursorCapsMode(type)
        keyboardSurface?.setAutoShift(caps != 0)
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
        dispatcher.commitText(text, currentInputConnection)
    }

    override fun onBackspace() {
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

    override fun onPasteText(text: String) {
        dispatcher.commitText(text, currentInputConnection)
    }

    override fun onSelectAll() {
        currentInputConnection?.performContextMenuAction(android.R.id.selectAll)
    }

    override fun onCopy() {
        currentInputConnection?.performContextMenuAction(android.R.id.copy)
    }

    override fun onPasteClipboard() {
        currentInputConnection?.performContextMenuAction(android.R.id.paste)
    }

    override fun getClipboardPreview(): String? {
        if (isSensitiveInput) return "Dinonaktifkan di kolom sensitif"
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val clip = cm.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(this)?.toString()
    }
}
