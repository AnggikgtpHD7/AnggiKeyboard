package com.anggik.keyboard.engine

import android.text.InputType
import android.view.inputmethod.EditorInfo

data class InputCompatProfile(
    val packageName: String,
    val isNullRawType: Boolean,
    val isTerminalApp: Boolean,
    val isPasswordField: Boolean,
    val isNoSuggestionField: Boolean,
    val disableComposing: Boolean,
    val useCtrlWForWordDelete: Boolean
) {
    companion object {
        val DEFAULT = InputCompatProfile(
            packageName = "",
            isNullRawType = false,
            isTerminalApp = false,
            isPasswordField = false,
            isNoSuggestionField = false,
            disableComposing = false,
            useCtrlWForWordDelete = false
        )
    }
}

object InputCompatibilityRouter {

    private val terminalPackages = setOf(
        "com.termux",
        "com.termux.x11",
        "org.connectbot",
        "com.sonelli.juicessh",
        "com.server.auditor.ssh.client"
    )

    fun resolve(info: EditorInfo?): InputCompatProfile {
        if (info == null) return InputCompatProfile.DEFAULT

        val pkg = info.packageName ?: ""
        val inputType = info.inputType
        val klass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION

        val isNullRaw = inputType == InputType.TYPE_NULL
        val isTerminal = pkg in terminalPackages

        val isPassword = klass == InputType.TYPE_CLASS_TEXT && (
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            )

        val isNoSuggest = (inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0 ||
            variation == InputType.TYPE_TEXT_VARIATION_URI ||
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_FILTER

        val disableComposing = isNullRaw || isTerminal || isPassword || isNoSuggest
        val useCtrlW = isNullRaw || isTerminal

        return InputCompatProfile(
            packageName = pkg,
            isNullRawType = isNullRaw,
            isTerminalApp = isTerminal,
            isPasswordField = isPassword,
            isNoSuggestionField = isNoSuggest,
            disableComposing = disableComposing,
            useCtrlWForWordDelete = useCtrlW
        )
    }
}
