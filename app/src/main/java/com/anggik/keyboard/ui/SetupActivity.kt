package com.anggik.keyboard.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.anggik.keyboard.service.AnggiKeyboardService

class SetupActivity : Activity() {

    private lateinit var statusView: TextView
    private lateinit var prefSummaryView: TextView

    private val devicePrefs by lazy {
        createDeviceProtectedStorageContext().getSharedPreferences("anggi_kb_state", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dp = resources.displayMetrics.density
        val pad = (18 * dp).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(0xFFEFF2F0.toInt())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            root.setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, pad + bars.bottom)
                insets
            }
        }

        val title = TextView(this).apply {
            text = "AnggiKeyboard Universal (API 26–36)"
            textSize = 20f
            setTextColor(0xFF121413.toInt())
        }

        statusView = TextView(this).apply {
            textSize = 13.5f
            setTextColor(0xFF4B504D.toInt())
            setPadding(0, (8 * dp).toInt(), 0, (10 * dp).toInt())
        }

        prefSummaryView = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFF121413.toInt())
            setPadding(0, 0, 0, (10 * dp).toInt())
        }

        val btnEnable = Button(this).apply {
            text = "1. Aktifkan di Pengaturan Keyboard"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        }

        val btnSelect = Button(this).apply {
            text = "2. Pilih AnggiKeyboard sebagai Default"
            setOnClickListener {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
        }

        val btnTheme = Button(this).apply {
            text = "3. Ganti Tema (Mint Light / AMOLED Black)"
            setOnClickListener {
                val next = !devicePrefs.getBoolean("amoled_dark", false)
                devicePrefs.edit().putBoolean("amoled_dark", next).apply()
                refreshPrefSummary()
            }
        }

        val btnScale = Button(this).apply {
            text = "4. Siklus Tinggi Tombol (90% / 100% / 110%)"
            setOnClickListener {
                val cur = devicePrefs.getFloat("height_scale", 1.0f)
                val next = when {
                    cur < 0.95f -> 1.0f
                    cur < 1.05f -> 1.1f
                    else -> 0.9f
                }
                devicePrefs.edit().putFloat("height_scale", next).apply()
                refreshPrefSummary()
            }
        }

        val btnHaptic = Button(this).apply {
            text = "5. Toggle Getaran Haptik"
            setOnClickListener {
                val next = !devicePrefs.getBoolean("haptic_enabled", true)
                devicePrefs.edit().putBoolean("haptic_enabled", next).apply()
                refreshPrefSummary()
            }
        }

        val testText = EditText(this).apply {
            hint = "Uji ketik, swipe/glide typing & prediksi kata..."
            gravity = Gravity.TOP or Gravity.START
            minLines = 2
            setBackgroundColor(0xFFFFFFFF.toInt())
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
        }

        val testNumber = EditText(this).apply {
            hint = "Uji mode Numpad otomatis (0–9)..."
            inputType = InputType.TYPE_CLASS_NUMBER
            setBackgroundColor(0xFFFFFFFF.toInt())
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
        }

        val gapLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (8 * dp).toInt() }

        root.addView(title)
        root.addView(statusView)
        root.addView(prefSummaryView)
        root.addView(btnEnable)
        root.addView(btnSelect)
        root.addView(btnTheme)
        root.addView(btnScale)
        root.addView(btnHaptic)
        root.addView(testText, gapLp)
        root.addView(testNumber, gapLp)
        setContentView(root)
        refreshPrefSummary()
    }

    private fun refreshPrefSummary() {
        val amoled = devicePrefs.getBoolean("amoled_dark", false)
        val scale = (devicePrefs.getFloat("height_scale", 1.0f) * 100).toInt()
        val haptic = devicePrefs.getBoolean("haptic_enabled", true)
        prefSummaryView.text = "Tema: ${if (amoled) "AMOLED True Black" else "Mint Light"} | Skala Tinggi: ${scale}% | Haptik: ${if (haptic) "ON" else "OFF"}"
    }

    override fun onResume() {
        super.onResume()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val targetId = ComponentName(this, AnggiKeyboardService::class.java).flattenToShortString()
        val enabled = imm.enabledInputMethodList.any { it.id == targetId }
        val defaultIme = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        statusView.text = "IME ID: $targetId\nTerdaftar: ${if (enabled) "AKTIF" else "BELUM"}\nDefault Aktif: ${defaultIme ?: "-"}"
        refreshPrefSummary()
    }
}
