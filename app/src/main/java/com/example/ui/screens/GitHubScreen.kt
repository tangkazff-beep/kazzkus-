package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.GitHubRepo
import com.example.ui.components.MarkdownCodeText
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.viewmodel.GitHubViewModel

@Composable
fun GitHubScreen(
    viewModel: GitHubViewModel,
    githubToken: String,
    responseLanguage: String,
    onOpenCodeInStudio: (language: String, code: String) -> Unit,
    onOpenSettingsForToken: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()

    LaunchedEffect(githubToken) {
        viewModel.syncToken(githubToken)
    }

    LaunchedEffect(Unit) {
        if (uiState.searchResults.isEmpty()) {
            viewModel.searchRepos()
        }
    }

    val selectedRepo = uiState.selectedRepo
    if (selectedRepo != null) {
        BackHandler {
            viewModel.navigateBackRepo()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (selectedRepo == null) {
            // Search & Repo Discovery
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    modifier = Modifier.weight(1f).testTag("github_search_input"),
                    placeholder = { Text("Cari repo GitHub (mis. owner/repo, jetpack compose)") },
                    singleLine = true
                )
                Button(
                    onClick = { viewModel.searchRepos() },
                    modifier = Modifier.height(54.dp).testTag("github_search_button")
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Cari GitHub")
                }
            }

            if (uiState.authenticatedUser != null) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Terhubung sebagai @${uiState.authenticatedUser?.login} (${uiState.myRepos.size} repo)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ingin akses repo privat Anda?",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = onOpenSettingsForToken) {
                            Text("Pasang Token GitHub", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            if (!uiState.errorMessage.isNullOrBlank()) {
                Text(
                    text = uiState.errorMessage ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (bookmarks.isNotEmpty()) {
                    item {
                        Text("Repositori Tersimpan (${bookmarks.size})", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                    items(bookmarks, key = { "bm_${it.id}" }) { bm ->
                        RepoCard(
                            fullName = bm.fullName,
                            description = bm.description,
                            language = bm.language,
                            stars = bm.stars,
                            isBookmarked = true,
                            onClick = { viewModel.openBookmark(bm) },
                            onToggleBookmark = {
                                viewModel.toggleBookmark(
                                    GitHubRepo(fullName = bm.fullName, description = bm.description, language = bm.language, stargazersCount = bm.stars, htmlUrl = bm.htmlUrl),
                                    true
                                )
                            }
                        )
                    }
                }

                if (uiState.myRepos.isNotEmpty()) {
                    item {
                        Text("Repositori Akun Anda", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    items(uiState.myRepos, key = { "my_${it.id}" }) { repo ->
                        val isBm = bookmarks.any { it.fullName == repo.fullName }
                        RepoCard(
                            fullName = repo.fullName,
                            description = repo.description.orEmpty(),
                            language = repo.language.orEmpty(),
                            stars = repo.stargazersCount,
                            isBookmarked = isBm,
                            onClick = { viewModel.openRepository(repo) },
                            onToggleBookmark = { viewModel.toggleBookmark(repo, isBm) }
                        )
                    }
                }

                item {
                    Text("Hasil Eksplorasi GitHub", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
                items(uiState.searchResults, key = { "sr_${it.id}" }) { repo ->
                    val isBm = bookmarks.any { it.fullName == repo.fullName }
                    RepoCard(
                        fullName = repo.fullName,
                        description = repo.description.orEmpty(),
                        language = repo.language.orEmpty(),
                        stars = repo.stargazersCount,
                        isBookmarked = isBm,
                        onClick = { viewModel.openRepository(repo) },
                        onToggleBookmark = { viewModel.toggleBookmark(repo, isBm) }
                    )
                }
            }
        } else {
            // Repository Explorer & AI Analyzer
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = { viewModel.navigateBackRepo() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(selectedRepo.fullName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = uiState.viewingFileName ?: "/${uiState.currentPath}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val isBm = bookmarks.any { it.fullName == selectedRepo.fullName }
                IconButton(onClick = { viewModel.toggleBookmark(selectedRepo, isBm) }) {
                    Icon(
                        imageVector = if (isBm) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Simpan Bookmark Repo",
                        tint = if (isBm) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            if (!uiState.errorMessage.isNullOrBlank()) {
                Text(uiState.errorMessage ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            if (uiState.viewingFileName != null && uiState.viewingFileContent != null) {
                // Source Code Viewer + 1-Tap AI Code Workflows
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = {
                            viewModel.analyzeCurrentFileWithAi(
                                "Jelaskan arsitektur, alur logika, dan fungsi utama file kode ini secara ringkas.",
                                responseLanguage
                            )
                        },
                        label = { Text("Jelaskan AI") },
                        leadingIcon = { Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = {
                            viewModel.analyzeCurrentFileWithAi(
                                "Lakukan code review & audit potensi bug, performa, dan keamanan pada file ini beserta saran perbaikannya.",
                                responseLanguage
                            )
                        },
                        label = { Text("Audit & Review AI") },
                        leadingIcon = { Icon(Icons.Default.Security, null, Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = {
                            val ext = uiState.viewingFileName?.substringAfterLast('.', "code").orEmpty()
                            onOpenCodeInStudio(ext, uiState.viewingFileContent.orEmpty())
                        },
                        label = { Text("Buka di Studio") },
                        leadingIcon = { Icon(Icons.Default.Code, null, Modifier.size(16.dp)) }
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (uiState.isAnalyzingWithAi) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text("Karsa AI sedang menganalisis file GitHub...")
                            }
                        }
                    }

                    if (!uiState.aiAnalysisResult.isNullOrBlank()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Hasil Analisis AI", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                MarkdownCodeText(
                                    markdown = uiState.aiAnalysisResult.orEmpty(),
                                    onOpenInCodeStudio = onOpenCodeInStudio
                                )
                            }
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        SelectionContainer {
                            Text(
                                text = uiState.viewingFileContent.orEmpty(),
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(12.dp)
                            )
                        }
                    }
                }
            } else {
                // Directory Tree & Recent Commits
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.recentCommits.isNotEmpty() && uiState.currentPath.isBlank()) {
                        item {
                            Text("Commit Terbaru", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                uiState.recentCommits.take(5).forEach { commit ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        shape = MaterialTheme.shapes.small,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Default.History, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                                            Text(
                                                text = commit.commit.message.lines().firstOrNull().orEmpty().take(42),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    items(uiState.directoryItems, key = { it.path }) { item ->
                        Card(
                            onClick = { viewModel.openContentItem(item) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (item.type == "dir") Icons.Default.Folder else Icons.Default.Description,
                                    contentDescription = null,
                                    tint = if (item.type == "dir") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                )
                                Text(item.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RepoCard(
    fullName: String,
    description: String,
    language: String,
    stars: Long,
    isBookmarked: Boolean,
    onClick: () -> Unit,
    onToggleBookmark: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(fullName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (description.isNotBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (language.isNotBlank()) {
                        Text(language, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Star, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                        Text(stars.toString(), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            IconButton(onClick = onToggleBookmark) {
                Icon(
                    imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = "Bookmark",
                    tint = if (isBookmarked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
