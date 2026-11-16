package com.example.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.AiSearchResultItem
import com.example.data.model.AttachmentPayload
import com.example.data.model.ChatMessage
import com.example.data.model.LessonEvaluationResult
import com.example.data.model.LessonStep
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File

class GeminiAiRepository {

    private companion object {
        const val TAG = "GeminiAiRepository"
        const val MODEL_DEFAULT = "gemini-3.8-flash"
        const val MODEL_LITE = "gemini-3.5-flash-lite"
        const val MODEL_IMAGE = "gemini-nano-banana-2.1"
        const val TIMEOUT_MS = 60_000L
    }

    private fun buildSystemInstruction(mode: String, language: String): Content {
        val langDirective = if (language == "Auto") {
            "Detect the user's language automatically and always reply naturally in that same language."
        } else {
            "Always respond in $language unless the user explicitly asks for another language."
        }
        val roleDirective = when (mode) {
            "analysis" -> "You are Karsa AI Data Analyst. Analyze datasets (CSV, JSON, tables, metrics) in real time: compute key patterns, anomalies, trends, statistics, and actionable insights cleanly using bullet points and markdown tables."
            "code" -> "You are Karsa AI Senior Full-Stack & Mobile Engineer. Write clean, production-grade code and self-contained HTML/CSS/JS web apps inside properly tagged markdown code fences (```html, ```kotlin, ```python, etc.)."
            else -> "You are Karsa AI, an intelligent multilingual assistant for real-time data analysis, software engineering, interactive coding guidance, and creative problem solving."
        }
        return content {
            text("$roleDirective $langDirective Be accurate, concise, well-structured, and helpful.")
        }
    }

