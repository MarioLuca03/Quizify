package com.example.myapp.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapp.data.model.QuizQuestion
import com.example.myapp.data.model.CompletedQuiz
import com.example.myapp.data.repository.PdfTextCacheRepository
import com.example.myapp.data.service.GroqService
import com.example.myapp.utils.PdfPageRelevanceSelector
import com.example.myapp.utils.PdfTextExtractor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class QuizUiState(
    val questions: List<QuizQuestion> = emptyList(),
    val subject: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedAnswers: Map<Int, Int> = emptyMap(),
    val currentQuestionIndex: Int = 0,
    val isFinished: Boolean = false,
    val score: Int = 0
)

class QuizViewModel(
    private val apiKey: String
) : ViewModel() {

    companion object {
        private const val QUESTIONS_PER_CALL = 2
        private const val MAX_TOP_UP_RETRIES = 2
    }
    
    private val _uiState = MutableStateFlow(QuizUiState())
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()
    
    private val groqService = GroqService(apiKey)

    fun generateQuizFromPdf(
        context: Context,
        pdfUri: Uri,
        numQuestions: Int,
        difficulty: String,
        previousPerformance: String?
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                selectedAnswers = emptyMap(),
                currentQuestionIndex = 0,
                isFinished = false,
                score = 0
            )
            
            try {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        pdfUri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: SecurityException) {
                }

                val pdfTextCacheRepository = PdfTextCacheRepository(context)
                val perPage = PdfTextExtractor.extractPerPageForSmartSummary(context, pdfUri).getOrElse { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Eroare la citirea PDF-ului: ${error.message}"
                    )
                    return@launch
                }

                val sessionPool = PdfPageRelevanceSelector.selectQuizSessionPool(
                    pages = perPage.pages,
                    pageStatuses = perPage.pageStatuses,
                    documentTotalPages = perPage.totalPages
                )

                if (sessionPool.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "PDF-ul nu conține text extras pe pagini utile. Poate fi scanat (imagine) sau fără text selectabil."
                    )
                    return@launch
                }

                val pdfText = PdfPageRelevanceSelector.buildQuizFallbackText(
                    pageNumbers = sessionPool,
                    pages = perPage.pages
                )
                pdfTextCacheRepository.saveText(pdfUri, pdfText)

                val quizSubject = "Quiz din PDF"

                val textByPage = perPage.pages.associateBy { it.pageNumber }
                val adaptivePerformance = previousPerformance?.takeIf { it.isNotBlank() }

                val idealCalls = questionsPerCallDistribution(numQuestions).size
                val numCalls = minOf(idealCalls, sessionPool.size).coerceAtLeast(1)
                val questionsPerCall = redistributeQuestions(numQuestions, numCalls)
                val selectedPages = PdfPageRelevanceSelector.pickRandomPages(
                    pool = sessionPool,
                    count = numCalls
                )
                val usedPages = mutableSetOf<Int>()
                val allQuestions = mutableListOf<QuizQuestion>()

                selectedPages.mapIndexed { index, pageNum ->
                    async {
                        val snippet = PdfPageRelevanceSelector.snippetForQuizPage(
                            textByPage[pageNum]?.normalizedText.orEmpty()
                        )
                        pageNum to fetchQuestionsForPage(
                            pageNum,
                            snippet,
                            questionsPerCall[index],
                            difficulty,
                            adaptivePerformance
                        )
                    }
                }.awaitAll().forEach { (pageNum, questions) ->
                    usedPages.add(pageNum)
                    allQuestions.addAll(questions)
                }

                var topUpRetries = 0
                while (allQuestions.size < numQuestions && topUpRetries < MAX_TOP_UP_RETRIES) {
                    val needed = numQuestions - allQuestions.size
                    val nextPage = sessionPool.filter { it !in usedPages }.shuffled().firstOrNull()
                        ?: sessionPool.shuffled().first()
                    usedPages.add(nextPage)
                    val snippet = PdfPageRelevanceSelector.snippetForQuizPage(
                        textByPage[nextPage]?.normalizedText.orEmpty()
                    )
                    allQuestions.addAll(
                        fetchQuestionsForPage(
                            pageNumber = nextPage,
                            pageText = snippet,
                            count = minOf(needed, QUESTIONS_PER_CALL),
                            difficulty = difficulty,
                            previousPerformance = adaptivePerformance
                        )
                    )
                    topUpRetries++
                }

                if (allQuestions.size < numQuestions) {
                    val needed = numQuestions - allQuestions.size
                    val fallbackText = pdfText.trim().take(8_000)
                    if (fallbackText.isNotBlank()) {
                        allQuestions.addAll(
                            fetchQuestionsForPage(
                                pageNumber = 0,
                                pageText = fallbackText,
                                count = needed,
                                difficulty = difficulty,
                                previousPerformance = adaptivePerformance
                            )
                        )
                    }
                }

                val finalQuestions = finalizeQuestions(allQuestions, numQuestions)
                if (finalQuestions.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Nu s-au putut genera întrebări din PDF."
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        questions = finalQuestions,
                        subject = quizSubject,
                        isLoading = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Eroare necunoscută"
                )
            }
        }
    }
    
    fun selectAnswer(questionIndex: Int, answerIndex: Int) {
        if (_uiState.value.selectedAnswers.containsKey(questionIndex)) {
            return
        }
        val currentAnswers = _uiState.value.selectedAnswers.toMutableMap()
        currentAnswers[questionIndex] = answerIndex
        _uiState.value = _uiState.value.copy(selectedAnswers = currentAnswers)
    }
    
    fun goToNextQuestion() {
        val currentIndex = _uiState.value.currentQuestionIndex
        val maxIndex = _uiState.value.questions.size - 1
        if (currentIndex < maxIndex) {
            _uiState.value = _uiState.value.copy(currentQuestionIndex = currentIndex + 1)
        }
    }
    
    fun goToPreviousQuestion() {
        val currentIndex = _uiState.value.currentQuestionIndex
        if (currentIndex > 0) {
            _uiState.value = _uiState.value.copy(currentQuestionIndex = currentIndex - 1)
        }
    }
    
    private suspend fun fetchQuestionsForPage(
        pageNumber: Int,
        pageText: String,
        count: Int,
        difficulty: String,
        previousPerformance: String?
    ): List<QuizQuestion> {
        if (count <= 0 || pageText.isBlank()) return emptyList()
        return groqService.generateQuizFromPageText(
            pageNumber = pageNumber,
            pageText = pageText,
            numQuestions = count,
            difficulty = difficulty,
            previousPerformance = previousPerformance
        )
            .getOrNull()
            ?.questions
            .orEmpty()
    }

    private fun questionsPerCallDistribution(total: Int): List<Int> {
        val numCalls = (total + QUESTIONS_PER_CALL - 1) / QUESTIONS_PER_CALL
        return redistributeQuestions(total, numCalls)
    }

    private fun redistributeQuestions(total: Int, slots: Int): List<Int> {
        if (slots <= 0) return emptyList()
        val base = total / slots
        val extra = total % slots
        return List(slots) { i -> base + if (i < extra) 1 else 0 }
    }

    private fun finalizeQuestions(pool: List<QuizQuestion>, numQuestions: Int): List<QuizQuestion> {
        val scored = pool.map { question -> question to scoreQuestion(question) }
            .sortedByDescending { it.second }
        val usedKeys = mutableSetOf<String>()
        val result = mutableListOf<QuizQuestion>()
        for ((question, _) in scored) {
            if (result.size >= numQuestions) break
            val key = question.question.trim().lowercase()
            if (key !in usedKeys) {
                result.add(question)
                usedKeys.add(key)
            }
        }
        if (result.size < numQuestions) {
            for ((question, _) in scored) {
                if (result.size >= numQuestions) break
                if (question !in result) result.add(question)
            }
        }
        return result.take(numQuestions)
    }

    private fun scoreQuestion(question: QuizQuestion): Int {
        var score = 0
        
        val questionLength = question.question.length
        when {
            questionLength in 30..150 -> score += 30
            questionLength in 20..200 -> score += 20
            else -> score += 10
        }
        
        val validOptions = question.options.count { it.trim().isNotEmpty() }
        score += validOptions * 10
        
        if (!question.explanation.isNullOrBlank()) {
            score += 20
        }
        
        val avgOptionLength = question.options.map { it.length }.average().toInt()
        if (avgOptionLength in 10..80) {
            score += 10
        }
        
        if (question.correctIndex in 0..3) {
            score += 10
        }
        
        return score
    }
    
    private fun selectBestQuestions(
        scoredQuestions: List<Triple<QuizQuestion, Int, Int>>,
        numQuestions: Int,
        totalChunks: Int
    ): List<QuizQuestion> {
        val selected = mutableListOf<QuizQuestion>()
        val usedChunkIndices = mutableSetOf<Int>()
        val usedQuestions = mutableSetOf<String>()
        
        val questionsByChunk = scoredQuestions.groupBy { it.third }
        
        questionsByChunk.forEach { (chunkIndex, questions) ->
            val bestFromChunk = questions.maxByOrNull { it.second }
            bestFromChunk?.let { (question, _, _) ->
                val questionKey = question.question.trim().lowercase()
                if (!usedQuestions.contains(questionKey) && selected.size < numQuestions) {
                    selected.add(question)
                    usedQuestions.add(questionKey)
                    usedChunkIndices.add(chunkIndex)
                }
            }
        }
        
        for ((question, _, _) in scoredQuestions) {
            if (selected.size >= numQuestions) break
            
            val questionKey = question.question.trim().lowercase()
            if (!usedQuestions.contains(questionKey)) {
                selected.add(question)
                usedQuestions.add(questionKey)
            }
        }
        
        return selected.shuffled().take(numQuestions)
    }

    fun calculateScore(
        pdfName: String? = null,
        pdfUri: Uri? = null,
        onQuizCompleted: ((CompletedQuiz) -> Unit)? = null
    ) {
        val questions = _uiState.value.questions
        val selectedAnswers = _uiState.value.selectedAnswers
        var correct = 0
        
        questions.forEachIndexed { index, question ->
            val selected = selectedAnswers[index]
            if (selected != null && selected == question.correctIndex) {
                correct++
            }
        }
        
        _uiState.value = _uiState.value.copy(
            score = correct,
            isFinished = true
        )
        
        val completedQuiz = CompletedQuiz(
            id = UUID.randomUUID().toString(),
            pdfName = pdfName,
            pdfUri = pdfUri,
            subject = _uiState.value.subject ?: "Quiz",
            score = correct,
            totalQuestions = questions.size,
            questions = questions,
            selectedAnswers = selectedAnswers
        )
        onQuizCompleted?.invoke(completedQuiz)
    }

    fun loadQuizFromHistory(quiz: CompletedQuiz) {
        _uiState.value = QuizUiState(
            questions = quiz.questions,
            subject = quiz.subject.ifBlank { "Quiz din istoric" },
            isLoading = false,
            error = null,
            selectedAnswers = emptyMap(),
            currentQuestionIndex = 0,
            isFinished = false,
            score = 0
        )
    }

    fun loadQuestionsFromHistoryPool(
        questions: List<QuizQuestion>,
        subject: String
    ) {
        _uiState.value = QuizUiState(
            questions = questions,
            subject = subject.ifBlank { "Quiz offline din istoric" },
            isLoading = false,
            error = null,
            selectedAnswers = emptyMap(),
            currentQuestionIndex = 0,
            isFinished = false,
            score = 0
        )
    }
}

