package com.example.myapp.utils

import com.example.myapp.data.model.AnswerEvaluation
import com.example.myapp.data.model.PageQuestionResult
import com.google.gson.JsonParser

object LocalLlmJsonParser {

    fun extractJsonBlock(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start >= 0 && end > start) return text.substring(start, end + 1)
        return null
    }

    fun parsePageQuestion(raw: String): PageQuestionResult? {
        val json = extractJsonBlock(raw) ?: return null
        return try {
            val o = JsonParser.parseString(json).asJsonObject
            val skip = when {
                o.has("skip") && o.get("skip").isJsonPrimitive -> {
                    val p = o.get("skip").asJsonPrimitive
                    p.isBoolean && p.asBoolean || p.asString.equals("true", ignoreCase = true)
                }
                else -> false
            }
            if (skip) return PageQuestionResult(skip = true)
            val q = o.get("intrebare")?.asString?.trim().orEmpty()
            val a = o.get("raspuns_asteptat")?.asString?.trim().orEmpty()
            if (q.isBlank() || a.isBlank()) null
            else PageQuestionResult(skip = false, intrebare = q, raspunsAsteptat = a)
        } catch (_: Exception) {
            null
        }
    }

    fun parseEvaluation(raw: String): AnswerEvaluation? {
        val json = extractJsonBlock(raw) ?: return null
        return try {
            val o = JsonParser.parseString(json).asJsonObject
            val corect = o.get("corect")?.asString?.trim()?.lowercase().orEmpty()
            if (corect !in setOf("da", "partial", "nu")) return null
            val scor = when {
                o.has("scor") && o.get("scor").isJsonPrimitive -> {
                    val p = o.get("scor").asJsonPrimitive
                    if (p.isNumber) p.asInt else p.asString.toIntOrNull()
                }
                else -> null
            } ?: return null
            val feedback = o.get("feedback")?.asString?.trim().orEmpty()
            if (feedback.isBlank()) return null
            AnswerEvaluation(
                corect = corect,
                scor = scor.coerceIn(0, 100),
                feedback = feedback
            )
        } catch (_: Exception) {
            null
        }
    }
}
