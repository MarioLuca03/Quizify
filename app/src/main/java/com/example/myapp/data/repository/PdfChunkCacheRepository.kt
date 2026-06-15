package com.example.myapp.data.repository

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import java.io.File
import java.security.MessageDigest

class PdfChunkCacheRepository(context: Context) {
    private val appContext = context.applicationContext
    private val gson = Gson()
    private val cacheDir: File = File(appContext.filesDir, CACHE_DIR_NAME).apply {
        if (!exists()) mkdirs()
    }

    private data class ChunkPayload(
        val signature: String,
        val chunks: List<String>,
        val createdAt: Long
    )

    fun loadChunks(uri: Uri, signature: String): List<String>? {
        val file = chunkFile(uri)
        if (!file.exists()) return null

        return try {
            val payload = gson.fromJson(file.readText(Charsets.UTF_8), ChunkPayload::class.java)
            if (payload.signature != signature) return null
            payload.chunks
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    fun saveChunks(uri: Uri, signature: String, chunks: List<String>) {
        val cleaned = chunks
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (cleaned.isEmpty()) return

        val payload = ChunkPayload(
            signature = signature,
            chunks = cleaned,
            createdAt = System.currentTimeMillis()
        )

        try {
            chunkFile(uri).writeText(gson.toJson(payload), Charsets.UTF_8)
        } catch (_: Exception) {
            // Best-effort cache only.
        }
    }

    fun buildSignature(text: String): String {
        val normalized = text.trim().take(800_000)
        return sha256(normalized)
    }

    private fun chunkFile(uri: Uri): File = File(cacheDir, "${sha256(uri.toString())}.json")

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val CACHE_DIR_NAME = "pdf_chunk_cache"
    }
}
