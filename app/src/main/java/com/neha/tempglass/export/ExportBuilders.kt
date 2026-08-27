package com.neha.tempglass.export

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ExportFormat(
    val fileExtension: String,
    val mimeType: String
) {
    CSV(fileExtension = "csv", mimeType = "text/csv"),
    PDF(fileExtension = "pdf", mimeType = "application/pdf")
}

data class ExportFilterRow(
    val filterName: String,
    val count: Int
)

data class ExportModelRow(
    val modelName: String,
    val brand: String,
    val boxNumber: Int,
    val type: String,
    val isUserAdded: Boolean,
    val isDeleted: Boolean,
    val isFavorite: Boolean,
    val additionalCompatible: List<String>
)

data class ExportPayload(
    val exportedAtMillis: Long,
    val filters: List<ExportFilterRow>,
    val models: List<ExportModelRow>
)

fun buildExportCsvBytes(payload: ExportPayload): ByteArray {
    val builder = StringBuilder()

    builder.appendLine("Exported At,${escapeCsv(formatExportDate(payload.exportedAtMillis))}")
    builder.appendLine()

    builder.appendLine("Filters")
    builder.appendLine("Filter,Count")
    payload.filters.forEach { filterRow ->
        builder.appendLine("${escapeCsv(filterRow.filterName)},${filterRow.count}")
    }

    builder.appendLine()
    builder.appendLine("Models")
    builder.appendLine("Model Name,Brand,Box Number,Type,User Added,Deleted,Favorite,Additional Compatible")

    payload.models.forEach { model ->
        val compatible = model.additionalCompatible.joinToString(" | ")
        builder.appendLine(
            listOf(
                escapeCsv(model.modelName),
                escapeCsv(model.brand),
                model.boxNumber.toString(),
                escapeCsv(model.type),
                if (model.isUserAdded) "Yes" else "No",
                if (model.isDeleted) "Yes" else "No",
                if (model.isFavorite) "Yes" else "No",
                escapeCsv(compatible)
            ).joinToString(",")
        )
    }

    return builder.toString().toByteArray(Charsets.UTF_8)
}

fun buildExportPdfBytes(payload: ExportPayload): ByteArray {
    val document = PdfDocument()
    val outputStream = ByteArrayOutputStream()

    val pageWidth = 595
    val pageHeight = 842
    val margin = 30f
    val contentWidth = pageWidth - margin * 2
    val lineHeight = 14f

    val titlePaint = Paint().apply {
        textSize = 16f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    val sectionPaint = Paint().apply {
        textSize = 12f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    val textPaint = Paint().apply {
        textSize = 10f
        typeface = Typeface.DEFAULT
    }

    var pageNumber = 1
    var page = document.startPage(
        PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
    )
    var canvas = page.canvas
    var y = margin

    fun startNewPage() {
        document.finishPage(page)
        pageNumber += 1
        page = document.startPage(
            PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        )
        canvas = page.canvas
        y = margin
    }

    fun ensureSpace(linesNeeded: Int = 1) {
        if (y + (lineHeight * linesNeeded) > pageHeight - margin) {
            startNewPage()
        }
    }

    fun drawWrappedText(text: String, paint: Paint) {
        val maxChars = ((contentWidth / (paint.textSize * 0.55f)).toInt()).coerceAtLeast(20)
        wrapText(text, maxChars).forEach { line ->
            ensureSpace()
            canvas.drawText(line, margin, y, paint)
            y += lineHeight
        }
    }

    drawWrappedText("TempGlass Export", titlePaint)
    drawWrappedText("Exported At: ${formatExportDate(payload.exportedAtMillis)}", textPaint)
    y += lineHeight * 0.5f

    drawWrappedText("Filters", sectionPaint)
    payload.filters.forEach { filter ->
        drawWrappedText("• ${filter.filterName}: ${filter.count}", textPaint)
    }

    y += lineHeight * 0.5f
    drawWrappedText("Models (${payload.models.size})", sectionPaint)

    payload.models.forEachIndexed { index, model ->
        drawWrappedText(
            "${index + 1}. ${model.modelName} | Brand: ${model.brand} | Box: ${model.boxNumber} | Type: ${model.type} | User Added: ${if (model.isUserAdded) "Yes" else "No"} | Deleted: ${if (model.isDeleted) "Yes" else "No"} | Favorite: ${if (model.isFavorite) "Yes" else "No"}",
            textPaint
        )

        if (model.additionalCompatible.isNotEmpty()) {
            drawWrappedText("   Compatible: ${model.additionalCompatible.joinToString(", ")}", textPaint)
        }

        y += lineHeight * 0.3f
    }

    document.finishPage(page)
    document.writeTo(outputStream)
    document.close()

    return outputStream.toByteArray()
}

private fun escapeCsv(value: String): String {
    if (value.none { it == ',' || it == '"' || it == '\n' || it == '\r' }) return value
    return "\"${value.replace("\"", "\"\"")}\""
}

private fun wrapText(text: String, maxCharsPerLine: Int): List<String> {
    if (text.length <= maxCharsPerLine) return listOf(text)

    val words = text.split(" ")
    val lines = mutableListOf<String>()
    var current = StringBuilder()

    words.forEach { word ->
        val candidate = if (current.isEmpty()) word else "$current $word"
        if (candidate.length <= maxCharsPerLine) {
            if (current.isNotEmpty()) current.append(' ')
            current.append(word)
        } else {
            if (current.isNotEmpty()) {
                lines += current.toString()
                current = StringBuilder(word)
            } else {
                lines += word.chunked(maxCharsPerLine)
            }
        }
    }

    if (current.isNotEmpty()) lines += current.toString()

    return lines
}

private fun formatExportDate(epochMillis: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    return formatter.format(Date(epochMillis))
}