    fun streamChatMessage(
        history: List<ChatMessage>,
        userPrompt: String,
        attachments: List<AttachmentPayload>,
        mode: String,
        language: String,
        fastMode: Boolean = false
    ): Flow<String> = flow {
        val modelName = if (fastMode) MODEL_LITE else MODEL_DEFAULT
        val model = Firebase.ai.generativeModel(
            modelName = modelName,
            systemInstruction = buildSystemInstruction(mode, language)
        )

        val chatHistory = history.takeLast(14).map { msg ->
            content(if (msg.role == "model") "model" else "user") {
                text(msg.text)
            }
        }
        val chat = model.startChat(history = chatHistory)

        val userContent = content("user") {
            for (attachment in attachments) {
                when (attachment) {
                    is AttachmentPayload.ImageAttachment -> {
                        image(attachment.bitmap)
                    }
                    is AttachmentPayload.AudioAttachment -> {
                        val file = File(attachment.filePath)
                        if (file.exists() && file.length() > 0L && file.length() <= 18_000_000L) {
                            inlineData(file.readBytes(), attachment.mimeType)
                        }
                    }
                    is AttachmentPayload.FileAttachment -> {
                        if (attachment.rawBytes != null && attachment.mimeType == "application/pdf" && attachment.rawBytes.size <= 15_000_000) {
                            inlineData(attachment.rawBytes, attachment.mimeType)
                        } else if (!attachment.textContent.isNullOrBlank()) {
                            val snippet = attachment.textContent.take(28000)
                            text("\n[File: ${attachment.displayName}]\n```\n$snippet\n```\n")
                        }
                    }
                }
            }
            val finalPrompt = userPrompt.ifBlank {
                if (attachments.any { it is AttachmentPayload.AudioAttachment }) {
                    "Dengarkan voice note ini, transkripsikan secara singkat, lalu jawab atau tindak lanjuti permintaan di dalamnya."
                } else {
                    "Tolong analisis lampiran ini secara menyeluruh."
                }
            }
            text(finalPrompt)
        }

        val accumulated = StringBuilder()
        withTimeout(TIMEOUT_MS) {
            chat.sendMessageStream(userContent).collect { chunk ->
                val delta = chunk.text.orEmpty()
                if (delta.isNotEmpty()) {
                    accumulated.append(delta)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("Model tidak mengembalikan teks respons.")
        }
    }

    suspend fun generateCodingCurriculum(
        topic: String,
        programmingLanguage: String,
        difficulty: String,
        responseLanguage: String
    ): List<LessonStep> {
        val stepSchema = Schema.obj(
            properties = mapOf(
                "title" to Schema.string(description = "Short step title"),
                "explanation" to Schema.string(description = "Clear step-by-step concept explanation"),
                "codeExample" to Schema.string(description = "Runnable example code snippet"),
                "challengePrompt" to Schema.string(description = "Hands-on coding exercise for the learner"),
                "hint" to Schema.string(description = "Helpful hint without giving away full solution")
            )
        )
        val curriculumSchema = Schema.obj(
            properties = mapOf(
                "steps" to Schema.array(
                    items = stepSchema,
                    description = "4 to 5 sequential interactive learning steps"
                )
            )
        )
        val model = Firebase.ai.generativeModel(
            modelName = MODEL_DEFAULT,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = curriculumSchema
            }
        )

        val prompt = """
            Buat kurikulum belajar pemrograman interaktif langkah demi langkah sebanyak 4 langkah.
            Topik: $topic
            Bahasa pemrograman: $programmingLanguage
            Tingkat kesulitan: $difficulty
            Bahasa penjelasan: $responseLanguage (jika Auto, gunakan Bahasa Indonesia yang jelas dan modern).
            Pastikan setiap langkah berurutan dari konsep dasar hingga praktik nyata, dengan contoh kode bersih dan tantangan interaktif.
        """.trimIndent()

        val response = withTimeout(TIMEOUT_MS) { model.generateContent(prompt) }
        val jsonText = response.text ?: throw IllegalStateException("Respons kurikulum kosong.")
        val root = JSONObject(jsonText)
        val arr = root.getJSONArray("steps")
        return (0 until arr.length()).map { idx ->
            val obj = arr.getJSONObject(idx)
            LessonStep(
                stepIndex = idx.toLong(),
                title = obj.optString("title", "Langkah ${idx + 1}"),
                explanation = obj.optString("explanation", ""),
                codeExample = obj.optString("codeExample", ""),
                challengePrompt = obj.optString("challengePrompt", ""),
                hint = obj.optString("hint", "")
            )
        }
    }

    suspend fun evaluateLessonAnswer(
        programmingLanguage: String,
        step: LessonStep,
        userAnswer: String,
        responseLanguage: String
    ): LessonEvaluationResult {
        val evalSchema = Schema.obj(
            properties = mapOf(
                "passed" to Schema.boolean(description = "True if the user's answer correctly solves the challenge"),
                "score" to Schema.integer(description = "Score from 0 to 100"),
                "feedback" to Schema.string(description = "Constructive, encouraging explanation of what is right or needs fixing"),
                "suggestedCorrection" to Schema.string(description = "Clean reference code or correction if needed")
            )
        )
        val model = Firebase.ai.generativeModel(
            modelName = MODEL_DEFAULT,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = evalSchema
            }
        )
        val prompt = """
            Evaluasi jawaban latihan pemrograman berikut dalam bahasa $responseLanguage (default Bahasa Indonesia).
            Bahasa pemrograman: $programmingLanguage
            Judul langkah: ${step.title}
            Instruksi tantangan: ${step.challengePrompt}
            Contoh kode acuan: ${step.codeExample}
            Jawaban pengguna:
            $userAnswer
        """.trimIndent()

        val response = withTimeout(TIMEOUT_MS) { model.generateContent(prompt) }
        val jsonText = response.text ?: throw IllegalStateException("Evaluasi AI kosong.")
        val json = JSONObject(jsonText)
        return LessonEvaluationResult(
            passed = json.optBoolean("passed", false),
            score = json.optInt("score", 0),
            feedback = json.optString("feedback", ""),
            suggestedCorrection = json.optString("suggestedCorrection", "")
        )
    }

