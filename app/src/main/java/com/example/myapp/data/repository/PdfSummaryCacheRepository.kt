package com.example.myapp.data.repository

import android.content.Context
import com.example.myapp.data.model.PdfSmartSummaryResult
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.io.File

/**
 * Cache pentru rezumatul rapid: cheie = SHA-256 pe conținutul binar + **domeniu** (tot PDF-ul vs interval pagini).
 */
class PdfSummaryCacheRepository(context: Context) {
    private val appContext = context.applicationContext
    private val gson = Gson()
    private val cacheDir: File = File(appContext.filesDir, CACHE_DIR_NAME).apply {
        if (!exists()) mkdirs()
    }

    fun load(contentSha256Hex: String, scopeId: String): PdfSmartSummaryResult? {
        val primary = cacheFile(contentSha256Hex, scopeId)
        val fromFile = readResult(primary)
        if (fromFile != null) return fromFile
        if (scopeId == SCOPE_ALL) {
            val legacy = File(cacheDir, "$contentSha256Hex.json")
            return readResult(legacy)
        }
        return null
    }

    fun save(contentSha256Hex: String, scopeId: String, result: PdfSmartSummaryResult) {
        if (result.summary.isBlank()) return
        try {
            cacheFile(contentSha256Hex, scopeId).writeText(gson.toJson(result), Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }

    /** Șterge toate variantele de cache pentru același PDF (orice interval / tot). */
    fun clearAllScopes(contentSha256Hex: String) {
        try {
            cacheDir.listFiles()?.forEach { f ->
                val n = f.name
                if (n.startsWith("${contentSha256Hex}_") || n == "$contentSha256Hex.json") {
                    f.delete()
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun readResult(file: File): PdfSmartSummaryResult? {
        if (!file.exists()) return null
        return try {
            gson.fromJson(file.readText(Charsets.UTF_8), PdfSmartSummaryResult::class.java)
                ?.takeIf { it.summary.isNotBlank() }
        } catch (_: JsonSyntaxException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun cacheFile(contentSha256Hex: String, scopeId: String): File =
        File(cacheDir, "${contentSha256Hex}_$scopeId.json")

    companion object {
        private const val CACHE_DIR_NAME = "pdf_summary_cache"
        const val SCOPE_ALL = "all"

        fun scopeIdForRange(from: Int, to: Int): String = "p${from}_$to"
    }
}
