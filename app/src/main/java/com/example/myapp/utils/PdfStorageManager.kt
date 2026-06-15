package com.example.myapp.utils

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object PdfStorageManager {
    private const val PDF_DIR_NAME = "pdfs"

    fun getPdfDirectory(context: Context): File {
        val pdfDir = File(context.filesDir, PDF_DIR_NAME)
        if (!pdfDir.exists()) {
            pdfDir.mkdirs()
        }
        return pdfDir
    }
    

    suspend fun savePdf(context: Context, uri: Uri, fileName: String? = null): Result<File> {
        return withContext(Dispatchers.IO) {
            try {
                val pdfDir = getPdfDirectory(context)
                val finalFileName = fileName ?: "pdf_${System.currentTimeMillis()}.pdf"
                val file = File(pdfDir, finalFileName)
                
                var uniqueFile = file
                var counter = 1
                while (uniqueFile.exists()) {
                    val nameWithoutExt = finalFileName.substringBeforeLast(".")
                    val ext = finalFileName.substringAfterLast(".", "")
                    uniqueFile = File(pdfDir, "${nameWithoutExt}_$counter.$ext")
                    counter++
                }
                
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(uniqueFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: return@withContext Result.failure(IOException("Nu s-a putut deschide PDF-ul"))
                
                Result.success(uniqueFile)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    

    fun getPdfFile(context: Context, fileName: String): File? {
        val pdfDir = getPdfDirectory(context)
        val file = File(pdfDir, fileName)
        return if (file.exists()) file else null
    }

    fun getAllPdfFiles(context: Context): List<File> {
        val pdfDir = getPdfDirectory(context)
        return pdfDir.listFiles()?.filter { it.isFile && it.name.endsWith(".pdf", ignoreCase = true) }?.sortedBy { it.name } ?: emptyList()
    }

    suspend fun deletePdf(file: File): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                if (file.exists() && file.delete()) {
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("Nu s-a putut șterge fișierul"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    fun getFileName(file: File): String {
        return file.name
    }
}

