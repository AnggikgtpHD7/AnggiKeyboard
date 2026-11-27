package com.anggik.keyboard.engine

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import java.io.File

data class ClipEntry(
    val id: Long,
    val text: String,
    val pinned: Boolean,
    val sensitive: Boolean,
    val timestampMs: Long
) {
    fun displayPreview(): String {
        if (sensitive) {
            return "•••••• (Sensitif • Auto-hapus 60d)"
        }
        val singleLine = text.replace('\n', ' ').trim()
        return if (singleLine.length > 44) singleLine.take(44) + "…" else singleLine
    }
}

class ClipboardVault(context: Context) {

    private val appContext = context.applicationContext
    private val dpContext = appContext.createDeviceProtectedStorageContext()
    private val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pinFile = File(dpContext.filesDir, "pinned_clips.b64")

    private val entries = ArrayList<ClipEntry>()
    private var nextId = 1L
    private var listening = false
    var onVaultUpdated: (() -> Unit)? = null

    private val otpPattern = Regex("^\\d{4,8}$")

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        captureCurrentPrimaryClip()
    }

    fun start() {
        if (listening) return
        loadPinnedFromStorage()
        captureCurrentPrimaryClip()
        clipboard.addPrimaryClipChangedListener(clipListener)
        listening = true
    }

    fun stop() {
        if (!listening) return
        clipboard.removePrimaryClipChangedListener(clipListener)
        mainHandler.removeCallbacksAndMessages(null)
        listening = false
    }

    fun getAll(): List<ClipEntry> {
        val pinned = entries.filter { it.pinned }
        val recent = entries.filter { !it.pinned }
        return pinned + recent
    }

    fun latestQuickChip(): ClipEntry? {
        val now = System.currentTimeMillis()
        return entries.firstOrNull { !it.sensitive && (now - it.timestampMs) < 180_000L }
    }

    fun togglePin(id: Long) {
        val idx = entries.indexOfFirst { it.id == id }
        if (idx < 0) return
        val item = entries[idx]
        if (item.sensitive) return
        entries[idx] = item.copy(pinned = !item.pinned)
        savePinnedToStorage()
        onVaultUpdated?.invoke()
    }

    fun remove(id: Long) {
        val removed = entries.removeAll { it.id == id }
        if (removed) {
            savePinnedToStorage()
            onVaultUpdated?.invoke()
        }
    }

    private fun captureCurrentPrimaryClip() {
        val clip = clipboard.primaryClip ?: return
        if (clip.itemCount <= 0) return
        val raw = clip.getItemAt(0).coerceToText(appContext)?.toString()?.trim() ?: return
        if (raw.isEmpty() || raw.length > 4096) return

        val isSystemSensitive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false) == true
        } else {
            false
        }
        val isHeuristicSensitive = otpPattern.matches(raw) || raw.startsWith("Bearer ", ignoreCase = true)
        val sensitive = isSystemSensitive || isHeuristicSensitive

        val existingIdx = entries.indexOfFirst { it.text == raw }
        if (existingIdx >= 0) {
            val existing = entries.removeAt(existingIdx)
            entries.add(0, existing.copy(timestampMs = System.currentTimeMillis(), sensitive = sensitive))
        } else {
            val itemId = nextId++
            entries.add(
                0,
                ClipEntry(
                    id = itemId,
                    text = raw,
                    pinned = false,
                    sensitive = sensitive,
                    timestampMs = System.currentTimeMillis()
                )
            )
            if (sensitive) {
                mainHandler.postDelayed({
                    if (entries.removeAll { it.id == itemId }) {
                        onVaultUpdated?.invoke()
                    }
                }, 60_000L)
            }
        }

        // Batasi maksimal 20 item non-pin di RAM
        var nonPinCount = 0
        val iter = entries.iterator()
        while (iter.hasNext()) {
            val e = iter.next()
            if (!e.pinned) {
                nonPinCount++
                if (nonPinCount > 20) iter.remove()
            }
        }

        onVaultUpdated?.invoke()
    }

    private fun loadPinnedFromStorage() {
        entries.removeAll { it.pinned }
        if (!pinFile.exists()) return
        try {
            pinFile.readLines().forEach { line ->
                if (line.isNotBlank()) {
                    val decoded = String(Base64.decode(line.trim(), Base64.NO_WRAP), Charsets.UTF_8)
                    if (decoded.isNotEmpty()) {
                        entries.add(
                            ClipEntry(
                                id = nextId++,
                                text = decoded,
                                pinned = true,
                                sensitive = false,
                                timestampMs = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Abaikan file korup
        }
    }

    private fun savePinnedToStorage() {
        try {
            val lines = entries.filter { it.pinned && !it.sensitive }.map {
                Base64.encodeToString(it.text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            }
            if (lines.isEmpty()) {
                if (pinFile.exists()) pinFile.delete()
            } else {
                pinFile.writeText(lines.joinToString("\n"))
            }
        } catch (_: Exception) {
            // Abaikan kegagalan I/O minor
        }
    }
}
