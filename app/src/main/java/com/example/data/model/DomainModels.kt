package com.example.data.model

import android.graphics.Bitmap

data class ChartPoint(
    val label: String,
    val value: Double
)

data class DataAnalysisSummary(
    val title: String,
    val rowCount: Int,
    val columnCount: Int,
    val numericColumnName: String,
    val minVal: Double,
    val maxVal: Double,
    val avgVal: Double,
    val sumVal: Double,
    val points: List<ChartPoint>
)

sealed interface AttachmentPayload {
    val displayName: String

    data class ImageAttachment(
        override val displayName: String,
        val bitmap: Bitmap
    ) : AttachmentPayload

    data class AudioAttachment(
        override val displayName: String,
        val filePath: String,
        val durationSeconds: Int,
        val mimeType: String = "audio/mp4"
    ) : AttachmentPayload

    data class FileAttachment(
        override val displayName: String,
        val mimeType: String,
        val textContent: String?,
        val rawBytes: ByteArray?,
        val analysisSummary: DataAnalysisSummary? = null
    ) : AttachmentPayload
}

data class AiSearchResultItem(
    val sourceType: String, // "chat", "lesson", "project", "github"
    val itemId: String,
    val title: String,
    val reason: String,
    val snippet: String
)

data class LessonEvaluationResult(
    val passed: Boolean,
    val score: Int,
    val feedback: String,
    val suggestedCorrection: String
)
