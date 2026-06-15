package com.example.myapp.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapp.data.model.PdfExtractionDiagnostics
import com.example.myapp.data.model.PdfSmartSummaryResult
import com.example.myapp.data.repository.PdfSummaryCacheRepository
import com.example.myapp.data.repository.PdfTextCacheRepository
import com.example.myapp.data.service.GroqService
import com.example.myapp.utils.NetworkUtils
import com.example.myapp.utils.PdfContentHasher
import com.example.myapp.utils.PdfPageRelevanceSelector
import com.example.myapp.utils.PdfTextExtractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FastSummaryViewModel(
    application: Application,
    private val apiKey: String
) : AndroidViewModel(application) {

    private val context: Context
        get() = getApplication<Application>().applicationContext

    private val textCacheRepository by lazy { PdfTextCacheRepository(context) }
    private val summaryCacheRepository by lazy { PdfSummaryCacheRepository(context) }
    private val groqService by lazy { GroqService(apiKey) }

    private val _pickedPdf = MutableStateFlow<PdfItem?>(null)
    val pickedPdf: StateFlow<PdfItem?> = _pickedPdf.asStateFlow()

    private val _diagnostics = MutableStateFlow<PdfExtractionDiagnostics?>(null)
    val diagnostics: StateFlow<PdfExtractionDiagnostics?> = _diagnostics.asStateFlow()

    private val _smartResult = MutableStateFlow<PdfSmartSummaryResult?>(null)
    val smartResult: StateFlow<PdfSmartSummaryResult?> = _smartResult.asStateFlow()

    private val _fromCache = MutableStateFlow(false)
    val fromCache: StateFlow<Boolean> = _fromCache.asStateFlow()

    private val _usedLocalAi = MutableStateFlow(false)
    val usedLocalAi: StateFlow<Boolean> = _usedLocalAi.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

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

    private val _isOfflineMode = MutableStateFlow(false)
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode.asStateFlow()

    init {
        refreshConnectivity()
    }

    fun refreshConnectivity() {
        _isOfflineMode.value = !NetworkUtils.isOnline(context)
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
        _diagnostics.value = null
        _smartResult.value = null
        _fromCache.value = false
        _usedLocalAi.value = false
        _error.value = null
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

    fun clearPickedPdf() {
        _pickedPdf.value = null
        _diagnostics.value = null
        _smartResult.value = null
        _fromCache.value = false
        _usedLocalAi.value = false
        _error.value = null
        _pdfPageCount.value = null
        _useFullDocument.value = true
        _rangeFrom.value = 1
        _rangeTo.value = 1
    }

    fun resetToPicker() {
        _smartResult.value = null
        _fromCache.value = false
        _usedLocalAi.value = false
        _diagnostics.value = null
        _error.value = null
        _isLoading.value = false
    }

    fun clearResult() = resetToPicker()

    fun summarize(forceRefresh: Boolean = false) {
        val item = _pickedPdf.value ?: return
        viewModelScope.launch {
            refreshConnectivity()
            _isLoading.value = true
            _error.value = null
            _smartResult.value = null
            _fromCache.value = false
            _usedLocalAi.value = false
            try {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        item.uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                }

                val contentHash = try {
                    PdfContentHasher.sha256HexOfPdf(context, item.uri)
                } catch (e: Exception) {
                    _error.value = e.message ?: "Nu s-a putut citi PDF-ul pentru cache."
                    _isLoading.value = false
                    return@launch
                }

                val useFull = _useFullDocument.value
                val from = _rangeFrom.value
                val to = _rangeTo.value

                if (_isOfflineMode.value) {
                    _error.value =
                        "Rezumatul rapid necesita internet. Modelul local este disponibil doar la Subiecte."
                    _isLoading.value = false
                    return@launch
                }

                if (forceRefresh) {
                    summaryCacheRepository.clearAllScopes(contentHash)
                }

                if (!forceRefresh && useFull) {
                    val cached = summaryCacheRepository.load(contentHash, PdfSummaryCacheRepository.SCOPE_ALL)
                    if (cached != null) {
                        _smartResult.value = cached
                        _fromCache.value = true
                        _usedLocalAi.value = cached.mode == MODE_LOCAL
                        _diagnostics.value = null
                        _isLoading.value = false
                        return@launch
                    }
                }

                val per = PdfTextExtractor.extractPerPageForSmartSummary(context, item.uri).getOrElse { e ->
                    _error.value = e.message ?: "Nu s-a putut extrage textul din PDF."
                    _isLoading.value = false
                    return@launch
                }

                _pdfPageCount.value = per.totalPages

                val effRange = if (useFull) null else com.example.myapp.utils.OfflinePdfConstraints.effectivePageRange(
                    per.totalPages, false, from, to
                )
                val scopeId = if (effRange == null) PdfSummaryCacheRepository.SCOPE_ALL
                else PdfSummaryCacheRepository.scopeIdForRange(effRange.first, effRange.last)

                if (!forceRefresh && !useFull) {
                    val cached = summaryCacheRepository.load(contentHash, scopeId)
                    if (cached != null) {
                        _smartResult.value = cached
                        _fromCache.value = true
                        _usedLocalAi.value = cached.mode == MODE_LOCAL
                        _diagnostics.value = null
                        _isLoading.value = false
                        return@launch
                    }
                }

                val (pagesUsed, selectedText) = PdfPageRelevanceSelector.selectPagesAndBuildModelText(
                    pages = per.pages,
                    pageStatuses = per.pageStatuses,
                    documentTotalPages = per.totalPages,
                    pageRange = effRange
                )
                if (selectedText.isBlank()) {
                    _error.value = "Nu exista suficient text selectabil in intervalul ales."
                    _isLoading.value = false
                    return@launch
                }

                val cappedNorm = capNormalizedForCache(per.fullNormalizedDocument())
                _diagnostics.value = PdfExtractionDiagnostics(
                    normalizedText = cappedNorm,
                    totalPages = per.totalPages,
                    pageStatuses = per.pageStatuses
                )
                textCacheRepository.saveText(item.uri, cappedNorm)

                val summaryText = groqService.summarizeSmartPdfFromSelectedText(selectedText).getOrElse { e ->
                    _error.value = e.message ?: "Eroare la generarea rezumatului."
                    _isLoading.value = false
                    return@launch
                }
                val mode = MODE_CLOUD

                val out = PdfSmartSummaryResult(
                    summary = summaryText,
                    pagesUsed = pagesUsed,
                    mode = mode,
                    scopeAll = effRange == null,
                    scopeFrom = effRange?.first,
                    scopeTo = effRange?.last
                )
                summaryCacheRepository.save(per.pdfContentSha256, scopeId, out)
                _smartResult.value = out
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun capNormalizedForCache(text: String): String {
        if (text.length <= MAX_NORMALIZED_STORE) return text
        return text.take(MAX_NORMALIZED_STORE) +
            "\n\n[... text trunchiat ...]"
    }

    companion object {
        private const val MAX_NORMALIZED_STORE = 400_000
        const val MODE_CLOUD = "fast_smart"
        const val MODE_LOCAL = "fast_smart_local"
    }
}

class FastSummaryViewModelFactory(
    private val application: Application,
    private val apiKey: String
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FastSummaryViewModel::class.java)) {
            return FastSummaryViewModel(application, apiKey) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
