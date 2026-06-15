package com.example.myapp.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

/**
 * Persists flash cards (question + answer + folder) locally.
 */
class CardsRepository(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val type = object : TypeToken<List<CardEntry>>() {}.type

    fun loadCards(): List<CardEntry> {
        val json = prefs.getString(KEY_CARDS, null) ?: return emptyList()
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveCards(cards: List<CardEntry>) {
        prefs.edit()
            .putString(KEY_CARDS, gson.toJson(cards))
            .apply()
    }

    fun addCard(question: String, answer: String, folder: String) {
        val list = loadCards().toMutableList()
        list.add(
            CardEntry(
                id = UUID.randomUUID().toString(),
                question = question,
                answer = answer,
                folder = folder.trim().ifBlank { "Altele" }
            )
        )
        saveCards(list)
    }

    fun removeCard(id: String) {
        val list = loadCards().filter { it.id != id }
        saveCards(list)
    }

    fun removeCardsForFolder(folder: String) {
        val key = folder.trim()
        if (key.isBlank()) return
        val list = loadCards().filterNot { entry ->
            entry.folder.trim().equals(key, ignoreCase = true)
        }
        saveCards(list)
    }

    data class CardEntry(
        val id: String,
        val question: String,
        val answer: String,
        val folder: String
    )

    companion object {
        private const val PREFS_NAME = "cards_prefs"
        private const val KEY_CARDS = "cards_list"
    }
}
