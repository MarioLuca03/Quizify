package com.example.myapp.utils

import java.text.Normalizer

/**
 * Curățare text românesc: repară mojibake și păstrează diacriticele (ă, â, î, ș, ț).
 * [toPlainAscii] rămâne doar dacă e nevoie explicit de ASCII.
 */
object RomanianAsciiNormalizer {

    /** Mojibake frecvent (UTF-8 citit ca Latin-1) → caractere românești corecte. */
    private val mojibakeToRomanian = listOf(
        "Äƒ" to "ă", "Ä‚" to "Ă", "ãƒ" to "ă", "Ã£" to "ă",
        "Ã¢" to "â", "Ã‚" to "Â", "ã¢" to "â",
        "Ã®" to "î", "ÃŽ" to "Î", "ã®" to "î",
        "È™" to "ș", "È›" to "ț", "È˜" to "Ș", "Èš" to "Ț",
        "ÅŸ" to "ș", "Å£" to "ț", "Åž" to "Ș", "Å¢" to "Ț",
        "Ä\u0083" to "ă", "Ä\u0082" to "Ă",
        "â€™" to "'", "â€œ" to "\"", "â€" to "\"",
        "Ã„" to "Ä", "Ã¶" to "ö", "Ã¼" to "ü"
    )

    /** Mojibake → ASCII (folosit doar de [toPlainAscii]). */
    private val mojibakeToAscii = listOf(
        "Äƒ" to "a", "Ä‚" to "A", "ãƒ" to "a", "Ã£" to "a",
        "Ã¢" to "a", "Ã‚" to "A", "ã¢" to "a",
        "Ã®" to "i", "ÃŽ" to "I", "ã®" to "i",
        "È™" to "s", "È›" to "t", "È˜" to "S", "Èš" to "T",
        "ÅŸ" to "s", "Å£" to "t", "Åž" to "S", "Å¢" to "T",
        "Ä\u0083" to "a", "Ä\u0082" to "A",
        "â€™" to "'", "â€œ" to "\"", "â€" to "\"",
        "Ã„" to "A", "Ã¶" to "o", "Ã¼" to "u",
        "hÎ" to "hi", "HÎ" to "Hi"
    )

    private val asciiMap = mapOf(
        'ă' to 'a', 'Ă' to 'A', 'â' to 'a', 'Â' to 'A',
        'î' to 'i', 'Î' to 'I', 'ï' to 'i', 'Ï' to 'I',
        'ș' to 's', 'Ș' to 'S', 'ş' to 's', 'Ş' to 'S',
        'ț' to 't', 'Ț' to 'T', 'ţ' to 't', 'Ţ' to 'T',
        'ä' to 'a', 'Ä' to 'A', 'à' to 'a', 'á' to 'a', 'ã' to 'a',
        'å' to 'a', 'æ' to 'a', 'ë' to 'e', 'è' to 'e', 'é' to 'e',
        'ê' to 'e', 'ö' to 'o', 'ò' to 'o', 'ó' to 'o', 'ô' to 'o',
        'õ' to 'o', 'ø' to 'o', 'ü' to 'u', 'ù' to 'u', 'ú' to 'u',
        'û' to 'u', 'ñ' to 'n', 'ç' to 'c', 'ß' to 's',
        'ð' to 'd', 'ý' to 'y', 'þ' to 't', 'œ' to 'o'
    )

    /** Repară encoding-ul și păstrează diacriticele românești. */
    fun fixRomanianText(text: String): String {
        var t = text
        for ((bad, good) in mojibakeToRomanian) {
            t = t.replace(bad, good, ignoreCase = false)
        }
        t = Normalizer.normalize(t, Normalizer.Form.NFC)
        return t
            .replace(Regex("[ \t]+"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    /** Text curat ASCII (fără diacritice). */
    fun toPlainAscii(text: String): String {
        var t = text
        for ((bad, good) in mojibakeToAscii) {
            t = t.replace(bad, good, ignoreCase = false)
        }
        t = Normalizer.normalize(t, Normalizer.Form.NFKD)
        t = t.replace(Regex("\\p{M}+"), "")
        val sb = StringBuilder(t.length)
        for (ch in t) {
            sb.append(mapCharToAscii(ch))
        }
        return sb.toString()
            .replace(Regex("[ \t]+"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    @Deprecated("Folosește fixRomanianText pentru afișare cu diacritice.", ReplaceWith("fixRomanianText(text)"))
    fun sanitizeForDisplay(text: String): String = fixRomanianText(text)

    private fun mapCharToAscii(ch: Char): Char {
        asciiMap[ch]?.let { return it }
        when {
            ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' -> return ch
            ch == '\n' || ch == '\r' || ch == '\t' -> return ch
            ch in " -.,;:!?()[]\"'/%+" -> return ch
            ch == '•' -> return '-'
            ch.code < 128 -> return ch
            else -> {
                val lower = ch.lowercaseChar()
                asciiMap[lower]?.let { mapped ->
                    return if (ch.isUpperCase()) mapped.uppercaseChar() else mapped
                }
                return ' '
            }
        }
    }
}
