package com.example.myapp.utils

import com.example.myapp.data.local.OfflineLlmModelConfig

/**
 * Reduce textul din paginile selectate la propozitii relevante inainte de inferenta locala.
 */
object LocalLlmTextPreprocessor {

    private val sentenceSplit = Regex("""(?<=[.!?])\s+""")

    private val keywordWeights = listOf(
        "definitie" to 3.0,
        "definitia" to 3.0,
        "important" to 2.5,
        "rezultat" to 2.5,
        "concluzie" to 3.0,
        "exemplu" to 2.0,
        "formula" to 2.5,
        "teorema" to 3.0,
        "lege" to 2.0,
        "principiu" to 2.5,
        "cauza" to 2.0,
        "efect" to 2.0,
        "proces" to 2.0,
        "structura" to 2.0,
        "functie" to 2.0,
        "caracteristici" to 2.5
    )

    fun prepareForLocalModel(
        rawPageText: String,
        maxChars: Int = OfflineLlmModelConfig.MAX_PAGE_CHARS_FOR_AI
    ): String {
        val trimmed = rawPageText.trim()
        if (trimmed.isBlank()) return ""
        if (trimmed.length <= maxChars) return trimmed

        val sections = splitByPageSections(trimmed)
        val scored = mutableListOf<Pair<Double, String>>()

        for ((header, body) in sections) {
            val sentences = extractSentences(body)
            for (sentence in sentences) {
                val score = scoreSentence(sentence)
                val line = if (header.isNotBlank()) "$header $sentence" else sentence
                scored.add(score to line)
            }
        }

        if (scored.isEmpty()) return trimmed.take(maxChars)

        val picked = scored
            .sortedByDescending { it.first }
            .map { it.second }
            .distinct()

        val sb = StringBuilder()
        for (line in picked) {
            val addition = if (sb.isEmpty()) line else "\n$line"
            if (sb.length + addition.length > maxChars) break
            sb.append(addition)
        }

        val result = sb.toString().trim()
        return result.ifBlank { trimmed.take(maxChars) }
    }

    private fun splitByPageSections(text: String): List<Pair<String, String>> {
        val headerRegex = Regex("""\n---\s*Pagina\s+(\d+)\s*---\s*\n""", RegexOption.IGNORE_CASE)
        val parts = text.split(headerRegex)
        if (parts.size <= 1) return listOf("" to text)

        val out = mutableListOf<Pair<String, String>>()
        var i = 1
        while (i < parts.size) {
            val pageNum = parts[i]
            val body = parts.getOrNull(i + 1)?.trim().orEmpty()
            if (body.isNotBlank()) {
                out.add("[Pag $pageNum]" to body)
            }
            i += 2
        }
        if (out.isEmpty()) return listOf("" to text)
        return out
    }

    private fun extractSentences(block: String): List<String> {
        return block
            .replace(Regex("""\[[.][.][^\]]*\]"""), " ")
            .lines()
            .flatMap { line ->
                sentenceSplit.split(line.trim())
                    .map { it.trim() }
                    .filter { it.length >= 25 }
            }
            .distinct()
    }

    private fun scoreSentence(sentence: String): Double {
        val t = sentence.trim()
        if (t.length < 25) return 0.0
        val lower = t.lowercase()
        var score = 0.0
        for ((kw, w) in keywordWeights) {
            if (lower.contains(kw)) score += w
        }
        val digitGroups = Regex("""\d+[.,]?\d*%?""").findAll(t).count()
        score += digitGroups.coerceAtMost(8) * 0.6
        if (t.endsWith(':')) score += 1.5
        val letters = t.count { it.isLetter() }
        if (letters > 8 && t == t.uppercase() && t.any { it.isLetter() }) score += 2.0
        score += (t.length / 120.0).coerceAtMost(4.0)
        if (lower.contains(" este ") || lower.contains(" sunt ") || lower.contains(" reprezinta ")) {
            score += 1.2
        }
        return score
    }
}
