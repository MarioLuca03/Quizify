package com.example.myapp.data.model

data class QuizResponse(
    val subject: String?,
    val numQuestions: Int?,
    val questions: List<QuizQuestion>
)

data class QuizQuestion(
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String?
)










