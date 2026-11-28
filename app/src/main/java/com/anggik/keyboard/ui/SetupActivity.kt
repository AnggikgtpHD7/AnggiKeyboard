package com.anggik.keyboard.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import com.anggik.keyboard.engine.HapticEngine
import com.anggik.keyboard.engine.HapticProfile
import com.anggik.keyboard.engine.SnippetEngine
import com.anggik.keyboard.engine.StateBackupManager
import com.anggik.keyboard.service.AnggiKeyboardService
import java.io.File

class SetupActivity : Activity() {

    private lateinit var rootHost: FrameLayout
    private var statusBadgeView: TextView? = null
    private var statusDetailView: TextView? = null
    private var dictCountView: TextView? = null
    private var backCallback: Any? = null

    private val dpContext by lazy {
        createDeviceProtectedStorageContext()
    }

    private val devicePrefs by lazy {
        dpContext.getSharedPreferences("anggi_kb_state", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val cb = OnBackInvokedCallback { finish() }
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                cb
            )
            backCallback = cb
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SplashBridge.attachExitAnimation(this)
        }

        rootHost = FrameLayout(this)
        setContentView(rootHost)
        rebuildSettingsUi()

        if (savedInstanceState == null) {
            val amoled = devicePrefs.getBoolean("amoled_dark", false)
            val overlay = LaunchSplashOverlay(this, SettingsPalette.fromContext(this@SetupActivity, amoled))
            rootHost.addView(
                overlay,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            overlay.playAndDismiss(rootHost)
        }
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            (backCallback as? OnBackInvokedCallback)?.let {
                onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it)
            }
        }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        refreshSystemStatus()
    }

    @Suppress("DEPRECATION")
    private fun rebuildSettingsUi() {
        val amoled = devicePrefs.getBoolean("amoled_dark", false)
        val palette = SettingsPalette.fromContext(this@SetupActivity, amoled)
        window.statusBarColor = palette.bgCanvas
        window.navigationBarColor = palette.bgCanvas

        val padH = UiKit.dp(this@SetupActivity, 14f)
        val padV = UiKit.dp(this@SetupActivity, 12f)

        val scroll = ScrollView(this@SetupActivity).apply {
            isVerticalScrollBarEnabled = false
            setBackgroundColor(palette.bgCanvas)
        }

        val content = LinearLayout(this@SetupActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, padV, padH, padV)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            scroll.setOnApplyWindowInsetsListener { _, insets ->
                val bars = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                )
                content.setPadding(
                    padH + bars.left,
                    padV + bars.top,
                    padH + bars.right,
                    padV + bars.bottom + UiKit.dp(this@SetupActivity, 12f)
                )
                insets
            }
        }

        // --- COMPACT HEADER ---
        val headerRow = LinearLayout(this@SetupActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                UiKit.dp(this@SetupActivity, 2f),
                UiKit.dp(this@SetupActivity, 2f),
                UiKit.dp(this@SetupActivity, 2f),
                UiKit.dp(this@SetupActivity, 8f)
            )
        }

        val titleCol = LinearLayout(this@SetupActivity).apply {
            orientation = LinearLayout.VERTICAL
        }

        val appTitle = TextView(this@SetupActivity).apply {
            text = "AnggiKeyboard"
            textSize = 20f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(palette.textPrimary)
        }

        val appSub = TextView(this@SetupActivity).apply {
            text = "Universal Canvas IME • API 26–36"
            textSize = 12f
            setTextColor(palette.textSecondary)
        }

        titleCol.addView(appTitle)
        titleCol.addView(appSub)

        val badge = TextView(this@SetupActivity).apply {
            textSize = 11f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(
                UiKit.dp(this@SetupActivity, 10f),
                UiKit.dp(this@SetupActivity, 5f),
                UiKit.dp(this@SetupActivity, 10f),
                UiKit.dp(this@SetupActivity, 5f)
            )
        }
        statusBadgeView = badge

        headerRow.addView(titleCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        headerRow.addView(badge)
        content.addView(headerRow)

        // --- CARD 1: AKTIVASI SISTEM ---
        val activationCard = UiKit.bentoCard(this@SetupActivity, palette)
        val detailTv = TextView(this@SetupActivity).apply {
            textSize = 12f
            setTextColor(palette.textSecondary)
            setPadding(0, 0, 0, UiKit.dp(this@SetupActivity, 10f))
        }
        statusDetailView = detailTv
        activationCard.addView(detailTv)

        val actionBtnRow = LinearLayout(this@SetupActivity).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val btnEnable = compactActionPill(
            label = "1. Aktifkan IME",
            bg = palette.accentSoft,
            fg = palette.accentPrimary,
            ripple = palette.accentPrimary
        ) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        val btnSelect = compactActionPill(
            label = "2. Set Default",
            bg = palette.accentPrimary,
            fg = palette.textOnAccent,
            ripple = 0x33FFFFFF
        ) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }

        actionBtnRow.addView(
            btnEnable,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = UiKit.dp(this@SetupActivity, 6f)
            }
        )
        actionBtnRow.addView(
            btnSelect,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = UiKit.dp(this@SetupActivity, 6f)
            }
        )
        activationCard.addView(actionBtnRow)
        content.addView(activationCard)

        // --- CARD 2: TAMPILAN & ERGONOMI ---
        content.addView(UiKit.sectionLabel(this@SetupActivity, "Tampilan & Ergonomi", palette))
        val visualCard = UiKit.bentoCard(this@SetupActivity, palette)

        visualCard.addView(
            buildStackedPrefRow(
                title = "Tema Permukaan",
                subtitle = "AMOLED menghemat daya piksel layar",
                palette = palette,
                control = UiKit.segmentedControl(
                    ctx = this@SetupActivity,
                    options = listOf("Mint Light", "AMOLED Black"),
                    selectedIndex = if (amoled) 1 else 0,
                    palette = palette
                ) { idx ->
                    val nextAmoled = idx == 1
                    if (devicePrefs.getBoolean("amoled_dark", false) != nextAmoled) {
                        devicePrefs.edit().putBoolean("amoled_dark", nextAmoled).apply()
                        rebuildSettingsUi()
                    }
                }
            )
        )

        visualCard.addView(UiKit.divider(this@SetupActivity, palette))

        val curAccent = devicePrefs.getInt("accent_id", 0)
        visualCard.addView(
            buildStackedPrefRow(
                title = "Warna Aksen & Monet",
                subtitle = "Monet mengikuti palet wallpaper sistem Android 12–16",
                palette = palette,
                control = UiKit.segmentedControl(
                    ctx = this@SetupActivity,
                    options = listOf("Emerald", "Cyan", "Amber", "Crimson", "Monet"),
                    selectedIndex = curAccent,
                    palette = palette
                ) { idx ->
                    if (devicePrefs.getInt("accent_id", 0) != idx) {
                        devicePrefs.edit().putInt("accent_id", idx).apply()
                        rebuildSettingsUi()
                    }
                }
            )
        )

        visualCard.addView(UiKit.divider(this@SetupActivity, palette))

        val curScale = devicePrefs.getFloat("height_scale", 1.0f)
        val scaleIdx = when {
            curScale < 0.95f -> 0
            curScale > 1.05f -> 2
            else -> 1
        }
        visualCard.addView(
            buildStackedPrefRow(
                title = "Tinggi Tombol",
                subtitle = "Proporsi vertikal area ketik",
                palette = palette,
                control = UiKit.segmentedControl(
                    ctx = this@SetupActivity,
                    options = listOf("90%", "100%", "110%"),
                    selectedIndex = scaleIdx,
                    palette = palette
                ) { idx ->
                    val valScale = when (idx) {
                        0 -> 0.9f
                        2 -> 1.1f
                        else -> 1.0f
                    }
                    devicePrefs.edit().putFloat("height_scale", valScale).apply()
                }
            )
        )

        visualCard.addView(UiKit.divider(this@SetupActivity, palette))

        val curHand = devicePrefs.getInt("hand_mode", 0)
        val handSegIdx = when (curHand) {
            2 -> 1
            1 -> 2
            else -> 0
        }
        visualCard.addView(
            buildStackedPrefRow(
                title = "Mode Satu Tangan",
                subtitle = "Lebar 82% dengan panel tukar sisi cepat",
                palette = palette,
                control = UiKit.segmentedControl(
                    ctx = this@SetupActivity,
                    options = listOf("Full", "Kiri", "Kanan"),
                    selectedIndex = handSegIdx,
                    palette = palette
                ) { idx ->
                    val mappedMode = when (idx) {
                        1 -> 2
                        2 -> 1
                        else -> 0
                    }
                    devicePrefs.edit().putInt("hand_mode", mappedMode).apply()
                }
            )
        )
        content.addView(visualCard)

        // --- CARD 3: INPUT & KAMUS LOKAL ---
        content.addView(UiKit.sectionLabel(this@SetupActivity, "Input & Kamus Adaptif", palette))
        val inputCard = UiKit.bentoCard(this@SetupActivity, palette)

        val isId = devicePrefs.getBoolean("lang_id", false)
        inputCard.addView(
            buildStackedPrefRow(
                title = "Bahasa Prediksi & Glide",
                subtitle = "Dapat diganti cepat lewat tahan spasi",
                palette = palette,
                control = UiKit.segmentedControl(
                    ctx = this@SetupActivity,
                    options = listOf("English", "Indonesia"),
                    selectedIndex = if (isId) 1 else 0,
                    palette = palette
                ) { idx ->
                    devicePrefs.edit().putBoolean("lang_id", idx == 1).apply()
                }
            )
        )

        inputCard.addView(UiKit.divider(this@SetupActivity, palette))

        val curHaptic = HapticEngine.currentStrength(this@SetupActivity)
        inputCard.addView(
            buildStackedPrefRow(
                title = "Profil Motor Linear Haptik",
                subtitle = HapticEngine.diagnostics(this@SetupActivity),
                palette = palette,
                control = UiKit.segmentedControl(
                    ctx = this@SetupActivity,
                    options = listOf("Mati", "Ringan", "Mekanikal", "Tegas"),
                    selectedIndex = curHaptic,
                    palette = palette
                ) { idx ->
                    devicePrefs.edit()
                        .putInt("haptic_strength", idx)
                        .putBoolean("haptic_enabled", idx > 0)
                        .apply()
                    if (idx > 0) {
                        HapticEngine.fire(this@SetupActivity, HapticProfile.KEY_ACTION)
                    }
                }
            )
        )

        inputCard.addView(UiKit.divider(this@SetupActivity, palette))

        val countTv = TextView(this@SetupActivity).apply {
            textSize = 11.5f
            setTextColor(palette.textSecondary)
        }
        dictCountView = countTv

        val btnClearDict = compactActionPill(
            label = "Reset",
            bg = palette.bgPillTrack,
            fg = palette.textPrimary,
            ripple = palette.accentPrimary
        ) {
            val f = File(dpContext.filesDir, "learned_words.txt")
            if (f.exists()) f.delete()
            refreshDictionaryCount()
        }

        val dictRow = LinearLayout(this@SetupActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val dictTextCol = LinearLayout(this@SetupActivity).apply {
            orientation = LinearLayout.VERTICAL
        }
        val dictTitle = TextView(this@SetupActivity).apply {
            text = "Kamus Pribadi (Device Protected)"
            textSize = 13.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(palette.textPrimary)
        }
        dictTextCol.addView(dictTitle)
        dictTextCol.addView(countTv)
        dictRow.addView(dictTextCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        dictRow.addView(btnClearDict)
        inputCard.addView(dictRow)

        content.addView(inputCard)

        // --- CARD 3B: FITUR CERDAS & PRODUKTIVITAS ---
        content.addView(UiKit.sectionLabel(this@SetupActivity, "Fitur Cerdas & Produktivitas", palette))
        val smartCard = UiKit.bentoCard(this@SetupActivity, palette)

        val clipVaultOn = devicePrefs.getBoolean("clipboard_vault", true)
        val clipVaultSwitch = ModernSwitchView(this@SetupActivity, clipVaultOn, palette) { checked ->
            devicePrefs.edit().putBoolean("clipboard_vault", checked).apply()
        }
        smartCard.addView(
            buildInlinePrefRow(
                title = "Clipboard Vault & Redaksi OTP",
                subtitle = "Simpan klip tersemat & hapus otomatis data sensitif 60d",
                palette = palette,
                trailing = clipVaultSwitch
            )
        )

        smartCard.addView(UiKit.divider(this@SetupActivity, palette))

        val mathMacroOn = devicePrefs.getBoolean("smart_math_macro", true)
        val mathMacroSwitch = ModernSwitchView(this@SetupActivity, mathMacroOn, palette) { checked ->
            devicePrefs.edit().putBoolean("smart_math_macro", checked).apply()
        }
        smartCard.addView(
            buildInlinePrefRow(
                title = "Kalkulator (=) & Makro Sistem",
                subtitle = "Hitung =128*16 & sisipkan /iso, /uuid, /ip, /dev",
                palette = palette,
                trailing = mathMacroSwitch
            )
        )
        smartCard.addView(UiKit.divider(this@SetupActivity, palette))

        val backupStatusTv = TextView(this@SetupActivity).apply {
            text = "Cadangkan preferensi, kamus & pin klip ke Download"
            textSize = 11.5f
            setTextColor(palette.textSecondary)
            setPadding(0, 0, 0, UiKit.dp(this@SetupActivity, 8f))
        }

        val backupTitleTv = TextView(this@SetupActivity).apply {
            text = "Backup & Restore JSON"
            textSize = 13.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(palette.textPrimary)
        }

        val btnExport = compactActionPill(
            label = "Ekspor JSON",
            bg = palette.accentSoft,
            fg = palette.accentPrimary,
            ripple = palette.accentPrimary
        ) {
            val res = StateBackupManager.exportToDownloads(this@SetupActivity)
            backupStatusTv.text = res.fold(
                onSuccess = { path -> "Tersimpan: $path" },
                onFailure = { err -> "Gagal ekspor: ${err.message}" }
            )
        }

        val btnImport = compactActionPill(
            label = "Impor Terbaru",
            bg = palette.bgPillTrack,
            fg = palette.textPrimary,
            ripple = palette.accentPrimary
        ) {
            val res = StateBackupManager.importLatestFromDownloads(this@SetupActivity)
            res.fold(
                onSuccess = { msg ->
                    rebuildSettingsUi()
                    statusDetailView?.text = msg
                },
                onFailure = { err ->
                    backupStatusTv.text = "Gagal impor: ${err.message}"
                }
            )
        }

        val backupBtnRow = LinearLayout(this@SetupActivity).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        backupBtnRow.addView(
            btnExport,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = UiKit.dp(this@SetupActivity, 6f)
            }
        )
        backupBtnRow.addView(
            btnImport,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = UiKit.dp(this@SetupActivity, 6f)
            }
        )

        smartCard.addView(backupTitleTv)
        smartCard.addView(backupStatusTv)
        smartCard.addView(backupBtnRow)

        content.addView(smartCard)

        // --- CARD 3C: CUSTOM TEXT SNIPPETS ---
        content.addView(UiKit.sectionLabel(this@SetupActivity, "Pintasan Teks Kustom (;trigger)", palette))
        val snippetCard = UiKit.bentoCard(this@SetupActivity, palette)

        val trigInput = EditText(this@SetupActivity).apply {
            hint = "Trigger (misal ;mail)"
            textSize = 12.5f
            setSingleLine(true)
            setTextColor(palette.textPrimary)
            setHintTextColor(palette.textSecondary)
            background = UiKit.roundedBox(palette.bgPillTrack, UiKit.dp(this@SetupActivity, 9f).toFloat())
            setPadding(
                UiKit.dp(this@SetupActivity, 10f),
                UiKit.dp(this@SetupActivity, 7f),
                UiKit.dp(this@SetupActivity, 10f),
                UiKit.dp(this@SetupActivity, 7f)
            )
        }

        val expInput = EditText(this@SetupActivity).apply {
            hint = "Teks pengganti lengkap..."
            textSize = 12.5f
            setSingleLine(true)
            setTextColor(palette.textPrimary)
            setHintTextColor(palette.textSecondary)
            background = UiKit.roundedBox(palette.bgPillTrack, UiKit.dp(this@SetupActivity, 9f).toFloat())
            setPadding(
                UiKit.dp(this@SetupActivity, 10f),
                UiKit.dp(this@SetupActivity, 7f),
                UiKit.dp(this@SetupActivity, 10f),
                UiKit.dp(this@SetupActivity, 7f)
            )
        }

        val snippetListHost = LinearLayout(this@SetupActivity).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun renderSnippetItems() {
            snippetListHost.removeAllViews()
            val saved = SnippetEngine.getAll(this@SetupActivity)
            if (saved.isEmpty()) {
                val emptyHint = TextView(this@SetupActivity).apply {
                    text = "Belum ada snippet • Ketik trigger di atas untuk ekspansi instan"
                    textSize = 11f
                    setTextColor(palette.textSecondary)
                    setPadding(0, UiKit.dp(this@SetupActivity, 6f), 0, 0)
                }
                snippetListHost.addView(emptyHint)
                return
            }
            saved.take(6).forEach { snip ->
                val row = LinearLayout(this@SetupActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, UiKit.dp(this@SetupActivity, 6f), 0, 0)
                }
                val labelTv = TextView(this@SetupActivity).apply {
                    text = "${snip.trigger}  →  ${snip.expansion}"
                    textSize = 12f
                    maxLines = 1
                    setTextColor(palette.textPrimary)
                }
                val delPill = compactActionPill(
                    label = "✕",
                    bg = palette.bgPillTrack,
                    fg = palette.textSecondary,
                    ripple = palette.accentPrimary
                ) {
                    SnippetEngine.remove(this@SetupActivity, snip.trigger)
                    renderSnippetItems()
                }
                row.addView(labelTv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(delPill)
                snippetListHost.addView(row)
            }
        }

        val addSnippetBtn = compactActionPill(
            label = "+ Simpan",
            bg = palette.accentPrimary,
            fg = palette.textOnAccent,
            ripple = 0x33FFFFFF
        ) {
            if (SnippetEngine.upsert(this@SetupActivity, trigInput.text.toString(), expInput.text.toString())) {
                trigInput.setText("")
                expInput.setText("")
                renderSnippetItems()
            }
        }

        val inputRow = LinearLayout(this@SetupActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        inputRow.addView(
            trigInput,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.36f).apply {
                marginEnd = UiKit.dp(this@SetupActivity, 6f)
            }
        )
        inputRow.addView(
            expInput,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.64f)
        )

        snippetCard.addView(inputRow)
        snippetCard.addView(
            addSnippetBtn,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = UiKit.dp(this@SetupActivity, 7f) }
        )
        snippetCard.addView(snippetListHost)
        renderSnippetItems()
        content.addView(snippetCard)

        // --- CARD 4: SANDBOX UJI KETIK ---
        content.addView(UiKit.sectionLabel(this@SetupActivity, "Sandbox Uji Langsung", palette))
        val sandboxCard = UiKit.bentoCard(this@SetupActivity, palette)

        val testText = EditText(this@SetupActivity).apply {
            hint = "Ketik, swipe/glide, atau coba makro shell..."
            textSize = 13.5f
            setTextColor(palette.textPrimary)
            setHintTextColor(palette.textSecondary)
            gravity = Gravity.TOP or Gravity.START
            minLines = 2
            imeOptions = EditorInfo.IME_ACTION_DONE
            background = UiKit.roundedBox(
                palette.bgPillTrack,
                UiKit.dp(this@SetupActivity, 10f).toFloat()
            )
            setPadding(
                UiKit.dp(this@SetupActivity, 12f),
                UiKit.dp(this@SetupActivity, 9f),
                UiKit.dp(this@SetupActivity, 12f),
                UiKit.dp(this@SetupActivity, 9f)
            )
        }

        val testNum = EditText(this@SetupActivity).apply {
            hint = "Kolom angka (uji auto-Numpad 0–9)..."
            textSize = 13.5f
            setTextColor(palette.textPrimary)
            setHintTextColor(palette.textSecondary)
            inputType = InputType.TYPE_CLASS_NUMBER
            imeOptions = EditorInfo.IME_ACTION_GO
            background = UiKit.roundedBox(
                palette.bgPillTrack,
                UiKit.dp(this@SetupActivity, 10f).toFloat()
            )
            setPadding(
                UiKit.dp(this@SetupActivity, 12f),
                UiKit.dp(this@SetupActivity, 9f),
                UiKit.dp(this@SetupActivity, 12f),
                UiKit.dp(this@SetupActivity, 9f)
            )
        }

        sandboxCard.addView(testText)
        sandboxCard.addView(
            testNum,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = UiKit.dp(this@SetupActivity, 8f) }
        )
        content.addView(sandboxCard)

        scroll.addView(content)
        rootHost.removeAllViews()
        rootHost.addView(
            scroll,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        refreshSystemStatus()
        refreshDictionaryCount()
    }

    private fun compactActionPill(
        label: String,
        bg: Int,
        fg: Int,
        ripple: Int,
        onClick: () -> Unit
    ): TextView = TextView(this@SetupActivity).apply {
        text = label
        textSize = 12.5f
        gravity = Gravity.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        setTextColor(fg)
        background = UiKit.ripplePill(bg, ripple, UiKit.dp(this@SetupActivity, 10f).toFloat())
        setPadding(
            UiKit.dp(this@SetupActivity, 14f),
            UiKit.dp(this@SetupActivity, 9f),
            UiKit.dp(this@SetupActivity, 14f),
            UiKit.dp(this@SetupActivity, 9f)
        )
        setOnClickListener {
            HapticEngine.fire(this, HapticProfile.UI_TAP)
            onClick()
        }
    }

    private fun buildStackedPrefRow(
        title: String,
        subtitle: String,
        palette: SettingsPalette,
        control: View
    ): LinearLayout = LinearLayout(this@SetupActivity).apply {
        orientation = LinearLayout.VERTICAL
        val tvTitle = TextView(this@SetupActivity).apply {
            text = title
            textSize = 13.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(palette.textPrimary)
        }
        val tvSub = TextView(this@SetupActivity).apply {
            text = subtitle
            textSize = 11.5f
            setTextColor(palette.textSecondary)
            setPadding(0, UiKit.dp(this@SetupActivity, 1f), 0, UiKit.dp(this@SetupActivity, 7f))
        }
        addView(tvTitle)
        addView(tvSub)
        addView(control)
    }

    private fun buildInlinePrefRow(
        title: String,
        subtitle: String,
        palette: SettingsPalette,
        trailing: View
    ): LinearLayout = LinearLayout(this@SetupActivity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val textCol = LinearLayout(this@SetupActivity).apply {
            orientation = LinearLayout.VERTICAL
        }
        val tvTitle = TextView(this@SetupActivity).apply {
            text = title
            textSize = 13.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(palette.textPrimary)
        }
        val tvSub = TextView(this@SetupActivity).apply {
            text = subtitle
            textSize = 11.5f
            setTextColor(palette.textSecondary)
        }
        textCol.addView(tvTitle)
        textCol.addView(tvSub)
        addView(
            textCol,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = UiKit.dp(this@SetupActivity, 10f)
            }
        )
        addView(trailing)
    }

    private fun refreshDictionaryCount() {
        val learnedFile = File(dpContext.filesDir, "learned_words.txt")
        val count = if (learnedFile.exists()) {
            learnedFile.readLines().count { it.isNotBlank() }
        } else {
            0
        }
        dictCountView?.text = "$count kata tersimpan offline"
    }

    private fun refreshSystemStatus() {
        val amoled = devicePrefs.getBoolean("amoled_dark", false)
        val palette = SettingsPalette.fromContext(this@SetupActivity, amoled)
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val targetId = ComponentName(this@SetupActivity, AnggiKeyboardService::class.java).flattenToShortString()
        val enabled = imm.enabledInputMethodList.any { it.id == targetId }
        val defaultIme = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val isDefault = defaultIme == targetId

        statusBadgeView?.apply {
            text = when {
                enabled && isDefault -> "● AKTIF"
                enabled -> "● TERDAFTAR"
                else -> "● NONAKTIF"
            }
            setTextColor(if (enabled && isDefault) palette.statusOkText else palette.statusWarnText)
            background = UiKit.roundedBox(
                if (enabled && isDefault) palette.statusOkBg else palette.statusWarnBg,
                UiKit.dp(this@SetupActivity, 20f).toFloat()
            )
        }

        statusDetailView?.text = "IME: $targetId\nDefault Sistem: ${defaultIme ?: "Belum dipilih"}"
    }
}
