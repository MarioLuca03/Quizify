package com.example.myapp.utils

/**
 * Heuristici simple: respinge întrebări clar în engleză de la modelul local.
 */
object OfflineRomanianTextGuard {

    private val romanianQuestionStarters = listOf(
        "ce ", "care ", "cum ", "cand ", "unde ", "de ce ", "cât", "câți", "câte",
        "in ce", "în ce", "prin ce", "pentru ce"
    )

    private val englishQuestionStarters = listOf(
        "what ", "how ", "why ", "when ", "where ", "which ", "who ",
        "explain ", "describe ", "define ", "what is ", "what are "
    )

    private val englishStopWords = setOf(
        "the", "what", "how", "why", "when", "where", "which", "who",
        "is", "are", "was", "were", "does", "do", "did", "this", "that",
        "explain", "describe", "define", "question", "answer"
    )

    private val romanianHints = setOf(
        "ce", "care", "cum", "cand", "când", "unde", "este", "sunt", "era",
        "fost", "din", "pentru", "despre", "rolul", "functia", "funcția",
        "explica", "explică", "inseamna", "înseamnă", "reprezinta", "reprezintă",
        "prin", "care", "să", "sa", "unui", "unei", "acest", "această", "acesta"
    )

    fun isValidRomanianQuestion(question: String): Boolean {
        val q = RomanianAsciiNormalizer.fixRomanianText(question).trim()
        if (q.length < 10 || !q.contains('?')) return false

        val lower = q.lowercase()
        if (englishQuestionStarters.any { lower.startsWith(it) }) return false

        val hasRomanianStarter = romanianQuestionStarters.any { lower.startsWith(it) }
        val hasDiacritics = q.any { it in "ăâîșțĂÂÎȘȚ" }
        val words = lower.split(Regex("""\s+""")).filter { it.length > 1 }
        val roHits = words.count { word ->
            romanianHints.any { hint -> word == hint || word.startsWith(hint) }
        }
        val enHits = words.count { it in englishStopWords }

        if (enHits >= 2 && enHits > roHits) return false
        return hasRomanianStarter || hasDiacritics || roHits >= 2
    }

    fun isAcceptableRomanianAnswer(answer: String): Boolean {
        val a = RomanianAsciiNormalizer.fixRomanianText(answer).trim()
        if (a.length < 5) return false
        val lower = a.lowercase()
        if (englishQuestionStarters.any { lower.startsWith(it) }) return false
        val words = lower.split(Regex("""\s+""")).filter { it.length > 1 }
        val enHits = words.count { it in englishStopWords }
        val roHits = words.count { word ->
            romanianHints.any { hint -> word == hint || word.startsWith(hint) }
        }
        if (words.size >= 4 && enHits >= 3 && roHits == 0) return false
        return true
    }
}
