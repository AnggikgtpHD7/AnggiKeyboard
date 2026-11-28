package com.anggik.keyboard.engine

import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import kotlin.math.hypot

class FastInputDispatcher {

    private val neighborMap: Map<Char, CharArray> = mapOf(
        'q' to charArrayOf('w', 'a', 's'),
        'w' to charArrayOf('q', 'e', 'a', 's', 'd'),
        'e' to charArrayOf('w', 'r', 's', 'd', 'f'),
        'r' to charArrayOf('e', 't', 'd', 'f', 'g'),
        't' to charArrayOf('r', 'y', 'f', 'g', 'h'),
        'y' to charArrayOf('t', 'u', 'g', 'h', 'j'),
        'u' to charArrayOf('y', 'i', 'h', 'j', 'k'),
        'i' to charArrayOf('u', 'o', 'j', 'k', 'l'),
        'o' to charArrayOf('i', 'p', 'k', 'l'),
        'p' to charArrayOf('o', 'l'),
        'a' to charArrayOf('q', 'w', 's', 'z'),
        's' to charArrayOf('q', 'w', 'e', 'a', 'd', 'z', 'x'),
        'd' to charArrayOf('w', 'e', 'r', 's', 'f', 'x', 'c'),
        'f' to charArrayOf('e', 'r', 't', 'd', 'g', 'c', 'v'),
        'g' to charArrayOf('r', 't', 'y', 'f', 'h', 'v', 'b'),
        'h' to charArrayOf('t', 'y', 'u', 'g', 'j', 'b', 'n'),
        'j' to charArrayOf('y', 'u', 'i', 'h', 'k', 'n', 'm'),
        'k' to charArrayOf('u', 'i', 'o', 'j', 'l', 'm'),
        'l' to charArrayOf('i', 'o', 'p', 'k'),
        'z' to charArrayOf('a', 's', 'x'),
        'x' to charArrayOf('s', 'd', 'z', 'c'),
        'c' to charArrayOf('d', 'f', 'x', 'v'),
        'v' to charArrayOf('f', 'g', 'c', 'b'),
        'b' to charArrayOf('g', 'h', 'v', 'n'),
        'n' to charArrayOf('h', 'j', 'b', 'm'),
        'm' to charArrayOf('j', 'k', 'n')
    )

    fun extractActiveWordPrefix(ic: InputConnection?, maxChars: Int = 32): String {
        val conn = ic ?: return ""
        val before = try {
            conn.getTextBeforeCursor(maxChars, 0)?.toString() ?: ""
        } catch (_: Throwable) {
            ""
        }
        if (before.isEmpty()) return ""
        val lastBreak = before.indexOfLast { it.isWhitespace() || !it.isLetterOrDigit() }
        return if (lastBreak >= 0) before.substring(lastBreak + 1) else before
    }

    fun extractPreviousWord(ic: InputConnection?, maxChars: Int = 64): String {
        val conn = ic ?: return ""
        val before = try {
            conn.getTextBeforeCursor(maxChars, 0)?.toString() ?: ""
        } catch (_: Throwable) {
            ""
        }
        val trimmed = before.trimEnd()
        if (trimmed.isEmpty()) return ""
        val lastBreak = trimmed.indexOfLast { it.isWhitespace() || !it.isLetterOrDigit() }
        return if (lastBreak >= 0) trimmed.substring(lastBreak + 1) else trimmed
    }

