package com.example.myapp.data.local

import android.content.Context

class OfflineLlmPreferences(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSelectedModelId(): String =
        prefs.getString(KEY_MODEL_ID, OfflineLlmModelCatalog.DEFAULT_MODEL_ID)
            ?: OfflineLlmModelCatalog.DEFAULT_MODEL_ID

    fun setSelectedModelId(id: String) {
        prefs.edit().putString(KEY_MODEL_ID, id).apply()
    }

    companion object {
        private const val PREFS_NAME = "offline_llm_prefs"
        private const val KEY_MODEL_ID = "selected_model_id"
    }
}
