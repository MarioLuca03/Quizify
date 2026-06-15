package com.example.myapp.utils

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.result.ActivityResultLauncher

class SpeechRecognitionManager(private val context: Context) {
    
    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }
    
    fun createSpeechIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ro-RO") // Romanian
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Spune răspunsul tău")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
    }
    
    fun parseSpeechResult(resultCode: Int, data: android.content.Intent?): String? {
        if (resultCode == android.app.Activity.RESULT_OK && data != null) {
            val results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            return results?.firstOrNull()
        }
        return null
    }
}



