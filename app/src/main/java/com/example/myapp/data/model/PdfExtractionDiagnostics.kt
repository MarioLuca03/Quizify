package com.example.myapp.data.model

/**
 * Stare text pentru o pagină PDF (extragere cu PDFBox, fără OCR).
 */
data class PdfPageTextStatus(
    val pageNumber: Int,
    val approxChars: Int,
    /** Pagină fără text selectabil suficient — ar necesita OCR. */
    val needsOcr: Boolean
)

/**
 * Rezultatul extragerii: text normalizat + diagnostic pe pagini.
 */
data class PdfExtractionDiagnostics(
    val normalizedText: String,
    val totalPages: Int,
    val pageStatuses: List<PdfPageTextStatus>
) {
    val needsAnyOcr: Boolean get() = pageStatuses.any { it.needsOcr }
    val pagesNeedingOcr: List<Int> get() = pageStatuses.filter { it.needsOcr }.map { it.pageNumber }
}
