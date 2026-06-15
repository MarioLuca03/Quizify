package com.example.myapp.utils

import com.example.myapp.data.local.OfflineLlmModelConfig
import com.example.myapp.data.model.PageNormalizedText
import com.example.myapp.data.model.PdfPageTextStatus

/**
 * Scor de relevanță per pagină (fără AI) și selecție de pagini pentru sumarizare rapidă.
 * [pageRange] opțional: limitează candidații la interval (inclusiv); scorurile „început/sfârșit”
 * se calculează relativ la interval, nu la tot documentul.
 */
object PdfPageRelevanceSelector {

    /** Pentru generare subiecte, PDF / interval până la 80 pagini: limită pagini trimise la model (TPM). */
    private const val EXAM_SUBJECTS_MAX_PAGES_UNDER_80 = 13

    /** Buget text combinat pentru subiecte (sub context mic al modelului + ieșire JSON mare). */
    private const val EXAM_SUBJECTS_MAX_INPUT_CHARS = 4_800
    private const val EXAM_SUBJECTS_MAX_SNIPPET_PER_PAGE = 360

    private const val MAX_CHARS_FOR_MODEL = 10_000
    private const val MAX_SNIPPET_CHARS_PER_PAGE = 620

    /** Quiz: top N pagini după scor — pool de candidați pentru selecție random. */
    const val QUIZ_CANDIDATE_POOL_SIZE = 30

    /** Quiz: câte pagini se aleg random din candidați la fiecare generare. */
    const val QUIZ_SESSION_POOL_SIZE = 15

    /** Legacy [selectTopPagesForQuiz]; quiz-ul activ folosește [selectQuizSessionPool]. */
    const val QUIZ_MAX_RELEVANT_PAGES = QUIZ_SESSION_POOL_SIZE

    /** Text trimis la model per pagină la generarea quiz-ului (1 apel = 1 pagină). */
    const val QUIZ_CHARS_PER_PAGE = 1_000

    private const val QUIZ_MAX_SELECTOR_CHARS = 24_000
    private const val QUIZ_MAX_SNIPPET_PER_PAGE = 1_200

    /** Legacy selectTopPagesForLocalModel (nu mai e folosit la Subiecte offline). */
    private const val LOCAL_MODEL_MAX_SELECTOR_CHARS = 6_000
    private const val LOCAL_MODEL_MAX_SNIPPET_PER_PAGE = 1_200

    private val keywordWeights = listOf(
        "abstract" to 4.0,
        "rezumat" to 4.0,
        "summary" to 3.5,
        "executive summary" to 5.0,
        "introducere" to 3.0,
        "introduction" to 3.0,
        "rezultate" to 3.5,
        "results" to 3.5,
        "findings" to 3.5,
        "discuție" to 3.5,
        "discussion" to 3.5,
        "concluzie" to 4.5,
        "concluzii" to 4.5,
        "conclusions" to 4.5,
        "recommendations" to 4.0,
        "recomandări" to 4.0
    )

    private fun baseContentRelevanceScore(text: String): Double {
        val t = text.trim()
        if (t.isBlank()) return 0.0
        val lower = t.lowercase()
        var score = 0.0
        for ((kw, w) in keywordWeights) {
            if (lower.contains(kw)) score += w
        }
        for (line in t.lines()) {
            val s = line.trim()
            if (s.length in 5..140) {
                if (s.endsWith(':')) score += 1.2
                val letters = s.count { it.isLetter() }
                if (letters > 5 && s == s.uppercase() && s.any { it.isLetter() }) score += 1.8
            }
        }
        val digitGroups = Regex("\\d+[.,]?\\d*%?").findAll(t).count()
        score += digitGroups.coerceAtMost(40) * 0.35
        val sentences = Regex("[.!?]+").findAll(t).count().coerceAtLeast(1)
        val density = sentences / (t.length / 1000.0 + 0.001)
        score += density.coerceAtMost(12.0) * 0.45
        score += (t.length / 2000.0).coerceAtMost(2.5)
        return score
    }

