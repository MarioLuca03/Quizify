package com.example.myapp.data.service



import android.content.Context

import com.example.myapp.data.local.OfflineLlmModelCatalog

import com.example.myapp.data.local.OfflineLlmModelConfig

import com.example.myapp.data.model.AnswerEvaluation

import com.example.myapp.data.model.PageQuestionResult

import com.example.myapp.utils.LocalLlmJsonParser

import com.example.myapp.utils.LocalLlmPromptGuard

import com.example.myapp.utils.LocalLlmQuestionParser

import com.example.myapp.utils.RomanianAsciiNormalizer

import com.google.mediapipe.tasks.genai.llminference.LlmInference

import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.sync.Mutex

import kotlinx.coroutines.sync.withLock

import kotlinx.coroutines.withContext



/**

 * AI local pentru Subiecte offline. Rulează doar pe CPU (stabil pe telefoane mid-range).

 */

object LocalLlmEngine {

    private val mutex = Mutex()

    private var llmInference: LlmInference? = null

    private var llmSession: LlmInferenceSession? = null

    private var loadedModelPath: String? = null



    suspend fun generatePageQuestion(

        context: Context,

        modelPath: String,

        pageText: String

    ): Result<PageQuestionResult> = withContext(Dispatchers.Default) {

        LocalLlmPromptGuard.verifyModelFile(modelPath, OfflineLlmModelCatalog.defaultModel.minBytes)

            ?.let { return@withContext Result.failure(Exception(it)) }



        val prompt = buildQuestionPrompt(pageText)

        runInference(context, modelPath, prompt, temperature = 0.1f).map { raw ->

            LocalLlmQuestionParser.parse(raw) ?: PageQuestionResult(skip = true)

        }

    }



    suspend fun evaluateUserAnswer(

        context: Context,

        modelPath: String,

        intrebare: String,

        raspunsAsteptat: String,

        raspunsElev: String

    ): Result<AnswerEvaluation> = withContext(Dispatchers.Default) {

        LocalLlmPromptGuard.verifyModelFile(modelPath, OfflineLlmModelCatalog.defaultModel.minBytes)

            ?.let { return@withContext Result.failure(Exception(it)) }



        val prompt = buildEvaluationPrompt(intrebare, raspunsAsteptat, raspunsElev)

        runInference(context, modelPath, prompt, temperature = 0.1f).map { raw ->

            LocalLlmJsonParser.parseEvaluation(raw)

                ?: throw Exception("Evaluarea nu a putut fi citita din raspunsul modelului.")

        }

    }



    private suspend fun runInference(

        context: Context,

        modelPath: String,

        prompt: String,

        temperature: Float

    ): Result<String> = withContext(Dispatchers.Default) {

        mutex.withLock {

            try {

                ensureEngine(context, modelPath)

                val engine = llmInference

                    ?: return@withLock Result.failure(Exception("Modelul local nu s-a incarcat."))

                recreateSession(engine, temperature)

                val session = llmSession

                    ?: return@withLock Result.failure(Exception("Sesiunea modelului local nu s-a creat."))

                val safePrompt = LocalLlmPromptGuard.trimPrompt(prompt)

                session.addQueryChunk(safePrompt)

                val raw = session.generateResponse().trim()

                if (raw.isBlank()) {

                    closeSessionOnly()

                    Result.failure(Exception("Modelul local nu a returnat text."))

                } else {

                    Result.success(RomanianAsciiNormalizer.fixRomanianText(raw))

                }

            } catch (e: OutOfMemoryError) {

                release()

                Result.failure(Exception("Memorie insuficienta pentru modelul local. Inchide alte aplicatii si incearca din nou."))

            } catch (e: Throwable) {

                closeSessionOnly()

                Result.failure(Exception(e.message ?: "Eroare la modelul local."))

            }

        }

    }



    private fun recreateSession(engine: LlmInference, temperature: Float) {

        closeSessionOnly()

        llmSession = LlmInferenceSession.createFromOptions(

            engine,

            LlmInferenceSession.LlmInferenceSessionOptions.builder()

                .setTemperature(temperature)

                .setTopK(8)

                .setTopP(0.85f)

                .build()

        )

    }



    private fun ensureEngine(context: Context, modelPath: String) {

        if (llmInference != null && loadedModelPath == modelPath) return

        release()

        loadedModelPath = modelPath

        val options = LlmInference.LlmInferenceOptions.builder()

            .setModelPath(modelPath)

            .setMaxTokens(OfflineLlmModelConfig.MODEL_KV_CACHE_TOKENS)

            .setPreferredBackend(LlmInference.Backend.CPU)

            .build()

        llmInference = LlmInference.createFromOptions(context.applicationContext, options)

    }



    private fun closeSessionOnly() {

        llmSession?.close()

        llmSession = null

    }



    fun release() {

        closeSessionOnly()

        llmInference?.close()

        llmInference = null

        loadedModelPath = null

    }



    private fun buildQuestionPrompt(pageText: String): String = """

        Scrie exact 2 randuri din fragment:

        I: intrebare scurta

        R: raspuns scurt (max 15 cuvinte)

        Daca nu e suficient text, scrie doar: SKIP



        $pageText

    """.trimIndent()



    private fun buildEvaluationPrompt(

        intrebare: String,

        raspunsAsteptat: String,

        raspunsElev: String

    ): String = """

        Evaluează răspunsul elevului comparându-l cu răspunsul așteptat.

        Răspunde doar în JSON valid.



        INTREBARE:

        $intrebare



        RASPUNS ASTEPTAT:

        $raspunsAsteptat



        RASPUNS ELEV:

        $raspunsElev



        Format:

        {

          "corect": "da | partial | nu",

          "scor": 0,

          "feedback": "..."

        }



        Reguli:

        - scorul este între 0 și 100

        - feedbackul are maximum 2 propoziții

        - acceptă formulări diferite dacă sensul este corect

        - nu inventa informații noi

        - dacă răspunsul elevului este vag, marchează "partial" sau "nu"

    """.trimIndent()

}

