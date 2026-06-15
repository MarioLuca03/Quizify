package com.example.myapp.ui.viewmodel



import android.app.Application

import android.content.Context

import androidx.lifecycle.AndroidViewModel

import androidx.lifecycle.viewModelScope

import com.example.myapp.data.local.OfflineLlmModelCatalog
import com.example.myapp.data.local.OfflineLlmModelConfig
import com.example.myapp.data.local.OfflineLlmModelRepository

import com.example.myapp.data.model.AnswerEvaluation

import com.example.myapp.data.model.ExamSubjectsPack

import com.example.myapp.data.model.OfflineQuizItem

import com.example.myapp.data.model.OfflineSubiectePhase

import com.example.myapp.data.model.PdfPageContent

import com.example.myapp.data.service.GroqService

import com.example.myapp.data.service.LocalLlmEngine

import com.example.myapp.utils.NetworkUtils

import com.example.myapp.utils.OfflineAnswerEvaluator

import com.example.myapp.utils.OfflinePdfConstraints

import com.example.myapp.utils.OfflineSubiectePageFilter

import com.example.myapp.utils.PdfPageRelevanceSelector

import com.example.myapp.utils.PdfTextExtractor

import kotlinx.coroutines.flow.MutableStateFlow

import kotlinx.coroutines.flow.StateFlow

import kotlinx.coroutines.flow.asStateFlow

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch



