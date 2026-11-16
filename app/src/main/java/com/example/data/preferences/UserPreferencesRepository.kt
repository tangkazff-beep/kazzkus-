package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.karsaDataStore: DataStore<Preferences> by preferencesDataStore(name = "karsa_prefs")

data class UserSettings(
    val isDarkTheme: Boolean = true,
    val preferredLanguage: String = "Indonesia",
    val githubToken: String = ""
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val PREFERRED_LANGUAGE = stringPreferencesKey("preferred_language")
        val GITHUB_TOKEN = stringPreferencesKey("github_token")
    }

    val settingsFlow: Flow<UserSettings> = context.karsaDataStore.data.map { prefs ->
        UserSettings(
            isDarkTheme = prefs[Keys.DARK_THEME] ?: true,
            preferredLanguage = prefs[Keys.PREFERRED_LANGUAGE] ?: "Indonesia",
            githubToken = prefs[Keys.GITHUB_TOKEN] ?: ""
        )
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.karsaDataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    suspend fun setPreferredLanguage(language: String) {
        context.karsaDataStore.edit { it[Keys.PREFERRED_LANGUAGE] = language }
    }

    suspend fun setGitHubToken(token: String) {
        context.karsaDataStore.edit { it[Keys.GITHUB_TOKEN] = token.trim() }
    }
}
