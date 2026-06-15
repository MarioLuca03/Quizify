package com.example.myapp.data.local



import android.content.Context

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.withContext

import okhttp3.OkHttpClient

import okhttp3.Request

import java.io.File



class OfflineLlmModelRepository(context: Context) {

    private val appContext = context.applicationContext

    private val preferences = OfflineLlmPreferences(appContext)



    init {

        preferences.setSelectedModelId(OfflineLlmModelCatalog.DEFAULT_MODEL_ID)

        cleanupLegacyModels()

    }



    fun getSelectedModel(): OfflineLlmModelOption =

        OfflineLlmModelCatalog.byId(preferences.getSelectedModelId())



    fun setSelectedModel(modelId: String) {

        preferences.setSelectedModelId(modelId)

    }



    fun isModelReady(model: OfflineLlmModelOption = getSelectedModel()): Boolean {

        val file = modelFile(model)

        return file.exists() && file.length() >= model.minBytes

    }



    fun getModelPath(model: OfflineLlmModelOption = getSelectedModel()): String =

        modelFile(model).absolutePath



    suspend fun downloadModel(

        model: OfflineLlmModelOption = getSelectedModel(),

        onProgress: (Float) -> Unit

    ): Result<Unit> = withContext(Dispatchers.IO) {

        val modelFile = modelFile(model)

        val partFile = File(appContext.filesDir, "${model.fileName}.part")

        suspend fun reportProgress(value: Float) {

            withContext(Dispatchers.Main.immediate) {

                onProgress(value.coerceIn(0f, 1f))

            }

        }

        try {

            reportProgress(0.02f)

            if (partFile.exists()) partFile.delete()

            val client = OkHttpClient.Builder().build()

            val request = Request.Builder().url(model.downloadUrl).build()

            client.newCall(request).execute().use { response ->

                if (!response.isSuccessful) {

                    return@withContext Result.failure(

                        Exception("Descarcare esuata (HTTP ${response.code}). Verifica internetul.")

                    )

                }

                val body = response.body

                    ?: return@withContext Result.failure(Exception("Raspuns gol de la server."))

                val headerBytes = body.contentLength()

                val estimatedTotal = if (headerBytes > 0L) headerBytes else model.minBytes

                var lastReported = 0f

                body.byteStream().use { input ->

                    partFile.outputStream().use { output ->

                        val buffer = ByteArray(64 * 1024)

                        var downloaded = 0L

                        var read: Int

                        while (input.read(buffer).also { read = it } != -1) {

                            output.write(buffer, 0, read)

                            downloaded += read

                            val fraction = (downloaded.toFloat() / estimatedTotal).coerceIn(0.02f, 0.99f)

                            if (fraction - lastReported >= 0.005f || downloaded == read.toLong()) {

                                lastReported = fraction

                                reportProgress(fraction)

                            }

                        }

                    }

                }

            }

            if (!partFile.exists() || partFile.length() < model.minBytes) {

                partFile.delete()

                return@withContext Result.failure(

                    Exception("Fisierul model pare incomplet. Incearca din nou pe Wi-Fi.")

                )

            }

            if (modelFile.exists()) modelFile.delete()

            if (!partFile.renameTo(modelFile)) {

                partFile.copyTo(modelFile, overwrite = true)

                partFile.delete()

            }

            reportProgress(1f)

            Result.success(Unit)

        } catch (e: Exception) {

            partFile.delete()

            Result.failure(e)

        }

    }



    private fun cleanupLegacyModels() {

        val filesDir = appContext.filesDir

        for (legacyName in OfflineLlmModelCatalog.legacyModelFiles) {

            File(filesDir, legacyName).delete()

            File(filesDir, "$legacyName.part").delete()

        }

    }



    private fun modelFile(model: OfflineLlmModelOption): File =

        File(appContext.filesDir, model.fileName)

}

