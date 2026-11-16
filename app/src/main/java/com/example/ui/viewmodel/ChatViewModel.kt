package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.AttachmentPayload
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.DataAnalysisSummary
import com.example.data.repository.GeminiAiRepository
import com.example.data.repository.KarsaFirestoreRepository
import com.example.utils.FileParserUtils
import com.example.utils.VoiceRecorderManager
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ChatUiState(
    val activeConversationId: String? = null,
    val mode: String = "chat", // "chat", "analysis", "code"
    val language: String = "Indonesia",
    val fastMode: Boolean = false,
    val promptInput: String = "",
    val pendingAttachments: List<AttachmentPayload> = emptyList(),
    val latestAnalysisSummary: DataAnalysisSummary? = null,
    val isStreaming: Boolean = false,
    val streamingResponse: String = "",
    val isRecordingVoice: Boolean = false,
    val recordingSeconds: Int = 0,
    val voiceAmplitude: Float = 0.1f,
    val errorMessage: String? = null,
    val statusBanner: String? = null
)

class ChatViewModel(
    private val firestoreRepo: KarsaFirestoreRepository,
    private val aiRepo: GeminiAiRepository,
    private val voiceManager: VoiceRecorderManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _activeConversationId = MutableStateFlow<String?>(null)
    private var recordingTickerJob: Job? = null
    private var activeRecordingFile: File? = null

    val conversations: StateFlow<List<Conversation>> = firestoreRepo.observeConversations()
        .catch { err ->
            val fs = generateSequence(err) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fs == null && err is CancellationException) throw err
            Log.w(TAG, "Error observing conversations", fs ?: err)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val messages: StateFlow<List<ChatMessage>> = _activeConversationId
        .flatMapLatest { convId ->
            if (convId.isNullOrBlank()) flowOf(emptyList())
            else firestoreRepo.observeMessages(convId)
                .catch { err ->
                    val fs = generateSequence(err) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fs == null && err is CancellationException) throw err
                    Log.w(TAG, "Error observing messages", fs ?: err)
                    emit(emptyList())
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun selectConversation(conversationId: String?, mode: String? = null) {
        _activeConversationId.value = conversationId
        _uiState.update {
            it.copy(
                activeConversationId = conversationId,
                mode = mode ?: it.mode,
                errorMessage = null
            )
        }
    }

    fun startNewConversation(mode: String = _uiState.value.mode) {
        _activeConversationId.value = null
        _uiState.update {
            it.copy(
                activeConversationId = null,
                mode = mode,
                latestAnalysisSummary = null,
                streamingResponse = "",
                errorMessage = null
            )
        }
    }

    fun setMode(mode: String) {
        _uiState.update { it.copy(mode = mode, errorMessage = null) }
    }

    fun setLanguage(language: String) {
        _uiState.update { it.copy(language = language) }
    }

    fun toggleFastMode() {
        _uiState.update { it.copy(fastMode = !it.fastMode) }
    }

    fun updatePromptInput(value: String) {
        _uiState.update { state ->
            val parsedSummary = if (state.mode == "analysis" && value.lines().size >= 3) {
                FileParserUtils.tryParseDataSummary("Data Input Langsung", value)
            } else state.latestAnalysisSummary
            state.copy(promptInput = value, latestAnalysisSummary = parsedSummary)
        }
    }

    fun addImageFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val attachment = withContext(Dispatchers.IO) {
                FileParserUtils.loadImageAttachment(context, uri)
            }
            if (attachment != null) {
                _uiState.update {
                    it.copy(pendingAttachments = it.pendingAttachments + attachment, errorMessage = null)
                }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal memuat gambar yang dipilih.") }
            }
        }
    }

    fun addFileFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val attachment = withContext(Dispatchers.IO) {
                FileParserUtils.loadFileAttachment(context, uri)
            }
            if (attachment != null) {
                _uiState.update { state ->
                    state.copy(
                        pendingAttachments = state.pendingAttachments + attachment,
                        latestAnalysisSummary = attachment.analysisSummary ?: state.latestAnalysisSummary,
                        mode = if (attachment.analysisSummary != null) "analysis" else state.mode,
                        errorMessage = null
                    )
                }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal membaca berkas. Pastikan ukuran di bawah 15 MB.") }
            }
        }
    }

    fun removeAttachment(index: Int) {
        _uiState.update { state ->
            val updated = state.pendingAttachments.toMutableList().also {
                if (index in it.indices) it.removeAt(index)
            }
            state.copy(pendingAttachments = updated)
        }
    }

    fun startVoiceRecording() {
        try {
            activeRecordingFile = voiceManager.startRecording()
            _uiState.update {
                it.copy(isRecordingVoice = true, recordingSeconds = 0, voiceAmplitude = 0.1f, errorMessage = null)
            }
            recordingTickerJob?.cancel()
            recordingTickerJob = viewModelScope.launch {
                while (true) {
                    delay(180L)
                    val amp = voiceManager.getMaxAmplitudeNormalized()
                    _uiState.update { s ->
                        s.copy(voiceAmplitude = amp)
                    }
                }
            }
            viewModelScope.launch {
                while (_uiState.value.isRecordingVoice) {
                    delay(1000L)
                    if (_uiState.value.isRecordingVoice) {
                        _uiState.update { s -> s.copy(recordingSeconds = s.recordingSeconds + 1) }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Voice recording start failed", e)
            _uiState.update { it.copy(isRecordingVoice = false, errorMessage = "Gagal memulai rekaman suara: ${e.localizedMessage}") }
        }
    }

    fun stopVoiceRecording(attachToMessage: Boolean) {
        recordingTickerJob?.cancel()
        val duration = _uiState.value.recordingSeconds.coerceAtLeast(1)
        val file = voiceManager.stopRecording() ?: activeRecordingFile
        _uiState.update { it.copy(isRecordingVoice = false) }
        if (attachToMessage && file != null && file.exists() && file.length() > 0L) {
            val audioAttachment = AttachmentPayload.AudioAttachment(
                displayName = "VoiceNote_${duration}d.m4a",
                filePath = file.absolutePath,
                durationSeconds = duration
            )
            _uiState.update {
                it.copy(pendingAttachments = it.pendingAttachments + audioAttachment)
            }
        }
    }

    fun playVoiceAttachment(filePath: String) {
        voiceManager.playAudio(filePath) {}
    }

    fun injectExternalPrompt(prompt: String, mode: String = "code") {
        startNewConversation(mode)
        _uiState.update { it.copy(promptInput = prompt, mode = mode) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null, statusBanner = null) }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            firestoreRepo.deleteConversation(conversationId)
            if (_activeConversationId.value == conversationId) {
                startNewConversation()
            }
        }
    }

    fun sendMessage(overridePrompt: String? = null) {
        val state = _uiState.value
        val prompt = (overridePrompt ?: state.promptInput).trim()
        val attachments = state.pendingAttachments
        if ((prompt.isBlank() && attachments.isEmpty()) || state.isStreaming) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isStreaming = true,
                    streamingResponse = "",
                    promptInput = if (overridePrompt == null) "" else it.promptInput,
                    pendingAttachments = emptyList(),
                    errorMessage = null
                )
            }

            try {
                val existingHistory = messages.value
                var convId = _activeConversationId.value
                val firstAttachment = attachments.firstOrNull()
                val attachType = when (firstAttachment) {
                    is AttachmentPayload.ImageAttachment -> "image"
                    is AttachmentPayload.AudioAttachment -> "audio"
                    is AttachmentPayload.FileAttachment -> "file"
                    null -> null
                }
                val titleText = prompt.ifBlank { firstAttachment?.displayName ?: "Obrolan Baru" }.take(60)

                if (convId == null) {
                    convId = firestoreRepo.createConversation(
                        title = titleText,
                        mode = state.mode,
                        language = state.language,
                        previewText = titleText
                    ).getOrThrow()
                    _activeConversationId.value = convId
                    _uiState.update { it.copy(activeConversationId = convId) }
                }

                val userVisibleText = prompt.ifBlank {
                    when (firstAttachment) {
                        is AttachmentPayload.AudioAttachment -> "[Mengirim Voice Note (${firstAttachment.durationSeconds} detik)]"
                        is AttachmentPayload.ImageAttachment -> "[Mengirim Foto: ${firstAttachment.displayName}]"
                        is AttachmentPayload.FileAttachment -> "[Mengirim Berkas: ${firstAttachment.displayName}]"
                        null -> ""
                    }
                }

                firestoreRepo.addMessage(
                    conversationId = convId,
                    role = "user",
                    text = userVisibleText,
                    attachmentName = firstAttachment?.displayName,
                    attachmentType = attachType
                ).getOrThrow()

                var finalReply = ""
                aiRepo.streamChatMessage(
                    history = existingHistory,
                    userPrompt = prompt,
                    attachments = attachments,
                    mode = state.mode,
                    language = state.language,
                    fastMode = state.fastMode
                ).collect { partial ->
                    finalReply = partial
                    _uiState.update { it.copy(streamingResponse = partial) }
                }

                firestoreRepo.addMessage(
                    conversationId = convId,
                    role = "model",
                    text = finalReply
                ).getOrThrow()

                firestoreRepo.updateConversationMetadata(
                    conversationId = convId,
                    previewText = finalReply.take(180),
                    messageCount = existingHistory.size + 2L
                )

                _uiState.update {
                    it.copy(isStreaming = false, streamingResponse = "")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chat sendMessage failed", e)
                _uiState.update {
                    it.copy(
                        isStreaming = false,
                        streamingResponse = "",
                        errorMessage = e.localizedMessage ?: "Gagal memproses pesan AI."
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.release()
    }

    companion object {
        private const val TAG = "ChatViewModel"

        fun provideFactory(
            firestoreRepo: KarsaFirestoreRepository,
            aiRepo: GeminiAiRepository,
            context: Context
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ChatViewModel(firestoreRepo, aiRepo, VoiceRecorderManager(context.applicationContext))
            }
        }
    }
}
