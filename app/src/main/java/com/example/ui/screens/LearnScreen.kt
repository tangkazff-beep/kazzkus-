package com.example.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.MarkdownCodeText
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.viewmodel.LearnViewModel

private val PROGRAMMING_LANGUAGES = listOf("Kotlin", "Python", "JavaScript", "HTML/CSS", "SQL", "Rust")
private val DIFFICULTIES = listOf("Pemula", "Menengah", "Lanjutan")

@Composable
fun LearnScreen(
    viewModel: LearnViewModel,
    responseLanguage: String,
    onOpenCodeInStudio: (language: String, code: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lessons by viewModel.lessons.collectAsStateWithLifecycle()
    val steps by viewModel.steps.collectAsStateWithLifecycle()

    val selectedLesson = lessons.firstOrNull { it.id == uiState.selectedLessonId }

    if (selectedLesson != null) {
        val currentIdx = selectedLesson.currentStepIndex.toInt().coerceIn(0, (steps.size - 1).coerceAtLeast(0))
        val activeStep = steps.getOrNull(currentIdx)

        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = { viewModel.selectLesson(null) },
                    modifier = Modifier.testTag("back_to_lessons_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali ke daftar pelajaran")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedLesson.topic,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "${selectedLesson.programmingLanguage} • ${selectedLesson.difficulty}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (steps.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (activeStep != null) {
                val progress = (selectedLesson.completedSteps.toFloat() / selectedLesson.totalSteps.coerceAtLeast(1L)).coerceIn(0f, 1f)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Langkah ${currentIdx + 1} dari ${steps.size}",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = "${selectedLesson.completedSteps}/${selectedLesson.totalSteps} selesai",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp)
                    )
                }

                // Step Explanation Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = activeStep.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            if (activeStep.isCompleted) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selesai",
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }

                        Text(
                            text = activeStep.explanation,
                            style = MaterialTheme.typography.bodyLarge
                        )

                        if (activeStep.codeExample.isNotBlank()) {
                            MarkdownCodeText(
                                markdown = "```${selectedLesson.programmingLanguage.lowercase()}\n${activeStep.codeExample}\n```",
                                onOpenInCodeStudio = onOpenCodeInStudio
                            )
                        }
                    }
                }

                // Interactive Challenge Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Latihan Praktik Interaktif",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = activeStep.challengePrompt,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        OutlinedTextField(
                            value = uiState.answerDraft.ifBlank { activeStep.userAnswer },
                            onValueChange = { viewModel.updateAnswerDraft(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("lesson_answer_input"),
                            textStyle = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 13.sp),
                            label = { Text("Tulis kode atau jawaban Anda") },
                            minLines = 4,
                            maxLines = 8
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { viewModel.toggleHint() },
                                modifier = Modifier.testTag("toggle_hint_button")
                            ) {
                                Icon(Icons.Default.Lightbulb, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (uiState.showHint) "Sembunyikan Petunjuk" else "Lihat Petunjuk")
                            }

                            Button(
                                onClick = { viewModel.checkStepAnswer(selectedLesson, activeStep, responseLanguage) },
                                enabled = !uiState.isCheckingAnswer && (uiState.answerDraft.isNotBlank() || activeStep.userAnswer.isNotBlank()),
                                modifier = Modifier.testTag("check_answer_button")
                            ) {
                                if (uiState.isCheckingAnswer) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                } else {
                                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text("Periksa AI")
                            }
                        }

                        if (uiState.showHint && activeStep.hint.isNotBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "Petunjuk: ${activeStep.hint}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        if (activeStep.aiFeedback.isNotBlank()) {
                            Surface(
                                color = if (activeStep.isCompleted) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Text(
                                    text = activeStep.aiFeedback,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }

                // Prev / Next Navigation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = { viewModel.navigateStep(selectedLesson, (currentIdx - 1).toLong()) },
                        enabled = currentIdx > 0
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sebelumnya")
                    }
                    Button(
                        onClick = { viewModel.navigateStep(selectedLesson, (currentIdx + 1).toLong()) },
                        enabled = currentIdx < steps.lastIndex
                    ) {
                        Text("Langkah Berikutnya")
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(18.dp))
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
        }
    } else {
        // Curriculum Builder + Saved Lessons List
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.School, null, tint = MaterialTheme.colorScheme.primary)
                            Text("Tutor Pemrograman Interaktif", style = MaterialTheme.typography.titleLarge)
                        }
                        Text(
                            text = "Pilih bahasa pemrograman dan topik untuk mendapatkan panduan interaktif langkah demi langkah dengan evaluasi kode AI.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = uiState.topicInput,
                            onValueChange = { viewModel.updateTopic(it) },
                            modifier = Modifier.fillMaxWidth().testTag("lesson_topic_input"),
                            label = { Text("Topik belajar (mis. Asynchronous Coroutines, DOM Events)") },
                            singleLine = true
                        )

                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PROGRAMMING_LANGUAGES.forEach { lang ->
                                FilterChip(
                                    selected = uiState.programmingLanguage == lang,
                                    onClick = { viewModel.setProgrammingLanguage(lang) },
                                    label = { Text(lang) }
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DIFFICULTIES.forEach { diff ->
                                FilterChip(
                                    selected = uiState.difficulty == diff,
                                    onClick = { viewModel.setDifficulty(diff) },
                                    label = { Text(diff) }
                                )
                            }
                        }

                        Button(
                            onClick = { viewModel.createCurriculum(responseLanguage) },
                            enabled = !uiState.isGeneratingCurriculum,
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("create_lesson_button")
                        ) {
                            if (uiState.isGeneratingCurriculum) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(Modifier.width(10.dp))
                                Text("Menyusun Kurikulum Langkah demi Langkah...")
                            } else {
                                Text("Mulai Panduan Interaktif")
                            }
                        }

                        if (!uiState.errorMessage.isNullOrBlank()) {
                            Text(
                                text = uiState.errorMessage ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            if (lessons.isNotEmpty()) {
                item {
                    Text(
                        text = "Jalur Belajar Anda (${lessons.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                items(lessons, key = { it.id }) { lesson ->
                    Card(
                        onClick = { viewModel.selectLesson(lesson) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(lesson.topic, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "${lesson.completedSteps}/${lesson.totalSteps} langkah",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = "${lesson.programmingLanguage} • ${lesson.difficulty}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
