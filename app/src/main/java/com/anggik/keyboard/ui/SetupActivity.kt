package com.anggik.keyboard.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.anggik.keyboard.service.AnggiKeyboardService

class SetupActivity : Activity() {

    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dp = resources.displayMetrics.density
        val pad = (20 * dp).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
            setBackgroundColor(0xFFEFF2F0.toInt())
        }

        val title = TextView(this).apply {
            text = "AnggiKeyboard Universal (API 26–36)"
            textSize = 20f
            setTextColor(0xFF121413.toInt())
        }

        statusView = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFF4B504D.toInt())
            setPadding(0, (12 * dp).toInt(), 0, (16 * dp).toInt())
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

        val testInput = EditText(this).apply {
            hint = "Ketuk di sini untuk uji ketik..."
            gravity = Gravity.TOP or Gravity.START
            minLines = 3
            setBackgroundColor(0xFFFFFFFF.toInt())
            setPadding((12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
        }

        root.addView(title)
        root.addView(statusView)
        root.addView(btnEnable)
        root.addView(btnSelect)
        root.addView(testInput)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val targetId = ComponentName(this, AnggiKeyboardService::class.java).flattenToShortString()
        val enabled = imm.enabledInputMethodList.any { it.id == targetId }
        val defaultIme = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        statusView.text = "IME ID: $targetId\nTerdaftar: ${if (enabled) "AKTIF" else "BELUM"}\nDefault Aktif: ${defaultIme ?: "-"}"
    }
}
