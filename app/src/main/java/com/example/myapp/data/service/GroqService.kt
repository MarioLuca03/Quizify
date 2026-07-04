package com.example.myapp.data.service

import com.example.myapp.data.model.AnswerEvaluation
import com.example.myapp.utils.LocalLlmJsonParser
import com.example.myapp.utils.PdfPageRelevanceSelector
import com.example.myapp.data.model.ExamSubjectItem
import com.example.myapp.data.model.ExamSubjectsPack
import com.example.myapp.data.model.QuizResponse
import com.example.myapp.data.model.QuizQuestion
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody

private const val GROQ_429_MAX_RETRIES = 8
/** Plafon TEXT pentru Sumar PDF rapid (aliniat cu PdfPageRelevanceSelector). */
private const val MAX_SMART_PDF_INPUT_CHARS = 10_000
/** Intrare pentru generare subiecte — aliniat cu PdfPageRelevanceSelector (flux examSubjects). */
private const val MAX_EXAM_SUBJECTS_SMART_INPUT_CHARS = 4_800

class GroqService(private val apiKey: String) {
    
    private val client = OkHttpClient()
    private val gson = Gson()
    private val baseUrl = "https://api.groq.com/openai/v1/chat/completions"
    
    /**
     * Generează întrebări grilă din textul unei pagini PDF.
     * Cu [difficulty] și [previousPerformance], personalizează dificultatea și explicațiile (învățare adaptivă).
     */
    suspend fun generateQuizFromPageText(
        pageNumber: Int,
        pageText: String,
        numQuestions: Int,
        difficulty: String? = null,
        previousPerformance: String? = null
    ): Result<QuizResponse> {
        return withContext(Dispatchers.IO) {
            try {
                if (numQuestions <= 0) {
                    return@withContext Result.failure(Exception("Număr invalid de întrebări."))
                }
                val cappedText = pageText.trim().take(PdfPageRelevanceSelector.QUIZ_CHARS_PER_PAGE)
                if (cappedText.isBlank()) {
                    return@withContext Result.failure(Exception("Fragmentul de pagină este gol."))
                }
                val pageLabel = if (pageNumber > 0) "pagina $pageNumber" else "fragment PDF"
                val adaptiveBlock = if (!difficulty.isNullOrBlank() && !previousPerformance.isNullOrBlank()) {
                    """
                    Învățare adaptivă (același PDF):
                    - Nivel țintă: $difficulty
                    - Istoric utilizator:
                    $previousPerformance
                    - Insistă pe conceptele greșite anterior dacă apar în text; explicații mai detaliate acolo.
                    - Pentru concepte bine stăpânite: întrebări puțin mai provocatoare, explicații scurte.
                    """.trimIndent()
                } else {
                    ""
                }
                val prompt = """
                    Ești profesor. Pe baza textului de la $pageLabel, generează exact $numQuestions întrebări grilă în română.
                    Reguli: întrebări scurte (max 2 propoziții); 4 variante scurte; correctIndex 0-3; explanation în 1 propoziție.
                    Cele $numQuestions întrebări trebuie să acopere concepte diferite din text.
                    Lucrează STRICT din text; nu inventa informații.
                    În JSON nu folosi ghilimele duble în interiorul stringurilor; fără markdown.
                    $adaptiveBlock

                    TEXT:
                    $cappedText

                    Returnează un singur obiect JSON:
                    {"subject":"Quiz din PDF","numQuestions":$numQuestions,"questions":[{"question":"…","options":["…","…","…","…"],"correctIndex":0,"explanation":"…"}]}
                """.trimIndent()

                val maxTokens = (numQuestions * 280 + 120).coerceIn(400, 1_800)
                var lastErr: Exception? = null
                retryLoop@ for (jsonMode in listOf(true, false)) {
                    try {
                        val response = makeRequest(
                            prompt,
                            maxTokens = maxTokens,
                            temperature = 0.3,
                            jsonObjectMode = jsonMode
                        )
                        return@withContext parseQuizResponse(response, "Quiz din PDF", numQuestions)
                    } catch (e: Exception) {
                        lastErr = e
                        val retry = jsonMode &&
                            (isProbablyJsonModeRejectedByApi(e) || isProbablyJsonParseFailure(e))
                        if (!retry) break@retryLoop
                    }
                }
                Result.failure(lastErr ?: Exception("Eroare la generarea întrebărilor."))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun chatWithPdfContext(pdfContext: String, conversation: List<Pair<String, String>>): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val truncatedContext = if (pdfContext.length > 12000) pdfContext.take(12000) + "..." else pdfContext
                val systemContent = """Ești Genius, un asistent educațional prietenos. Utilizatorul îți cere un rezumat al unui document PDF. 

Scopul tău:
- Fă un rezumat clar, structurat și ușor de înțeles al întregului document.
- Evidențiază ideile principale, conceptele cheie și eventualele concluzii.
- Folosește titluri, subtitluri și bullet points acolo unde are sens.
- Răspunde în limba în care îți scrie utilizatorul (de obicei română).

IMPORTANT:
- Dacă anumite părți din document sunt foarte tehnice sau matematice, explică-le pe scurt, pe înțelesul unui elev.
- Nu inventa informații care nu apar în document.

Fragment din documentul utilizatorului:
$truncatedContext"""
                val messagesArray = com.google.gson.JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("role", "system")
                        addProperty("content", systemContent)
                    })
                    conversation.forEach { (role, content) ->
                        add(JsonObject().apply {
                            addProperty("role", role)
                            addProperty("content", content)
                        })
                    }
                }
                val jsonBody = JsonObject().apply {
                    addProperty("model", "llama-3.1-8b-instant")
                    add("messages", messagesArray)
                    addProperty("temperature", 0.5)
                    // Mai mult spațiu pentru un rezumat detaliat
                    addProperty("max_tokens", 3500)
                }
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = gson.toJson(jsonBody).toRequestBody(mediaType)
                val request = Request.Builder()
                    .url(baseUrl)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()
                    if (!response.isSuccessful) {
                        val err = try {
                            com.google.gson.JsonParser().parse(responseBody ?: "{}").asJsonObject
                                .get("error")?.asJsonObject?.get("message")?.asString ?: responseBody
                        } catch (e: Exception) { responseBody }
                        return@withContext Result.failure(Exception("API: $err"))
                    }
                    val json = com.google.gson.JsonParser().parse(responseBody ?: "{}").asJsonObject
                    val content = json.getAsJsonArray("choices")?.get(0)?.asJsonObject
                        ?.getAsJsonObject("message")?.get("content")?.asString
                        ?: return@withContext Result.failure(Exception("Răspuns gol de la API"))
                    Result.success(content)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Rezumă un singur fragment (chunk) de PDF.
     */
    suspend fun summarizePdfChunk(chunk: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val systemContent = """Ești Genius, un asistent educațional prietenos.

Ți se oferă un fragment dintr-un document PDF. Fă un REZUMAT DETALIAT al acestui fragment:
- explică ideile principale,
- menționează conceptele cheie,
- folosește paragrafe clare și, unde are sens, bullet points.
Răspunde în română.

Fragment:
$chunk"""

                val messagesArray = com.google.gson.JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("role", "system")
                        addProperty("content", systemContent)
                    })
                }

                val jsonBody = JsonObject().apply {
                    addProperty("model", "llama-3.1-8b-instant")
                    add("messages", messagesArray)
                    addProperty("temperature", 0.5)
                    addProperty("max_tokens", 1500)
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = gson.toJson(jsonBody).toRequestBody(mediaType)
                val request = Request.Builder()
                    .url(baseUrl)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()
                    if (!response.isSuccessful) {
                        val err = try {
                            com.google.gson.JsonParser().parse(responseBody ?: "{}").asJsonObject
                                .get("error")?.asJsonObject?.get("message")?.asString ?: responseBody
                        } catch (e: Exception) { responseBody }
                        return@withContext Result.failure(Exception("API: $err"))
                    }
                    val json = com.google.gson.JsonParser().parse(responseBody ?: "{}").asJsonObject
                    val content = json.getAsJsonArray("choices")?.get(0)?.asJsonObject
                        ?.getAsJsonObject("message")?.get("content")?.asString
                        ?: return@withContext Result.failure(Exception("Răspuns gol de la API"))
                    Result.success(content)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Un singur apel Groq: doar **subiecte de examen** cu rezolvare (fără rezumat).
     * Textul de intrare e deja filtrat local (ca la Sumar PDF).
     */
    suspend fun generateExamSubjectsFromSmartSelection(
        pdfName: String,
        selectedText: String
    ): Result<ExamSubjectsPack> {
        return withContext(Dispatchers.IO) {
            try {
                val cappedText = selectedText.trim().take(MAX_EXAM_SUBJECTS_SMART_INPUT_CHARS)
                if (cappedText.isBlank()) {
                    return@withContext Result.failure(Exception("Textul pentru subiecte este gol."))
                }
                val prompt = """
                    Ești profesor pentru materia din documentul PDF „$pdfName".
                    Mai jos ai fragmente extrase din paginile considerate cele mai relevante; nu reprezintă neapărat tot PDF-ul.
                    Lucrează STRICT din acest text. Nu inventa teoreme, date sau rezultate care nu apar în el.

                    TEXT:
                    $cappedText

                    Sarcină (răspuns STRICT în română):
                    Generează exact 10 subiecte de examen. În JSON, câmpul „examSubjects" este o listă cu 10 obiecte.
                    Fiecare obiect are:
                    - „question": enunț clar, tip subiect la examen;
                    - „solution": rezolvare concisă (maxim ~80 cuvinte, un singur paragraf).

                    Reguli OBLIGATORII pentru JSON valid (altfel răspunsul e inutilizabil):
                    - Returnează UN SINGUR obiect JSON rădăcină cu cheia examSubjects (array).
                    - În interiorul valorilor string pentru „question" și „solution" NU pune rând nou real (Enter).
                      Dacă ai nevoie de pauză, folosește spațiu sau punct; nu lăsa ghilimele neînchise.
                    - Nu folosi ghilimele duble (") în textul enunțului sau al rezolvării; folosește « » sau formulări fără ".
                    - Fără markdown, fără ```, fără comentarii în afara JSON-ului.

                    Format exact:
                    {
                      "examSubjects": [
                        { "question": "…", "solution": "…" }
                      ]
                    }
                """.trimIndent()

                var pack: ExamSubjectsPack? = null
                var lastErr: Exception? = null
                retryLoop@ for (jsonMode in listOf(true, false)) {
                    try {
                        val response = makeRequest(
                            prompt,
                            maxTokens = 3600,
                            temperature = 0.28,
                            jsonObjectMode = jsonMode
                        )
                        pack = parseExamSubjectsPack(parseChoiceMessagePlain(response))
                        break@retryLoop
                    } catch (e: Exception) {
                        lastErr = e
                        val retry = jsonMode &&
                            (isProbablyJsonModeRejectedByApi(e) || isProbablyJsonParseFailure(e))
                        if (!retry) {
                            return@withContext Result.failure(formatExamSubjectsError(e))
                        }
                    }
                }
                Result.success(pack ?: return@withContext Result.failure(formatExamSubjectsError(lastErr!!)))
            } catch (e: Exception) {
                Result.failure(formatExamSubjectsError(e))
            }
        }
    }

    suspend fun evaluateExamAnswer(
        question: String,
        expectedSolution: String,
        userAnswer: String
    ): Result<AnswerEvaluation> {
        return withContext(Dispatchers.IO) {
            try {
                val trimmedAnswer = userAnswer.trim()
                if (trimmedAnswer.isBlank()) {
                    return@withContext Result.failure(Exception("Scrie un raspuns inainte de verificare."))
                }
                val prompt = """
                    Evalueaza raspunsul elevului comparandu-l cu rezolvarea asteptata.
                    Raspunde doar in JSON valid, in romana.

                    INTREBARE:
                    $question

                    REZOLVARE ASTEPTATA:
                    $expectedSolution

                    RASPUNS ELEV:
                    $trimmedAnswer

                    Format:
                    {"corect":"da | partial | nu","scor":0,"feedback":"..."}

                    Reguli:
                    - scorul este intre 0 si 100
                    - feedbackul are maximum 2 propozitii
                    - accepta formulări diferite daca sensul este corect
                    - nu inventa informatii noi
                    - daca raspunsul elevului este vag, marcheaza partial sau nu
                """.trimIndent()

                var lastErr: Exception? = null
                retryLoop@ for (jsonMode in listOf(true, false)) {
                    try {
                        val response = makeRequest(
                            prompt,
                            maxTokens = 320,
                            temperature = 0.2,
                            jsonObjectMode = jsonMode
                        )
                        val eval = LocalLlmJsonParser.parseEvaluation(parseChoiceMessagePlain(response))
                            ?: throw Exception("Evaluarea nu a putut fi citita din raspunsul modelului.")
                        return@withContext Result.success(eval)
                    } catch (e: Exception) {
                        lastErr = e
                        val retry = jsonMode &&
                            (isProbablyJsonModeRejectedByApi(e) || isProbablyJsonParseFailure(e))
                        if (!retry) break@retryLoop
                    }
                }
                Result.failure(lastErr ?: Exception("Eroare la evaluarea raspunsului."))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun isProbablyJsonModeRejectedByApi(e: Exception): Boolean {
        val m = e.message.orEmpty()
        return m.contains("API Error 400", ignoreCase = true) ||
            m.contains("response_format", ignoreCase = true) ||
            m.contains("json_object", ignoreCase = true)
    }

    private fun isProbablyJsonParseFailure(e: Throwable): Boolean {
        var c: Throwable? = e
        while (c != null) {
            val simple = c.javaClass.simpleName
            if (simple.contains("JsonSyntax", ignoreCase = true) ||
                simple.contains("MalformedJson", ignoreCase = true)
            ) {
                return true
            }
            val m = c.message.orEmpty()
            if (m.contains("Unterminated", ignoreCase = true) ||
                m.contains("MalformedJson", ignoreCase = true) ||
                m.contains("JsonSyntaxException", ignoreCase = true) ||
                m.contains("$.examSubjects", ignoreCase = true)
            ) {
                return true
            }
            c = c.cause
        }
        return false
    }

    private fun formatExamSubjectsError(e: Exception): Exception {
        val raw = e.message.orEmpty()
        val friendly = when {
            isProbablyJsonParseFailure(e) ->
                "Modelul a returnat JSON nevalid (de obicei ghilimele sau rânduri în textul rezolvării). " +
                    "Apasă din nou „Generează subiecte” sau alege alt PDF."
            raw.contains("Expected", ignoreCase = true) &&
                raw.contains("json", ignoreCase = true) ->
                "Modelul a returnat JSON nevalid (de obicei ghilimele sau rânduri în textul rezolvării). " +
                    "Apasă din nou „Generează subiecte” sau alege alt PDF."
            raw.contains("maximum context", ignoreCase = true) ||
                (raw.contains("token", ignoreCase = true) && raw.contains("limit", ignoreCase = true)) ||
                raw.contains("context length", ignoreCase = true) ||
                raw.contains("too large", ignoreCase = true) ||
                raw.contains("request too long", ignoreCase = true) ->
                "Cererea e prea mare pentru model (prea mult text din PDF).\n\n" +
                    "Alege mai jos „Interval”, selectează paginile din care vrei subiecte (un capitol mai scurt), " +
                    "apoi apasă din nou „Generează subiecte”."
            else -> raw.ifBlank { "Eroare la generarea subiectelor." }
        }
        return Exception(friendly, e)
    }

    private fun parseExamSubjectsPack(message: String): ExamSubjectsPack {
        val jsonContent = extractJsonFromResponse(message)
        val packJson = com.google.gson.JsonParser.parseString(jsonContent).asJsonObject
        val arr = packJson.getAsJsonArray("examSubjects")
            ?: throw Exception("Lipsește examSubjects în răspuns.")
        val subjects = mutableListOf<ExamSubjectItem>()
        arr.forEach { el ->
            if (!el.isJsonObject) return@forEach
            val o = el.asJsonObject
            val qEl = o.get("question") ?: return@forEach
            val sEl = o.get("solution") ?: return@forEach
            if (!qEl.isJsonPrimitive || !sEl.isJsonPrimitive) return@forEach
            val q = qEl.asString.trim()
            val s = sEl.asString.trim()
            if (q.isNotBlank() && s.isNotBlank()) {
                subjects.add(ExamSubjectItem(question = q, solution = s))
            }
        }
        if (subjects.size < 8) {
            throw Exception(
                "Modelul a returnat doar ${subjects.size} subiecte valide (așteptat 10). Încearcă din nou."
            )
        }
        return ExamSubjectsPack(subjects = subjects.take(10))
    }

    private fun parseChoiceMessagePlain(responseJson: String): String {
        val jsonObject = com.google.gson.JsonParser().parse(responseJson).asJsonObject
        val choices = jsonObject.getAsJsonArray("choices")
            ?: throw Exception("Răspuns API invalid (fără choices).")
        if (choices.size() == 0) throw Exception("Nu s-a primit răspuns de la API.")
        return choices[0].asJsonObject
            .getAsJsonObject("message")
            .get("content")
            .asString
    }

    /**
     * Un singur apel AI: rezumat 5–8 bullet-uri din textul deja selectat (pagini relevante).
     * Lista `pagesUsed` se completează în aplicație, nu de model.
     */
    suspend fun summarizeSmartPdfFromSelectedText(selectedText: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val cappedText = selectedText.trim().take(MAX_SMART_PDF_INPUT_CHARS)
                val prompt = """
                    Ai mai jos texte extrase din paginile cele mai relevante ale unui PDF. Generează un rezumat scurt în limba română, în 5–8 bullet-uri. Include ideile principale, concluziile și cifrele importante. Nu inventa informații care nu apar în text.

                    TEXT:
                    $cappedText
                """.trimIndent()
                // Sub bugetul TPM per cerere (tier on_demand): max_tokens mic = mai puțini tokeni „rezervați”.
                val response = makeRequest(prompt, maxTokens = 480, temperature = 0.28)
                Result.success(parseChoiceMessagePlain(response).trim())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun generateStudyPlanFromPdf(pdfName: String, pdfText: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val truncatedText = if (pdfText.length > 18000) pdfText.take(18000) else pdfText
                val prompt = """
                    Ești un mentor educațional. Creează un plan de învățare clar pentru documentul "$pdfName".

                    Reguli:
                    - Răspunde în română.
                    - Structurează răspunsul pe secțiuni cu titluri scurte.
                    - Include: obiective, pași de învățare, recapitulare și testare.
                    - Pentru fiecare pas, recomandă durată estimată.
                    - Fii practic și concis (maxim 400 cuvinte).

                    Conținut document:
                    $truncatedText
                """.trimIndent()

                val response = makeRequest(prompt, maxTokens = 4000, temperature = 0.7)
                val jsonObject = com.google.gson.JsonParser().parse(response).asJsonObject
                val choices = jsonObject.getAsJsonArray("choices")
                if (choices.size() == 0) {
                    return@withContext Result.failure(Exception("Nu s-a primit răspuns pentru planul de învățare."))
                }
                val content = choices[0].asJsonObject
                    .getAsJsonObject("message")
                    .get("content")
                    .asString
                    .trim()
                Result.success(content)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun generateEssentialSummaryFromSummaries(
        pdfName: String,
        summaries: List<String>
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val compactSummaries = summaries
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .joinToString("\n\n---\n\n")
                    .take(12000)

                if (compactSummaries.isBlank()) {
                    return@withContext Result.failure(
                        Exception("Nu există suficiente date pentru rezumat.")
                    )
                }

                val prompt = """
                    Ești un asistent educațional. Creează un REZUMAT cu conceptele esențiale pentru documentul "$pdfName",
                    folosind notițele de mai jos care acoperă întregul PDF.

                    Reguli:
                    - Răspunde în română.
                    - Structurează în secțiuni clare:
                      1) Concepte esențiale
                      2) Idei-cheie de reținut
                      3) Capcane frecvente / confuzii posibile
                      4) Întrebări de autoverificare (3-5)
                    - Folosește bullet-uri unde ajută.
                    - Fii clar, practic și fără repetiții (max 450 cuvinte).

                    Notițe agregate din tot PDF-ul:
                    $compactSummaries
                """.trimIndent()

                val response = makeRequest(prompt, maxTokens = 4000, temperature = 0.7)
                val jsonObject = com.google.gson.JsonParser().parse(response).asJsonObject
                val choices = jsonObject.getAsJsonArray("choices")
                if (choices.size() == 0) {
                    return@withContext Result.failure(
                        Exception("Nu s-a primit răspuns pentru rezumat.")
                    )
                }
                val content = choices[0].asJsonObject
                    .getAsJsonObject("message")
                    .get("content")
                    .asString
                    .trim()
                Result.success(content)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private suspend fun makeRequest(
        prompt: String,
        maxTokens: Int = 4000,
        temperature: Double = 0.7,
        jsonObjectMode: Boolean = false
    ): String {
        val jsonBody = JsonObject().apply {
            addProperty("model", "llama-3.1-8b-instant")
            add("messages", com.google.gson.JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", prompt)
                })
            })
            addProperty("temperature", temperature)
            addProperty("max_tokens", maxTokens)
            if (jsonObjectMode) {
                add(
                    "response_format",
                    JsonObject().apply { addProperty("type", "json_object") }
                )
            }
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = gson.toJson(jsonBody).toRequestBody(mediaType)

        val request = Request.Builder()
            .url(baseUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        var attempt = 0
        while (attempt < GROQ_429_MAX_RETRIES) {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (response.code == 429) {
                    val waitMs = groq429WaitMillis(response, responseBody, attempt)
                    delay(waitMs)
                    attempt++
                    return@use
                }
                if (!response.isSuccessful) {
                    val errorMessage = try {
                        val errorJson = com.google.gson.JsonParser().parse(responseBody ?: "{}").asJsonObject
                        errorJson.get("error")?.asJsonObject?.get("message")?.asString
                            ?: responseBody
                            ?: "Unknown error"
                    } catch (e: Exception) {
                        responseBody ?: "Unknown error"
                    }
                    throw Exception("API Error ${response.code}: $errorMessage")
                }
                return responseBody ?: throw Exception("Empty response body")
            }
        }
        throw Exception(
            "Limită de rată Groq (429): prea multe cereri. Așteaptă ~1 minut sau încearcă din nou. " +
                "Detalii: https://console.groq.com/settings/billing"
        )
    }

    /** Groq include în mesaj „try again in Xs”; altfel folosim Retry-After sau backoff. */
    private fun groq429WaitMillis(response: Response, responseBody: String?, attempt: Int): Long {
        val headerSec = response.header("Retry-After")?.toDoubleOrNull()
        if (headerSec != null && headerSec > 0) {
            return (headerSec * 1000).toLong().coerceIn(2000L, 60_000L)
        }
        val body = responseBody.orEmpty()
        val m = Regex("try again in\\s+([0-9.]+)\\s*s", RegexOption.IGNORE_CASE).find(body)
        if (m != null) {
            val sec = m.groupValues[1].toDoubleOrNull() ?: 10.0
            return ((sec + 0.5) * 1000).toLong().coerceIn(2000L, 60_000L)
        }
        return (8000L + attempt * 2000L).coerceAtMost(60_000L)
    }
    
    private fun parseQuizResponse(responseJson: String, defaultSubject: String, defaultNumQuestions: Int): Result<QuizResponse> {
        try {
            val jsonObject = com.google.gson.JsonParser().parse(responseJson).asJsonObject
            val choices = jsonObject.getAsJsonArray("choices")
            
            if (choices.size() == 0) {
                return Result.failure(Exception("Nu s-au primit răspunsuri de la API"))
            }
            
            val message = choices[0].asJsonObject
                .getAsJsonObject("message")
                .get("content")
                .asString
            
            val jsonContent = extractJsonFromResponse(message)
            
            val quizJson = com.google.gson.JsonParser().parse(jsonContent).asJsonObject
            
            val subject = quizJson.get("subject")?.asString ?: defaultSubject
            val numQuestions = quizJson.get("numQuestions")?.asInt ?: defaultNumQuestions
            val questionsArray = quizJson.getAsJsonArray("questions")
            
            val questions = mutableListOf<QuizQuestion>()
            questionsArray.forEach { questionElement ->
                val q = questionElement.asJsonObject
                val optionsList = mutableListOf<String>()
                q.getAsJsonArray("options").forEach { option ->
                    optionsList.add(option.asString)
                }
                
                questions.add(
                    QuizQuestion(
                        question = q.get("question").asString,
                        options = optionsList,
                        correctIndex = q.get("correctIndex").asInt,
                        explanation = q.get("explanation")?.asString
                    )
                )
            }
            
            return Result.success(
                QuizResponse(
                    subject = subject,
                    numQuestions = numQuestions,
                    questions = questions
                )
            )
        } catch (e: Exception) {
            return Result.failure(Exception("Eroare la parsarea răspunsului: ${e.message}", e))
        }
    }
    
    private fun extractJsonFromResponse(content: String): String {
        var cleaned = content.trim()
        
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.removePrefix("```json").trim()
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.removePrefix("```").trim()
        }
        
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.removeSuffix("```").trim()
        }
        
        val startIndex = cleaned.indexOf('{')
        val endIndex = cleaned.lastIndexOf('}')
        
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return cleaned.substring(startIndex, endIndex + 1)
        }
        
        return cleaned
    }
}
