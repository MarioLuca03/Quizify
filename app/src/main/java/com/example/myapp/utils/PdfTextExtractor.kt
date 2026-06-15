package com.example.myapp.utils

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.example.myapp.data.model.PageNormalizedText
import com.example.myapp.data.model.PdfExtractionDiagnostics
import com.example.myapp.data.model.PdfPageTextStatus
import com.example.myapp.data.model.PerPageSmartPdfExtraction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfTextExtractor {

    /** Prag minim de caractere pe pagină pentru a considera că există text selectabil (fără OCR). */
    private const val MIN_CHARS_SELECTABLE_PAGE = 18

    /** Limită de siguranță pentru textul total extras (memorie / timp). */
    private const val MAX_EXTRACTED_CHARS = 400_000
    @Volatile
    private var isInitialized = false
    
    private fun initPdfBox(context: Context) {
        if (!isInitialized) {
            synchronized(this) {
                if (!isInitialized) {
                    PDFBoxResourceLoader.init(context)
                    isInitialized = true
                }
            }
        }
    }
    
    suspend fun extractText(context: Context, uri: Uri, maxLength: Int = Int.MAX_VALUE): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                initPdfBox(context)
                
                val pdfFile = if (uri.scheme == "file") {
                    File(uri.path ?: return@withContext Result.failure(Exception("Invalid file URI")))
                } else {
                    val tempFile = File(context.cacheDir, "temp_pdf_${System.currentTimeMillis()}.pdf")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    } ?: return@withContext Result.failure(Exception("Nu s-a putut deschide PDF-ul"))
                    tempFile
                }
                
                val document = PDDocument.load(pdfFile)
                val stripper = PDFTextStripper()
                stripper.setStartPage(1)
                stripper.setEndPage(document.numberOfPages)
                val text = stripper.getText(document)
                document.close()
                
                if (text.isBlank()) {
                    return@withContext Result.failure(Exception("PDF-ul nu conține text extras. PDF-ul poate fi scanat (imagine) sau nu are text selectabil."))
                }
                
                val cleanedText = text.trim().replace(Regex("\\s+"), " ")
                val finalText = if (cleanedText.length > maxLength) {
                    cleanedText.substring(0, maxLength) + "..."
                } else {
                    cleanedText
                }
                
                if (finalText.isBlank()) {
                    return@withContext Result.failure(Exception("Textul extras este gol sau conține doar spații."))
                }
                
                Result.success(finalText)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /** Număr de pagini fără extragere completă de text (rapid, pentru UI). */
    suspend fun getPdfPageCount(context: Context, uri: Uri): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                initPdfBox(context)
                if (uri.scheme == "file") {
                    val f = File(uri.path ?: return@withContext Result.failure(Exception("URI fișier invalid.")))
                    if (!f.exists()) return@withContext Result.failure(Exception("Fișierul PDF nu există."))
                    PDDocument.load(f).use { doc ->
                        val n = doc.numberOfPages
                        if (n <= 0) return@withContext Result.failure(Exception("PDF fără pagini."))
                        return@withContext Result.success(n)
                    }
                }
                val tempFile = File(context.cacheDir, "pdf_count_${System.currentTimeMillis()}.pdf")
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                    } ?: return@withContext Result.failure(Exception("Nu s-a putut deschide PDF-ul."))
                    PDDocument.load(tempFile).use { doc ->
                        val n = doc.numberOfPages
                        if (n <= 0) return@withContext Result.failure(Exception("PDF fără pagini."))
                        return@withContext Result.success(n)
                    }
                } finally {
                    tempFile.delete()
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Extrage text pagină cu pagină, detectează paginile fără text selectabil (OCR necesar — neimplementat),
     * concatenează și normalizează textul pentru sumarizare.
     */
    suspend fun extractWithDiagnostics(
        context: Context,
        uri: Uri
    ): Result<PdfExtractionDiagnostics> {
        return withContext(Dispatchers.IO) {
            try {
                initPdfBox(context)
                val pdfFile = if (uri.scheme == "file") {
                    File(uri.path ?: return@withContext Result.failure(Exception("URI fișier invalid.")))
                } else {
                    val tempFile = File(context.cacheDir, "temp_pdf_diag_${System.currentTimeMillis()}.pdf")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                    } ?: return@withContext Result.failure(Exception("Nu s-a putut deschide PDF-ul."))
                    tempFile
                }

                val document = PDDocument.load(pdfFile)
                val totalPages = document.numberOfPages
                if (totalPages <= 0) {
                    document.close()
                    return@withContext Result.failure(Exception("PDF fără pagini."))
                }

                val stripper = PDFTextStripper()
                val pageStatuses = mutableListOf<PdfPageTextStatus>()
                val joined = StringBuilder()

                for (p in 1..totalPages) {
                    stripper.startPage = p
                    stripper.endPage = p
                    val pageRaw = stripper.getText(document).trim()
                    val len = pageRaw.length
                    val needsOcr = len < MIN_CHARS_SELECTABLE_PAGE
                    pageStatuses.add(PdfPageTextStatus(pageNumber = p, approxChars = len, needsOcr = needsOcr))
                    if (pageRaw.isNotBlank()) {
                        if (joined.isNotEmpty()) joined.append("\n\n")
                        joined.append(pageRaw)
                    }
                }
                document.close()

                val rawJoined = joined.toString()
                if (rawJoined.isBlank()) {
                    return@withContext Result.failure(
                        Exception("PDF fără text selectabil pe nicio pagină. Probabil scanat (imagini) — OCR nu este disponibil în aplicație.")
                    )
                }

                val normalized = PdfTextNormalizer.normalizeExtractedPdfText(rawJoined)
                val capped = if (normalized.length > MAX_EXTRACTED_CHARS) {
                    normalized.take(MAX_EXTRACTED_CHARS) + "\n\n[... text trunchiat la ${MAX_EXTRACTED_CHARS} caractere pentru limite de performanță ...]"
                } else {
                    normalized
                }

                Result.success(
                    PdfExtractionDiagnostics(
                        normalizedText = capped,
                        totalPages = totalPages,
                        pageStatuses = pageStatuses
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Extrage text normalizat din **toate** paginile, calculează SHA-256 pe conținutul binar al PDF-ului
     * (fără OCR). Pentru `content://` se copiază într-un fișier temporar; pentru `file://` se citește direct.
     */
    suspend fun extractPerPageForSmartSummary(
        context: Context,
        uri: Uri
    ): Result<PerPageSmartPdfExtraction> {
        return withContext(Dispatchers.IO) {
            try {
                initPdfBox(context)
                if (uri.scheme == "file") {
                    val f = File(uri.path ?: return@withContext Result.failure(Exception("URI fișier invalid.")))
                    if (!f.exists()) {
                        return@withContext Result.failure(Exception("Fișierul PDF nu există."))
                    }
                    val sha = PdfContentHasher.sha256HexOfFile(f)
                    PDDocument.load(f).use { document ->
                        return@withContext extractPerPageFromLoadedDocument(document, sha)
                    }
                } else {
                    val tempFile = File(context.cacheDir, "pdf_smart_${System.currentTimeMillis()}.pdf")
                    try {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                        } ?: return@withContext Result.failure(Exception("Nu s-a putut deschide PDF-ul."))
                        val sha = PdfContentHasher.sha256HexOfFile(tempFile)
                        PDDocument.load(tempFile).use { document ->
                            return@withContext extractPerPageFromLoadedDocument(document, sha)
                        }
                    } finally {
                        tempFile.delete()
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun extractPerPageFromLoadedDocument(
        document: PDDocument,
        pdfContentSha256: String
    ): Result<PerPageSmartPdfExtraction> {
        val total = document.numberOfPages
        if (total <= 0) {
            return Result.failure(Exception("PDF fără pagini."))
        }
        val stripper = PDFTextStripper()
        val pages = mutableListOf<PageNormalizedText>()
        val statuses = mutableListOf<PdfPageTextStatus>()
        for (p in 1..total) {
            stripper.startPage = p
            stripper.endPage = p
            val raw = stripper.getText(document).trim()
            val norm = PdfTextNormalizer.normalizeExtractedPdfText(raw)
            val needsOcr = raw.length < MIN_CHARS_SELECTABLE_PAGE
            statuses.add(PdfPageTextStatus(pageNumber = p, approxChars = raw.length, needsOcr = needsOcr))
            pages.add(PageNormalizedText(pageNumber = p, normalizedText = norm))
        }
        if (pages.none { it.normalizedText.isNotBlank() }) {
            return Result.failure(
                Exception("PDF fără text selectabil pe nicio pagină — OCR nu este implementat.")
            )
        }
        return Result.success(
            PerPageSmartPdfExtraction(
                pdfContentSha256 = pdfContentSha256,
                totalPages = total,
                pages = pages,
                pageStatuses = statuses
            )
        )
    }

    fun splitIntoChunks(text: String, minChars: Int = 2000, maxChars: Int = 3000): List<String> {
        val chunks = mutableListOf<String>()
        var currentIndex = 0
        
        while (currentIndex < text.length) {
            val remainingText = text.substring(currentIndex)
            
            if (remainingText.length <= maxChars) {
                if (remainingText.trim().isNotEmpty()) {
                    chunks.add(remainingText.trim())
                }
                break
            }
            
            val targetEndIndex = (currentIndex + maxChars).coerceAtMost(text.length)
            var endIndex = targetEndIndex
            
            if (endIndex < text.length) {
                val sentenceEndIndex = text.indexOfAny(charArrayOf('.', '!', '?'), currentIndex + minChars)
                if (sentenceEndIndex in (currentIndex + minChars)..targetEndIndex) {
                    endIndex = sentenceEndIndex + 1
                } else {
                    val lastSpaceIndex = text.lastIndexOf(' ', targetEndIndex)
                    val lastNewlineIndex = text.lastIndexOf('\n', targetEndIndex)
                    val boundaryIndex = maxOf(lastSpaceIndex, lastNewlineIndex)
                    
                    if (boundaryIndex > currentIndex + minChars) {
                        endIndex = boundaryIndex + 1
                    }
                }
            }
            
            val chunk = text.substring(currentIndex, endIndex).trim()
            if (chunk.isNotEmpty()) {
                chunks.add(chunk)
            }
            
            currentIndex = endIndex
        }
        
        return chunks
    }
}



