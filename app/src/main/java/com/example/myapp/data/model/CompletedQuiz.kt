package com.example.myapp.data.model

import android.net.Uri
import java.util.Date

data class CompletedQuiz(
    val id: String,
    val pdfName: String?,
    val pdfUri: Uri?,
    val subject: String,
    val score: Int,
    val totalQuestions: Int,
    val questions: List<QuizQuestion>,
    val selectedAnswers: Map<Int, Int>, // questionIndex to answerIndex
    val completedAt: Long = System.currentTimeMillis()
) {
    val percentage: Int
        get() = if (totalQuestions > 0) {
            ((score.toFloat() / totalQuestions) * 100).toInt()
        } else 0
}

