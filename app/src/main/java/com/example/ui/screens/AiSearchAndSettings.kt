package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AiSearchResultItem
import com.example.data.model.CodeProject
import com.example.data.model.CodingLesson
import com.example.data.model.Conversation
import com.example.data.model.GitHubBookmark
import com.example.data.preferences.UserSettings
import com.example.ui.auth.signOutUser
import com.example.ui.viewmodel.SearchViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSearchModalSheet(
    viewModel: SearchViewModel,
    conversations: List<Conversation>,
    lessons: List<CodingLesson>,
    projects: List<CodeProject>,
    bookmarks: List<GitHubBookmark>,
    responseLanguage: String,
    onSelectResult: (AiSearchResultItem) -> Unit,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.updateQuery(uiState.query, conversations, lessons, projects, bookmarks)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(560.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Pencarian Berbasis AI",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Navigasi cepat seluruh riwayat obrolan, pelajaran coding, proyek web, dan repositori GitHub.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = uiState.query,
                onValueChange = {
                    viewModel.updateQuery(it, conversations, lessons, projects, bookmarks)
                },
                modifier = Modifier.fillMaxWidth().testTag("ai_search_input"),
                placeholder = { Text("Cari topik, kode, error, atau makna...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true
            )

            Button(
                onClick = {
                    viewModel.runAiSemanticSearch(conversations, lessons, projects, bookmarks, responseLanguage)
                },
                enabled = uiState.query.isNotBlank() && !uiState.isAiSearching,
                modifier = Modifier.fillMaxWidth().testTag("run_ai_semantic_search_button")
            ) {
                if (uiState.isAiSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Menganalisis Semantik Data...")
                } else {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Cari Cerdas dengan Gemini AI")
                }
            }

            if (!uiState.errorMessage.isNullOrBlank()) {
                Text(uiState.errorMessage ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.results, key = { "${it.sourceType}_${it.itemId}" }) { item ->
                    val icon = when (item.sourceType) {
                        "lesson" -> Icons.Default.School
                        "project" -> Icons.Default.Code
                        "github" -> Icons.Default.Bookmark
                        else -> Icons.Default.ChatBubbleOutline
                    }
                    Card(
                        onClick = {
                            onSelectResult(item)
                            onDismiss()
                        },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.reason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                if (item.snippet.isNotBlank()) {
                                    Text(item.snippet, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsModalSheet(
    settings: UserSettings,
    onToggleDarkTheme: (Boolean) -> Unit,
    onSelectLanguage: (String) -> Unit,
    onSaveGitHubToken: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var tokenDraft by remember(settings.githubToken) { mutableStateOf(settings.githubToken) }
    val currentUser = Firebase.auth.currentUser

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Pengaturan Karsa AI", style = MaterialTheme.typography.titleLarge)

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(currentUser?.displayName ?: "Pengguna Google", style = MaterialTheme.typography.titleSmall)
                        Text(currentUser?.email.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(
                        onClick = {
                            signOutUser(credentialManager, scope) {
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Keluar")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.DarkMode, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Tema Gelap Obsidian", style = MaterialTheme.typography.bodyLarge)
                }
                Switch(
                    checked = settings.isDarkTheme,
                    onCheckedChange = onToggleDarkTheme,
                    modifier = Modifier.testTag("theme_switch")
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Language, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Bahasa Utama AI", style = MaterialTheme.typography.titleSmall)
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Indonesia", "English", "Auto", "日本語", "العربية").forEach { lang ->
                        FilterChip(
                            selected = settings.preferredLanguage == lang,
                            onClick = { onSelectLanguage(lang) },
                            label = { Text(lang) }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Key, null, tint = MaterialTheme.colorScheme.secondary)
                    Text("Token GitHub Pribadi (Opsional)", style = MaterialTheme.typography.titleSmall)
                }
                OutlinedTextField(
                    value = tokenDraft,
                    onValueChange = { tokenDraft = it },
                    modifier = Modifier.fillMaxWidth().testTag("github_pat_input"),
                    placeholder = { Text("ghp_... (disimpan aman di perangkat)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                Button(
                    onClick = {
                        onSaveGitHubToken(tokenDraft)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("save_github_token_button")
                ) {
                    Text("Simpan Pengaturan")
                }
            }

            Spacer(Modifier.height(14.dp))
        }
    }
}