    suspend fun generateCodeOrWebApp(
        prompt: String,
        targetLanguage: String,
        existingCode: String? = null,
        responseLanguage: String = "Indonesia"
    ): Pair<String, String> {
        val codeSchema = Schema.obj(
            properties = mapOf(
                "title" to Schema.string(description = "Short descriptive title for this project"),
                "code" to Schema.string(description = "Complete, clean, runnable source code without markdown fences")
            )
        )
        val model = Firebase.ai.generativeModel(
            modelName = MODEL_DEFAULT,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = codeSchema
            }
        )
        val fullPrompt = buildString {
            append("You are an expert software and web developer. Output ONLY raw code inside the 'code' field (no ``` fences). ")
            append("Target language/stack: $targetLanguage. User explanation/comments language: $responseLanguage. ")
            if (targetLanguage.contains("HTML", ignoreCase = true) || targetLanguage.contains("Web", ignoreCase = true)) {
                append("Create a complete single-file responsive HTML5 document with embedded <style> and <script> that looks modern, interactive, and works cleanly inside a mobile WebView. ")
            }
            if (!existingCode.isNullOrBlank()) {
                append("\nModify/enhance this existing code:\n$existingCode\n")
            }
            append("\nUser request: $prompt")
        }
        val response = withTimeout(TIMEOUT_MS) { model.generateContent(fullPrompt) }
        val jsonText = response.text ?: throw IllegalStateException("Kode yang dihasilkan kosong.")
        val json = JSONObject(jsonText)
        val title = json.optString("title", "Proyek Studio").ifBlank { "Proyek Studio" }
        val code = json.optString("code", "").ifBlank { throw IllegalStateException("Field kode kosong.") }
        return title to code
    }

    suspend fun generateOrEditImage(
        prompt: String,
        inputBitmap: Bitmap?,
        aspectRatio: AspectRatio
    ): Bitmap {
        val config = generationConfig {
            responseModalities = listOf(ResponseModality.IMAGE)
            imageConfig = ImageConfig.builder()
                .setAspectRatio(aspectRatio)
                .setImageSize(ImageSize.SIZE_1K)
                .build()
        }
        val model = Firebase.ai.generativeModel(
            modelName = MODEL_IMAGE,
            generationConfig = config
        )

        val response = withTimeout(TIMEOUT_MS) {
            if (inputBitmap != null) {
                model.generateContent(
                    content {
                        image(inputBitmap)
                        text(prompt)
                    }
                )
            } else {
                model.generateContent(prompt)
            }
        }
        val imagePart = response.candidates.firstOrNull()
            ?.content?.parts?.filterIsInstance<ImagePart>()
            ?.firstOrNull()
        return imagePart?.image
            ?: throw IllegalStateException("Model gambar tidak mengembalikan output gambar.")
    }

    suspend fun semanticSearchUserData(
        query: String,
        corpusItemsJson: String,
        responseLanguage: String
    ): List<AiSearchResultItem> {
        val itemSchema = Schema.obj(
            properties = mapOf(
                "sourceType" to Schema.enumeration(listOf("chat", "lesson", "project", "github"), "Type of record"),
                "itemId" to Schema.string(description = "Exact id from corpus"),
                "title" to Schema.string(description = "Item title"),
                "reason" to Schema.string(description = "Brief AI explanation of why this matches the user's query"),
                "snippet" to Schema.string(description = "Relevant excerpt")
            )
        )
        val searchSchema = Schema.obj(
            properties = mapOf(
                "matches" to Schema.array(items = itemSchema, description = "Up to 8 most relevant matches ranked by relevance")
            )
        )
        val model = Firebase.ai.generativeModel(
            modelName = MODEL_LITE,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = searchSchema
            }
        )
        val prompt = """
            Lakukan pencarian semantik berbasis AI atas data pengguna berikut dalam bahasa $responseLanguage (default Bahasa Indonesia).
            Query pencarian: "$query"
            Kumpulan item pengguna (JSON):
            $corpusItemsJson
            Pilih item yang paling relevan secara makna/topik (tidak harus persis kata per kata), urutkan dari yang paling relevan, dan jelaskan alasannya dengan singkat.
        """.trimIndent()

        val response = withTimeout(TIMEOUT_MS) { model.generateContent(prompt) }
        val jsonText = response.text ?: return emptyList()
        val arr = JSONObject(jsonText).optJSONArray("matches") ?: return emptyList()
        return (0 until arr.length()).map { idx ->
            val obj = arr.getJSONObject(idx)
            AiSearchResultItem(
                sourceType = obj.optString("sourceType", "chat"),
                itemId = obj.optString("itemId", ""),
                title = obj.optString("title", ""),
                reason = obj.optString("reason", ""),
                snippet = obj.optString("snippet", "")
            )
        }
    }

    suspend fun analyzeGitHubCode(
        repoFullName: String,
        filePath: String,
        fileContent: String,
        taskPrompt: String,
        responseLanguage: String
    ): String {
        val model = Firebase.ai.generativeModel(
            modelName = MODEL_DEFAULT,
            systemInstruction = buildSystemInstruction("code", responseLanguage)
        )
        val prompt = """
            Repositori GitHub: $repoFullName
            File: $filePath
            Instruksi: $taskPrompt
            
            Isi file:
            ```
            ${fileContent.take(26000)}
            ```
        """.trimIndent()
        val response = withTimeout(TIMEOUT_MS) { model.generateContent(prompt) }
        return response.text?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Respons analisis GitHub kosong.")
    }
}
