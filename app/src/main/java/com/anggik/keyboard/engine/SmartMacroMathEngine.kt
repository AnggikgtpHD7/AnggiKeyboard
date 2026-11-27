package com.anggik.keyboard.engine

import android.os.Build
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

data class LiveActionChip(
    val triggerLength: Int,
    val badgeLabel: String,
    val replacementText: String
)

object SmartMacroMathEngine {

    data class MacroPreset(
        val trigger: String,
        val label: String,
        val resolver: () -> String
    )

    val presets: List<MacroPreset> = listOf(
        MacroPreset("/iso", "ISO Time") {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())
        },
        MacroPreset("/ts", "Epoch Sec") {
            (System.currentTimeMillis() / 1000L).toString()
        },
        MacroPreset("/uuid", "UUID v4") {
            UUID.randomUUID().toString()
        },
        MacroPreset("/dev", "Device Info") {
            "${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        },
        MacroPreset("/ip", "Local IPv4") {
            resolveLocalIpv4()
        },
        MacroPreset("/rish", "rish Shell") {
            "rish -c 'export PATH=/system/bin:/system/xbin:\$PATH; '"
        }
    )

    fun evaluateLive(textBeforeCursor: String?): LiveActionChip? {
        if (textBeforeCursor.isNullOrEmpty()) return null

        // 1. Cek Dynamic Macro Trigger di akhir kursor
        for (preset in presets) {
            if (textBeforeCursor.endsWith(preset.trigger)) {
                val beforeIdx = textBeforeCursor.length - preset.trigger.length - 1
                if (beforeIdx < 0 || textBeforeCursor[beforeIdx].isWhitespace()) {
                    val value = preset.resolver()
                    return LiveActionChip(
                        triggerLength = preset.trigger.length,
                        badgeLabel = "${preset.trigger} → ${value.take(22)}",
                        replacementText = value
                    )
                }
            }
        }

        // 2. Cek Inline Math Expression diawali '=' (contoh: =128*16+64)
        val eqIdx = textBeforeCursor.lastIndexOf('=')
        if (eqIdx >= 0) {
            val validBoundary = eqIdx == 0 || textBeforeCursor[eqIdx - 1].isWhitespace()
            val rawExpr = textBeforeCursor.substring(eqIdx + 1)
            if (validBoundary && rawExpr.length in 2..48 && rawExpr.any { it in "+-*/%" }) {
                val result = evalMath(rawExpr)
                if (result != null) {
                    val fullLen = rawExpr.length + 1
                    return LiveActionChip(
                        triggerLength = fullLen,
                        badgeLabel = "= $result",
                        replacementText = result
                    )
                }
            }
        }

        return null
    }

    fun evalMath(expr: String): String? {
        val clean = expr.replace(" ", "")
        if (clean.isEmpty()) return null
        return try {
            val parser = Parser(clean)
            val value = parser.parseExpression()
            if (parser.pos != clean.length || value.isNaN() || value.isInfinite()) {
                null
            } else {
                formatNumber(value)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun formatNumber(value: Double): String {
        val bd = BigDecimal.valueOf(value)
            .setScale(6, RoundingMode.HALF_UP)
            .stripTrailingZeros()
        return bd.toPlainString()
    }

    private fun resolveLocalIpv4(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: continue
                    }
                }
            }
            "127.0.0.1"
        } catch (_: Exception) {
            "127.0.0.1"
        }
    }

    private class Parser(private val s: String) {
        var pos = 0

        fun parseExpression(): Double {
            var x = parseTerm()
            while (pos < s.length) {
                when (s[pos]) {
                    '+' -> { pos++; x += parseTerm() }
                    '-' -> { pos++; x -= parseTerm() }
                    else -> return x
                }
            }
            return x
        }

        private fun parseTerm(): Double {
            var x = parseFactor()
            while (pos < s.length) {
                when (s[pos]) {
                    '*' -> { pos++; x *= parseFactor() }
                    '/' -> {
                        pos++
                        val d = parseFactor()
                        if (d == 0.0) return Double.NaN
                        x /= d
                    }
                    '%' -> {
                        pos++
                        val d = parseFactor()
                        if (d == 0.0) return Double.NaN
                        x %= d
                    }
                    else -> return x
                }
            }
            return x
        }

        private fun parseFactor(): Double {
            if (pos < s.length) {
                if (s[pos] == '+') { pos++; return parseFactor() }
                if (s[pos] == '-') { pos++; return -parseFactor() }
            }
            val start = pos
            return if (pos < s.length && s[pos] == '(') {
                pos++
                val x = parseExpression()
                if (pos >= s.length || s[pos] != ')') throw IllegalArgumentException("Unclosed paren")
                pos++
                x
            } else {
                while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
                if (start == pos) throw IllegalArgumentException("Unexpected token")
                s.substring(start, pos).toDouble()
            }
        }
    }
}
