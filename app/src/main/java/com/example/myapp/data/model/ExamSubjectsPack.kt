package com.example.myapp.data.model

/** Un subiect de examen cu enunț și rezolvare. */
data class ExamSubjectItem(
    val question: String,
    val solution: String
)

/** Răspuns doar cu lista de subiecte (fără rezumat separat). */
data class ExamSubjectsPack(
    val subjects: List<ExamSubjectItem>
)