    /** Bonus pentru primele / ultimele pagini din domeniul de sumarizare (tot PDF-ul sau interval). */
    private fun firstLastBonusInScope(
        pageNumber: Int,
        documentTotalPages: Int,
        pageRange: IntRange?
    ): Double {
        if (documentTotalPages <= 0) return 0.0
        return if (pageRange == null) {
            var b = 0.0
            if (pageNumber <= 2) b += 3.5
            if (pageNumber >= documentTotalPages - 1) b += 3.5
            b
        } else {
            val n = pageRange.last - pageRange.first + 1
            if (n <= 0) return 0.0
            val pos = pageNumber - pageRange.first + 1
            var b = 0.0
            if (pos <= 2) b += 3.5
            if (pos >= n - 1) b += 3.5
            b
        }
    }

    fun scorePage(pageNumber: Int, totalPages: Int, text: String): Double =
        baseContentRelevanceScore(text) + firstLastBonusInScope(pageNumber, totalPages, null)

    private fun scorePageInSelection(
        pageNumber: Int,
        documentTotalPages: Int,
        pageRange: IntRange?,
        text: String
    ): Double =
        baseContentRelevanceScore(text) + firstLastBonusInScope(pageNumber, documentTotalPages, pageRange)

    /**
     * @param pageRange dacă nu e null, doar paginile din interval (inclusiv, clamp la 1..documentTotalPages)
     *        intră în selecție; pragurile de „pagini maxime” se aplică la lungimea intervalului.
     * @param forExamSubjects dacă e true: pentru domenii de până la 80 pagini se aleg cel mult 13 pagini
     *        și textul e tăiat mai agresiv (buget mic pentru cererea la model).
     */
    /**
     * Pentru modul offline: alege automat cele mai relevante [OfflineLlmModelConfig.MAX_OFFLINE_PAGES]
     * pagini din [pageRange] sau din tot documentul daca [pageRange] e null.
     */
    /**
     * Pentru quiz PDF: daca documentul are cel mult [QUIZ_MAX_RELEVANT_PAGES] pagini, foloseste
     * toate paginile utile; altfel cele [QUIZ_MAX_RELEVANT_PAGES] cele mai relevante.
     */
    fun selectTopPagesForQuiz(
        pages: List<PageNormalizedText>,
        pageStatuses: List<PdfPageTextStatus>,
        documentTotalPages: Int
    ): Pair<List<Int>, String> {
        val pageCap = if (documentTotalPages <= QUIZ_MAX_RELEVANT_PAGES) {
            documentTotalPages
        } else {
            QUIZ_MAX_RELEVANT_PAGES
        }
        return selectPagesAndBuildModelText(
            pages = pages,
            pageStatuses = pageStatuses,
            documentTotalPages = documentTotalPages,
            pageRange = null,
            forExamSubjects = false,
            fixedMaxPages = pageCap,
            maxInputChars = QUIZ_MAX_SELECTOR_CHARS,
            maxSnippetPerPage = QUIZ_MAX_SNIPPET_PER_PAGE
        )
    }

    fun selectTopPagesForLocalModel(
        pages: List<PageNormalizedText>,
        pageStatuses: List<PdfPageTextStatus>,
        documentTotalPages: Int,
        pageRange: IntRange? = null,
        forExamSubjects: Boolean = false
    ): Pair<List<Int>, String> = selectPagesAndBuildModelText(
        pages = pages,
        pageStatuses = pageStatuses,
        documentTotalPages = documentTotalPages,
        pageRange = pageRange,
        forExamSubjects = forExamSubjects,
        fixedMaxPages = OfflineLlmModelConfig.MAX_OFFLINE_PAGES,
        maxInputChars = LOCAL_MODEL_MAX_SELECTOR_CHARS,
        maxSnippetPerPage = LOCAL_MODEL_MAX_SNIPPET_PER_PAGE
    )

