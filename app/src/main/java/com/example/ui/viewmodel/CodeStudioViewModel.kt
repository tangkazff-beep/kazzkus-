package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.CodeProject
import com.example.data.repository.GeminiAiRepository
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

data class CodeStudioUiState(
    val activeProjectId: String? = null,
    val title: String = "Aplikasi Web Karsa",
    val targetLanguage: String = "HTML/Web",
    val promptInput: String = "",
    val code: String = DEFAULT_STARTER_HTML,
    val showPreview: Boolean = true,
    val mobileViewport: Boolean = true,
    val isGenerating: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null
) {
    val isWebPreviewable: Boolean
        get() = targetLanguage.contains("HTML", ignoreCase = true) ||
            targetLanguage.contains("Web", ignoreCase = true) ||
            code.trimStart().startsWith("<!DOCTYPE", ignoreCase = true) ||
            code.trimStart().startsWith("<html", ignoreCase = true)
}

private const val DEFAULT_STARTER_HTML = """<!DOCTYPE html>
<html lang="id">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <style>
    body { font-family: system-ui, sans-serif; margin: 0; padding: 24px; background: #0f172a; color: #f8fafc; display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 85vh; text-align: center; }
    .card { background: #1e293b; border: 1px solid #334155; border-radius: 18px; padding: 24px; max-width: 360px; box-shadow: 0 12px 30px rgba(0,0,0,0.35); }
    h1 { color: #22d3ee; font-size: 22px; margin-bottom: 8px; }
    p { color: #94a3b8; font-size: 14px; line-height: 1.5; }
    button { margin-top: 14px; background: linear-gradient(135deg, #22d3ee, #0891b2); color: #0b0f17; border: none; padding: 10px 18px; border-radius: 10px; font-weight: 700; cursor: pointer; }
  </style>
</head>
<body>
  <div class="card">
    <h1>Karsa Web Studio</h1>
    <p>Ketik ide web atau aplikasi Anda di atas, lalu tekan Buat untuk melihat pratinjau langsung.</p>
    <button onclick="document.querySelector('p').innerText='Pratinjau interaktif aktif!'">Uji Interaksi</button>
  </div>
</body>
</html>"""

class CodeStudioViewModel(
    private val firestoreRepo: KarsaFirestoreRepository,
    private val aiRepo: GeminiAiRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CodeStudioUiState())
    val uiState: StateFlow<CodeStudioUiState> = _uiState.asStateFlow()

    val savedProjects: StateFlow<List<CodeProject>> = firestoreRepo.observeProjects()
        .catch { err ->
            val fs = generateSequence(err) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fs == null && err is CancellationException) throw err
            Log.w(TAG, "Error observing projects", fs ?: err)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun updatePrompt(value: String) = _uiState.update { it.copy(promptInput = value) }
    fun updateCode(value: String) = _uiState.update { it.copy(code = value) }
    fun setTargetLanguage(lang: String) = _uiState.update {
        it.copy(targetLanguage = lang, showPreview = lang.contains("HTML", ignoreCase = true) || lang.contains("Web", ignoreCase = true))
    }
    fun setShowPreview(preview: Boolean) = _uiState.update { it.copy(showPreview = preview) }
    fun toggleViewport() = _uiState.update { it.copy(mobileViewport = !it.mobileViewport) }
    fun clearMessages() = _uiState.update { it.copy(errorMessage = null, statusMessage = null) }

    fun loadCodeFromExternal(title: String, language: String, code: String) {
        val isWeb = language.contains("html", ignoreCase = true) || language.contains("web", ignoreCase = true)
        _uiState.update {
            it.copy(
                activeProjectId = null,
                title = title,
                targetLanguage = if (isWeb) "HTML/Web" else language,
                code = code,
                showPreview = isWeb,
                statusMessage = "Kode dimuat ke Studio."
            )
        }
    }

    fun openSavedProject(project: CodeProject) {
        _uiState.update {
            it.copy(
                activeProjectId = project.id,
                title = project.title,
                targetLanguage = project.language,
                promptInput = project.prompt,
                code = project.code,
                showPreview = project.isWebPreviewable,
                errorMessage = null
            )
        }
    }

    fun generateCode(modifyCurrent: Boolean, responseLanguage: String) {
        val state = _uiState.value
        val prompt = state.promptInput.trim()
        if (prompt.isBlank() || state.isGenerating) return

        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, errorMessage = null, statusMessage = null) }
            try {
                val (generatedTitle, generatedCode) = aiRepo.generateCodeOrWebApp(
                    prompt = prompt,
                    targetLanguage = state.targetLanguage,
                    existingCode = if (modifyCurrent) state.code else null,
                    responseLanguage = responseLanguage
                )
                val isWeb = state.targetLanguage.contains("HTML", ignoreCase = true) ||
                    state.targetLanguage.contains("Web", ignoreCase = true)
                val projectId = firestoreRepo.saveCodeProject(
                    projectId = if (modifyCurrent) state.activeProjectId else null,
                    title = generatedTitle,
                    language = state.targetLanguage,
                    prompt = prompt,
                    code = generatedCode,
                    isWebPreviewable = isWeb
                ).getOrNull()

                _uiState.update {
                    it.copy(
                        activeProjectId = projectId ?: it.activeProjectId,
                        title = generatedTitle,
                        code = generatedCode,
                        showPreview = isWeb,
                        isGenerating = false,
                        statusMessage = "Kode berhasil dibuat & disimpan ke cloud."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Code generation failed", e)
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        errorMessage = e.localizedMessage ?: "Gagal menghasilkan kode."
                    )
                }
            }
        }
    }

    fun saveCurrentProject() {
        val state = _uiState.value
        viewModelScope.launch {
            val res = firestoreRepo.saveCodeProject(
                projectId = state.activeProjectId,
                title = state.title,
                language = state.targetLanguage,
                prompt = state.promptInput,
                code = state.code,
                isWebPreviewable = state.isWebPreviewable
            )
            res.onSuccess { newId ->
                _uiState.update { it.copy(activeProjectId = newId, statusMessage = "Proyek disimpan.") }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.localizedMessage ?: "Gagal menyimpan proyek.") }
            }
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            firestoreRepo.deleteCodeProject(projectId)
            if (_uiState.value.activeProjectId == projectId) {
                _uiState.update { it.copy(activeProjectId = null) }
            }
        }
    }

    companion object {
        private const val TAG = "CodeStudioVM"
        fun provideFactory(
            firestoreRepo: KarsaFirestoreRepository,
            aiRepo: GeminiAiRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { CodeStudioViewModel(firestoreRepo, aiRepo) }
        }
    }
}
