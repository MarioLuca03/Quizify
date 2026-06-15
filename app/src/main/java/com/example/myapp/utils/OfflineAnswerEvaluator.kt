package com.example.myapp.utils

import com.example.myapp.data.model.AnswerEvaluation

/**
 * Evaluare instantanee offline: compară răspunsul elevului cu cel așteptat (fără model local).
 */
object OfflineAnswerEvaluator {

    private val stopWords = setOf(
        "si", "sau", "de", "la", "in", "cu", "un", "o", "este", "sunt", "era", "erau",
        "ca", "care", "ce", "din", "pentru", "pe", "nu", "mai", "se", "al", "ai", "au",
        "fi", "fost", "avea", "aveau", "doar", "foarte", "tot", "toate", "acest", "aceasta"
    )

    fun evaluate(
        question: String,
        expectedAnswer: String,
        userAnswer: String
    ): AnswerEvaluation {
        val userTokens = tokenize(userAnswer)
        if (userTokens.isEmpty()) {
            return AnswerEvaluation(
                corect = "nu",
                scor = 0,
                feedback = "Raspunsul este gol sau prea scurt."
            )
        }

        val expectedTokens = tokenize(expectedAnswer)
        if (expectedTokens.isEmpty()) {
            return AnswerEvaluation(
                corect = "partial",
                scor = 50,
                feedback = "Nu am putut compara automat; verifica manual cu materialul."
            )
        }

        val overlap = userTokens.intersect(expectedTokens).size
        val recall = overlap.toDouble() / expectedTokens.size
        val precision = overlap.toDouble() / userTokens.size
        val score = ((recall * 0.8 + precision * 0.2) * 100).toInt().coerceIn(0, 100)

        val corect = when {
            score >= 72 -> "da"
            score >= 38 -> "partial"
            else -> "nu"
        }

        val feedback = when (corect) {
            "da" -> "Foarte bine! Ai acoperit ideile principale din raspunsul asteptat."
            "partial" -> "Partial corect. Mai lipsesc cateva elemente cheie din raspunsul complet."
            else -> "Raspunsul nu acopera suficient continutul asteptat. Reia fragmentul din pagina si incearca din nou."
        }

        return AnswerEvaluation(corect = corect, scor = score, feedback = feedback)
    }

    private fun tokenize(text: String): Set<String> {
        val normalized = RomanianAsciiNormalizer.toPlainAscii(text.lowercase())
        return normalized
            .replace(Regex("""[^a-z0-9'\s-]"""), " ")
            .split(Regex("""\s+"""))
            .map { it.trim().trim('-', '\'') }
            .filter { it.length >= 3 && it !in stopWords }
            .toSet()
    }
}
