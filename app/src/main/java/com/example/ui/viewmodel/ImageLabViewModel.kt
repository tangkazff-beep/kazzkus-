package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.repository.GeminiAiRepository
import com.example.utils.FileParserUtils
import com.google.firebase.ai.type.AspectRatio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ImageLabUiState {
    data object Idle : ImageLabUiState
    data object Loading : ImageLabUiState
    data class Success(val resultBitmap: Bitmap) : ImageLabUiState
    data class Error(val message: String) : ImageLabUiState
}

class ImageLabViewModel(
    private val aiRepo: GeminiAiRepository
) : ViewModel() {

    private val _genState = MutableStateFlow<ImageLabUiState>(ImageLabUiState.Idle)
    val genState: StateFlow<ImageLabUiState> = _genState.asStateFlow()

    private val _prompt = MutableStateFlow("")
    val prompt: StateFlow<String> = _prompt.asStateFlow()

    private val _sourceBitmap = MutableStateFlow<Bitmap?>(null)
    val sourceBitmap: StateFlow<Bitmap?> = _sourceBitmap.asStateFlow()

    private val _aspectRatio = MutableStateFlow(AspectRatio.SQUARE_1x1)
    val aspectRatio: StateFlow<AspectRatio> = _aspectRatio.asStateFlow()

    fun updatePrompt(value: String) {
        _prompt.value = value
    }

    fun setAspectRatio(ratio: AspectRatio) {
        _aspectRatio.value = ratio
    }

    fun clearSourceImage() {
        _sourceBitmap.value = null
    }

    fun useOutputAsSource(bitmap: Bitmap) {
        _sourceBitmap.value = bitmap
    }

    fun loadSourceFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                FileParserUtils.loadImageAttachment(context, uri)
            }
            if (loaded != null) {
                _sourceBitmap.value = loaded.bitmap
            } else {
                _genState.value = ImageLabUiState.Error("Gagal memuat foto untuk diedit.")
            }
        }
    }

    fun generateOrEdit() {
        val text = _prompt.value.trim()
        if (text.isBlank()) return
        val input = _sourceBitmap.value
        val ratio = _aspectRatio.value

        viewModelScope.launch {
            _genState.value = ImageLabUiState.Loading
            try {
                val outputBitmap = aiRepo.generateOrEditImage(
                    prompt = text,
                    inputBitmap = input,
                    aspectRatio = ratio
                )
                _genState.value = ImageLabUiState.Success(outputBitmap)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Image generation timed out", e)
                _genState.value = ImageLabUiState.Error("Waktu pembuatan/edit gambar habis (60 detik). Coba lagi.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Image generation/editing failed", e)
                _genState.value = ImageLabUiState.Error(
                    e.localizedMessage ?: "Gagal memproses gambar. Pastikan paket Firebase Blaze aktif untuk model gambar."
                )
            }
        }
    }

    companion object {
        private const val TAG = "ImageLabVM"
        fun provideFactory(aiRepo: GeminiAiRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { ImageLabViewModel(aiRepo) }
        }
    }
}
