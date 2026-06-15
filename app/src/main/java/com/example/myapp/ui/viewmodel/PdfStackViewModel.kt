package com.example.myapp.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapp.data.repository.CardsRepository
import com.example.myapp.data.repository.CompletedQuizRepository
import com.example.myapp.data.repository.PdfListRepository
import com.example.myapp.utils.PdfStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PdfItem(
    val uri: Uri,
    val name: String,
    val category: String
)

class PdfStackViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application.applicationContext
    private val repository = PdfListRepository(app)
    private val completedQuizRepository = CompletedQuizRepository(app)
    private val cardsRepository = CardsRepository(app)
    private val pdfDir = PdfStorageManager.getPdfDirectory(app)

    private val _pdfItems = MutableStateFlow<List<PdfItem>>(emptyList())
    val pdfItems: StateFlow<List<PdfItem>> = _pdfItems.asStateFlow()

    val pdfUris: StateFlow<List<Uri>> = _pdfItems.map { items ->
        items.map { it.uri }
    }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _folders = MutableStateFlow<List<String>>(emptyList())
    val folders: StateFlow<List<String>> = _folders.asStateFlow()

    init {
        loadPersistedList()
        loadFolders()
    }

    private fun loadFolders() {
        _folders.value = repository.loadFolders()
    }

    private fun loadPersistedList() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val entries = repository.loadList()
                val items = entries.mapNotNull { entry ->
                    val file = File(pdfDir, entry.fileName)
                    if (file.exists()) {
                        val category = entry.category ?: "Altele"
                        PdfItem(Uri.fromFile(file), entry.displayName, category)
                    } else null
                }
                _pdfItems.value = items
                // Merge folders with categories from loaded PDFs (for old data)
                val categoriesFromPdfs = items.map { it.category }.toSet()
                _folders.value = (_folders.value + categoriesFromPdfs).distinct()
                if (categoriesFromPdfs.isNotEmpty()) repository.saveFolders(_folders.value)
                // Remove stale entries from persistence if some files were missing
                if (items.size < entries.size) {
                    repository.saveList(items.map { item ->
                        val path = item.uri.path ?: return@map null
                        PdfListRepository.PdfEntry(
                            fileName = path.substringAfterLast('/'),
                            displayName = item.name,
                            category = item.category
                        )
                    }.filterNotNull())
                }
            }
        }
    }

    private fun persistList() {
        val entries = _pdfItems.value.mapNotNull { item ->
            when (item.uri.scheme) {
                "file" -> {
                    val path = item.uri.path ?: return@mapNotNull null
                    val fileName = path.substringAfterLast('/')
                    if (fileName.isNotEmpty()) {
                        PdfListRepository.PdfEntry(
                            fileName = fileName,
                            displayName = item.name,
                            category = item.category
                        )
                    } else null
                }
                else -> null
            }
        }
        repository.saveList(entries)
    }

    fun addFolder(name: String) {
        val trimmed = name.trim().ifBlank { return }
        if (_folders.value.contains(trimmed)) return
        _folders.value = _folders.value + trimmed
        repository.saveFolders(_folders.value)
    }

    fun addPdf(uri: Uri, name: String, category: String) {
        if (_pdfItems.value.size >= 5) return
        val folder = category.trim().ifBlank { "Altele" }
        if (!_folders.value.contains(folder)) {
            _folders.value = _folders.value + folder
            repository.saveFolders(_folders.value)
        }
        val item = PdfItem(uri = uri, name = name, category = folder)
        _pdfItems.value = _pdfItems.value + item
        persistList()
    }

    fun getPdfName(uri: Uri): String? {
        return _pdfItems.value.find { it.uri == uri }?.name
    }

    fun removePdf(index: Int) {
        val list = _pdfItems.value
        if (index !in list.indices) return
        val item = list[index]
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                when (item.uri.scheme) {
                    "file" -> {
                        val path = item.uri.path ?: ""
                        if (path.isNotEmpty()) {
                            val file = File(path)
                            PdfStorageManager.deletePdf(file)
                        }
                    }
                }
                completedQuizRepository.removeQuizzesForPdf(item.uri)
                cardsRepository.removeCardsForFolder(item.name)
            }
            val newList = list.toMutableList().apply { removeAt(index) }
            _pdfItems.value = newList
            persistList()
        }
    }

    fun updatePdfName(index: Int, newName: String) {
        val newList = _pdfItems.value.toMutableList()
        if (index in newList.indices) {
            newList[index] = newList[index].copy(name = newName)
            _pdfItems.value = newList
            persistList()
        }
    }
}