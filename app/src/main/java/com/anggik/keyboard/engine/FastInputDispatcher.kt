package com.anggik.keyboard.engine

import android.os.SystemClock
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

class FastInputDispatcher {

    private val asciiCache: Array<String> = Array(128) { code -> code.toChar().toString() }

    fun commitText(text: CharSequence, ic: InputConnection?) {
        if (ic == null || text.isEmpty()) return
        val resolved: CharSequence = if (text.length == 1 && text[0].code in 0..127) {
            asciiCache[text[0].code]
        } else {
            text
        }
        ic.beginBatchEdit()
        try {
            ic.commitText(resolved, 1)
        } finally {
            ic.endBatchEdit()
        }
    }

    fun sendBackspace(ic: InputConnection?) {
        if (ic == null) return
        ic.beginBatchEdit()
        try {
            val selected = ic.getSelectedText(0)
            if (!selected.isNullOrEmpty()) {
                ic.commitText("", 1)
            } else {
                ic.deleteSurroundingText(1, 0)
            }
        } finally {
            ic.endBatchEdit()
        }
    }

    fun deleteWordBeforeCursor(ic: InputConnection?) {
        if (ic == null) return
        val before = ic.getTextBeforeCursor(64, 0) ?: return
        if (before.isEmpty()) return
        var idx = before.length - 1
        while (idx >= 0 && before[idx].isWhitespace()) idx--
        while (idx >= 0 && !before[idx].isWhitespace()) idx--
        val deleteCount = before.length - (idx + 1)
        if (deleteCount > 0) {
            ic.beginBatchEdit()
            try {
                ic.deleteSurroundingText(deleteCount, 0)
            } finally {
                ic.endBatchEdit()
            }
        }
    }

    fun sendKeyCode(keyCode: Int, ic: InputConnection?) {
        if (ic == null) return
        val now = SystemClock.uptimeMillis()
        ic.sendKeyEvent(
            KeyEvent(
                now, now, KeyEvent.ACTION_DOWN, keyCode, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
            )
        )
        ic.sendKeyEvent(
            KeyEvent(
                now, now, KeyEvent.ACTION_UP, keyCode, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
            )
        )
    }

    fun performEditorAction(info: EditorInfo?, ic: InputConnection?) {
        if (ic == null) return
        if (info == null) {
            commitText("\n", ic)
            return
        }
        val noEnterAction = (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        if (!noEnterAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            commitText("\n", ic)
        }
    }
}
