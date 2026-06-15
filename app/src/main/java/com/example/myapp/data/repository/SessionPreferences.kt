package com.example.myapp.data.repository

import android.content.Context

class SessionPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isRememberMeEnabled(): Boolean = prefs.getBoolean(KEY_REMEMBER_ME, true)

    fun setRememberMeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMEMBER_ME, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "session_preferences"
        private const val KEY_REMEMBER_ME = "remember_me"
    }
}
