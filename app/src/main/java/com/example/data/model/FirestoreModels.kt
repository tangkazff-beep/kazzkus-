package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val preferredLanguage: String = "Indonesia",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val mode: String = "chat",
    val language: String = "Indonesia",
    val previewText: String = "",
    val messageCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val conversationId: String = "",
    val role: String = "user",
    val text: String = "",
    val attachmentName: String? = null,
    val attachmentType: String? = null,
    val codeLanguage: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CodingLesson(
    @DocumentId val id: String = "",
    val userId: String = "",
    val topic: String = "",
    val programmingLanguage: String = "",
    val difficulty: String = "Pemula",
    val currentStepIndex: Long = 0L,
    val totalSteps: Long = 1L,
    val completedSteps: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class LessonStep(
    @DocumentId val id: String = "",
    val userId: String = "",
    val lessonId: String = "",
    val stepIndex: Long = 0L,
    val title: String = "",
    val explanation: String = "",
    val codeExample: String = "",
    val challengePrompt: String = "",
    val hint: String = "",
    val userAnswer: String = "",
    val aiFeedback: String = "",
    val isCompleted: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CodeProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val language: String = "HTML/Web",
    val prompt: String = "",
    val code: String = "",
    val isWebPreviewable: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class GitHubBookmark(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fullName: String = "",
    val description: String = "",
    val language: String = "",
    val stars: Long = 0L,
    val htmlUrl: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
