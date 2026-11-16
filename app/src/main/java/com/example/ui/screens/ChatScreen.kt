package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ChatMessage
import com.example.ui.components.AttachmentPreviewBar
import com.example.ui.components.DataAnalysisChartCard
import com.example.ui.components.MarkdownCodeText
import com.example.ui.components.VoiceNoteRecorderBar
import com.example.ui.viewmodel.ChatViewModel
import com.example.utils.DocumentExportManager

private val SUPPORTED_LANGUAGES = listOf(
    "Indonesia", "English", "Auto", "日本語", "العربية", "中文", "Español"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenCodeInStudio: (language: String, code: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()

    var showHistorySheet by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showLanguageMenu by remember { mutableStateOf(false) }
    var exportNotification by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.addImageFromUri(context, uri)
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.addFileFromUri(context, uri)
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceRecording()
        } else {
            exportNotification = "Izin mikrofon diperlukan untuk merekam Voice Note."
        }
    }

    val activeTitle = remember(conversations, uiState.activeConversationId) {
        conversations.firstOrNull { it.id == uiState.activeConversationId }?.title ?: "Obrolan Karsa AI"
    }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    DocumentExportManager.writePdfToStream(activeTitle, messages, out)
                }
            }.onSuccess {
                exportNotification = "Dokumen PDF berhasil disimpan."
            }.onFailure {
                exportNotification = "Gagal menyimpan PDF: ${it.localizedMessage}"
            }
        }
    }

    val saveMarkdownLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(DocumentExportManager.buildMarkdownDocument(activeTitle, messages).toByteArray())
                }
            }.onSuccess {
                exportNotification = "Dokumen Markdown berhasil disimpan."
            }.onFailure {
                exportNotification = "Gagal menyimpan Markdown: ${it.localizedMessage}"
            }
        }
    }

    val saveHtmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/html")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(DocumentExportManager.buildHtmlDocument(activeTitle, messages).toByteArray())
                }
            }.onSuccess {
                exportNotification = "Laporan HTML berhasil disimpan."
            }.onFailure {
                exportNotification = "Gagal menyimpan HTML: ${it.localizedMessage}"
            }
        }
    }

    LaunchedEffect(messages.size, uiState.streamingResponse.length) {
        if (messages.isNotEmpty() || uiState.streamingResponse.isNotEmpty()) {
            val target = (messages.size + if (uiState.streamingResponse.isNotEmpty()) 1 else 0).coerceAtLeast(1) - 1
            listState.animateScrollToItem(target)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Top Utility Strip: Modes, Language, History, Export
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = uiState.mode == "chat",
                onClick = { viewModel.setMode("chat") },
                label = { Text("Obrolan") },
                leadingIcon = { Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp)) },
                modifier = Modifier.testTag("mode_chat_chip")
            )
            FilterChip(
                selected = uiState.mode == "analysis",
                onClick = { viewModel.setMode("analysis") },
                label = { Text("Analisis Data") },
                leadingIcon = { Icon(Icons.Default.Analytics, null, Modifier.size(16.dp)) },
                modifier = Modifier.testTag("mode_analysis_chip")
            )
            FilterChip(
                selected = uiState.mode == "code",
                onClick = { viewModel.setMode("code") },
                label = { Text("Koding & Web") },
                leadingIcon = { Icon(Icons.Default.Code, null, Modifier.size(16.dp)) },
                modifier = Modifier.testTag("mode_code_chip")
            )

            Box {
                AssistChip(
                    onClick = { showLanguageMenu = true },
                    label = { Text(uiState.language) },
                    leadingIcon = { Icon(Icons.Default.Language, null, Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("language_selector_chip")
                )
                DropdownMenu(
                    expanded = showLanguageMenu,
                    onDismissRequest = { showLanguageMenu = false }
                ) {
                    SUPPORTED_LANGUAGES.forEach { lang ->
                        DropdownMenuItem(
                            text = { Text(lang) },
                            onClick = {
                                viewModel.setLanguage(lang)
                                showLanguageMenu = false
                            }
                        )
                    }
                }
            }

            FilterChip(
                selected = uiState.fastMode,
                onClick = { viewModel.toggleFastMode() },
                label = { Text(if (uiState.fastMode) "Cepat" else "Standar") },
                leadingIcon = { Icon(Icons.Default.Speed, null, Modifier.size(16.dp)) },
                modifier = Modifier.testTag("model_speed_chip")
            )

            AssistChip(
                onClick = { showHistorySheet = true },
                label = { Text("Riwayat (${conversations.size})") },
                leadingIcon = { Icon(Icons.Default.History, null, Modifier.size(16.dp)) },
                modifier = Modifier.testTag("chat_history_button")
            )

            if (messages.isNotEmpty()) {
                AssistChip(
                    onClick = { showExportDialog = true },
                    label = { Text("Ekspor") },
                    leadingIcon = { Icon(Icons.Default.Description, null, Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("export_chat_button")
                )
            }
        }

        // Messages + Data Chart Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty() && !uiState.isStreaming) {
                EmptyChatWelcome(
                    mode = uiState.mode,
                    onQuickPrompt = { prompt -> viewModel.sendMessage(prompt) }
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    uiState.latestAnalysisSummary?.let { summary ->
                        item(key = "live_data_chart") {
                            DataAnalysisChartCard(summary = summary)
                        }
                    }

                    items(messages, key = { it.id.ifBlank { it.hashCode().toString() } }) { msg ->
                        ChatBubbleItem(
                            message = msg,
                            onOpenCodeInStudio = onOpenCodeInStudio
                        )
                    }

                    if (uiState.isStreaming) {
                        item(key = "streaming_bubble") {
                            StreamingAssistantBubble(
                                text = uiState.streamingResponse,
                                onOpenCodeInStudio = onOpenCodeInStudio
                            )
                        }
                    }
                }
            }
        }

        // Error or Export Banner
        val activeError = uiState.errorMessage ?: exportNotification
        if (!activeError.isNullOrBlank()) {
            Surface(
                color = if (uiState.errorMessage != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = activeError,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.errorMessage != null) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        viewModel.clearError()
                        exportNotification = null
                    }) {
                        Text("Tutup")
                    }
                }
            }
        }

        if (uiState.isRecordingVoice) {
            VoiceNoteRecorderBar(
                recordingSeconds = uiState.recordingSeconds,
                amplitude = uiState.voiceAmplitude,
                onCancel = { viewModel.stopVoiceRecording(attachToMessage = false) },
                onDoneAndAttach = { viewModel.stopVoiceRecording(attachToMessage = true) }
            )
        }

        AttachmentPreviewBar(
            attachments = uiState.pendingAttachments,
            onRemove = { idx -> viewModel.removeAttachment(idx) },
            onPlayAudio = { path -> viewModel.playVoiceAttachment(path) }
        )

        // Bottom Composer
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = uiState.promptInput,
                    onValueChange = { viewModel.updatePromptInput(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("chat_prompt_input"),
                    placeholder = {
                        Text(
                            when (uiState.mode) {
                                "analysis" -> "Tempel data CSV/JSON atau tanyakan analisis real-time..."
                                "code" -> "Minta kode, HTML web app, atau debug error..."
                                else -> "Tanya apa saja dalam bahasa apa pun..."
                            }
                        )
                    },
                    maxLines = 4,
                    shape = MaterialTheme.shapes.medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.testTag("attach_photo_button")
                        ) {
                            Icon(Icons.Default.Image, contentDescription = "Tambah Foto")
                        }
                        IconButton(
                            onClick = {
                                filePickerLauncher.launch(
                                    arrayOf("text/*", "application/json", "application/pdf", "*/*")
                                )
                            },
                            modifier = Modifier.testTag("attach_file_button")
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = "Tambah Berkas CSV/JSON/PDF")
                        }
                        IconButton(
                            onClick = {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    viewModel.startVoiceRecording()
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            enabled = !uiState.isRecordingVoice,
                            modifier = Modifier.testTag("record_voice_button")
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "Rekam Voice Note",
                                tint = if (uiState.isRecordingVoice) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = { viewModel.startNewConversation() },
                            modifier = Modifier.testTag("new_chat_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Obrolan Baru")
                        }
                    }

                    FilledIconButton(
                        onClick = { viewModel.sendMessage() },
                        enabled = !uiState.isStreaming && (uiState.promptInput.isNotBlank() || uiState.pendingAttachments.isNotEmpty()),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("send_message_button")
                    ) {
                        if (uiState.isStreaming) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Kirim Pesan")
                        }
                    }
                }
            }
        }
    }

    if (showHistorySheet) {
        ModalBottomSheet(onDismissRequest = { showHistorySheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Riwayat Obrolan", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = {
                        viewModel.startNewConversation()
                        showHistorySheet = false
                    }) {
                        Text("+ Obrolan Baru")
                    }
                }
                if (conversations.isEmpty()) {
                    Text(
                        text = "Belum ada obrolan tersimpan.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 20.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.height(340.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(conversations, key = { it.id }) { conv ->
                            Card(
                                onClick = {
                                    viewModel.selectConversation(conv.id, conv.mode)
                                    showHistorySheet = false
                                },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (conv.id == uiState.activeConversationId) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = conv.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${conv.mode.uppercase()} • ${conv.language} • ${conv.previewText}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(onClick = { viewModel.deleteConversation(conv.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Hapus Obrolan")
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showExportDialog) {
        val safeBaseName = activeTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(32).ifBlank { "karsa_chat" }
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Ekspor Obrolan Rapi") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Pilih format dokumen untuk menyimpan ke perangkat atau membagikan langsung:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        onClick = {
                            showExportDialog = false
                            savePdfLauncher.launch("$safeBaseName.pdf")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Simpan sebagai PDF (.pdf)")
                    }
                    OutlinedButton(
                        onClick = {
                            showExportDialog = false
                            saveMarkdownLauncher.launch("$safeBaseName.md")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Description, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Simpan sebagai Markdown (.md)")
                    }
                    OutlinedButton(
                        onClick = {
                            showExportDialog = false
                            saveHtmlLauncher.launch("$safeBaseName.html")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Code, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Simpan sebagai Laporan HTML (.html)")
                    }
                    OutlinedButton(
                        onClick = {
                            showExportDialog = false
                            DocumentExportManager.shareFileViaFileProvider(
                                context = context,
                                fileName = "$safeBaseName.pdf",
                                mimeType = "application/pdf"
                            ) { out ->
                                DocumentExportManager.writePdfToStream(activeTitle, messages, out)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Bagikan PDF Langsung")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Tutup")
                }
            }
        )
    }
}

@Composable
private fun EmptyChatWelcome(
    mode: String,
    onQuickPrompt: (String) -> Unit
) {
    val suggestions = when (mode) {
        "analysis" -> listOf(
            "Analisis tren data berikut dan buat tabel ringkasan:\nBulan,Penjualan,Biaya\nJan,120,70\nFeb,145,75\nMar,190,82\nApr,230,90",
            "Bandingkan metrik konversi e-commerce dan beri rekomendasi optimasi",
            "Deteksi anomali pada log latensi server JSON"
        )
        "code" -> listOf(
            "Buatkan halaman web kalkulator investasi HTML/CSS/JS yang modern",
            "Tulis API client Retrofit Kotlin dengan Coroutines & error handling",
            "Jelaskan arsitektur Clean Architecture di Android Jetpack Compose"
        )
        else -> listOf(
            "Jelaskan konsep Machine Learning dalam Bahasa Indonesia & Inggris",
            "Buatkan ringkasan rencana kerja harian untuk developer",
            "Bantu saya merancang skema database aplikasi SaaS"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(14.dp)
                    .size(30.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = when (mode) {
                "analysis" -> "Mode Analisis Data Real-Time"
                "code" -> "Mode Asisten Koding & Web"
                else -> "Mulai Obrolan Cerdas"
            },
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Kirim teks, voice note, foto, atau berkas CSV/JSON/PDF untuk dianalisis langsung.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(18.dp))
        Column(
            modifier = Modifier.widthIn(max = 540.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            suggestions.forEach { sample ->
                Card(
                    onClick = { onQuickPrompt(sample) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = sample,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubbleItem(
    message: ChatMessage,
    onOpenCodeInStudio: (language: String, code: String) -> Unit
) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.widthIn(max = 560.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (isUser) "Anda" else "Karsa AI",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isUser) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                )
                if (!message.attachmentName.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "Lampiran: ${message.attachmentName}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                MarkdownCodeText(
                    markdown = message.text,
                    onOpenInCodeStudio = if (!isUser) onOpenCodeInStudio else null
                )
            }
        }
    }
}

@Composable
private fun StreamingAssistantBubble(
    text: String,
    onOpenCodeInStudio: (language: String, code: String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        modifier = Modifier.widthIn(max = 560.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Text(
                    text = "Karsa AI sedang berpikir...",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (text.isNotBlank()) {
                MarkdownCodeText(
                    markdown = text,
                    onOpenInCodeStudio = onOpenCodeInStudio
                )
            }
        }
    }
}
