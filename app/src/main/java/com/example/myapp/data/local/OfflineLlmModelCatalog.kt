package com.example.myapp.data.local

import java.io.File

/**
 * Modele MediaPipe (.task) pentru inferenta offline — variante litert-community.
 */
data class OfflineLlmModelOption(
    val id: String,
    val displayName: String,
    val fileName: String,
    val downloadUrl: String,
    val minBytes: Long,
    val sizeLabel: String,
    val summary: String
)

object OfflineLlmModelCatalog {

    const val DEFAULT_MODEL_ID = "qwen25_15b"

    val qwen25OneFiveB = OfflineLlmModelOption(
        id = "qwen25_15b",
        displayName = "Qwen2.5 1.5B",
        fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/" +
            "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        minBytes = 1_450_000_000L,
        sizeLabel = "~1.6 GB",
        summary = "Model offline pentru intrebari din PDF in romana."
    )

    val defaultModel: OfflineLlmModelOption get() = qwen25OneFiveB

    val all: List<OfflineLlmModelOption> = listOf(qwen25OneFiveB)

    /** Fisiere model vechi — sterse la migrare. */
    val legacyModelFiles: List<String> = listOf(
        "Phi-4-mini-instruct_multi-prefill-seq_q8_ekv1280.task",
        "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"
    )

    fun byId(id: String): OfflineLlmModelOption =
        all.find { it.id == id } ?: defaultModel

    fun minBytesForPath(modelPath: String): Long {
        val name = File(modelPath).name
        return all.find { it.fileName == name }?.minBytes ?: defaultModel.minBytes
    }
}
