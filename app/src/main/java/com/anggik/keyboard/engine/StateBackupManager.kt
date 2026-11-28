package com.anggik.keyboard.engine

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StateBackupManager {

    fun exportToDownloads(context: Context): Result<String> {
        return try {
            val dpCtx = context.createDeviceProtectedStorageContext()
            val prefs = dpCtx.getSharedPreferences("anggi_kb_state", Context.MODE_PRIVATE)

            val root = JSONObject().apply {
                put("schema", "anggik_keyboard_v1")
                put("exported_at", System.currentTimeMillis())

                val prefObj = JSONObject()
                prefs.all.forEach { (k, v) ->
                    when (v) {
                        is Boolean -> prefObj.put(k, v)
                        is Int -> prefObj.put(k, v)
                        is Float -> prefObj.put(k, v.toDouble())
                        is Long -> prefObj.put(k, v)
                        is String -> prefObj.put(k, v)
                    }
                }
                put("prefs", prefObj)
                put("learned_words", readLinesArray(File(dpCtx.filesDir, "learned_words.txt")))
                put("pinned_clips_b64", readLinesArray(File(dpCtx.filesDir, "pinned_clips.b64")))
                put("recent_symbols", readLinesArray(File(dpCtx.filesDir, "recent_symbols.txt")))
                put("custom_snippets", readLinesArray(File(dpCtx.filesDir, "custom_snippets.tsv")))
            }

            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "AnggiKeyboard_backup_$stamp.json"
            val payload = root.toString(2).toByteArray(Charsets.UTF_8)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values
                ) ?: return Result.failure(IllegalStateException("Gagal membuat entri MediaStore"))

                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(payload)
                } ?: return Result.failure(IllegalStateException("OutputStream null"))

                Result.success("Download/$fileName")
            } else {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: dpCtx.filesDir
                val outFile = File(dir, fileName)
                outFile.writeBytes(payload)
                Result.success(outFile.absolutePath)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun importLatestFromDownloads(context: Context): Result<String> {
        return try {
            val jsonRaw = readLatestBackupText(context)
                ?: return Result.failure(IllegalStateException("File AnggiKeyboard_backup_*.json tidak ditemukan"))

            val root = JSONObject(jsonRaw)
            if (root.optString("schema") != "anggik_keyboard_v1") {
                return Result.failure(IllegalArgumentException("Format skema backup tidak valid"))
            }

            val dpCtx = context.createDeviceProtectedStorageContext()
            val prefs = dpCtx.getSharedPreferences("anggi_kb_state", Context.MODE_PRIVATE)
            val editor = prefs.edit()

            val prefObj = root.optJSONObject("prefs")
            if (prefObj != null) {
                val keys = prefObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = prefObj.get(k)
                    when (v) {
                        is Boolean -> editor.putBoolean(k, v)
                        is Int -> editor.putInt(k, v)
                        is Double -> editor.putFloat(k, v.toFloat())
                        is Long -> editor.putLong(k, v)
                        is String -> editor.putString(k, v)
                    }
                }
            }
            editor.apply()

            val wordsCount = writeLinesArray(
                File(dpCtx.filesDir, "learned_words.txt"),
                root.optJSONArray("learned_words")
            )
            val pinsCount = writeLinesArray(
                File(dpCtx.filesDir, "pinned_clips.b64"),
                root.optJSONArray("pinned_clips_b64")
            )
            writeLinesArray(
                File(dpCtx.filesDir, "recent_symbols.txt"),
                root.optJSONArray("recent_symbols")
            )
            writeLinesArray(
                File(dpCtx.filesDir, "custom_snippets.tsv"),
                root.optJSONArray("custom_snippets")
            )

            Result.success("Dipulihkan: $wordsCount kata, $pinsCount pin klip")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun readLatestBackupText(context: Context): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME
            )
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
            val args = arrayOf("AnggiKeyboard_backup_%.json")
            val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"

            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                args,
                sortOrder
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                    return context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                        it.readText()
                    }
                }
            }
        }
        val fallbackDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
        val latest = fallbackDir.listFiles { f ->
            f.name.startsWith("AnggiKeyboard_backup_") && f.name.endsWith(".json")
        }?.maxByOrNull { it.lastModified() } ?: return null
        return latest.readText(Charsets.UTF_8)
    }

    private fun readLinesArray(file: File): JSONArray {
        val arr = JSONArray()
        if (file.exists()) {
            file.readLines().filter { it.isNotBlank() }.forEach { arr.put(it) }
        }
        return arr
    }

    private fun writeLinesArray(file: File, arr: JSONArray?): Int {
        if (arr == null || arr.length() == 0) return 0
        val lines = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val item = arr.optString(i)
            if (item.isNotBlank()) lines.add(item)
        }
        file.writeText(lines.joinToString("\n"))
        return lines.size
    }
}
