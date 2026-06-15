package com.example.myapp.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class PdfStackViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PdfStackViewModel::class.java)) {
            return PdfStackViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
