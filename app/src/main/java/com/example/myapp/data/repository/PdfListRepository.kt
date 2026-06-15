package com.example.myapp.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Persists the list of PDFs (fileName in app storage + display name) so the list
 * survives app restarts. Uses SharedPreferences and JSON.
 */
class PdfListRepository(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val type = object : TypeToken<List<PdfEntry>>() {}.type
    private val foldersType = object : TypeToken<List<String>>() {}.type

    fun loadList(): List<PdfEntry> {
        val json = prefs.getString(KEY_PDF_LIST, null) ?: return emptyList()
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveList(entries: List<PdfEntry>) {
        prefs.edit()
            .putString(KEY_PDF_LIST, gson.toJson(entries))
            .apply()
    }

    fun loadFolders(): List<String> {
        val json = prefs.getString(KEY_FOLDERS, null) ?: return emptyList()
        return try {
            gson.fromJson(json, foldersType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveFolders(folders: List<String>) {
        prefs.edit()
            .putString(KEY_FOLDERS, gson.toJson(folders))
            .apply()
    }

    data class PdfEntry(
        val fileName: String,
        val displayName: String,
        val category: String? = null
    )

    companion object {
        private const val PREFS_NAME = "pdf_list_prefs"
        private const val KEY_PDF_LIST = "pdf_list"
        private const val KEY_FOLDERS = "pdf_folders"
    }
}
