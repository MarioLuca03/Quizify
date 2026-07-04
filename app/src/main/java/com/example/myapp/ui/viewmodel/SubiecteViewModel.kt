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

import com.example.myapp.data.model.OfflineQuestionChunk
import com.example.myapp.data.model.OfflineQuizItem

import com.example.myapp.data.model.OfflineSubiectePhase

import com.example.myapp.data.model.PerPageSmartPdfExtraction

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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext



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

    private val _sessionNotice = MutableStateFlow<String?>(null)

    val sessionNotice: StateFlow<String?> = _sessionNotice.asStateFlow()



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

    private val _selectedOfflineModelId = MutableStateFlow(OfflineLlmModelCatalog.DEFAULT_MODEL_ID)

    val selectedOfflineModelId: StateFlow<String> = _selectedOfflineModelId.asStateFlow()



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



    private val _chunkPool = MutableStateFlow<List<OfflineQuestionChunk>>(emptyList())

    private val _usedChunkIds = MutableStateFlow<Set<String>>(emptySet())



    private val _currentQuizItem = MutableStateFlow<OfflineQuizItem?>(null)

    val currentQuizItem: StateFlow<OfflineQuizItem?> = _currentQuizItem.asStateFlow()



    private val _loadingStatus = MutableStateFlow("")

    val loadingStatus: StateFlow<String> = _loadingStatus.asStateFlow()

    private val _isModelWarmingUp = MutableStateFlow(false)

    val isModelWarmingUp: StateFlow<Boolean> = _isModelWarmingUp.asStateFlow()

    private val _isFinalizingSession = MutableStateFlow(false)

    val isFinalizingSession: StateFlow<Boolean> = _isFinalizingSession.asStateFlow()

    private var prefetchJob: Job? = null
    private var prefetchedItem: OfflineQuizItem? = null

    private var offlineWorkJob: Job? = null
    private var generationEpoch = 0

    private var modelWarmJob: Job? = null
    private var offlineModelWarmedUp = false
    private var failedChunkId: String? = null

    val isOfflineQuizActive: Boolean

        get() = _offlinePhase.value != OfflineSubiectePhase.Idle &&

            _offlinePhase.value != OfflineSubiectePhase.Exhausted



    fun hasMoreOfflineQuestions(): Boolean {
        val used = _usedChunkIds.value
        val current = _currentQuizItem.value?.chunkId
        return _chunkPool.value.any { it.id !in used && it.id != current }
    }



    init {

        refreshOfflineModelStatus()

        refreshConnectivity()

    }



    fun refreshConnectivity() {

        _isOfflineMode.value = !NetworkUtils.isOnline(context)

    }



    fun refreshOfflineModelStatus() {
        _selectedOfflineModelId.value = offlineModelRepository.getSelectedModel().id
        _offlineModelReady.value = offlineModelRepository.isModelReady()
    }

    fun onSubiecteScreenVisible() {
        refreshOfflineModelStatus()
        warmUpModelIfReady()
    }

    private fun warmUpModelIfReady() {
        if (!offlineModelRepository.isModelReady()) return
        if (offlineModelWarmedUp || modelWarmJob?.isActive == true) return
        modelWarmJob = viewModelScope.launch(Dispatchers.Default) {
            _isModelWarmingUp.value = true
            LocalLlmEngine.warmUp(context, offlineModelRepository.getModelPath())
            offlineModelWarmedUp = true
            _isModelWarmingUp.value = false
        }
    }

    fun setSelectedOfflineModel(modelId: String) {
        if (_isDownloadingModel.value) return
        offlineModelRepository.setSelectedModel(modelId)
        _selectedOfflineModelId.value = modelId
        offlineModelWarmedUp = false
        LocalLlmEngine.release()
        refreshOfflineModelStatus()
    }

    fun isOfflineModelInstalled(modelId: String): Boolean =
        offlineModelRepository.isModelReady(modelId)

    fun downloadOfflineModel() {
        if (_isDownloadingModel.value) return

        val model = offlineModelRepository.getSelectedModel()

        if (!NetworkUtils.isOnline(context)) {
            _error.value =
                "Ai nevoie de internet pentru descarcarea ${model.displayName} (${model.sizeLabel})."
            return
        }

        viewModelScope.launch {
            _isDownloadingModel.value = true
            _offlineModelReady.value = false
            _error.value = null
            _modelDownloadProgress.value = 0.02f

            offlineModelRepository.downloadModel(model) { progress ->
                _modelDownloadProgress.value = progress
            }.fold(
                onSuccess = {
                    _offlineModelReady.value = true
                    _modelDownloadProgress.value = 1f
                    warmUpModelIfReady()
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

        clearQuizSession()

        refreshConnectivity()

        _useFullDocument.value = true

        _rangeFrom.value = 1

        _rangeTo.value = 1

        _pdfPageCount.value = null

        viewModelScope.launch(Dispatchers.IO) {

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

        generationEpoch++
        offlineWorkJob?.cancel()
        offlineWorkJob = null

        clearQuizSession()

        LocalLlmEngine.release()

        offlineModelWarmedUp = false

        _isLoading.value = false
        _isFinalizingSession.value = false

    }



    private fun clearQuizSession() {

        _examPack.value = null

        _error.value = null

        _sessionNotice.value = null

        _usedLocalAi.value = false

        _offlinePhase.value = OfflineSubiectePhase.Idle

        _chunkPool.value = emptyList()

        _usedChunkIds.value = emptySet()

        _currentQuizItem.value = null

        _loadingStatus.value = ""

        clearPrefetch()
        failedChunkId = null

    }



    fun generate() {

        val item = _pickedPdf.value ?: return

        launchOfflineWork {

            refreshConnectivity()

            _error.value = null

            _sessionNotice.value = null

            _examPack.value = null

            val offline = _isOfflineMode.value
            if (offline && hasUnusedOfflineChunks() && failedChunkId == null) {
                _usedLocalAi.value = true
                loadNextQuestion()
                return@launchOfflineWork
            }

            _usedLocalAi.value = false

            clearOfflineQuizOnly()



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



    fun hasUnusedOfflineChunks(): Boolean {
        val used = _usedChunkIds.value
        return _chunkPool.value.any { it.id !in used }
    }

    fun hasPendingFragmentFailure(): Boolean = failedChunkId != null

    fun canFinalizeOfflineSession(): Boolean {
        if (!_isOfflineMode.value) return false
        return when (_offlinePhase.value) {
            OfflineSubiectePhase.LoadingQuestion,
            OfflineSubiectePhase.GenerationFailed,
            OfflineSubiectePhase.Question,
            OfflineSubiectePhase.Feedback -> true
            OfflineSubiectePhase.Idle -> _isLoading.value && _usedLocalAi.value
            else -> false
        }
    }

    fun finalizeOfflineSession() {
        if (!canFinalizeOfflineSession() || _isFinalizingSession.value) return

        viewModelScope.launch {
            _isFinalizingSession.value = true
            _isLoading.value = true
            _loadingStatus.value = "Se opreste modelul…"
            generationEpoch++
            offlineWorkJob?.cancel()
            offlineWorkJob = null
            clearPrefetch()
            modelWarmJob?.cancel()
            modelWarmJob = null
            offlineModelWarmedUp = false

            withContext(Dispatchers.Default) {
                LocalLlmEngine.release()
            }

            if (_offlinePhase.value == OfflineSubiectePhase.Feedback) {
                val finished = _currentQuizItem.value
                if (finished != null) {
                    _usedChunkIds.value = _usedChunkIds.value + finished.chunkId
                }
            }

            failedChunkId = null
            _currentQuizItem.value = null
            _offlinePhase.value = OfflineSubiectePhase.Idle
            _isLoading.value = false
            _loadingStatus.value = ""
            _error.value = null
            _isModelWarmingUp.value = false

            val completed = _usedChunkIds.value.size
            _sessionNotice.value = when {
                completed == 1 ->
                    "Sesiune finalizata. Ai parcurs 1 fragment. Poti continua mai tarziu."
                completed > 1 ->
                    "Sesiune finalizata. Ai parcurs $completed fragmente. Poti continua mai tarziu."
                _chunkPool.value.isNotEmpty() ->
                    "Sesiune finalizata. Poti continua mai tarziu."
                else -> "Sesiune finalizata."
            }

            if (_chunkPool.value.isNotEmpty()) {
                _usedLocalAi.value = true
            }

            _isFinalizingSession.value = false
        }
    }

    private fun launchOfflineWork(block: suspend () -> Unit) {
        offlineWorkJob?.cancel()
        offlineWorkJob = viewModelScope.launch {
            try {
                block()
            } catch (_: CancellationException) {
                // Sesiune anulata prin finalizeaza / navigare.
            }
        }
    }

    fun canSkipToNextOfflineChunk(): Boolean {
        val failed = failedChunkId ?: return false
        if (_currentQuizItem.value != null) return false
        if (_offlinePhase.value != OfflineSubiectePhase.GenerationFailed &&
            _offlinePhase.value != OfflineSubiectePhase.Idle
        ) {
            return false
        }
        val usedWithFailed = _usedChunkIds.value + failed
        return _chunkPool.value.any { it.id !in usedWithFailed }
    }

    fun skipCurrentOfflineChunk() {
        val failed = failedChunkId ?: return
        if (!canSkipToNextOfflineChunk()) return
        _usedChunkIds.value = _usedChunkIds.value + failed
        failedChunkId = null
        _error.value = null
        _offlinePhase.value = OfflineSubiectePhase.LoadingQuestion
        _isLoading.value = true
        _loadingStatus.value = "Generez intrebarea…"
        launchOfflineWork {
            loadNextQuestion()
        }
    }

    fun dismissGenerationFailure() {
        failedChunkId = null
        _error.value = null
        _loadingStatus.value = ""
        _offlinePhase.value = OfflineSubiectePhase.Idle
        _isLoading.value = false
    }



    fun continueOfflineQuiz() {

        if (_offlinePhase.value != OfflineSubiectePhase.Feedback) return

        val finished = _currentQuizItem.value ?: return

        _usedChunkIds.value = _usedChunkIds.value + finished.chunkId

        _currentQuizItem.value = null

        launchOfflineWork {

            loadNextQuestion()

        }

    }



    private suspend fun startOfflineQuiz(item: PdfItem) {

        if (!offlineModelRepository.isModelReady()) {

            _error.value =
                "Esti offline. Descarca ${offlineModelRepository.getSelectedModel().displayName} cand ai internet."

            return

        }

        val epoch = generationEpoch

        _isLoading.value = true

        _loadingStatus.value = "Citesc PDF-ul…"

        _usedLocalAi.value = true

        try {

            val per = extractPdf(item).getOrElse { e ->

                _error.value = e.message ?: "Nu s-a putut extrage textul din PDF."

                return

            }

            if (epoch != generationEpoch) return

            _pdfPageCount.value = per.totalPages



            val useFull = _useFullDocument.value

            val effRange = if (useFull) null else OfflinePdfConstraints.effectivePageRange(

                per.totalPages, false, _rangeFrom.value, _rangeTo.value

            )



            val pool = OfflineSubiectePageFilter.buildShuffledChunkPool(

                pages = per.pages,

                pageStatuses = per.pageStatuses,

                documentTotalPages = per.totalPages,

                pageRange = effRange

            )

            if (pool.isEmpty()) {

                _error.value =

                    "Niciun fragment de text suficient in PDF (min. ${OfflineLlmModelConfig.CHUNK_WORDS_MIN} cuvinte per fragment). " +
                        "Alege alt interval sau un PDF cu mai mult continut."

                return

            }

            if (epoch != generationEpoch) return

            _chunkPool.value = pool

            _usedChunkIds.value = emptySet()

            loadNextQuestion()

        } finally {

            if (_offlinePhase.value == OfflineSubiectePhase.Idle) {

                _isLoading.value = false

            }

        }

    }



    private suspend fun loadNextQuestion() {
        val epoch = generationEpoch
        prefetchJob?.cancel()
        val used = _usedChunkIds.value
        val nextChunk = _chunkPool.value.firstOrNull { it.id !in used }

        if (nextChunk == null) {
            if (epoch != generationEpoch) return
            clearPrefetch()
            _offlinePhase.value = OfflineSubiectePhase.Exhausted
            _isLoading.value = false
            return
        }

        val cached = prefetchedItem
        prefetchedItem = null
        if (cached != null && cached.chunkId !in used) {
            if (epoch != generationEpoch) return
            applyQuestion(cached, epoch)
            return
        }

        _offlinePhase.value = OfflineSubiectePhase.LoadingQuestion
        _isLoading.value = true
        _loadingStatus.value = "Generez intrebarea…"
        _error.value = null

        val modelPath = offlineModelRepository.getModelPath()
        val item = generateQuestionForChunk(nextChunk, modelPath)
        if (epoch != generationEpoch) return
        if (item == null) {
            failedChunkId = nextChunk.id
            val message = "Nu s-a putut genera o intrebare din acest fragment."
            _error.value = message
            _loadingStatus.value = message
            clearPrefetch()
            _offlinePhase.value = OfflineSubiectePhase.GenerationFailed
            _isLoading.value = true
            return
        }

        failedChunkId = null
        applyQuestion(item, epoch)
    }

    private fun applyQuestion(item: OfflineQuizItem, epoch: Int) {
        if (epoch != generationEpoch) return
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
            val epoch = generationEpoch
            delay(400)
            if (!isActive || epoch != generationEpoch) return@launch
            val used = _usedChunkIds.value
            val current = _currentQuizItem.value?.chunkId
            val nextChunk = _chunkPool.value.firstOrNull {
                it.id !in used && it.id != current
            } ?: run {
                prefetchedItem = null
                return@launch
            }

            val modelPath = offlineModelRepository.getModelPath()
            val prefetched = generateQuestionForChunk(nextChunk, modelPath)
            if (epoch != generationEpoch) return@launch
            prefetchedItem = prefetched
        }
    }

    private suspend fun generateQuestionForChunk(
        chunk: OfflineQuestionChunk,
        modelPath: String
    ): OfflineQuizItem? {
        val chunkText = chunk.text.trim()
        if (chunkText.isBlank()) return null

        val result = LocalLlmEngine.generatePageQuestion(
            context = context,
            modelPath = modelPath,
            pageText = chunkText
        ).getOrNull() ?: return null

        if (result.skip ||
            result.intrebare.isBlank() ||
            result.raspunsAsteptat.isBlank()
        ) {
            return null
        }

        return OfflineQuizItem(
            chunkId = chunk.id,
            pageNumber = chunk.pageNumber,
            question = result.intrebare,
            expectedAnswer = result.raspunsAsteptat
        )
    }

    private fun clearPrefetch() {
        prefetchJob?.cancel()
        prefetchJob = null
        prefetchedItem = null
    }

    private suspend fun extractPdf(item: PdfItem): Result<PerPageSmartPdfExtraction> =
        withContext(Dispatchers.IO) {
            takeUriPermission(item)
            PdfTextExtractor.extractPerPageForSmartSummary(context, item.uri)
        }

    override fun onCleared() {
        offlineWorkJob?.cancel()
        modelWarmJob?.cancel()
        LocalLlmEngine.release()
        super.onCleared()
    }



    private suspend fun startOnlineGeneration(item: PdfItem) {

        _isLoading.value = true

        try {

            takeUriPermission(item)

            val useFull = _useFullDocument.value

            val from = _rangeFrom.value

            val to = _rangeTo.value



            takeUriPermission(item)

            val per = extractPdf(item).getOrElse { e ->

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
        _chunkPool.value = emptyList()
        _usedChunkIds.value = emptySet()
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


