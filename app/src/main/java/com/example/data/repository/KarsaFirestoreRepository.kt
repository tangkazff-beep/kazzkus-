package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.CodeProject
import com.example.data.model.CodingLesson
import com.example.data.model.Conversation
import com.example.data.model.GitHubBookmark
import com.example.data.model.LessonStep
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class KarsaFirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in before accessing Firestore.")
    }

    private fun <T> observeCollection(
        query: Query,
        path: String,
        clazz: Class<T>
    ): Flow<List<T>> = flow {
        val uid = requireUserId()
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(clazz, ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    // --- User Profile ---
    suspend fun ensureUserProfile(displayName: String, preferredLanguage: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val safeName = displayName.trim().ifBlank { "Pengguna Karsa" }.take(100)
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "preferredLanguage" to preferredLanguage.take(30),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                docRef.update(
                    mapOf(
                        "preferredLanguage" to preferredLanguage.take(30),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun getUserProfile(): Result<UserProfile?> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            docRef.get().await().toObject(UserProfile::class.java, ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    // --- Conversations & Messages ---
    fun observeConversations(): Flow<List<Conversation>> {
        val uid = auth.currentUser?.uid ?: ""
        return observeCollection(
            query = db.collection("users").document(uid)
                .collection("conversations")
                .orderBy("updatedAt", Query.Direction.DESCENDING),
            path = "users/$uid/conversations",
            clazz = Conversation::class.java
        )
    }

    fun observeMessages(conversationId: String): Flow<List<ChatMessage>> {
        val uid = auth.currentUser?.uid ?: ""
        return observeCollection(
            query = db.collection("users").document(uid)
                .collection("conversations").document(conversationId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING),
            path = "users/$uid/conversations/$conversationId/messages",
            clazz = ChatMessage::class.java
        )
    }

    suspend fun createConversation(
        title: String,
        mode: String,
        language: String,
        previewText: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("conversations").document()
        val safeMode = if (mode in listOf("chat", "analysis", "code")) mode else "chat"
        val payload = mapOf(
            "userId" to uid,
            "title" to title.trim().ifBlank { "Obrolan Baru" }.take(160),
            "mode" to safeMode,
            "language" to language.ifBlank { "Indonesia" }.take(30),
            "previewText" to previewText.take(400),
            "messageCount" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateConversationMetadata(
        conversationId: String,
        title: String? = null,
        mode: String? = null,
        language: String? = null,
        previewText: String,
        messageCount: Long
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("conversations").document(conversationId)
        val updates = buildMap<String, Any> {
            if (!title.isNullOrBlank()) put("title", title.trim().take(160))
            if (mode != null && mode in listOf("chat", "analysis", "code")) put("mode", mode)
            if (!language.isNullOrBlank()) put("language", language.take(30))
            put("previewText", previewText.take(400))
            put("messageCount", messageCount.coerceAtLeast(0L))
            put("updatedAt", FieldValue.serverTimestamp())
        }
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun addMessage(
        conversationId: String,
        role: String,
        text: String,
        attachmentName: String? = null,
        attachmentType: String? = null,
        codeLanguage: String? = null
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val msgRef = db.collection("users").document(uid)
            .collection("conversations").document(conversationId)
            .collection("messages").document()
        val payload = buildMap<String, Any> {
            put("userId", uid)
            put("conversationId", conversationId)
            put("role", if (role == "model") "model" else "user")
            put("text", text.take(32000))
            if (!attachmentName.isNullOrBlank()) put("attachmentName", attachmentName.take(200))
            if (!attachmentType.isNullOrBlank()) put("attachmentType", attachmentType.take(60))
            if (!codeLanguage.isNullOrBlank()) put("codeLanguage", codeLanguage.take(40))
            put("createdAt", FieldValue.serverTimestamp())
        }
        try {
            msgRef.set(payload).await()
            msgRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, msgRef.path)
            throw e
        }
    }

    suspend fun deleteConversation(conversationId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val convRef = db.collection("users").document(uid)
            .collection("conversations").document(conversationId)
        try {
            val msgs = convRef.collection("messages").get().await()
            for (doc in msgs.documents) {
                doc.reference.delete().await()
            }
            convRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, convRef.path)
            throw e
        }
    }

    // --- Interactive Coding Lessons ---
    fun observeLessons(): Flow<List<CodingLesson>> {
        val uid = auth.currentUser?.uid ?: ""
        return observeCollection(
            query = db.collection("users").document(uid)
                .collection("lessons")
                .orderBy("updatedAt", Query.Direction.DESCENDING),
            path = "users/$uid/lessons",
            clazz = CodingLesson::class.java
        )
    }

    fun observeLessonSteps(lessonId: String): Flow<List<LessonStep>> {
        val uid = auth.currentUser?.uid ?: ""
        return observeCollection(
            query = db.collection("users").document(uid)
                .collection("lessons").document(lessonId)
                .collection("steps")
                .orderBy("stepIndex", Query.Direction.ASCENDING),
            path = "users/$uid/lessons/$lessonId/steps",
            clazz = LessonStep::class.java
        )
    }

    suspend fun createLessonWithSteps(
        topic: String,
        programmingLanguage: String,
        difficulty: String,
        steps: List<LessonStep>
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val safeDifficulty = if (difficulty in listOf("Pemula", "Menengah", "Lanjutan")) difficulty else "Pemula"
        val lessonRef = db.collection("users").document(uid).collection("lessons").document()
        val lessonPayload = mapOf(
            "userId" to uid,
            "topic" to topic.trim().ifBlank { "Belajar Coding" }.take(120),
            "programmingLanguage" to programmingLanguage.trim().ifBlank { "Kotlin" }.take(40),
            "difficulty" to safeDifficulty,
            "currentStepIndex" to 0L,
            "totalSteps" to steps.size.coerceIn(1, 20).toLong(),
            "completedSteps" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            lessonRef.set(lessonPayload).await()
            steps.take(20).forEachIndexed { index, step ->
                val stepRef = lessonRef.collection("steps").document("step_$index")
                val stepPayload = mapOf(
                    "userId" to uid,
                    "lessonId" to lessonRef.id,
                    "stepIndex" to index.toLong(),
                    "title" to step.title.ifBlank { "Langkah ${index + 1}" }.take(160),
                    "explanation" to step.explanation.ifBlank { "Materi pembelajaran." }.take(8000),
                    "codeExample" to step.codeExample.take(8000),
                    "challengePrompt" to step.challengePrompt.ifBlank { "Tulis kode latihan." }.take(2000),
                    "hint" to step.hint.take(1000),
                    "userAnswer" to "",
                    "aiFeedback" to "",
                    "isCompleted" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                stepRef.set(stepPayload).await()
            }
            lessonRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, lessonRef.path)
            throw e
        }
    }

    suspend fun updateLessonStepProgress(
        lessonId: String,
        stepId: String,
        userAnswer: String,
        aiFeedback: String,
        isCompleted: Boolean,
        newCurrentStepIndex: Long,
        newCompletedSteps: Long
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val lessonRef = db.collection("users").document(uid).collection("lessons").document(lessonId)
        val stepRef = lessonRef.collection("steps").document(stepId)
        try {
            stepRef.update(
                mapOf(
                    "userAnswer" to userAnswer.take(8000),
                    "aiFeedback" to aiFeedback.take(4000),
                    "isCompleted" to isCompleted,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            lessonRef.update(
                mapOf(
                    "currentStepIndex" to newCurrentStepIndex.coerceIn(0L, 20L),
                    "completedSteps" to newCompletedSteps.coerceIn(0L, 20L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, stepRef.path)
            throw e
        }
    }

    suspend fun updateLessonActiveStep(lessonId: String, stepIndex: Long, completedSteps: Long): Result<Unit> = runCatching {
        val uid = requireUserId()
        val lessonRef = db.collection("users").document(uid).collection("lessons").document(lessonId)
        try {
            lessonRef.update(
                mapOf(
                    "currentStepIndex" to stepIndex.coerceIn(0L, 20L),
                    "completedSteps" to completedSteps.coerceIn(0L, 20L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, lessonRef.path)
            throw e
        }
    }

    // --- Code & Web Projects ---
    fun observeProjects(): Flow<List<CodeProject>> {
        val uid = auth.currentUser?.uid ?: ""
        return observeCollection(
            query = db.collection("users").document(uid)
                .collection("projects")
                .orderBy("updatedAt", Query.Direction.DESCENDING),
            path = "users/$uid/projects",
            clazz = CodeProject::class.java
        )
    }

    suspend fun saveCodeProject(
        projectId: String? = null,
        title: String,
        language: String,
        prompt: String,
        code: String,
        isWebPreviewable: Boolean
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val collRef = db.collection("users").document(uid).collection("projects")
        val docRef = if (projectId.isNullOrBlank()) collRef.document() else collRef.document(projectId)
        try {
            if (projectId.isNullOrBlank()) {
                val payload = mapOf(
                    "userId" to uid,
                    "title" to title.trim().ifBlank { "Proyek Studio" }.take(160),
                    "language" to language.trim().ifBlank { "HTML/Web" }.take(40),
                    "prompt" to prompt.take(2000),
                    "code" to code.take(64000),
                    "isWebPreviewable" to isWebPreviewable,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                docRef.update(
                    mapOf(
                        "title" to title.trim().ifBlank { "Proyek Studio" }.take(160),
                        "language" to language.trim().ifBlank { "HTML/Web" }.take(40),
                        "code" to code.take(64000),
                        "isWebPreviewable" to isWebPreviewable,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun deleteCodeProject(projectId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("projects").document(projectId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- GitHub Bookmarks ---
    fun observeGitHubBookmarks(): Flow<List<GitHubBookmark>> {
        val uid = auth.currentUser?.uid ?: ""
        return observeCollection(
            query = db.collection("users").document(uid)
                .collection("githubBookmarks")
                .orderBy("createdAt", Query.Direction.DESCENDING),
            path = "users/$uid/githubBookmarks",
            clazz = GitHubBookmark::class.java
        )
    }

    suspend fun addGitHubBookmark(
        fullName: String,
        description: String,
        language: String,
        stars: Long,
        htmlUrl: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val sanitizedId = fullName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)
        val docRef = db.collection("users").document(uid)
            .collection("githubBookmarks").document(sanitizedId)
        val payload = mapOf(
            "userId" to uid,
            "fullName" to fullName.take(160),
            "description" to description.take(600),
            "language" to language.take(50),
            "stars" to stars.coerceAtLeast(0L),
            "htmlUrl" to htmlUrl.take(300),
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun removeGitHubBookmark(bookmarkId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("githubBookmarks").document(bookmarkId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }
}
