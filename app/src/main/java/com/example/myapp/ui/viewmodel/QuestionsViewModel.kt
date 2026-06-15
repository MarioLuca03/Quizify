package com.example.myapp.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapp.data.model.Question
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class QuestionsUiState(
    val questions: List<Question> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedAnswers: Map<Int, Int> = emptyMap()
)

class QuestionsViewModel(
    private val context: Context,
    private val pdfUri: Uri,
    private val apiKey: String
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(QuestionsUiState())
    val uiState: StateFlow<QuestionsUiState> = _uiState.asStateFlow()
    
    fun generateQuestions() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Funcționalitatea de generare întrebări nu este disponibilă"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Eroare necunoscută"
                )
            }
        }
    }
    
    fun selectAnswer(questionIndex: Int, answerIndex: Int) {
        val currentAnswers = _uiState.value.selectedAnswers.toMutableMap()
        currentAnswers[questionIndex] = answerIndex
        _uiState.value = _uiState.value.copy(selectedAnswers = currentAnswers)
    }
}

