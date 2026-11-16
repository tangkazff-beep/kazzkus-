package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.CodingLesson
import com.example.data.model.LessonStep
import com.example.data.repository.GeminiAiRepository
import com.example.data.repository.KarsaFirestoreRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LearnUiState(
    val selectedLessonId: String? = null,
    val topicInput: String = "",
    val programmingLanguage: String = "Kotlin",
    val difficulty: String = "Pemula",
    val answerDraft: String = "",
    val showHint: Boolean = false,
    val isGeneratingCurriculum: Boolean = false,
    val isCheckingAnswer: Boolean = false,
    val errorMessage: String? = null
)

class LearnViewModel(
    private val firestoreRepo: KarsaFirestoreRepository,
    private val aiRepo: GeminiAiRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LearnUiState())
    val uiState: StateFlow<LearnUiState> = _uiState.asStateFlow()

    private val _activeLessonId = MutableStateFlow<String?>(null)

    val lessons: StateFlow<List<CodingLesson>> = firestoreRepo.observeLessons()
        .catch { err ->
            val fs = generateSequence(err) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fs == null && err is CancellationException) throw err
            Log.w(TAG, "Error observing lessons", fs ?: err)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val steps: StateFlow<List<LessonStep>> = _activeLessonId
        .flatMapLatest { id ->
            if (id.isNullOrBlank()) flowOf(emptyList())
            else firestoreRepo.observeLessonSteps(id)
                .catch { err ->
                    val fs = generateSequence(err) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fs == null && err is CancellationException) throw err
                    Log.w(TAG, "Error observing lesson steps", fs ?: err)
                    emit(emptyList())
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun updateTopic(value: String) = _uiState.update { it.copy(topicInput = value) }
    fun setProgrammingLanguage(lang: String) = _uiState.update { it.copy(programmingLanguage = lang) }
    fun setDifficulty(diff: String) = _uiState.update { it.copy(difficulty = diff) }
    fun updateAnswerDraft(ans: String) = _uiState.update { it.copy(answerDraft = ans) }
    fun toggleHint() = _uiState.update { it.copy(showHint = !it.showHint) }
    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    fun selectLesson(lesson: CodingLesson?) {
        _activeLessonId.value = lesson?.id
        _uiState.update {
            it.copy(
                selectedLessonId = lesson?.id,
                answerDraft = "",
                showHint = false,
                errorMessage = null
            )
        }
    }

    fun createCurriculum(responseLanguage: String) {
        val state = _uiState.value
        val topic = state.topicInput.trim().ifBlank { "Dasar ${state.programmingLanguage}" }
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingCurriculum = true, errorMessage = null) }
            try {
                val generatedSteps = aiRepo.generateCodingCurriculum(
                    topic = topic,
                    programmingLanguage = state.programmingLanguage,
                    difficulty = state.difficulty,
                    responseLanguage = responseLanguage
                )
                val lessonId = firestoreRepo.createLessonWithSteps(
                    topic = topic,
                    programmingLanguage = state.programmingLanguage,
                    difficulty = state.difficulty,
                    steps = generatedSteps
                ).getOrThrow()
                _activeLessonId.value = lessonId
                _uiState.update {
                    it.copy(
                        selectedLessonId = lessonId,
                        topicInput = "",
                        isGeneratingCurriculum = false,
                        answerDraft = "",
                        showHint = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate curriculum", e)
                _uiState.update {
                    it.copy(
                        isGeneratingCurriculum = false,
                        errorMessage = e.localizedMessage ?: "Gagal membuat panduan belajar."
                    )
                }
            }
        }
    }

    fun checkStepAnswer(lesson: CodingLesson, step: LessonStep, responseLanguage: String) {
        val answer = _uiState.value.answerDraft.trim()
        if (answer.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingAnswer = true, errorMessage = null) }
            try {
                val eval = aiRepo.evaluateLessonAnswer(
                    programmingLanguage = lesson.programmingLanguage,
                    step = step,
                    userAnswer = answer,
                    responseLanguage = responseLanguage
                )
                val feedbackText = buildString {
                    append("Skor: ${eval.score}/100 • ")
                    append(if (eval.passed) "Lulus!" else "Perlu Perbaikan")
                    append("\n\n${eval.feedback}")
                    if (!eval.passed && eval.suggestedCorrection.isNotBlank()) {
                        append("\n\nContoh perbaikan:\n${eval.suggestedCorrection}")
                    }
                }
                val completedCount = steps.value.count { it.isCompleted || (it.id == step.id && eval.passed) }.toLong()
                val nextIndex = if (eval.passed && step.stepIndex + 1 < lesson.totalSteps) {
                    step.stepIndex + 1
                } else {
                    step.stepIndex
                }
                firestoreRepo.updateLessonStepProgress(
                    lessonId = lesson.id,
                    stepId = step.id,
                    userAnswer = answer,
                    aiFeedback = feedbackText,
                    isCompleted = eval.passed || step.isCompleted,
                    newCurrentStepIndex = nextIndex,
                    newCompletedSteps = completedCount
                ).getOrThrow()

                _uiState.update {
                    it.copy(
                        isCheckingAnswer = false,
                        answerDraft = if (eval.passed) "" else answer,
                        showHint = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed evaluating step answer", e)
                _uiState.update {
                    it.copy(
                        isCheckingAnswer = false,
                        errorMessage = e.localizedMessage ?: "Gagal memeriksa jawaban."
                    )
                }
            }
        }
    }

    fun navigateStep(lesson: CodingLesson, targetIndex: Long) {
        viewModelScope.launch {
            val completedCount = steps.value.count { it.isCompleted }.toLong()
            firestoreRepo.updateLessonActiveStep(lesson.id, targetIndex, completedCount)
            val targetStep = steps.value.firstOrNull { it.stepIndex == targetIndex }
            _uiState.update {
                it.copy(answerDraft = targetStep?.userAnswer.orEmpty(), showHint = false)
            }
        }
    }

    companion object {
        private const val TAG = "LearnViewModel"
        fun provideFactory(
            firestoreRepo: KarsaFirestoreRepository,
            aiRepo: GeminiAiRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { LearnViewModel(firestoreRepo, aiRepo) }
        }
    }
}
