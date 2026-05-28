package com.aura.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "Aura_prefs")

class AppDataStore(private val context: Context) {

    private val dataStore = context.dataStore

    companion object {
        val KEY_API_KEY = stringPreferencesKey("api_key")
        val KEY_DEFAULT_QUALITY = stringPreferencesKey("default_quality")
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_IS_SETUP_DONE = booleanPreferencesKey("is_setup_done")
        val KEY_ADULT_CONTENT = booleanPreferencesKey("adult_content_enabled")
        val KEY_CUSTOM_IPTV_URL = stringPreferencesKey("custom_iptv_url")
        val KEY_AD_PROVIDER = stringPreferencesKey("ad_provider")
    }

    val apiKey: Flow<String?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_API_KEY] }

    val defaultQuality: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_DEFAULT_QUALITY] ?: "720p" }

    val isSetupDone: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_IS_SETUP_DONE] ?: false }

    val adultContentEnabled: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_ADULT_CONTENT] ?: false }

    val customIptvUrl: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_CUSTOM_IPTV_URL] ?: "" }

    val adProvider: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_AD_PROVIDER] ?: "startio" }


    suspend fun saveApiKey(apiKey: String) {
        dataStore.edit {
            it[KEY_API_KEY] = apiKey
            it[KEY_IS_SETUP_DONE] = true
        }
    }

    suspend fun saveDefaultQuality(quality: String) {
        dataStore.edit { it[KEY_DEFAULT_QUALITY] = quality }
    }

    suspend fun clearApiKey() {
        dataStore.edit {
            it.remove(KEY_API_KEY)
            it[KEY_IS_SETUP_DONE] = false
        }
    }

    suspend fun saveAdultContent(enabled: Boolean) {
        dataStore.edit { it[KEY_ADULT_CONTENT] = enabled }
    }

    suspend fun saveCustomIptvUrl(url: String) {
        dataStore.edit { it[KEY_CUSTOM_IPTV_URL] = url }
    }

    suspend fun saveAdProvider(provider: String) {
        dataStore.edit { it[KEY_AD_PROVIDER] = provider }
    }

}

