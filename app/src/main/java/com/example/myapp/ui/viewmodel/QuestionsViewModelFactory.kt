package com.example.myapp.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class QuestionsViewModelFactory(
    private val context: Context,
    private val pdfUri: Uri,
    private val apiKey: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(QuestionsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return QuestionsViewModel(context, pdfUri, apiKey) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}











