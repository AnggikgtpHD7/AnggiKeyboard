package com.anggik.keyboard.engine

import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import kotlin.math.hypot

object FastInputDispatcher {

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

    fun commitCharFast(ic: InputConnection?, ch: Char): Boolean {
        val conn = ic ?: return false
        return try {
            conn.commitText(ch.toString(), 1)
            true
        } catch (_: Throwable) {
            false
        }
    }

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

        // Hanya koreksi bila titik sentuh jelas berada di tepi luar pusat tombol utama
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

    fun sendKey(ic: InputConnection?, keyCode: Int): Boolean {
        val conn = ic ?: return false
        return try {
            conn.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            conn.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            true
        } catch (_: Throwable) {
            false
        }
    }
}
