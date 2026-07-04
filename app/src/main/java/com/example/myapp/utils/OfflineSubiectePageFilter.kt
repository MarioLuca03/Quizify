package com.example.myapp.utils

import com.example.myapp.data.local.OfflineLlmModelConfig
import com.example.myapp.data.model.OfflineQuestionChunk
import com.example.myapp.data.model.PageNormalizedText
import com.example.myapp.data.model.PdfPageContent
import com.example.myapp.data.model.PdfPageTextStatus

object OfflineSubiectePageFilter {

    private val bulletSplit = Regex("""\s*•\s*""")
    private val codeHeavyPattern = Regex(
        """NTSTATUS|PDEVICE_OBJECT|PIRP\b|IoCreate\w*|KeInitializeEvent|->|;\s*\}"""
    )
    private val lowValuePattern = Regex(
        """(?i)^\s*(cuprins|continut|content|thank you|multumim)\s*$"""
    )

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
     * Pool de chunk-uri pregătite local: top după scor (cel mai bun primul).
     * Fiecare chunk are text suficient pentru o singură inferență (fără retry pe alte pagini).
     */
    fun buildShuffledChunkPool(
        pages: List<PageNormalizedText>,
        pageStatuses: List<PdfPageTextStatus>,
        documentTotalPages: Int,
        pageRange: IntRange?
    ): List<OfflineQuestionChunk> {
        val ocrSet = pageStatuses.filter { it.needsOcr }.map { it.pageNumber }.toSet()
        val inScope: (Int) -> Boolean = { p -> pageRange == null || p in pageRange }

        val orderedPages = pages
            .filter { it.pageNumber !in ocrSet && inScope(it.pageNumber) && it.normalizedText.isNotBlank() }
            .sortedBy { it.pageNumber }

        val chunks = extractChunks(orderedPages, documentTotalPages)
        return chunks
            .sortedByDescending { it.score }
            .take(OfflineLlmModelConfig.OFFLINE_CANDIDATE_POOL_SIZE)
    }

    private fun extractChunks(
        orderedPages: List<PageNormalizedText>,
        documentTotalPages: Int
    ): List<OfflineQuestionChunk> {
        val segments = orderedPages.flatMap { page ->
            splitIntoSegments(page.normalizedText).map { page.pageNumber to it }
        }
        if (segments.isEmpty()) return emptyList()

        val minWords = OfflineLlmModelConfig.CHUNK_WORDS_MIN
        val maxWords = OfflineLlmModelConfig.CHUNK_WORDS_MAX
        val result = mutableListOf<OfflineQuestionChunk>()
        var segmentIndex = 0
        var chunkCounter = 0

        while (segmentIndex < segments.size) {
            val parts = mutableListOf<Pair<Int, String>>()
            var totalWords = 0

            while (segmentIndex < segments.size) {
                val (pageNum, segment) = segments[segmentIndex]
                val segmentWords = countWords(segment)
                if (totalWords + segmentWords > maxWords && totalWords >= minWords) break

                parts.add(pageNum to segment)
                totalWords += segmentWords
                segmentIndex++

                if (totalWords >= minWords) break
            }

            if (totalWords < minWords) continue

            val text = prepareChunkText(parts)
            if (!passesChunkGate(text)) continue

            val pageNumber = parts.first().first
            val id = "c${chunkCounter++}_p$pageNumber"
            val score = PdfPageRelevanceSelector.scorePage(pageNumber, documentTotalPages, text)
            result.add(
                OfflineQuestionChunk(
                    id = id,
                    pageNumber = pageNumber,
                    text = text,
                    score = score
                )
            )
        }

        return result
    }

    private fun splitIntoSegments(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return emptyList()

        val rawParts = if (trimmed.contains('•')) {
            bulletSplit.split(trimmed)
        } else {
            trimmed.lines()
        }

        return rawParts
            .map { it.trim() }
            .filter { segment ->
                countWords(segment) >= 3 && !lowValuePattern.matches(segment)
            }
    }

    private fun prepareChunkText(parts: List<Pair<Int, String>>): String {
        val focused = parts.joinToString(" • ") { it.second }
        return LocalLlmTextPreprocessor.prepareForLocalModel(
            rawPageText = focused,
            maxChars = OfflineLlmModelConfig.MAX_PAGE_CHARS_FOR_AI
        ).trim()
    }

    private fun passesChunkGate(text: String): Boolean {
        if (text.isBlank()) return false
        if (countWords(text) < OfflineLlmModelConfig.CHUNK_WORDS_MIN) return false
        if (codeHeavyPattern.containsMatchIn(text)) return false
        val letters = text.count { it.isLetter() }
        if (letters < 80) return false
        return true
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
