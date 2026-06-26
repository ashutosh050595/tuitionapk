package com.example.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.example.data.AppConfig
import com.example.data.FeeTransaction
import com.example.data.Student
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object ReceiptPdfHelper {

    fun generateAndSaveReceiptPdf(
        context: Context,
        student: Student,
        transaction: FeeTransaction,
        config: AppConfig,
        remainingDues: Double
    ): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size: 595 x 842 pt
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint()
        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
            isAntiAlias = true
        }

        // --- DRAWING HEADER ---
        // Draw Banner Background Accent
        paint.color = Color.parseColor("#6750A4") // M3 Primary Color
        canvas.drawRect(30f, 30f, 565f, 90f, paint)

        // Tuition Name (Header)
        textPaint.apply {
            color = Color.WHITE
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(config.tuitionName.uppercase(), 50f, 65f, textPaint)

        // Tagline / Tutor Name
        textPaint.apply {
            color = Color.argb(200, 255, 255, 255)
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText("Tutor: ${config.tutorName}  |  Contact: ${config.phone}", 50f, 80f, textPaint)

        // Reset Text Paint to Dark Neutral
        textPaint.color = Color.BLACK

        // --- RECEIPT INFO ---
        textPaint.apply {
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("OFFICIAL PAYMENT RECEIPT", 30f, 130f, textPaint)

        textPaint.apply {
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText("Receipt No: ${transaction.receiptNumber}", 30f, 155f, textPaint)
        canvas.drawText("Date: ${transaction.paymentDate}", 30f, 170f, textPaint)
        canvas.drawText("Payment Mode: ${transaction.paymentMode.uppercase()}", 30f, 185f, textPaint)
        if (!transaction.transactionRef.isNullOrBlank()) {
            canvas.drawText("Ref No: ${transaction.transactionRef}", 30f, 200f, textPaint)
        }

        // --- STUDENT DETAILS ---
        val rightX = 320f
        textPaint.apply {
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("BILLED TO:", rightX, 130f, textPaint)

        textPaint.apply {
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText("Student Name: ${student.name}", rightX, 145f, textPaint)
        canvas.drawText("Class: ${student.className}", rightX, 160f, textPaint)
        canvas.drawText("Parent's Email: ${student.parentEmail}", rightX, 175f, textPaint)
        canvas.drawText("Parent's Phone: ${student.phone}", rightX, 190f, textPaint)

        // Draw Divider
        paint.color = Color.LTGRAY
        canvas.drawLine(30f, 220f, 565f, 220f, paint)

        // --- TABLE HEADERS ---
        var currentY = 250f
        paint.color = Color.parseColor("#F3EDF7") // Light primary container bg
        canvas.drawRect(30f, currentY - 15f, 565f, currentY + 10f, paint)

        textPaint.apply {
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.parseColor("#6750A4")
        }
        canvas.drawText("SI NO.", 45f, currentY, textPaint)
        canvas.drawText("PARTICULARS / DESCRIPTION", 100f, currentY, textPaint)
        canvas.drawText("AMOUNT (INR)", 450f, currentY, textPaint)

        // --- TABLE ITEMS ---
        currentY = 290f
        textPaint.apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText("1", 45f, currentY, textPaint)
        
        val monthsStr = if (transaction.monthsPaid.isNotEmpty()) {
            transaction.monthsPaid.joinToString(", ")
        } else {
            "Custom Fee / Partial Tuition Payment"
        }
        canvas.drawText("Tuition Fee for: $monthsStr", 100f, currentY, textPaint)
        
        textPaint.apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(String.format("Rs. %,.2f", transaction.amount), 450f, currentY, textPaint)

        // Draw Row Line Divider
        paint.color = Color.parseColor("#E0E0E0")
        canvas.drawLine(30f, currentY + 15f, 565f, currentY + 15f, paint)

        // --- SUMMARY BILLING SECTION ---
        currentY = 340f
        textPaint.apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 11f
        }
        canvas.drawText("TOTAL AMOUNT PAID:", 300f, currentY, textPaint)
        textPaint.apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.parseColor("#2E7D32") // Green accent
        }
        canvas.drawText(String.format("Rs. %,.2f", transaction.amount), 450f, currentY, textPaint)

        currentY += 20f
        textPaint.apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            color = Color.BLACK
        }
        canvas.drawText("REMAINING OUTSTANDING DUES:", 300f, currentY, textPaint)
        textPaint.apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = if (remainingDues > 0) Color.RED else Color.BLACK
        }
        canvas.drawText(String.format("Rs. %,.2f", remainingDues), 450f, currentY, textPaint)

        // Draw Divider
        paint.color = Color.LTGRAY
        canvas.drawLine(30f, currentY + 30f, 565f, currentY + 30f, paint)

        // --- FOOTER NOTE ---
        currentY += 60f
        textPaint.apply {
            color = Color.GRAY
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        }
        canvas.drawText("Thank you for choosing ${config.tuitionName}! This is a computer-generated official receipt.", 50f, currentY, textPaint)
        canvas.drawText("For any queries, please email us at ${config.email}.", 50f, currentY + 15f, textPaint)

        pdfDocument.finishPage(page)

        // Save PDF to Downloads
        val fileName = "Receipt_${transaction.receiptNumber}.pdf"
        var pdfFile: File? = null
        var outputStream: OutputStream? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // MediaStore approach for Android Q+
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    outputStream = resolver.openOutputStream(uri)
                    pdfFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
                }
            } else {
                // Direct file system access for older SDKs
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                pdfFile = File(downloadsDir, fileName)
                outputStream = FileOutputStream(pdfFile)
            }

            if (outputStream != null) {
                pdfDocument.writeTo(outputStream)
                Toast.makeText(context, "Receipt PDF downloaded to Downloads folder", Toast.LENGTH_LONG).show()
            } else {
                throw Exception("Failed to open output stream")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error saving receipt PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        } finally {
            try {
                outputStream?.close()
            } catch (e: Exception) {}
            pdfDocument.close()
        }

        return pdfFile
    }
}