class SubiecteViewModel(

    application: Application,

    private val apiKey: String

) : AndroidViewModel(application) {



    private val context: Context

        get() = getApplication<Application>().applicationContext



    private val offlineModelRepository by lazy { OfflineLlmModelRepository(context) }

    private val groqService by lazy { GroqService(apiKey) }



    private val _pickedPdf = MutableStateFlow<PdfItem?>(null)

    val pickedPdf: StateFlow<PdfItem?> = _pickedPdf.asStateFlow()



    private val _isLoading = MutableStateFlow(false)

    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()



    private val _examPack = MutableStateFlow<ExamSubjectsPack?>(null)

    val examPack: StateFlow<ExamSubjectsPack?> = _examPack.asStateFlow()



    private val _error = MutableStateFlow<String?>(null)

    val error: StateFlow<String?> = _error.asStateFlow()



    private val _pdfPageCount = MutableStateFlow<Int?>(null)

    val pdfPageCount: StateFlow<Int?> = _pdfPageCount.asStateFlow()



    private val _useFullDocument = MutableStateFlow(true)

    val useFullDocument: StateFlow<Boolean> = _useFullDocument.asStateFlow()



    private val _rangeFrom = MutableStateFlow(1)

    val rangeFrom: StateFlow<Int> = _rangeFrom.asStateFlow()



    private val _rangeTo = MutableStateFlow(1)

    val rangeTo: StateFlow<Int> = _rangeTo.asStateFlow()



    private val _offlineModelReady = MutableStateFlow(false)

    val offlineModelReady: StateFlow<Boolean> = _offlineModelReady.asStateFlow()



    private val _isDownloadingModel = MutableStateFlow(false)

    val isDownloadingModel: StateFlow<Boolean> = _isDownloadingModel.asStateFlow()



    private val _modelDownloadProgress = MutableStateFlow(0f)

    val modelDownloadProgress: StateFlow<Float> = _modelDownloadProgress.asStateFlow()



    private val _isOfflineMode = MutableStateFlow(false)

    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode.asStateFlow()



    private val _usedLocalAi = MutableStateFlow(false)

    val usedLocalAi: StateFlow<Boolean> = _usedLocalAi.asStateFlow()



    private val _offlinePhase = MutableStateFlow(OfflineSubiectePhase.Idle)

    val offlinePhase: StateFlow<OfflineSubiectePhase> = _offlinePhase.asStateFlow()



    private val _pagePool = MutableStateFlow<List<PdfPageContent>>(emptyList())

    val pagePool: StateFlow<List<PdfPageContent>> = _pagePool.asStateFlow()



    private val _usedPageNumbers = MutableStateFlow<Set<Int>>(emptySet())

    val usedPageNumbers: StateFlow<Set<Int>> = _usedPageNumbers.asStateFlow()



    private val _currentQuizItem = MutableStateFlow<OfflineQuizItem?>(null)

    val currentQuizItem: StateFlow<OfflineQuizItem?> = _currentQuizItem.asStateFlow()



    private val _loadingStatus = MutableStateFlow("")

    val loadingStatus: StateFlow<String> = _loadingStatus.asStateFlow()

    private var prefetchJob: Job? = null
    private var prefetchedItem: OfflineQuizItem? = null

    val isOfflineQuizActive: Boolean

        get() = _offlinePhase.value != OfflineSubiectePhase.Idle &&

            _offlinePhase.value != OfflineSubiectePhase.Exhausted



    fun hasMoreOfflineQuestions(): Boolean {

        val used = _usedPageNumbers.value

        val current = _currentQuizItem.value?.pageNumber

        return _pagePool.value.any { it.pageNumber !in used && it.pageNumber != current }

    }



    init {

        refreshOfflineModelStatus()

        refreshConnectivity()

    }



    fun refreshConnectivity() {

        _isOfflineMode.value = !NetworkUtils.isOnline(context)

    }



    fun refreshOfflineModelStatus() {

        _offlineModelReady.value = offlineModelRepository.isModelReady()

    }



    fun downloadOfflineModel() {

        if (_isDownloadingModel.value) return

        if (!NetworkUtils.isOnline(context)) {

            _error.value =
                "Ai nevoie de internet pentru descarcarea ${OfflineLlmModelCatalog.defaultModel.displayName} (~520 MB)."

            return

        }

        viewModelScope.launch {

            _isDownloadingModel.value = true

            _offlineModelReady.value = false

            _error.value = null

            _modelDownloadProgress.value = 0.02f

            offlineModelRepository.downloadModel { progress ->

                _modelDownloadProgress.value = progress

            }.fold(

                onSuccess = {

                    _offlineModelReady.value = true

                    _modelDownloadProgress.value = 1f

                },

                onFailure = { e ->

                    _error.value = e.message ?: "Descarcarea modelului a esuat."

                    _offlineModelReady.value = offlineModelRepository.isModelReady()

                }

            )

            _isDownloadingModel.value = false

        }

    }



    fun setUseFullDocument(value: Boolean) {

        refreshConnectivity()

        _useFullDocument.value = value

        _error.value = null

    }



    fun setRangeFrom(value: Int) {

        _rangeFrom.value = value.coerceAtLeast(1)

    }



    fun setRangeTo(value: Int) {

        _rangeTo.value = value.coerceAtLeast(1)

    }



    fun pickPdf(item: PdfItem) {

        _pickedPdf.value = item

        clearAllSessions()

        refreshConnectivity()

        _useFullDocument.value = true

        _rangeFrom.value = 1

        _rangeTo.value = 1

        _pdfPageCount.value = null

        viewModelScope.launch {

            PdfTextExtractor.getPdfPageCount(context, item.uri).fold(

                onSuccess = { n ->

                    _pdfPageCount.value = n

                    _rangeFrom.value = 1

                    _rangeTo.value = n

                },

                onFailure = { _pdfPageCount.value = null }

            )

        }

    }



    fun resetToPicker() {

        clearAllSessions()

        _isLoading.value = false

    }



    private fun clearAllSessions() {

        _examPack.value = null

        _error.value = null

        _usedLocalAi.value = false

        _offlinePhase.value = OfflineSubiectePhase.Idle

        _pagePool.value = emptyList()

        _usedPageNumbers.value = emptySet()

        _currentQuizItem.value = null

        _loadingStatus.value = ""

        clearPrefetch()
        LocalLlmEngine.release()

    }



    fun generate() {

        val item = _pickedPdf.value ?: return

        viewModelScope.launch {

            refreshConnectivity()

            _error.value = null

            _examPack.value = null

            _usedLocalAi.value = false

            clearOfflineQuizOnly()



            val offline = _isOfflineMode.value

            if (offline) {

                startOfflineQuiz(item)

            } else {

                startOnlineGeneration(item)

            }

        }

    }



    suspend fun evaluateOnlineAnswer(

        question: String,

        expectedSolution: String,

        userAnswer: String

    ): Result<AnswerEvaluation> = groqService.evaluateExamAnswer(

        question = question,

        expectedSolution = expectedSolution,

        userAnswer = userAnswer

    )



    fun submitOfflineAnswer(userAnswer: String) {
        val answer = userAnswer.trim()
        if (answer.isBlank()) {
            _error.value = "Scrie un raspuns inainte de a continua."
            return
        }
        val item = _currentQuizItem.value ?: return
        if (_offlinePhase.value != OfflineSubiectePhase.Question) return

        _error.value = null
        val evaluation = OfflineAnswerEvaluator.evaluate(
            question = item.question,
            expectedAnswer = item.expectedAnswer,
            userAnswer = answer
        )
        _currentQuizItem.value = item.copy(
            userAnswer = answer,
            evaluation = evaluation
        )
        _offlinePhase.value = OfflineSubiectePhase.Feedback
        startPrefetchNextQuestion()
    }



    fun continueOfflineQuiz() {

        if (_offlinePhase.value != OfflineSubiectePhase.Feedback) return

        val finished = _currentQuizItem.value ?: return

        _usedPageNumbers.value = _usedPageNumbers.value + finished.pageNumber

        _currentQuizItem.value = null

        viewModelScope.launch {

            loadNextQuestion()

        }

    }



    private suspend fun startOfflineQuiz(item: PdfItem) {

        if (!offlineModelRepository.isModelReady()) {

            _error.value =
                "Esti offline. Descarca ${OfflineLlmModelCatalog.defaultModel.displayName} cand ai internet (~520 MB)."

            return

        }



        _isLoading.value = true

        _loadingStatus.value = "Citesc PDF-ul…"

        _usedLocalAi.value = true

        try {

            takeUriPermission(item)

            val per = PdfTextExtractor.extractPerPageForSmartSummary(context, item.uri).getOrElse { e ->

                _error.value = e.message ?: "Nu s-a putut extrage textul din PDF."

                return

            }

            _pdfPageCount.value = per.totalPages



            val useFull = _useFullDocument.value

            val effRange = if (useFull) null else OfflinePdfConstraints.effectivePageRange(

                per.totalPages, false, _rangeFrom.value, _rangeTo.value

            )



            val pool = OfflineSubiectePageFilter.buildShuffledQuestionPool(

                pages = per.pages,

                pageStatuses = per.pageStatuses,

                documentTotalPages = per.totalPages,

                pageRange = effRange

            )

            if (pool.isEmpty()) {

                _error.value =

                    "Nicio pagina valida (min. ${OfflineLlmModelConfig.MIN_PAGE_WORDS} cuvinte si " +
                        "${OfflineLlmModelConfig.MIN_PAGE_CHARS} caractere). Alege alt interval sau un PDF cu mai mult text."

                return

            }



            _pagePool.value = pool

            _usedPageNumbers.value = emptySet()

            loadNextQuestion()

        } finally {

            if (_offlinePhase.value == OfflineSubiectePhase.Idle) {

                _isLoading.value = false

            }

        }

    }



    private suspend fun loadNextQuestion() {
        prefetchJob?.cancel()
        val used = _usedPageNumbers.value.toMutableSet()
        val remaining = _pagePool.value.filter { it.pageNumber !in used }

        if (remaining.isEmpty()) {
            clearPrefetch()
            LocalLlmEngine.release()
            _offlinePhase.value = OfflineSubiectePhase.Exhausted
            _isLoading.value = false
            return
        }

        val cached = prefetchedItem
        prefetchedItem = null
        if (cached != null && cached.pageNumber !in used) {
            applyQuestion(cached)
            return
        }

        val modelPath = offlineModelRepository.getModelPath()
        for (page in remaining) {
            _offlinePhase.value = OfflineSubiectePhase.LoadingQuestion
            _isLoading.value = true
            _loadingStatus.value = "Generez intrebarea (pagina ${page.pageNumber})…"
            _error.value = null

            val item = generateQuestionForPage(page, modelPath, isPrefetch = false)
            if (item == null) {
                used.add(page.pageNumber)
                _usedPageNumbers.value = used.toSet()
                continue
            }

            applyQuestion(item)
            return
        }

        clearPrefetch()
        LocalLlmEngine.release()
        _offlinePhase.value = OfflineSubiectePhase.Exhausted
        _isLoading.value = false
    }

    private fun applyQuestion(item: OfflineQuizItem) {
        _currentQuizItem.value = item
        _offlinePhase.value = OfflineSubiectePhase.Question
        _isLoading.value = false
        startPrefetchNextQuestion()
    }

    private fun startPrefetchNextQuestion() {
        prefetchJob?.cancel()
        if (!hasMoreOfflineQuestions()) {
            prefetchedItem = null
            return
        }

        prefetchJob = viewModelScope.launch(Dispatchers.Default) {
            delay(400)
            if (!isActive) return@launch
            val used = _usedPageNumbers.value
            val current = _currentQuizItem.value?.pageNumber
            val candidates = _pagePool.value.filter {
                it.pageNumber !in used && it.pageNumber != current
            }
            if (candidates.isEmpty()) {
                prefetchedItem = null
                return@launch
            }

            val modelPath = offlineModelRepository.getModelPath()
            for (page in candidates) {
                if (!isActive) return@launch
                val item = generateQuestionForPage(page, modelPath, isPrefetch = true) ?: continue
                prefetchedItem = item
                return@launch
            }
            prefetchedItem = null
        }
    }

    private suspend fun generateQuestionForPage(
        page: PdfPageContent,
        modelPath: String,
        isPrefetch: Boolean = true
    ): OfflineQuizItem? {
        val chunkText = OfflineSubiectePageFilter.textChunkForAi(page)
        if (chunkText.isBlank()) return null

        val questionResult = LocalLlmEngine.generatePageQuestion(
            context = context,
            modelPath = modelPath,
            pageText = chunkText
        ).getOrElse { e ->
            if (!isPrefetch) {
                _error.value = e.message ?: "Generarea intrebarii a esuat."
                _offlinePhase.value = OfflineSubiectePhase.Idle
            }
            return null
        }

        if (questionResult.skip ||
            questionResult.intrebare.isBlank() ||
            questionResult.raspunsAsteptat.isBlank()
        ) {
            return null
        }

        return OfflineQuizItem(
            pageNumber = page.pageNumber,
            question = questionResult.intrebare,
            expectedAnswer = questionResult.raspunsAsteptat
        )
    }

    private fun clearPrefetch() {
        prefetchJob?.cancel()
        prefetchJob = null
        prefetchedItem = null
    }



    private suspend fun startOnlineGeneration(item: PdfItem) {

        _isLoading.value = true

        try {

            takeUriPermission(item)

            val useFull = _useFullDocument.value

            val from = _rangeFrom.value

            val to = _rangeTo.value



            val per = PdfTextExtractor.extractPerPageForSmartSummary(context, item.uri).getOrElse { e ->

                _error.value = e.message ?: "Nu s-a putut extrage textul din PDF."

                return

            }

            _pdfPageCount.value = per.totalPages



            val effRange = if (useFull) null else OfflinePdfConstraints.effectivePageRange(

                per.totalPages, false, from, to

            )



            val (_, selectedText) = PdfPageRelevanceSelector.selectPagesAndBuildModelText(

                pages = per.pages,

                pageStatuses = per.pageStatuses,

                documentTotalPages = per.totalPages,

                pageRange = effRange,

                forExamSubjects = true

            )

            if (selectedText.isBlank()) {

                _error.value =

                    "Nu exista suficient text in intervalul ales. Ajusteaza paginile sau alege alt PDF."

                return

            }



            groqService.generateExamSubjectsFromSmartSelection(item.name, selectedText).fold(

                onSuccess = { pack -> _examPack.value = pack },

                onFailure = { e -> _error.value = e.message ?: "Eroare la generare." }

            )

        } finally {

            _isLoading.value = false

        }

    }



    private fun clearOfflineQuizOnly() {
        _offlinePhase.value = OfflineSubiectePhase.Idle
        _pagePool.value = emptyList()
        _usedPageNumbers.value = emptySet()
        _currentQuizItem.value = null
        _loadingStatus.value = ""
        clearPrefetch()
    }



    private fun takeUriPermission(item: PdfItem) {

        try {

            context.contentResolver.takePersistableUriPermission(

                item.uri,

                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION

            )

        } catch (_: SecurityException) {

        }

    }

}



class SubiecteViewModelFactory(

    private val application: Application,

    private val apiKey: String

) : androidx.lifecycle.ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")

    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {

        if (modelClass.isAssignableFrom(SubiecteViewModel::class.java)) {

            return SubiecteViewModel(application, apiKey) as T

        }

        throw IllegalArgumentException("Unknown ViewModel class")

    }

}


