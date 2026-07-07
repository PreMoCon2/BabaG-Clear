package com.example.autoclear

import android.content.Context

class SettingsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val preferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = preferences.getBoolean(KEY_FEATURE_ENABLED, true)

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_FEATURE_ENABLED, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "auto_clear_preferences"
        private const val KEY_FEATURE_ENABLED = "feature_enabled"

        fun isEnabled(context: Context): Boolean {
            return context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_FEATURE_ENABLED, true)
        }
    }
}
