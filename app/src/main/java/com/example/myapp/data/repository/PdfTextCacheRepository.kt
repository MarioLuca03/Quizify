package com.example.myapp.data.repository

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest

class PdfTextCacheRepository(context: Context) {
    private val appContext = context.applicationContext
    private val cacheDir: File = File(appContext.filesDir, CACHE_DIR_NAME).apply {
        if (!exists()) mkdirs()
    }

    fun loadText(uri: Uri): String? {
        val file = cacheFile(uri)
        if (!file.exists()) return null
        return try {
            file.readText(Charsets.UTF_8)
                .trim()
                .takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    fun saveText(uri: Uri, text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        val file = cacheFile(uri)
        try {
            file.writeText(clean, Charsets.UTF_8)
        } catch (_: Exception) {
            // Cache best-effort: ignore write failures.
        }
    }

    private fun cacheFile(uri: Uri): File {
        val key = sha256(uri.toString())
        return File(cacheDir, "$key.txt")
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val CACHE_DIR_NAME = "pdf_text_cache"
    }
}
