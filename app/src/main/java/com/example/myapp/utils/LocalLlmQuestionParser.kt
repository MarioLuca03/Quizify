package com.example.myapp.utils

import com.example.myapp.data.model.PageQuestionResult

/**
 * Parser pentru răspunsuri I:/R: sau JSON (question / referenceAnswer), doar română.
 */
object LocalLlmQuestionParser {

    private val skipPattern = Regex("""(?im)^\s*SKIP\s*$""")
    private val questionLine = Regex("""(?im)^\s*I\s*:\s*(.+)$""")
    private val answerLine = Regex("""(?im)^\s*R\s*:\s*(.+)$""")

    fun parse(raw: String): PageQuestionResult? {
        val trimmed = LocalLlmJsonParser.sanitizeRawModelOutput(raw)
        if (trimmed.isBlank()) return null
        if (skipPattern.containsMatchIn(trimmed) ||
            trimmed.equals("SKIP", ignoreCase = true)
        ) {
            return PageQuestionResult(skip = true)
        }

        val q = questionLine.find(trimmed)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val a = answerLine.find(trimmed)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (q.isNotBlank() && a.isNotBlank()) {
            return validate(PageQuestionResult(skip = false, intrebare = q, raspunsAsteptat = a))
        }

        return validate(LocalLlmJsonParser.parsePageQuestion(trimmed))
    }

    private fun validate(result: PageQuestionResult?): PageQuestionResult? {
        if (result == null || result.skip) return result

        var q = RomanianAsciiNormalizer.fixRomanianText(result.intrebare).trim()
        val a = RomanianAsciiNormalizer.fixRomanianText(result.raspunsAsteptat).trim()

        if (!q.contains('?')) {
            q = "$q?"
        }
        if (!OfflineRomanianTextGuard.isValidRomanianQuestion(q)) return null
        if (!OfflineRomanianTextGuard.isAcceptableRomanianAnswer(a)) return null

        return result.copy(intrebare = q, raspunsAsteptat = a)
    }
}
