# AnggiKeyboard — Absolute Context & Engineering Lock (v2.0.0)

## 1. Environment & Target Hardware
- **Developer:** Anggik (Medan, Sumatera Utara, Indonesia)
- **Target Device:** Infinix GT 30 Pro 5G (`X6873`), MediaTek Dimensity 8300, 144Hz LTPS Display, Android 16 (`API 36`), XOS + Lawnchair (`app.lawnchair.nightly`)
- **Workspace:** Termux Userspace + System Shell (`rish` UID 2000 via Shizuku), Git, GitHub Actions CI/CD (`origin` & `next` -> `main`)

## 2. Non-Negotiable Engineering Policies
- **Zero External Dependencies:** 100% Pure Android SDK (`minSdk = 26`, `compileSdk = 36`, `targetSdk = 36`), nol `androidx.*`.
- **Ignore APK Size / Maximize Speed & Compatibility:** `isMinifyEnabled = false`, `isShrinkResources = false`, `useLegacyPackaging = false` (uncompressed DEX `mmap` pada ART).
- **Direct Boot Storage (`DeviceProtectedStorage`):** Seluruh preferensi (`anggi_kb_state`: `amoled_dark`, `accent_id`, `height_scale`, `hand_mode`, `lang_id`, `haptic_percent`, `haptic_enabled`, `clipboard_vault`, `smart_math_macro`), kamus (`learned_words.txt`), pin clipboard (`pinned_clips.b64`), riwayat simbol (`recent_symbols.txt`), dan snippet (`custom_snippets.tsv`) tersimpan di `createDeviceProtectedStorageContext().filesDir`.
- **Mandatory Post-Build Protocol:** Setiap akhir tahap *build* wajib menyajikan ide penambahan/penerapan terbaru dan mutakhir yang selaras dengan kapabilitas sistem Android terbaru (`Android 16 / API 36`), diikuti **System Risk Analysis**.

## 3. Verified Module Inventory (20 Core Units — v2.0.0)
1. `model/KeyModel.kt` — Definisi model tombol dan koordinat tata letak.
2. `ui/AcceleratedKeyboardSurface.kt` — Permukaan render tombol 144Hz berbasis `Canvas` murni (`LAYER_TYPE_HARDWARE`).
3. `engine/FastInputDispatcher.kt` — Pengirim *commit* ketik berlatensi rendah.
4. `engine/GlideTypingEngine.kt` — Mesin ketik usap (*swipe/glide*) lokal.
5. `engine/LocalWordPredictor.kt` — Prediksi kata lokal & pembelajaran adaptif offline (`EN`/`ID`).
6. `engine/InlineAutofillHelper.kt` — Integrasi `InlineSuggestionsRequest` sistem Android.
7. `ui/UiKit.kt` — Bento UI + `SettingsPalette.fromContext` (*Mint Light*, *AMOLED Black*, 4 aksen, & *Monet* `API 31+`) + `ModernSwitchView` + `ModernSliderView` (`0%–100%`).
8. `ui/SetupActivity.kt` — Antarmuka konfigurasi Bento UI (`API 26–36`, `OnBackInvokedCallback`, slider getar kontinu, sandbox uji ketik & numpad).
9. `ui/SplashBridge.kt` & `ui/LaunchSplashOverlay.kt` + Adaptive/Monochrome Vector Icon (`mipmap-anydpi-v26` & `v33`, `values-v31/themes.xml`).
10. `engine/SmartMacroMathEngine.kt` — Kalkulator inline `=` (*recursive-descent parser*), token sistem (`/iso`, `/ts`, `/uuid`, `/dev`, `/ip`, `/rish`), & resolusi `;snippet`.
11. `engine/ClipboardVault.kt` — Manajer papan klip lokal dengan deteksi `ClipDescription.EXTRA_IS_SENSITIVE` (`API 33+`), pola OTP, & auto-hapus RAM 60 detik.
12. `ui/CommandStripView.kt` — Bilah aksi `38dp` (`Klip`, `Makro`, `Kursor` trackpad DPAD + seleksi `SEL`, `☺` simbol, *live action chip*).
13. `ui/SymbolEmojiDrawer.kt` — Matriks emoji, simbol teknis, & kaomoji dalam satu node `SymbolGridCanvas`.
14. `engine/StateBackupManager.kt` — Ekspor/impor JSON tanpa izin penyimpanan via `MediaStore.Downloads` (`API 29–36`).
15. `engine/SnippetEngine.kt` — Mesin ekspansi pintasan teks kustom (`;trigger`).
16. `ui/TouchPipelineOptimizer.kt` — `UnbufferedInputRootLayout` (`requestUnbufferedDispatch`), penguncian mode tampilan tertinggi (`144Hz` via `DisplayManager`), & `prewarmGlyphAtlas()`.
17. `engine/InputCompatibilityRouter.kt` — Profil kompatibilitas otomatis (`com.termux` / `TYPE_NULL` -> `Ctrl+W`, password, URI/email).
18. `engine/HapticEngine.kt` — X-Axis Linear Motor Haptic Composition skala kontinu `0%–100%` (`VibratorManager` + `VibrationEffect.Composition` + `VibrationAttributes.USAGE_TOUCH`).
19. `ui/DrawerBackDispatcher.kt` — Intersepsi Android 16 Predictive Back (`OnBackInvokedCallback` `PRIORITY_OVERLAY`, `API 33–36`) untuk menutup laci utilitas terlebih dahulu.
20. `service/AnggiKeyboardService.kt` — Orkestrator utama `InputMethodService`.
