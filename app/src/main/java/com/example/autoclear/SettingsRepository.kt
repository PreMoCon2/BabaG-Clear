package com.example.autoclear

import android.content.Context

// Small shared-preferences wrapper used by both the Compose UI and the
// AccessibilityService so they read the same on/off flag.
class SettingsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val preferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Default to enabled so first-run behavior matches the original app goal.
    fun isEnabled(): Boolean = preferences.getBoolean(KEY_FEATURE_ENABLED, true)

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_FEATURE_ENABLED, enabled).apply()
    }

    // The speed slider is stored as a simple integer multiplier so the UI and
    // service can both reason about the same 1x-10x scale.
    fun getSpeedMultiplier(): Int {
        return preferences
            .getInt(KEY_SPEED_MULTIPLIER, DEFAULT_SPEED_MULTIPLIER)
            .coerceIn(MIN_SPEED_MULTIPLIER, MAX_SPEED_MULTIPLIER)
    }

    fun setSpeedMultiplier(multiplier: Int) {
        preferences.edit()
            .putInt(
                KEY_SPEED_MULTIPLIER,
                multiplier.coerceIn(MIN_SPEED_MULTIPLIER, MAX_SPEED_MULTIPLIER),
            )
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "auto_clear_preferences"
        private const val KEY_FEATURE_ENABLED = "feature_enabled"
        private const val KEY_SPEED_MULTIPLIER = "speed_multiplier"
        private const val MIN_SPEED_MULTIPLIER = 1
        private const val MAX_SPEED_MULTIPLIER = 10
        private const val DEFAULT_SPEED_MULTIPLIER = 5

        // Static-style helper for service code paths where creating a repository
        // instance would add noise without giving us any extra value.
        fun isEnabled(context: Context): Boolean {
            return context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_FEATURE_ENABLED, true)
        }

        fun getSpeedMultiplier(context: Context): Int {
            return context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_SPEED_MULTIPLIER, DEFAULT_SPEED_MULTIPLIER)
                .coerceIn(MIN_SPEED_MULTIPLIER, MAX_SPEED_MULTIPLIER)
        }
    }
}
