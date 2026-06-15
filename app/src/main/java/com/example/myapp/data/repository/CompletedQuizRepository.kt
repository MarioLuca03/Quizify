package com.example.myapp.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.example.myapp.data.model.CompletedQuiz
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Persists completed quizzes (including scores and answers) locally,
 * so that history and progress charts survive app restarts.
 */
class CompletedQuizRepository(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val type = object : TypeToken<List<SerializedCompletedQuiz>>() {}.type

    fun loadCompletedQuizzes(): List<CompletedQuiz> {
        val json = prefs.getString(KEY_COMPLETED_QUIZZES, null) ?: return emptyList()
        return try {
            val serializedList: List<SerializedCompletedQuiz> = gson.fromJson(json, type) ?: emptyList()
            serializedList.map { it.toDomain() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveCompletedQuizzes(quizzes: List<CompletedQuiz>) {
        val serialized = quizzes.map { SerializedCompletedQuiz.fromDomain(it) }
        prefs.edit()
            .putString(KEY_COMPLETED_QUIZZES, gson.toJson(serialized))
            .apply()
    }

    fun addCompletedQuiz(quiz: CompletedQuiz) {
        val current = loadCompletedQuizzes().toMutableList()
        current.add(0, quiz) // cel mai recent la început
        saveCompletedQuizzes(current)
    }

    fun clearAll() {
        prefs.edit().remove(KEY_COMPLETED_QUIZZES).apply()
    }

    fun removeQuizzesForPdf(pdfUri: Uri) {
        val uriString = pdfUri.toString()
        val updated = loadCompletedQuizzes().filterNot { quiz ->
            quiz.pdfUri?.toString() == uriString
        }
        saveCompletedQuizzes(updated)
    }

    /**
     * DTO pentru serializarea sigură a lui CompletedQuiz (Uri -> String).
     */
    private data class SerializedCompletedQuiz(
        val id: String,
        val pdfName: String?,
        val pdfUriString: String?,
        val subject: String,
        val score: Int,
        val totalQuestions: Int,
        val questions: List<com.example.myapp.data.model.QuizQuestion>,
        val selectedAnswers: Map<Int, Int>,
        val completedAt: Long
    ) {
        fun toDomain(): CompletedQuiz {
            val uri = pdfUriString?.let {
                runCatching { Uri.parse(it) }.getOrNull()
            }
            return CompletedQuiz(
                id = id,
                pdfName = pdfName,
                pdfUri = uri,
                subject = subject,
                score = score,
                totalQuestions = totalQuestions,
                questions = questions,
                selectedAnswers = selectedAnswers,
                completedAt = completedAt
            )
        }

        companion object {
            fun fromDomain(quiz: CompletedQuiz): SerializedCompletedQuiz {
                return SerializedCompletedQuiz(
                    id = quiz.id,
                    pdfName = quiz.pdfName,
                    pdfUriString = quiz.pdfUri?.toString(),
                    subject = quiz.subject,
                    score = quiz.score,
                    totalQuestions = quiz.totalQuestions,
                    questions = quiz.questions,
                    selectedAnswers = quiz.selectedAnswers,
                    completedAt = quiz.completedAt
                )
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "completed_quizzes_prefs"
        private const val KEY_COMPLETED_QUIZZES = "completed_quizzes_list"
    }
}

