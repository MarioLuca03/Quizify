package com.example.myapp.utils

import com.example.myapp.data.local.OfflineLlmModelConfig
import com.example.myapp.data.model.PageNormalizedText
import com.example.myapp.data.model.PdfPageContent
import com.example.myapp.data.model.PdfPageTextStatus

object OfflineSubiectePageFilter {

    fun buildPagesInScope(
        pages: List<PageNormalizedText>,
        pageStatuses: List<PdfPageTextStatus>,
        pageRange: IntRange?
    ): List<PdfPageContent> {
        val ocrSet = pageStatuses.filter { it.needsOcr }.map { it.pageNumber }.toSet()
        val inScope: (Int) -> Boolean = { p ->
            pageRange == null || p in pageRange
        }

        return pages
            .filter { it.pageNumber !in ocrSet && inScope(it.pageNumber) }
            .sortedBy { it.pageNumber }
            .map { page -> toPdfPageContent(page.normalizedText, page.pageNumber) }
    }

    fun validPages(all: List<PdfPageContent>): List<PdfPageContent> =
        all.filter { it.valid }

    /**
     * Extrage un fragment de [CHUNK_WORDS_MIN]–[CHUNK_WORDS_MAX] cuvinte din mijlocul paginii.
     */
    fun textChunkForAi(page: PdfPageContent): String {
        val focused = LocalLlmTextPreprocessor.prepareForLocalModel(
            rawPageText = page.text,
            maxChars = OfflineLlmModelConfig.MAX_PAGE_CHARS_FOR_AI
        )
        val words = focused.trim()
            .split(Regex("""\s+"""))
            .filter { it.isNotBlank() }
        if (words.isEmpty()) return ""

        val maxW = OfflineLlmModelConfig.CHUNK_WORDS_MAX
        if (words.size <= maxW) return words.joinToString(" ")

        val start = ((words.size - maxW) / 2).coerceAtLeast(0)
        return words.drop(start).take(maxW).joinToString(" ")
    }

    /**
     * Top pagini după scor (în [pageRange]), filtrate valide, amestecate — pool pentru întrebări offline.
     */
    fun buildShuffledQuestionPool(
        pages: List<PageNormalizedText>,
        pageStatuses: List<PdfPageTextStatus>,
        documentTotalPages: Int,
        pageRange: IntRange?
    ): List<PdfPageContent> {
        val inScope: (Int) -> Boolean = { p -> pageRange == null || p in pageRange }
        val ranked = PdfPageRelevanceSelector.rankPagesForQuiz(
            pages = pages,
            pageStatuses = pageStatuses,
            documentTotalPages = documentTotalPages,
            topN = OfflineLlmModelConfig.OFFLINE_CANDIDATE_POOL_SIZE
        ).filter { inScope(it) }

        val byPage = buildPagesInScope(pages, pageStatuses, pageRange).associateBy { it.pageNumber }
        return ranked
            .mapNotNull { byPage[it] }
            .filter { it.valid }
            .shuffled()
    }

    private fun toPdfPageContent(raw: String, pageNumber: Int): PdfPageContent {
        val text = raw.trim()
        val words = countWords(text)
        val chars = text.length
        val valid = words >= OfflineLlmModelConfig.MIN_PAGE_WORDS &&
            chars >= OfflineLlmModelConfig.MIN_PAGE_CHARS
        return PdfPageContent(
            pageNumber = pageNumber,
            text = text,
            wordCount = words,
            charCount = chars,
            valid = valid
        )
    }

    private fun countWords(text: String): Int {
        if (text.isBlank()) return 0
        return text.split(Regex("""\s+""")).count { it.isNotBlank() }
    }
}
