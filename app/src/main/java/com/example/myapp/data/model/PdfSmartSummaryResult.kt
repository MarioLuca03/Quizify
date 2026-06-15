package com.example.myapp.data.model

/**
 * Răspuns sumar rapid „smart” (un singur apel AI + pagini selectate local).
 */
data class PdfSmartSummaryResult(
    val summary: String,
    val pagesUsed: List<Int>,
    val mode: String = "fast_smart",
    /** true = tot documentul; false = doar [scopeFrom..scopeTo] (inclusiv). */
    val scopeAll: Boolean = true,
    val scopeFrom: Int? = null,
    val scopeTo: Int? = null
)
