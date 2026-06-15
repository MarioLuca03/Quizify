package com.example.myapp.utils

import com.example.myapp.data.local.OfflineLlmModelConfig
import java.io.File

/**
 * Limite sigure pentru modele ekv1280 (1280 tokeni intrare+iesire).
 * Crash-ul nativ apare daca promptul depaseste cache-ul KV, indiferent de RAM-ul telefonului.
 */
object LocalLlmPromptGuard {

    private const val CHARS_PER_TOKEN_ESTIMATE = 3.5
    private const val DECODE_TOKEN_RESERVE = 180

    val maxPromptChars: Int
        get() {
            val inputTokens =
                OfflineLlmModelConfig.MODEL_KV_CACHE_TOKENS - DECODE_TOKEN_RESERVE
            return (inputTokens * CHARS_PER_TOKEN_ESTIMATE).toInt().coerceAtMost(2_800)
        }

    fun trimPrompt(prompt: String): String {
        val p = prompt.trim()
        val cap = maxPromptChars
        if (p.length <= cap) return p
        return p.take(cap) + "\n[... text scurtat pentru modelul local ...]"
    }

    fun verifyModelFile(modelPath: String, minBytes: Long): String? {
        val file = File(modelPath)
        if (!file.isFile) return "Modelul local lipseste. Descarca-l din nou (Qwen2.5 0.5B)."
        if (file.length() < minBytes) {
            return "Fisierul model pare incomplet (${file.length() / 1_000_000} MB). " +
                "Sterge si descarca din nou pe Wi-Fi."
        }
        return null
    }
}