    fun selectPagesAndBuildModelText(
        pages: List<PageNormalizedText>,
        pageStatuses: List<PdfPageTextStatus>,
        documentTotalPages: Int,
        pageRange: IntRange? = null,
        forExamSubjects: Boolean = false,
        fixedMaxPages: Int? = null,
        maxInputChars: Int? = null,
        maxSnippetPerPage: Int? = null
    ): Pair<List<Int>, String> {
        if (documentTotalPages <= 0) return emptyList<Int>() to ""

        val clampedRange = pageRange?.let { r ->
            val first = r.first.coerceIn(1, documentTotalPages)
            val last = r.last.coerceIn(first, documentTotalPages)
            first..last
        }

        val inScope: (Int) -> Boolean = { p ->
            clampedRange == null || p in clampedRange
        }

        val ocrSet = pageStatuses.filter { it.needsOcr }.map { it.pageNumber }.toSet()
        val usable = pages
            .filter { it.pageNumber !in ocrSet && it.normalizedText.isNotBlank() && inScope(it.pageNumber) }
            .sortedBy { it.pageNumber }

        if (usable.isEmpty()) return emptyList<Int>() to ""

        val scopePageCount = clampedRange?.let { it.last - it.first + 1 } ?: documentTotalPages

        val maxPages = fixedMaxPages ?: when {
            forExamSubjects && scopePageCount <= 80 -> minOf(EXAM_SUBJECTS_MAX_PAGES_UNDER_80, usable.size)
            scopePageCount < 20 -> usable.size
            scopePageCount <= 80 -> minOf(15, usable.size)
            else -> minOf(22, usable.size)
        }

        if (fixedMaxPages == null && scopePageCount < 20 && maxPages >= usable.size) {
            return buildOrderedText(
                usable.map { it.pageNumber }.sorted(),
                usable,
                forExamSubjects,
                maxInputChars,
                maxSnippetPerPage
            )
        }

        val scored = usable.map { p ->
            Triple(
                p.pageNumber,
                scorePageInSelection(p.pageNumber, documentTotalPages, clampedRange, p.normalizedText),
                p.normalizedText
            )
        }

        val must = mutableSetOf<Int>().apply {
            if (clampedRange != null) {
                val fp = clampedRange.first
                val lp = clampedRange.last
                if (usable.any { it.pageNumber == fp }) add(fp)
                if (lp > fp && usable.any { it.pageNumber == fp + 1 }) add(fp + 1)
                if (usable.any { it.pageNumber == lp }) add(lp)
                if (lp > fp && usable.any { it.pageNumber == lp - 1 }) add(lp - 1)
            } else {
                if (usable.any { it.pageNumber == 1 }) add(1)
                if (usable.any { it.pageNumber == 2 }) add(2)
                if (usable.any { it.pageNumber == documentTotalPages }) add(documentTotalPages)
                if (documentTotalPages >= 2 && usable.any { it.pageNumber == documentTotalPages - 1 }) {
                    add(documentTotalPages - 1)
                }
            }
        }

        val orderedByScore = scored.sortedByDescending { it.second }.map { it.first }
        var idx = 0
        while (must.size < maxPages && idx < orderedByScore.size) {
            must.add(orderedByScore[idx])
            idx++
        }

        val picked = must.filter { p -> usable.any { it.pageNumber == p } }.sorted()
        val textByPage = usable.associateBy { it.pageNumber }
        return buildOrderedText(
            picked,
            picked.mapNotNull { textByPage[it] },
            forExamSubjects,
            maxInputChars,
            maxSnippetPerPage
        )
    }

