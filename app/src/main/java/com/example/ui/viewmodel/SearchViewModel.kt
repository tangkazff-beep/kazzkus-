package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.AiSearchResultItem
import com.example.data.model.CodeProject
import com.example.data.model.CodingLesson
import com.example.data.model.Conversation
import com.example.data.model.GitHubBookmark
import com.example.data.repository.GeminiAiRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class SearchUiState(
    val query: String = "",
    val results: List<AiSearchResultItem> = emptyList(),
    val isAiSearching: Boolean = false,
    val usedAiSearch: Boolean = false,
    val errorMessage: String? = null
)

class SearchViewModel(
    private val aiRepo: GeminiAiRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun updateQuery(
        query: String,
        conversations: List<Conversation>,
        lessons: List<CodingLesson>,
        projects: List<CodeProject>,
        bookmarks: List<GitHubBookmark>
    ) {
        val localMatches = performInstantLocalFilter(query, conversations, lessons, projects, bookmarks)
        _uiState.update {
            it.copy(query = query, results = localMatches, usedAiSearch = false, errorMessage = null)
        }
    }

    fun runAiSemanticSearch(
        conversations: List<Conversation>,
        lessons: List<CodingLesson>,
        projects: List<CodeProject>,
        bookmarks: List<GitHubBookmark>,
        responseLanguage: String
    ) {
        val query = _uiState.value.query.trim()
        if (query.isBlank()) return

        val corpusArray = JSONArray()
        conversations.take(20).forEach { c ->
            corpusArray.put(JSONObject().apply {
                put("sourceType", "chat")
                put("itemId", c.id)
                put("title", c.title)
                put("summary", "${c.mode} • ${c.previewText}")
            })
        }
        lessons.take(15).forEach { l ->
            corpusArray.put(JSONObject().apply {
                put("sourceType", "lesson")
                put("itemId", l.id)
                put("title", l.topic)
                put("summary", "${l.programmingLanguage} (${l.difficulty})")
            })
        }
        projects.take(15).forEach { p ->
            corpusArray.put(JSONObject().apply {
                put("sourceType", "project")
                put("itemId", p.id)
                put("title", p.title)
                put("summary", "${p.language} • ${p.prompt.take(120)}")
            })
        }
        bookmarks.take(15).forEach { b ->
            corpusArray.put(JSONObject().apply {
                put("sourceType", "github")
                put("itemId", b.id)
                put("title", b.fullName)
                put("summary", "${b.language} • ${b.description}")
            })
        }

        if (corpusArray.length() == 0) {
            _uiState.update { it.copy(results = emptyList(), usedAiSearch = true) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isAiSearching = true, errorMessage = null) }
            try {
                val aiResults = aiRepo.semanticSearchUserData(
                    query = query,
                    corpusItemsJson = corpusArray.toString(),
                    responseLanguage = responseLanguage
                )
                _uiState.update {
                    it.copy(
                        results = aiResults,
                        isAiSearching = false,
                        usedAiSearch = true
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI semantic search failed", e)
                _uiState.update {
                    it.copy(
                        isAiSearching = false,
                        errorMessage = e.localizedMessage ?: "Gagal melakukan pencarian AI."
                    )
                }
            }
        }
    }

    private fun performInstantLocalFilter(
        query: String,
        conversations: List<Conversation>,
        lessons: List<CodingLesson>,
        projects: List<CodeProject>,
        bookmarks: List<GitHubBookmark>
    ): List<AiSearchResultItem> {
        val q = query.trim().lowercase()
        val items = mutableListOf<AiSearchResultItem>()
        conversations.filter { q.isBlank() || it.title.lowercase().contains(q) || it.previewText.lowercase().contains(q) }
            .take(8)
            .forEach {
                items.add(AiSearchResultItem("chat", it.id, it.title, "Riwayat Obrolan (${it.mode})", it.previewText))
            }
        lessons.filter { q.isBlank() || it.topic.lowercase().contains(q) || it.programmingLanguage.lowercase().contains(q) }
            .take(6)
            .forEach {
                items.add(AiSearchResultItem("lesson", it.id, it.topic, "Pelajaran ${it.programmingLanguage} • ${it.difficulty}", "Langkah ${it.completedSteps}/${it.totalSteps}"))
            }
        projects.filter { q.isBlank() || it.title.lowercase().contains(q) || it.language.lowercase().contains(q) || it.prompt.lowercase().contains(q) }
            .take(6)
            .forEach {
                items.add(AiSearchResultItem("project", it.id, it.title, "Proyek ${it.language}", it.prompt))
            }
        bookmarks.filter { q.isBlank() || it.fullName.lowercase().contains(q) || it.description.lowercase().contains(q) }
            .take(6)
            .forEach {
                items.add(AiSearchResultItem("github", it.id, it.fullName, "Repositori GitHub (${it.language})", it.description))
            }
        return items
    }

    companion object {
        private const val TAG = "SearchVM"
        fun provideFactory(aiRepo: GeminiAiRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { SearchViewModel(aiRepo) }
        }
    }
}
