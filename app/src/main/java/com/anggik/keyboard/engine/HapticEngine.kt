package com.anggik.keyboard.engine

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

enum class HapticProfile {
    KEY_CHAR,
    KEY_ACTION,
    TRACKPAD_TICK,
    UI_TAP
}

object HapticEngine {

    @Volatile
    private var cachedVibrator: Vibrator? = null

    @Volatile
    private var primitiveSupportChecked = false

    @Volatile
    private var supportsClickAndTick = false

    @Volatile
    private var supportsLowTick = false

    private fun resolveVibrator(context: Context): Vibrator? {
        cachedVibrator?.let { return it }
        val appCtx = context.applicationContext
        val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = appCtx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appCtx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vib != null) {
            cachedVibrator = vib
            if (!primitiveSupportChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                primitiveSupportChecked = true
                try {
                    supportsClickAndTick = vib.areAllPrimitivesSupported(
                        VibrationEffect.Composition.PRIMITIVE_CLICK,
                        VibrationEffect.Composition.PRIMITIVE_TICK
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        supportsLowTick = vib.areAllPrimitivesSupported(
                            VibrationEffect.Composition.PRIMITIVE_LOW_TICK
                        )
                    }
                } catch (_: Exception) {
                    supportsClickAndTick = false
                    supportsLowTick = false
                }
            }
        }
        return vib
    }

    fun currentPercent(context: Context): Int {
        val dpCtx = context.applicationContext.createDeviceProtectedStorageContext()
        val prefs = dpCtx.getSharedPreferences("anggi_kb_state", Context.MODE_PRIVATE)
        if (prefs.contains("haptic_percent")) {
            return prefs.getInt("haptic_percent", 65).coerceIn(0, 100)
        }
        if (prefs.contains("haptic_strength")) {
            return when (prefs.getInt("haptic_strength", 2)) {
                0 -> 0
                1 -> 30
                3 -> 95
                else -> 65
            }
        }
        return if (prefs.getBoolean("haptic_enabled", true)) 65 else 0
    }

    fun setPercent(context: Context, percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        val dpCtx = context.applicationContext.createDeviceProtectedStorageContext()
        dpCtx.getSharedPreferences("anggi_kb_state", Context.MODE_PRIVATE)
            .edit()
            .putInt("haptic_percent", clamped)
            .putBoolean("haptic_enabled", clamped > 0)
            .apply()
    }

    fun describeIntensity(percent: Int): String = when {
        percent <= 0 -> "Mati (0%)"
        percent <= 25 -> "Ultra Halus ($percent%)"
        percent <= 55 -> "Ringan ($percent%)"
        percent <= 80 -> "Mekanikal ($percent%)"
        else -> "Tegas Linear ($percent%)"
    }

    fun fire(view: View, profile: HapticProfile = HapticProfile.UI_TAP) {
        if (!fire(view.context, profile)) {
            val fallbackFlag = when (profile) {
                HapticProfile.TRACKPAD_TICK -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    HapticFeedbackConstants.TEXT_HANDLE_MOVE
                } else {
                    HapticFeedbackConstants.CLOCK_TICK
                }
                else -> HapticFeedbackConstants.KEYBOARD_TAP
            }
            view.performHapticFeedback(fallbackFlag)
        }
    }

    fun fire(context: Context, profile: HapticProfile = HapticProfile.KEY_CHAR): Boolean {
        return fireWithPercent(context, profile, currentPercent(context))
    }

    fun fireWithPercent(context: Context, profile: HapticProfile, percent: Int): Boolean {
        val clamped = percent.coerceIn(0, 100)
        if (clamped <= 0) return true

        val vibrator = resolveVibrator(context) ?: return false
        if (!vibrator.hasVibrator()) return false

        val norm = clamped / 100f
        return try {
            val effect = buildEffect(profile, norm) ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val attrs = VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_TOUCH)
                    .build()
                vibrator.vibrate(effect, attrs)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun buildEffect(profile: HapticProfile, norm: Float): VibrationEffect? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && supportsClickAndTick) {
            val comp = VibrationEffect.startComposition()
            when (profile) {
                HapticProfile.KEY_CHAR -> {
                    val scale = (0.12f + (0.76f * norm)).coerceIn(0.10f, 1.0f)
                    comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, scale)
                }
                HapticProfile.KEY_ACTION -> {
                    val primary = (0.20f + (0.80f * norm)).coerceIn(0.18f, 1.0f)
                    val tail = (0.10f + (0.35f * norm)).coerceIn(0.08f, 0.55f)
                    comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, primary)
                    comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, tail, 9)
                }
                HapticProfile.TRACKPAD_TICK -> {
                    val scale = (0.10f + (0.52f * norm)).coerceIn(0.08f, 0.72f)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && supportsLowTick) {
                        comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, scale)
                    } else {
                        comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, scale)
                    }
                }
                HapticProfile.UI_TAP -> {
                    val scale = (0.15f + (0.72f * norm)).coerceIn(0.12f, 0.95f)
                    comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, scale)
                }
            }
            return comp.compose()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val predefined = when {
                norm < 0.30f -> VibrationEffect.EFFECT_TICK
                profile == HapticProfile.KEY_ACTION && norm > 0.65f -> VibrationEffect.EFFECT_HEAVY_CLICK
                profile == HapticProfile.TRACKPAD_TICK -> VibrationEffect.EFFECT_TICK
                else -> VibrationEffect.EFFECT_CLICK
            }
            return VibrationEffect.createPredefined(predefined)
        }

        val baseMs = when (profile) {
            HapticProfile.KEY_ACTION -> 15L
            HapticProfile.TRACKPAD_TICK -> 5L
            else -> 9L
        }
        val ms = (baseMs * (0.45f + 0.75f * norm)).toLong().coerceIn(3L, 20L)
        val amp = (35 + (220 * norm)).toInt().coerceIn(30, 255)
        return VibrationEffect.createOneShot(ms, amp)
    }

    fun diagnostics(context: Context): String {
        val vib = resolveVibrator(context) ?: return "Motor: Tidak tersedia"
        val ampCtrl = vib.hasAmplitudeControl()
        val mode = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && supportsClickAndTick -> "Linear Composition (API ${Build.VERSION.SDK_INT})"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> "Predefined Waveform"
            else -> "OneShot Amplitude"
        }
        return "$mode • Amplitude=$ampCtrl"
    }
}
