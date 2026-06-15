package com.example.myapp.data.local



/**

 * Model MediaPipe (.task) pentru inferenta offline — Qwen2.5 0.5B (litert-community).

 */

data class OfflineLlmModelOption(

    val id: String,

    val displayName: String,

    val fileName: String,

    val downloadUrl: String,

    val minBytes: Long,

    val summary: String

)



object OfflineLlmModelCatalog {

    const val DEFAULT_MODEL_ID = "qwen25_05b"



    val qwen25HalfB = OfflineLlmModelOption(

        id = "qwen25_05b",

        displayName = "Qwen2.5 0.5B",

        fileName = "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",

        downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/" +

            "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",

        minBytes = 400_000_000L,

        summary = "Model offline rapid pentru subiecte si explicatii din PDF. ~520 MB."

    )



    val defaultModel: OfflineLlmModelOption get() = qwen25HalfB



    val all: List<OfflineLlmModelOption> = listOf(qwen25HalfB)



    /** Fisiere model vechi — sterse la migrare. */

    val legacyModelFiles: List<String> = listOf(

        "Phi-4-mini-instruct_multi-prefill-seq_q8_ekv1280.task"

    )



    fun byId(id: String): OfflineLlmModelOption =

        all.find { it.id == id } ?: defaultModel

}

