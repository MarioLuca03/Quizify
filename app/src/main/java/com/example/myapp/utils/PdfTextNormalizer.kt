package com.example.myapp.utils

/**
 * Curățare text extras din PDF pentru sumarizare:
 * spații excesive, marcaje de pagină repetitive, linii evident goale / numerotare pagină.
 */
object PdfTextNormalizer {

    private val repetitivePageLine = Regex(
        """(?i)^\s*(pag\.?\s*\d+(\s*/\s*\d+)?|page\s+\d+\s+of\s+\d+|-\s*\d+\s*-|\d+\s*/\s*\d+)\s*$"""
    )

    fun normalizeExtractedPdfText(raw: String): String {
        var t = raw.replace('\r', '\n').trim()
        t = t.replace(Regex("[ \t]+"), " ")
        t = t.replace(Regex("\n{3,}"), "\n\n")
        val lines = t.lines()
            .map { it.trimEnd() }
            .filter { line ->
                if (line.isBlank()) return@filter true
                !repetitivePageLine.matches(line)
            }
        t = lines.joinToString("\n").trim()
        t = t.replace(Regex(" *\n *"), "\n")
        t = t.replace(Regex("\n{3,}"), "\n\n")
        return t.trim()
    }
}
