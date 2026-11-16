package com.example.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LaptopMac
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.viewmodel.CodeStudioViewModel

private val STUDIO_STACKS = listOf("HTML/Web", "Kotlin", "Python", "JavaScript", "SQL")

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CodeWebStudioScreen(
    viewModel: CodeStudioViewModel,
    responseLanguage: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val savedProjects by viewModel.savedProjects.collectAsStateWithLifecycle()
    var activeTab by remember { mutableIntStateOf(if (uiState.showPreview) 0 else 1) }

    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/html")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(uiState.code.toByteArray())
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Prompt & Stack Selector
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    STUDIO_STACKS.forEach { stack ->
                        FilterChip(
                            selected = uiState.targetLanguage == stack,
                            onClick = { viewModel.setTargetLanguage(stack) },
                            label = { Text(stack) }
                        )
                    }
                }

                OutlinedTextField(
                    value = uiState.promptInput,
                    onValueChange = { viewModel.updatePrompt(it) },
                    modifier = Modifier.fillMaxWidth().testTag("studio_prompt_input"),
                    placeholder = { Text("Deskripsikan web/kode yang ingin dibuat (mis. Dashboard Portofolio Dark)...") },
                    maxLines = 3
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.generateCode(modifyCurrent = false, responseLanguage = responseLanguage)
                            if (uiState.isWebPreviewable) activeTab = 0 else activeTab = 1
                        },
                        enabled = !uiState.isGenerating && uiState.promptInput.isNotBlank(),
                        modifier = Modifier.weight(1f).testTag("studio_generate_button")
                    ) {
                        if (uiState.isGenerating) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(6.dp))
                        } else {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text("Buat Baru")
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.generateCode(modifyCurrent = true, responseLanguage = responseLanguage)
                        },
                        enabled = !uiState.isGenerating && uiState.promptInput.isNotBlank() && uiState.code.isNotBlank(),
                        modifier = Modifier.weight(1f).testTag("studio_modify_button")
                    ) {
                        Icon(Icons.Default.AutoFixHigh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Modifikasi")
                    }
                }
            }
        }

        // View Switcher: Preview | Source | Saved
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                shape = SegmentedButtonDefaults.itemShape(0, 3),
                enabled = uiState.isWebPreviewable
            ) {
                Text("Pratinjau Web")
            }
            SegmentedButton(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                shape = SegmentedButtonDefaults.itemShape(1, 3)
            ) {
                Text("Kode Sumber")
            }
            SegmentedButton(
                selected = activeTab == 2,
                onClick = { activeTab = 2 },
                shape = SegmentedButtonDefaults.itemShape(2, 3)
            ) {
                Text("Tersimpan (${savedProjects.size})")
            }
        }

        // Toolbar Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = uiState.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Row {
                if (activeTab == 0) {
                    IconButton(onClick = { viewModel.toggleViewport() }) {
                        Icon(
                            imageVector = if (uiState.mobileViewport) Icons.Default.PhoneAndroid else Icons.Default.LaptopMac,
                            contentDescription = "Ubah Viewport"
                        )
                    }
                }
                IconButton(onClick = { clipboard.setText(AnnotatedString(uiState.code)) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Salin Kode")
                }
                IconButton(onClick = { viewModel.saveCurrentProject() }) {
                    Icon(Icons.Default.Save, contentDescription = "Simpan ke Cloud")
                }
                IconButton(onClick = {
                    val safeName = uiState.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "web_karsa" }
                    exportFileLauncher.launch("$safeName.html")
                }) {
                    Icon(Icons.Default.Download, contentDescription = "Ekspor HTML")
                }
            }
        }

        val banner = uiState.errorMessage ?: uiState.statusMessage
        if (!banner.isNullOrBlank()) {
            Text(
                text = banner,
                color = if (uiState.errorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Active Content Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (activeTab) {
                0 -> {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .let {
                                if (uiState.mobileViewport) it.widthIn(max = 420.dp).align(Alignment.Center) else it
                            },
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.allowFileAccess = false
                                    webViewClient = WebViewClient()
                                }
                            },
                            update = { webView ->
                                webView.loadDataWithBaseURL(
                                    "https://karsa.local/",
                                    uiState.code,
                                    "text/html",
                                    "UTF-8",
                                    null
                                )
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                1 -> {
                    OutlinedTextField(
                        value = uiState.code,
                        onValueChange = { viewModel.updateCode(it) },
                        modifier = Modifier.fillMaxSize().testTag("studio_code_editor"),
                        textStyle = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 12.5.sp, lineHeight = 18.sp),
                        label = { Text("Editor Kode (${uiState.targetLanguage})") }
                    )
                }
                else -> {
                    if (savedProjects.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Belum ada proyek tersimpan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(savedProjects, key = { it.id }) { proj ->
                                Card(
                                    onClick = {
                                        viewModel.openSavedProject(proj)
                                        activeTab = if (proj.isWebPreviewable) 0 else 1
                                    },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (proj.isWebPreviewable) Icons.Default.Code else Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(proj.title, style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                text = "${proj.language} • ${proj.prompt}",
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = { viewModel.deleteProject(proj.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Hapus Proyek")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
