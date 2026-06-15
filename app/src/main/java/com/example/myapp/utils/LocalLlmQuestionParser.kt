package com.example.myapp.utils

import com.example.myapp.data.model.PageQuestionResult

/**
 * Parser rapid pentru răspunsuri scurte I:/R: (preferat) sau JSON (fallback).
 */
object LocalLlmQuestionParser {

    private val skipPattern = Regex("""(?im)^\s*SKIP\s*$""")
    private val questionLine = Regex("""(?im)^\s*I\s*:\s*(.+)$""")
    private val answerLine = Regex("""(?im)^\s*R\s*:\s*(.+)$""")

    fun parse(raw: String): PageQuestionResult? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        if (skipPattern.containsMatchIn(trimmed) ||
            trimmed.equals("SKIP", ignoreCase = true)
        ) {
            return PageQuestionResult(skip = true)
        }

        val q = questionLine.find(trimmed)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val a = answerLine.find(trimmed)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (q.isNotBlank() && a.isNotBlank()) {
            return PageQuestionResult(skip = false, intrebare = q, raspunsAsteptat = a)
        }

        return LocalLlmJsonParser.parsePageQuestion(raw)
    }
}
