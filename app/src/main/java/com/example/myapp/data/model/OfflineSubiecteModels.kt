package com.example.myapp.data.model

/** Pagină PDF după filtrare (pentru quiz offline). */
data class PdfPageContent(
    val pageNumber: Int,
    val text: String,
    val wordCount: Int,
    val charCount: Int,
    val valid: Boolean
)

/** O întrebare activă / finalizată pe o pagină validă. */
data class OfflineQuizItem(
    val pageNumber: Int,
    val question: String,
    val expectedAnswer: String,
    val userAnswer: String? = null,
    val evaluation: AnswerEvaluation? = null
)

/** Rezultat evaluare răspuns elev (JSON de la model). */
data class AnswerEvaluation(
    val corect: String,
    val scor: Int,
    val feedback: String
)

/** Rezultat parsare generare întrebare. */
data class PageQuestionResult(
    val skip: Boolean,
    val intrebare: String = "",
    val raspunsAsteptat: String = ""
)

enum class OfflineSubiectePhase {
    Idle,
    LoadingQuestion,
    Question,
    LoadingEvaluation,
    Feedback,
    /** Nu mai sunt pagini noi în pool; utilizatorul poate reveni la picker. */
    Exhausted
}
