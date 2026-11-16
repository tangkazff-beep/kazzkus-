package com.example.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.data.model.ChatMessage
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DocumentExportManager {

    fun buildMarkdownDocument(title: String, messages: List<ChatMessage>): String {
        val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
        return buildString {
            appendLine("# $title")
            appendLine("_Diekspor dari Karsa AI • ${dateStr}_")
            appendLine()
            appendLine("---")
            appendLine()
            messages.forEach { msg ->
                val sender = if (msg.role == "model") "**Karsa AI**" else "**Pengguna**"
                appendLine("### $sender")
                if (!msg.attachmentName.isNullOrBlank()) {
                    appendLine("> Lampiran: `${msg.attachmentName}` (${msg.attachmentType ?: "file"})")
                    appendLine()
                }
                appendLine(msg.text)
                appendLine()
                appendLine("---")
                appendLine()
            }
        }
    }

    fun buildHtmlDocument(title: String, messages: List<ChatMessage>): String {
        val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
        fun escapeHtml(raw: String): String = raw
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\n", "<br/>")

        val body = messages.joinToString("\n") { msg ->
            val roleLabel = if (msg.role == "model") "Karsa AI" else "Pengguna"
            val cls = if (msg.role == "model") "model" else "user"
            val attachHtml = if (!msg.attachmentName.isNullOrBlank()) {
                "<div class='attach'>Lampiran: ${escapeHtml(msg.attachmentName)}</div>"
            } else ""
            """
            <div class='msg $cls'>
              <div class='role'>$roleLabel</div>
              $attachHtml
              <div class='content'>${escapeHtml(msg.text)}</div>
            </div>
            """.trimIndent()
        }
        return """
        <!DOCTYPE html>
        <html lang="id">
        <head>
          <meta charset="utf-8"/>
          <title>${escapeHtml(title)}</title>
          <style>
            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; max-width: 820px; margin: 32px auto; padding: 0 20px; color: #0f172a; background: #f8fafc; line-height: 1.6; }
            header { border-bottom: 2px solid #0891b2; padding-bottom: 14px; margin-bottom: 24px; }
            h1 { margin: 0; font-size: 24px; color: #0f172a; }
            .meta { color: #64748b; font-size: 13px; margin-top: 4px; }
            .msg { background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; padding: 16px 20px; margin-bottom: 16px; }
            .msg.user { border-left: 4px solid #f59e0b; }
            .msg.model { border-left: 4px solid #0891b2; }
            .role { font-weight: 700; font-size: 13px; text-transform: uppercase; letter-spacing: 0.05em; color: #0891b2; margin-bottom: 8px; }
            .msg.user .role { color: #d97706; }
            .attach { font-size: 12px; color: #475569; background: #f1f5f9; display: inline-block; padding: 3px 8px; border-radius: 6px; margin-bottom: 8px; }
          </style>
        </head>
        <body>
          <header>
            <h1>${escapeHtml(title)}</h1>
            <div class="meta">Laporan Obrolan Karsa AI &bull; $dateStr</div>
          </header>
          $body
        </body>
        </html>
        """.trimIndent()
    }

    fun writePdfToStream(title: String, messages: List<ChatMessage>, outputStream: OutputStream) {
        val pdfDocument = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 44
        val contentWidth = pageWidth - margin * 2
        val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 10f
        }
        val roleModelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0891B2")
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val roleUserPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D97706")
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            textSize = 11f
        }
        val dividerPaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 1f
        }

        var pageNumber = 1
        var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var yPos = margin.toFloat()

        fun startNewPage() {
            canvas.drawText("Halaman $pageNumber", (pageWidth - margin - 60).toFloat(), (pageHeight - 20).toFloat(), metaPaint)
            pdfDocument.finishPage(page)
            pageNumber++
            page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            yPos = margin.toFloat()
        }

        // Document Header
        val titleLayout = StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, contentWidth).build()
        canvas.save()
        canvas.translate(margin.toFloat(), yPos)
        titleLayout.draw(canvas)
        canvas.restore()
        yPos += titleLayout.height + 6f

        canvas.drawText("Karsa AI • Diekspor pada $dateStr", margin.toFloat(), yPos + 10f, metaPaint)
        yPos += 22f
        canvas.drawLine(margin.toFloat(), yPos, (pageWidth - margin).toFloat(), yPos, dividerPaint)
        yPos += 18f

        for (msg in messages) {
            val isModel = msg.role == "model"
            val headerLabel = if (isModel) "KARSA AI" else "PENGGUNA"
            if (yPos > pageHeight - margin - 60) startNewPage()

            canvas.drawText(headerLabel, margin.toFloat(), yPos + 10f, if (isModel) roleModelPaint else roleUserPaint)
            yPos += 16f

            if (!msg.attachmentName.isNullOrBlank()) {
                canvas.drawText("Lampiran: ${msg.attachmentName}", margin.toFloat(), yPos + 10f, metaPaint)
                yPos += 14f
            }

            val paragraphs = msg.text.ifBlank { "(kosong)" }.split("\n")
            for (para in paragraphs) {
                val safePara = para.ifEmpty { " " }
                val layout = StaticLayout.Builder.obtain(safePara, 0, safePara.length, bodyPaint, contentWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(2f, 1.15f)
                    .build()
                if (yPos + layout.height > pageHeight - margin - 24) {
                    startNewPage()
                }
                canvas.save()
                canvas.translate(margin.toFloat(), yPos)
                layout.draw(canvas)
                canvas.restore()
                yPos += layout.height + 4f
            }

            yPos += 8f
            if (yPos < pageHeight - margin - 20) {
                canvas.drawLine(margin.toFloat(), yPos, (pageWidth - margin).toFloat(), yPos, dividerPaint)
                yPos += 14f
            }
        }

        canvas.drawText("Halaman $pageNumber", (pageWidth - margin - 60).toFloat(), (pageHeight - 20).toFloat(), metaPaint)
        pdfDocument.finishPage(page)
        pdfDocument.writeTo(outputStream)
        pdfDocument.close()
    }

    fun shareFileViaFileProvider(
        context: Context,
        fileName: String,
        mimeType: String,
        writer: (OutputStream) -> Unit
    ) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { writer(it) }
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Bagikan Dokumen"))
    }

    fun shareBitmap(context: Context, bitmap: Bitmap) {
        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        val file = File(dir, "karsa_image_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Bagikan Gambar"))
    }
}
