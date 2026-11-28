package com.anggik.keyboard.engine

import android.content.Context
import java.io.File

data class TextSnippet(
    val trigger: String,
    val expansion: String
)

object SnippetEngine {

    private fun snippetFile(context: Context): File {
        val dpCtx = context.applicationContext.createDeviceProtectedStorageContext()
        return File(dpCtx.filesDir, "custom_snippets.tsv")
    }

    fun getAll(context: Context): List<TextSnippet> {
        val file = snippetFile(context)
        if (!file.exists()) return emptyList()
        return try {
            file.readLines().mapNotNull { line ->
                val parts = line.split('\t', limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    TextSnippet(parts[0].trim(), parts[1])
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun upsert(context: Context, rawTrigger: String, rawExpansion: String): Boolean {
        val cleanTrigger = rawTrigger.trim().replace("\t", "").replace("\n", "").replace("\r", "")
        val cleanExpansion = rawExpansion.trim().replace("\t", " ").replace("\r", "")
        if (cleanTrigger.length < 2 || cleanExpansion.isEmpty()) return false

        val list = getAll(context).filterNot { it.trigger.equals(cleanTrigger, ignoreCase = true) }.toMutableList()
        list.add(0, TextSnippet(cleanTrigger, cleanExpansion))
        saveAll(context, list.take(40))
        return true
    }

    fun remove(context: Context, trigger: String) {
        val list = getAll(context).filterNot { it.trigger == trigger }
        saveAll(context, list)
    }

    fun matchSuffix(context: Context, textBeforeCursor: String?): TextSnippet? {
        if (textBeforeCursor.isNullOrEmpty()) return null
        val snippets = getAll(context)
        for (item in snippets) {
            if (textBeforeCursor.endsWith(item.trigger)) {
                val boundaryIdx = textBeforeCursor.length - item.trigger.length - 1
                if (boundaryIdx < 0 || textBeforeCursor[boundaryIdx].isWhitespace()) {
                    return item
                }
            }
        }
        return null
    }

    private fun saveAll(context: Context, items: List<TextSnippet>) {
        try {
            val file = snippetFile(context)
            if (items.isEmpty()) {
                if (file.exists()) file.delete()
            } else {
                file.writeText(items.joinToString("\n") { "${it.trigger}\t${it.expansion}" })
            }
        } catch (_: Exception) {
            // Abaikan error I/O minor
        }
    }
}
