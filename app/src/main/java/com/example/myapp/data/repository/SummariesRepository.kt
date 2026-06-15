package com.example.myapp.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class SummaryEntry(
    val id: String,
    val pdfName: String,
    val pdfUriString: String?,
    val content: String,
    val createdAt: Long
) {
    val pdfUri: Uri?
        get() = pdfUriString?.let { Uri.parse(it) }
}

class SummariesRepository(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val type = object : TypeToken<List<SummaryEntry>>() {}.type

    fun loadSummaries(): List<SummaryEntry> {
        val json = prefs.getString(KEY_SUMMARIES, null) ?: return emptyList()
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveSummaries(list: List<SummaryEntry>) {
        prefs.edit()
            .putString(KEY_SUMMARIES, gson.toJson(list))
            .apply()
    }

    fun addSummary(entry: SummaryEntry) {
        val current = loadSummaries().toMutableList()
        current.add(0, entry)
        saveSummaries(current)
    }

    fun removeSummary(id: String) {
        val filtered = loadSummaries().filterNot { it.id == id }
        saveSummaries(filtered)
    }

    fun clear() {
        prefs.edit().remove(KEY_SUMMARIES).apply()
    }

    companion object {
        private const val PREFS_NAME = "summaries_prefs"
        private const val KEY_SUMMARIES = "summaries_list"
    }
}

