package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.GitHubBookmark
import com.example.data.model.GitHubCommitItem
import com.example.data.model.GitHubContentItem
import com.example.data.model.GitHubRepo
import com.example.data.model.GitHubUser
import com.example.data.repository.GeminiAiRepository
import com.example.data.repository.GitHubRepository
import com.example.data.repository.KarsaFirestoreRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GitHubUiState(
    val searchQuery: String = "kotlin compose",
    val tokenInput: String = "",
    val authenticatedUser: GitHubUser? = null,
    val myRepos: List<GitHubRepo> = emptyList(),
    val searchResults: List<GitHubRepo> = emptyList(),
    val selectedRepo: GitHubRepo? = null,
    val currentPath: String = "",
    val directoryItems: List<GitHubContentItem> = emptyList(),
    val recentCommits: List<GitHubCommitItem> = emptyList(),
    val viewingFileName: String? = null,
    val viewingFileContent: String? = null,
    val aiAnalysisResult: String? = null,
    val isLoading: Boolean = false,
    val isAnalyzingWithAi: Boolean = false,
    val errorMessage: String? = null
)

class GitHubViewModel(
    private val githubRepo: GitHubRepository,
    private val firestoreRepo: KarsaFirestoreRepository,
    private val aiRepo: GeminiAiRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GitHubUiState())
    val uiState: StateFlow<GitHubUiState> = _uiState.asStateFlow()

    val bookmarks: StateFlow<List<GitHubBookmark>> = firestoreRepo.observeGitHubBookmarks()
        .catch { err ->
            val fs = generateSequence(err) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fs == null && err is CancellationException) throw err
            Log.w(TAG, "Error observing GitHub bookmarks", fs ?: err)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private var activeToken: String = ""

    fun syncToken(token: String) {
        if (token != activeToken) {
            activeToken = token
            _uiState.update { it.copy(tokenInput = token) }
            if (token.isNotBlank()) {
                loadAuthenticatedAccount(token)
            } else {
                _uiState.update { it.copy(authenticatedUser = null, myRepos = emptyList()) }
            }
        }
    }

    fun updateSearchQuery(query: String) = _uiState.update { it.copy(searchQuery = query) }
    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
    fun closeFileViewer() = _uiState.update {
        it.copy(viewingFileName = null, viewingFileContent = null, aiAnalysisResult = null)
    }

    private fun loadAuthenticatedAccount(token: String) {
        viewModelScope.launch {
            githubRepo.getAuthenticatedProfile(token).onSuccess { user ->
                _uiState.update { it.copy(authenticatedUser = user) }
            }
            githubRepo.getMyRepositories(token).onSuccess { repos ->
                _uiState.update { it.copy(myRepos = repos) }
            }
        }
    }

    fun searchRepos() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            githubRepo.searchRepositories(query, activeToken)
                .onSuccess { repos ->
                    _uiState.update { it.copy(searchResults = repos, isLoading = false) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = err.localizedMessage ?: "Gagal mencari repositori GitHub.")
                    }
                }
        }
    }

    fun openRepository(repo: GitHubRepo) {
        _uiState.update {
            it.copy(
                selectedRepo = repo,
                currentPath = "",
                viewingFileName = null,
                viewingFileContent = null,
                aiAnalysisResult = null,
                isLoading = true,
                errorMessage = null
            )
        }
        val parts = repo.fullName.split("/")
        if (parts.size != 2) return
        val owner = parts[0]
        val name = parts[1]
        viewModelScope.launch {
            val dirRes = githubRepo.getDirectoryContents(owner, name, "", activeToken)
            val commitsRes = githubRepo.getRecentCommits(owner, name, activeToken)
            _uiState.update {
                it.copy(
                    directoryItems = dirRes.getOrDefault(emptyList()),
                    recentCommits = commitsRes.getOrDefault(emptyList()),
                    isLoading = false,
                    errorMessage = dirRes.exceptionOrNull()?.localizedMessage
                )
            }
        }
    }

    fun openBookmark(bookmark: GitHubBookmark) {
        val parts = bookmark.fullName.split("/")
        if (parts.size != 2) return
        openRepository(
            GitHubRepo(
                name = parts[1],
                fullName = bookmark.fullName,
                description = bookmark.description,
                language = bookmark.language,
                stargazersCount = bookmark.stars,
                htmlUrl = bookmark.htmlUrl
            )
        )
    }

    fun navigateBackRepo() {
        val state = _uiState.value
        if (state.viewingFileName != null) {
            closeFileViewer()
            return
        }
        val repo = state.selectedRepo ?: return
        if (state.currentPath.isNotBlank()) {
            val parentPath = state.currentPath.substringBeforeLast('/', "")
            loadDirectory(repo, parentPath)
        } else {
            _uiState.update {
                it.copy(selectedRepo = null, directoryItems = emptyList(), recentCommits = emptyList())
            }
        }
    }

    fun openContentItem(item: GitHubContentItem) {
        val repo = _uiState.value.selectedRepo ?: return
        if (item.type == "dir") {
            loadDirectory(repo, item.path)
        } else {
            loadFile(repo, item)
        }
    }

    private fun loadDirectory(repo: GitHubRepo, path: String) {
        val parts = repo.fullName.split("/")
        if (parts.size != 2) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            githubRepo.getDirectoryContents(parts[0], parts[1], path, activeToken)
                .onSuccess { items ->
                    _uiState.update { it.copy(currentPath = path, directoryItems = items, isLoading = false) }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.localizedMessage) }
                }
        }
    }

    private fun loadFile(repo: GitHubRepo, item: GitHubContentItem) {
        val parts = repo.fullName.split("/")
        if (parts.size != 2) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, aiAnalysisResult = null) }
            githubRepo.getDecodedFileContent(parts[0], parts[1], item.path, activeToken)
                .onSuccess { text ->
                    _uiState.update {
                        it.copy(viewingFileName = item.path, viewingFileContent = text, isLoading = false)
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.localizedMessage) }
                }
        }
    }

    fun analyzeCurrentFileWithAi(taskPrompt: String, responseLanguage: String) {
        val repo = _uiState.value.selectedRepo ?: return
        val path = _uiState.value.viewingFileName ?: return
        val content = _uiState.value.viewingFileContent ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzingWithAi = true, errorMessage = null) }
            try {
                val analysis = aiRepo.analyzeGitHubCode(
                    repoFullName = repo.fullName,
                    filePath = path,
                    fileContent = content,
                    taskPrompt = taskPrompt,
                    responseLanguage = responseLanguage
                )
                _uiState.update { it.copy(aiAnalysisResult = analysis, isAnalyzingWithAi = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isAnalyzingWithAi = false, errorMessage = e.localizedMessage ?: "Gagal menganalisis kode GitHub.")
                }
            }
        }
    }

    fun toggleBookmark(repo: GitHubRepo, currentlyBookmarked: Boolean) {
        viewModelScope.launch {
            if (currentlyBookmarked) {
                val id = repo.fullName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)
                firestoreRepo.removeGitHubBookmark(id)
            } else {
                firestoreRepo.addGitHubBookmark(
                    fullName = repo.fullName,
                    description = repo.description.orEmpty(),
                    language = repo.language.orEmpty(),
                    stars = repo.stargazersCount,
                    htmlUrl = repo.htmlUrl
                )
            }
        }
    }

    companion object {
        private const val TAG = "GitHubVM"
        fun provideFactory(
            firestoreRepo: KarsaFirestoreRepository,
            aiRepo: GeminiAiRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { GitHubViewModel(GitHubRepository(), firestoreRepo, aiRepo) }
        }
    }
}
