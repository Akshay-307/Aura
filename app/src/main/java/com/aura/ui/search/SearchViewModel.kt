package com.aura.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.data.local.SearchHistoryDao
import com.aura.data.local.SearchHistoryEntity
import com.aura.data.datastore.AppDataStore
import com.aura.data.model.SearchResult
import com.aura.domain.usecase.SearchUseCase
import com.aura.utils.NetworkResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SearchViewModel(
    private val searchUseCase: SearchUseCase,
    private val searchHistoryDao: SearchHistoryDao,
    private val dataStore: AppDataStore
) : ViewModel() {

    private val _searchState = MutableStateFlow<NetworkResult<List<SearchResult>>>(NetworkResult.Error("idle"))
    val searchState: StateFlow<NetworkResult<List<SearchResult>>> = _searchState

    val searchHistory = searchHistoryDao.getSearchHistory()

    private val httpClient = okhttp3.OkHttpClient()
    private val _suggestionsState = MutableStateFlow<List<String>>(emptyList())
    val suggestionsState: StateFlow<List<String>> = _suggestionsState

    private var debounceJob: Job? = null

    fun fetchSuggestions(query: String) {
        if (query.isBlank()) {
            _suggestionsState.value = emptyList()
            return
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                val url = "https://suggestqueries.google.com/complete/search?client=youtube&q=$encoded&hl=en"
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (body.startsWith("[")) {
                        val jsonArray = org.json.JSONArray(body)
                        if (jsonArray.length() > 1) {
                            val suggestionsArray = jsonArray.getJSONArray(1)
                            val list = mutableListOf<String>()
                            for (i in 0 until minOf(suggestionsArray.length(), 6)) {
                                list.add(suggestionsArray.getString(i))
                            }
                            _suggestionsState.value = list
                            return@launch
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore error, fallback to empty
            }
            _suggestionsState.value = emptyList()
        }
    }

    fun search(query: String, tab: SearchTab = SearchTab.ALL) {
        if (query.isBlank()) {
            _searchState.value = NetworkResult.Error("idle")
            return
        }

        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(500) // 500ms debounce
            val includeAdult = try { dataStore.adultContentEnabled.first() } catch (_: Exception) { false }
            val flow = when (tab) {
                SearchTab.ALL -> searchUseCase.searchAll(query, includeAdult)
                SearchTab.MOVIES -> searchUseCase.searchMovies(query, includeAdult)
                SearchTab.ANIME -> searchUseCase.searchAnime(query, includeAdult)
            }
            flow.collect { _searchState.value = it }
        }
    }

    fun saveSearchHistory(query: String) {
        viewModelScope.launch {
            searchHistoryDao.insert(SearchHistoryEntity(query = query))
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            searchHistoryDao.clearAll()
        }
    }
}

enum class SearchTab { ALL, MOVIES, ANIME }

