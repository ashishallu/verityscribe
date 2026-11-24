package com.example.data

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.example.model.ConsultationRecord
import java.io.File
import java.io.FileOutputStream

object PrescriptionPdfExporter {

    fun exportPrescriptionPdf(context: Context, record: ConsultationRecord): Result<File> {
        return runCatching {
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            val titlePaint = Paint().apply {
                color = Color.rgb(33, 90, 224)
                textSize = 22f
                isFakeBoldText = true
            }
            val headerPaint = Paint().apply {
                color = Color.rgb(20, 24, 36)
                textSize = 15f
                isFakeBoldText = true
            }
            val bodyPaint = Paint().apply {
                color = Color.rgb(55, 65, 81)
                textSize = 13f
            }
            val mutedPaint = Paint().apply {
                color = Color.rgb(107, 114, 128)
                textSize = 11.5f
            }
            val linePaint = Paint().apply {
                color = Color.rgb(226, 229, 240)
                strokeWidth = 1.5f
            }

            var y = 64f
            canvas.drawText("Verity Clinical Prescription", 48f, y, titlePaint)
            y += 24f
            canvas.drawText("${record.doctorName} • ${record.hospital}", 48f, y, mutedPaint)
            y += 18f
            canvas.drawText("Consultation Date: ${record.dateLabel} at ${record.timeLabel}", 48f, y, mutedPaint)
            y += 20f
            canvas.drawLine(48f, y, 547f, y, linePaint)

            y += 32f
            canvas.drawText("Generated Prescription", 48f, y, headerPaint)
            y += 24f
            canvas.drawText("Patient: ${record.patientName}", 48f, y, bodyPaint)
            y += 20f
            canvas.drawText("Diagnosis: ${record.diagnosis}", 48f, y, bodyPaint)
            y += 28f

            canvas.drawText("Medications", 48f, y, headerPaint)
            y += 22f
            record.prescriptions.forEach { line ->
                canvas.drawText("• ${line.medicineName} — ${line.instruction}", 56f, y, bodyPaint)
                y += 20f
            }

            y += 16f
            canvas.drawText("Follow-up: ${record.followUpDate}", 48f, y, bodyPaint)
            y += 32f
            canvas.drawLine(48f, y, 547f, y, linePaint)
            y += 22f
            canvas.drawText("Status: ${record.status} • ${record.verifiedLabel}", 48f, y, mutedPaint)

            document.finishPage(page)

            val outputDir = File(context.filesDir, "prescriptions").apply { mkdirs() }
            val safeName = record.doctorName.replace(" ", "_").replace(".", "")
            val outputFile = File(outputDir, "prescription_${safeName}_${record.id}.pdf")
            FileOutputStream(outputFile).use { out ->
                document.writeTo(out)
            }
            document.close()
            outputFile
        }
    }
}