    fun commitText(ic: InputConnection?, text: CharSequence, newCursorPosition: Int = 1): Boolean {
        val conn = ic ?: return false
        return try {
            conn.commitText(text, newCursorPosition)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun commitCharFast(ic: InputConnection?, ch: Char): Boolean {
        return commitText(ic, ch.toString(), 1)
    }

    fun sendCtrlChar(ic: InputConnection?, ch: Char): Boolean {
        val conn = ic ?: return false
        val upper = ch.uppercaseChar()
        if (upper in 'A'..'Z') {
            val ctrlCode = (upper.code - 'A'.code + 1).toChar().toString()
            return commitText(conn, ctrlCode, 1)
        }
        return commitText(conn, ch.toString(), 1)
    }

    fun tryConvertDoubleSpaceToPeriod(
        ic: InputConnection?,
        enabled: Boolean = true,
        nowMs: Long = System.currentTimeMillis(),
        lastSpaceMs: Long = 0L,
        windowMs: Long = 450L
    ): Boolean {
        if (!enabled) return false
        val conn = ic ?: return false
        if (lastSpaceMs > 0L && (nowMs - lastSpaceMs) > windowMs) return false
        return try {
            val before = conn.getTextBeforeCursor(3, 0)?.toString() ?: return false
            if (before.length >= 2 && before.endsWith(" ") && before[before.length - 2].isLetterOrDigit()) {
                conn.deleteSurroundingText(1, 0)
                conn.commitText(". ", 1)
                true
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun replaceActiveWordWithSuggestion(
        ic: InputConnection?,
        suggestion: String,
        appendSpace: Boolean = true
    ): Boolean {
        val conn = ic ?: return false
        if (suggestion.isEmpty()) return false
        return try {
            val prefix = extractActiveWordPrefix(conn, 32)
            if (prefix.isNotEmpty()) {
                conn.deleteSurroundingText(prefix.length, 0)
            }
            val finalText = if (appendSpace) "$suggestion " else suggestion
            conn.commitText(finalText, 1)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun sendBackspace(ic: InputConnection?): Boolean {
        return sendKeyCode(ic, KeyEvent.KEYCODE_DEL)
    }

    fun deleteWordBeforeCursor(ic: InputConnection?, maxChars: Int = 64): Boolean {
        val conn = ic ?: return false
        return try {
            val before = conn.getTextBeforeCursor(maxChars, 0)?.toString() ?: return false
            if (before.isEmpty()) return false
            var idx = before.length - 1
            while (idx >= 0 && before[idx].isWhitespace()) idx--
            while (idx >= 0 && !before[idx].isWhitespace()) idx--
            val deleteLen = (before.length - 1 - idx).coerceAtLeast(1)
            conn.deleteSurroundingText(deleteLen, 0)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun performEditorAction(ic: InputConnection?, actionId: Int): Boolean {
        val conn = ic ?: return false
        return try {
            conn.performEditorAction(actionId)
        } catch (_: Throwable) {
            false
        }
    }

    fun sendKeyCode(ic: InputConnection?, keyCode: Int): Boolean {
        val conn = ic ?: return false
        return try {
            conn.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            conn.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun sendKey(ic: InputConnection?, keyCode: Int): Boolean = sendKeyCode(ic, keyCode)

    fun resolveNearestNeighbor(
        tapX: Float,
        tapY: Float,
        primaryChar: Char,
        candidates: List<Triple<Char, Float, Float>>,
        maxDriftPx: Float
    ): Char {
        if (candidates.isEmpty() || maxDriftPx <= 0f) return primaryChar
        val lowerPrimary = primaryChar.lowercaseChar()
        val allowedNeighbors = neighborMap[lowerPrimary] ?: return primaryChar

        val primaryCenter = candidates.firstOrNull { it.first == primaryChar }
        val primaryDist = if (primaryCenter != null) {
            hypot(tapX - primaryCenter.second, tapY - primaryCenter.third)
        } else {
            maxDriftPx * 0.5f
        }

        if (primaryDist <= maxDriftPx * 0.45f) return primaryChar

        var bestChar = primaryChar
        var bestDist = primaryDist

        for ((candChar, cx, cy) in candidates) {
            if (candChar.lowercaseChar() !in allowedNeighbors) continue
            val d = hypot(tapX - cx, tapY - cy)
            if (d <= maxDriftPx && d < bestDist * 0.68f) {
                bestDist = d
                bestChar = candChar
            }
        }
        return bestChar
    }
}
