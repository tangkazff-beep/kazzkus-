package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.LessonStep
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class KarsaRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createConversationAndAddMessage_authenticatedOwner_succeeds() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repo = KarsaFirestoreRepository(firestore, auth)

        val convResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createConversation(
                title = "Analisis CSV ${UUID.randomUUID()}",
                mode = "analysis",
                language = "Indonesia",
                previewText = "Data penjualan"
            )
        }
        assertTrue(convResult.isSuccess)
        val convId = convResult.getOrThrow()
        assertTrue(convId.isNotBlank())

        val msgResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.addMessage(
                conversationId = convId,
                role = "user",
                text = "Hitung rata-rata penjualan",
                attachmentName = "sales.csv",
                attachmentType = "file"
            )
        }
        assertTrue(msgResult.isSuccess)

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeMessages(convId).first { it.isNotEmpty() }
        }
        assertEquals(1, observed.size)
        assertEquals("sales.csv", observed.first().attachmentName)
    }

    @Test
    fun createLessonAndAdvanceStep_authenticatedOwner_succeeds() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repo = KarsaFirestoreRepository(firestore, auth)

        val lessonId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createLessonWithSteps(
                topic = "Dasar Python",
                programmingLanguage = "Python",
                difficulty = "Pemula",
                steps = listOf(
                    LessonStep(
                        stepIndex = 0L,
                        title = "Variabel",
                        explanation = "Variabel menyimpan nilai.",
                        codeExample = "x = 10",
                        challengePrompt = "Buat variabel nama = 'Karsa'",
                        hint = "Gunakan tanda kutip"
                    )
                )
            ).getOrThrow()
        }

        val steps = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeLessonSteps(lessonId).first { it.isNotEmpty() }
        }
        assertEquals(1, steps.size)

        val updateRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.updateLessonStepProgress(
                lessonId = lessonId,
                stepId = steps.first().id,
                userAnswer = "nama = 'Karsa'",
                aiFeedback = "Tepat sekali!",
                isCompleted = true,
                newCurrentStepIndex = 0L,
                newCompletedSteps = 1L
            )
        }
        assertTrue(updateRes.isSuccess)
    }

    @Test
    fun saveCodeProject_andBookmarkGitHub_authenticatedOwner_succeeds() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repo = KarsaFirestoreRepository(firestore, auth)

        val projectId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.saveCodeProject(
                title = "Kalkulator Web",
                language = "HTML/Web",
                prompt = "Buat kalkulator modern",
                code = "<html><body><h1>Calc</h1></body></html>",
                isWebPreviewable = true
            ).getOrThrow()
        }
        assertTrue(projectId.isNotBlank())

        val bookmarkRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.addGitHubBookmark(
                fullName = "JetBrains/kotlin",
                description = "The Kotlin Programming Language",
                language = "Kotlin",
                stars = 50000L,
                htmlUrl = "https://github.com/JetBrains/kotlin"
            )
        }
        assertTrue(bookmarkRes.isSuccess)
    }

    @Test
    fun crossUserRead_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = KarsaFirestoreRepository(firestore, auth)
        val convId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createConversation("Private Chat", "chat", "Indonesia", "Rahasia").getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("conversations").document(convId)
                    .get()
                    .await()
            }
            fail("Expected PERMISSION_DENIED when reading another user's conversation")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
        }
    }

    @Test
    fun observeConversations_unauthenticated_failsWithIllegalStateOrPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("conversations")
                    .get()
                    .await()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated read")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice.karsa@test.com"
        const val BOB_EMAIL = "bob.karsa@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
