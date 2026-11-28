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

    fun currentStrength(context: Context): Int {
        val dpCtx = context.applicationContext.createDeviceProtectedStorageContext()
        val prefs = dpCtx.getSharedPreferences("anggi_kb_state", Context.MODE_PRIVATE)
        if (prefs.contains("haptic_strength")) {
            return prefs.getInt("haptic_strength", 2).coerceIn(0, 3)
        }
        return if (prefs.getBoolean("haptic_enabled", true)) 2 else 0
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
        val strength = currentStrength(context)
        if (strength <= 0) return true

        val vibrator = resolveVibrator(context) ?: return false
        if (!vibrator.hasVibrator()) return false

        val gain = when (strength) {
            1 -> 0.62f
            3 -> 1.32f
            else -> 1.0f
        }

        return try {
            val effect = buildEffect(profile, gain) ?: return false
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

    private fun buildEffect(profile: HapticProfile, gain: Float): VibrationEffect? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && supportsClickAndTick) {
            val comp = VibrationEffect.startComposition()
            when (profile) {
                HapticProfile.KEY_CHAR -> {
                    val scale = (0.44f * gain).coerceIn(0.15f, 1.0f)
                    comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, scale)
                }
                HapticProfile.KEY_ACTION -> {
                    val primary = (0.76f * gain).coerceIn(0.25f, 1.0f)
                    val tail = (0.24f * gain).coerceIn(0.10f, 0.60f)
                    comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, primary)
                    comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, tail, 10)
                }
                HapticProfile.TRACKPAD_TICK -> {
                    val scale = (0.28f * gain).coerceIn(0.12f, 0.85f)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && supportsLowTick) {
                        comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, scale)
                    } else {
                        comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, scale)
                    }
                }
                HapticProfile.UI_TAP -> {
                    val scale = (0.52f * gain).coerceIn(0.18f, 1.0f)
                    comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, scale)
                }
            }
            return comp.compose()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val predefined = when (profile) {
                HapticProfile.KEY_ACTION -> VibrationEffect.EFFECT_HEAVY_CLICK
                HapticProfile.TRACKPAD_TICK -> VibrationEffect.EFFECT_TICK
                else -> VibrationEffect.EFFECT_CLICK
            }
            return VibrationEffect.createPredefined(predefined)
        }

        val ms = when (profile) {
            HapticProfile.KEY_ACTION -> 14L
            HapticProfile.TRACKPAD_TICK -> 5L
            else -> 8L
        }
        val amp = ((when (profile) {
            HapticProfile.KEY_ACTION -> 175
            HapticProfile.TRACKPAD_TICK -> 75
            else -> 115
        }) * gain).toInt().coerceIn(30, 255)

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
