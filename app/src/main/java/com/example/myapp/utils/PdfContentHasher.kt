package com.example.myapp.utils

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object PdfContentHasher {

    /**
     * SHA-256 hex pe **conținutul binar** al PDF-ului (același fișier = același hash),
     * pentru cache-ul de rezumat.
     */
    fun sha256HexOfPdf(context: Context, uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        if (uri.scheme == "file") {
            val path = uri.path ?: throw Exception("URI fișier invalid.")
            hashFileToDigest(File(path), digest)
        } else {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buf = ByteArray(16_384)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    digest.update(buf, 0, n)
                }
            } ?: throw Exception("Nu s-a putut deschide PDF-ul pentru hash.")
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun sha256HexOfFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        hashFileToDigest(file, digest)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun hashFileToDigest(file: File, digest: MessageDigest) {
        FileInputStream(file).use { input ->
            val buf = ByteArray(16_384)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
    }
}