    private fun buildOrderedText(
        pageNumbers: List<Int>,
        pagesInOrder: List<PageNormalizedText>,
        forExamSubjects: Boolean = false,
        maxInputChars: Int? = null,
        maxSnippetPerPage: Int? = null
    ): Pair<List<Int>, String> {
        val maxTotal = maxInputChars ?: if (forExamSubjects) {
            EXAM_SUBJECTS_MAX_INPUT_CHARS
        } else {
            MAX_CHARS_FOR_MODEL
        }
        val maxSnippet = maxSnippetPerPage ?: if (forExamSubjects) {
            EXAM_SUBJECTS_MAX_SNIPPET_PER_PAGE
        } else {
            MAX_SNIPPET_CHARS_PER_PAGE
        }
        val map = pagesInOrder.associateBy { it.pageNumber }
        val sb = StringBuilder()
        val used = mutableListOf<Int>()
        for (p in pageNumbers.sorted()) {
            val raw = map[p]?.normalizedText?.trim() ?: continue
            val header = "\n--- Pagina $p ---\n"
            val remaining = maxTotal - sb.length
            if (remaining <= header.length + 40) break
            val bodyRoom = (remaining - header.length).coerceAtMost(maxSnippet)
            val body = if (raw.length <= bodyRoom) {
                raw
            } else {
                raw.take(bodyRoom) + "\n[... fragment trunchiat pentru limită API ...]"
            }
            sb.append(header).append(body)
            used.add(p)
        }
        var text = sb.toString().trim()
        if (text.length > maxTotal) {
            text = text.take(maxTotal) + "\n[... text trunchiat pentru limită model ...]"
        }
        return used to text
    }

    /**
     * Top [topN] pagini după scor de relevanță (fără text combinat).
     */
    fun rankPagesForQuiz(
        pages: List<PageNormalizedText>,
        pageStatuses: List<PdfPageTextStatus>,
        documentTotalPages: Int,
        topN: Int = QUIZ_CANDIDATE_POOL_SIZE
    ): List<Int> {
        if (documentTotalPages <= 0) return emptyList()

        val ocrSet = pageStatuses.filter { it.needsOcr }.map { it.pageNumber }.toSet()
        val usable = pages
            .filter { it.pageNumber !in ocrSet && it.normalizedText.isNotBlank() }
            .sortedBy { it.pageNumber }

        if (usable.isEmpty()) return emptyList()

        val limit = topN.coerceAtMost(usable.size)
        return usable
            .map { p ->
                p.pageNumber to scorePageInSelection(
                    p.pageNumber,
                    documentTotalPages,
                    null,
                    p.normalizedText
                )
            }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    /**
     * La fiecare generare: [QUIZ_SESSION_POOL_SIZE] pagini random din top [QUIZ_CANDIDATE_POOL_SIZE] după scor.
     */
    fun selectQuizSessionPool(
        pages: List<PageNormalizedText>,
        pageStatuses: List<PdfPageTextStatus>,
        documentTotalPages: Int
    ): List<Int> {
        val ranked = rankPagesForQuiz(pages, pageStatuses, documentTotalPages)
        if (ranked.isEmpty()) return emptyList()
        val takeCount = minOf(QUIZ_SESSION_POOL_SIZE, ranked.size)
        return ranked.shuffled().take(takeCount)
    }

    /** [count] pagini alese random din [pool] (ex. pagini pentru apelurile API). */
    fun pickRandomPages(pool: List<Int>, count: Int): List<Int> {
        if (pool.isEmpty() || count <= 0) return emptyList()
        return pool.shuffled().take(count.coerceAtMost(pool.size))
    }

    fun buildQuizFallbackText(
        pageNumbers: List<Int>,
        pages: List<PageNormalizedText>
    ): String {
        val byPage = pages.associateBy { it.pageNumber }
        return pageNumbers.sorted().mapNotNull { pageNum ->
            val snippet = snippetForQuizPage(byPage[pageNum]?.normalizedText.orEmpty())
            if (snippet.isBlank()) null
            else "--- Pagina $pageNum ---\n$snippet"
        }.joinToString("\n\n")
    }

    fun snippetForQuizPage(normalizedText: String): String =
        normalizedText.trim().take(QUIZ_CHARS_PER_PAGE)
}
