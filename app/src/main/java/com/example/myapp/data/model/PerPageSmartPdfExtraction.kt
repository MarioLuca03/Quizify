package com.example.myapp.data.model

/**
 * Extragere locală completă: hash conținut PDF + text pe pagini + diagnostic OCR.
 */
data class PerPageSmartPdfExtraction(
    val pdfContentSha256: String,
    val totalPages: Int,
    val pages: List<PageNormalizedText>,
    val pageStatuses: List<PdfPageTextStatus>
) {
    val needsAnyOcr: Boolean get() = pageStatuses.any { it.needsOcr }
    val pagesNeedingOcr: List<Int> get() = pageStatuses.filter { it.needsOcr }.map { it.pageNumber }

    /** Text complet normalizat (toate paginile), pentru cache text în app. */
    fun fullNormalizedDocument(): String =
        pages.sortedBy { it.pageNumber }
            .map { it.normalizedText.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
}
