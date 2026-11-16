package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.model.AttachmentPayload
import com.example.data.model.ChartPoint
import com.example.data.model.DataAnalysisSummary
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.max

object FileParserUtils {

    fun loadImageAttachment(context: Context, uri: Uri): AttachmentPayload.ImageAttachment? {
        val name = queryFileName(context, uri) ?: "foto_${System.currentTimeMillis()}.jpg"
        val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)

        val maxDim = 1600
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxDim || bounds.outHeight / sampleSize > maxDim) {
            sampleSize *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, opts) ?: return null
        return AttachmentPayload.ImageAttachment(displayName = name, bitmap = bitmap)
    }

    fun loadFileAttachment(context: Context, uri: Uri): AttachmentPayload.FileAttachment? {
        val name = queryFileName(context, uri) ?: "berkas_${System.currentTimeMillis()}"
        val mimeType = context.contentResolver.getType(uri) ?: guessMimeFromName(name)
        val rawBytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            var total = 0
            while (true) {
                val read = input.read(chunk)
                if (read <= 0) break
                total += read
                if (total > 15_000_000) break
                buffer.write(chunk, 0, read)
            }
            buffer.toByteArray()
        } ?: return null

        val isPdf = mimeType == "application/pdf" || name.endsWith(".pdf", ignoreCase = true)
        val textContent = if (!isPdf) {
            String(rawBytes, Charsets.UTF_8)
        } else {
            null
        }
        val analysis = if (textContent != null) {
            tryParseDataSummary(name, textContent)
        } else {
            null
        }
        return AttachmentPayload.FileAttachment(
            displayName = name,
            mimeType = mimeType,
            textContent = textContent,
            rawBytes = if (isPdf) rawBytes else null,
            analysisSummary = analysis
        )
    }

    fun tryParseDataSummary(title: String, rawText: String): DataAnalysisSummary? {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return null
        return try {
            if (trimmed.startsWith("[")) {
                parseJsonArraySummary(title, JSONArray(trimmed))
            } else if (trimmed.startsWith("{")) {
                val obj = JSONObject(trimmed)
                val firstArrayKey = obj.keys().asSequence().firstOrNull { obj.optJSONArray(it) != null }
                if (firstArrayKey != null) {
                    parseJsonArraySummary(title, obj.getJSONArray(firstArrayKey))
                } else null
            } else {
                parseCsvSummary(title, trimmed)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseJsonArraySummary(title: String, array: JSONArray): DataAnalysisSummary? {
        if (array.length() == 0) return null
        val firstObj = array.optJSONObject(0) ?: return null
        val keys = firstObj.keys().asSequence().toList()
        if (keys.isEmpty()) return null

        var labelKey: String? = null
        var numericKey: String? = null
        for (k in keys) {
            val v = firstObj.opt(k)
            if (numericKey == null && v is Number) numericKey = k
            if (labelKey == null && v is String) labelKey = k
        }
        val numCol = numericKey ?: return null
        val points = mutableListOf<ChartPoint>()
        for (i in 0 until minOf(array.length(), 40)) {
            val row = array.optJSONObject(i) ?: continue
            val value = row.optDouble(numCol, Double.NaN)
            if (!value.isNaN()) {
                val label = if (labelKey != null) row.optString(labelKey, "#${i + 1}") else "#${i + 1}"
                points.add(ChartPoint(label.take(14), value))
            }
        }
        if (points.isEmpty()) return null
        val values = points.map { it.value }
        return DataAnalysisSummary(
            title = title,
            rowCount = array.length(),
            columnCount = keys.size,
            numericColumnName = numCol,
            minVal = values.min(),
            maxVal = values.max(),
            avgVal = values.average(),
            sumVal = values.sum(),
            points = points
        )
    }

    private fun parseCsvSummary(title: String, text: String): DataAnalysisSummary? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.size < 2) return null
        val delimiter = if (lines.first().contains('\t')) '\t' else if (lines.first().contains(';')) ';' else ','
        val headers = lines.first().split(delimiter).map { it.trim().trim('"') }
        if (headers.size < 1) return null

        val rows = lines.drop(1).map { line -> line.split(delimiter).map { it.trim().trim('"') } }
        var numericColIdx = -1
        for (col in headers.indices) {
            val parsedCount = rows.take(10).count { r -> r.getOrNull(col)?.toDoubleOrNull() != null }
            if (parsedCount >= max(1, minOf(rows.size, 3))) {
                numericColIdx = col
                break
            }
        }
        if (numericColIdx == -1) return null
        val labelColIdx = headers.indices.firstOrNull { it != numericColIdx } ?: numericColIdx

        val points = mutableListOf<ChartPoint>()
        for ((idx, row) in rows.take(40).withIndex()) {
            val num = row.getOrNull(numericColIdx)?.toDoubleOrNull() ?: continue
            val lbl = row.getOrNull(labelColIdx)?.takeIf { it.isNotBlank() } ?: "#${idx + 1}"
            points.add(ChartPoint(lbl.take(14), num))
        }
        if (points.isEmpty()) return null
        val values = points.map { it.value }
        return DataAnalysisSummary(
            title = title,
            rowCount = rows.size,
            columnCount = headers.size,
            numericColumnName = headers[numericColIdx],
            minVal = values.min(),
            maxVal = values.max(),
            avgVal = values.average(),
            sumVal = values.sum(),
            points = points
        )
    }

    private fun queryFileName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }

    private fun guessMimeFromName(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".pdf") -> "application/pdf"
            lower.endsWith(".csv") -> "text/csv"
            lower.endsWith(".json") -> "application/json"
            else -> "text/plain"
        }
    }
}
