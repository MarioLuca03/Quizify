package com.example.myapp.ui.screens

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapp.data.model.QuizQuestion
import com.example.myapp.data.model.CompletedQuiz
import com.example.myapp.data.repository.CardsRepository
import com.example.myapp.data.repository.PdfListRepository
import com.example.myapp.ui.components.AnswerOption
import com.example.myapp.ui.components.tech.PdfFlowLoadingAnimation
import com.example.myapp.ui.viewmodel.QuizViewModel
import com.example.myapp.ui.viewmodel.QuizViewModelFactory
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    apiKey: String,
    pdfUri: android.net.Uri,
    numQuestions: Int,
    isExamMode: Boolean = false,
    pdfName: String? = null,
    completedQuizzes: List<CompletedQuiz> = emptyList(),
    onQuizCompleted: ((com.example.myapp.data.model.CompletedQuiz) -> Unit)? = null,
    onBack: () -> Unit,
    viewModel: QuizViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = QuizViewModelFactory(apiKey)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val loadingMessages = remember {
        listOf(
            "Citesc PDF-ul…",
            "Analizez performanța anterioară…",
            "Aleg paginile cele mai relevante…",
            "Pregatesc intrebarile din material…",
            "Generez quiz-ul adaptiv…",
            "Aproape gata…"
        )
    }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val totalExamSeconds = remember(isExamMode, numQuestions) {
        if (isExamMode) numQuestions * 30 else 0
    }
    var remainingExamSeconds by remember(isExamMode, numQuestions) {
        mutableIntStateOf(totalExamSeconds)
    }

    var showWildcardDialog by remember { mutableStateOf(false) }
    var wildcardQuestion by remember { mutableStateOf("") }
    var wildcardAnswer by remember { mutableStateOf("") }
    var wildcardFolderIndex by remember { mutableStateOf(0) }
    var wildcardNewFolder by remember { mutableStateOf("") }

    fun isOnlineNow(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Rezumat performanță pe același PDF, pentru învățare adaptivă la generare.
     */
    fun buildPreviousPerformanceSummary(): Pair<String, String> {
        val label = pdfName?.takeIf { it.isNotBlank() } ?: "acest PDF"
        val pdfQuizzes = completedQuizzes
            .filter { it.pdfUri?.toString() == pdfUri.toString() }
            .sortedByDescending { it.completedAt }
            .take(10)

        if (pdfQuizzes.isEmpty()) {
            return "mediu" to "Utilizator nou pentru \"$label\". Presupune nivel mediu și explică conceptele clar din text."
        }

        val percentages = pdfQuizzes.map { it.percentage }
        val avg = percentages.average().toInt()

        val difficulty = when {
            avg < 50 -> "ușor"
            avg in 50..80 -> "mediu"
            else -> "greu"
        }

        val lastQuiz = pdfQuizzes.first()
        val wrongQuestions = mutableListOf<String>()

        lastQuiz.questions.forEachIndexed { index, question ->
            val selected = lastQuiz.selectedAnswers[index]
            if (selected == null || selected != question.correctIndex) {
                wrongQuestions.add(question.question)
            }
        }

        val performanceText = buildString {
            appendLine("Utilizatorul a completat ${pdfQuizzes.size} quiz-uri pe \"$label\".")
            appendLine("Acuratețe medie: $avg%.")
            if (percentages.isNotEmpty()) {
                appendLine("Cel mai bun scor: ${percentages.maxOrNull()}%, cel mai slab: ${percentages.minOrNull()}%.")
            }
            if (wrongQuestions.isNotEmpty()) {
                appendLine("Întrebări/concepte unde a greșit recent:")
                wrongQuestions.take(5).forEach { q ->
                    appendLine("- $q")
                }
                appendLine("Insistă pe aceste concepte dacă apar în fragmentul de pagină; explicații detaliate.")
            } else {
                appendLine("În ultimul quiz, utilizatorul a răspuns corect la toate întrebările.")
                appendLine("Poți crește puțin dificultatea și folosi explicații mai scurte.")
            }
        }

        return difficulty to performanceText
    }

    fun startQuizGeneration() {
        if (uiState.isLoading || uiState.questions.isNotEmpty()) return
        if (isOnlineNow()) {
            val (difficulty, performanceText) = buildPreviousPerformanceSummary()
            viewModel.generateQuizFromPdf(
                context = context,
                pdfUri = pdfUri,
                numQuestions = numQuestions,
                difficulty = difficulty,
                previousPerformance = performanceText
            )
        } else {
            val pool = completedQuizzes
                .filter { it.pdfUri?.toString() == pdfUri.toString() && it.questions.isNotEmpty() }
                .flatMap { it.questions }
                .distinctBy { it.question.trim().lowercase() }

            if (pool.isNotEmpty()) {
                val selectedQuestions = mutableListOf<QuizQuestion>()
                while (selectedQuestions.size < numQuestions) {
                    val needed = numQuestions - selectedQuestions.size
                    selectedQuestions.addAll(pool.shuffled().take(needed))
                }

                viewModel.loadQuestionsFromHistoryPool(
                    questions = selectedQuestions.take(numQuestions),
                    subject = "Quiz offline din PDF"
                )
                scope.launch {
                    snackbarHostState.showSnackbar(
                        "Ești offline. Am generat întrebări random din istoricul acestui PDF."
                    )
                }
            } else {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        "Ești offline. Nu există quiz-uri în istoric pentru acest PDF."
                    )
                }
            }
        }
    }

    LaunchedEffect(pdfUri, numQuestions) {
        startQuizGeneration()
    }

    LaunchedEffect(isExamMode, uiState.questions, uiState.isFinished) {
        if (!isExamMode || uiState.questions.isEmpty() || uiState.isFinished) return@LaunchedEffect
        remainingExamSeconds = totalExamSeconds
        while (remainingExamSeconds > 0 && !uiState.isFinished) {
            delay(1000)
            remainingExamSeconds--
        }
        if (remainingExamSeconds <= 0 && !uiState.isFinished) {
            viewModel.calculateScore(
                pdfName = pdfName,
                pdfUri = pdfUri,
                onQuizCompleted = onQuizCompleted
            )
        }
    }

    if (showWildcardDialog) {
        // Determinăm folderul implicit pe baza numelui PDF-ului, dacă există,
        // altfel folosim "Altele".
        val defaultFolder = pdfName?.takeIf { it.isNotBlank() } ?: "Altele"
        AlertDialog(
            onDismissRequest = {
                showWildcardDialog = false
            },
            title = { Text("Adaugă la Cards") },
            text = {
                Text(
                    text = "Întrebarea va fi salvată automat în folderul \"$defaultFolder\".",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        CardsRepository(context).addCard(
                            wildcardQuestion,
                            wildcardAnswer,
                            defaultFolder
                        )
                        showWildcardDialog = false
                        wildcardQuestion = ""
                        wildcardAnswer = ""
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showWildcardDialog = false }) {
                    Text("Renunță")
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { paddingValues ->
        when {
            uiState.isLoading || (uiState.questions.isEmpty() && uiState.error == null) -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .statusBarsPadding(),
                    contentAlignment = Alignment.Center
                ) {
                    PdfFlowLoadingAnimation(messages = loadingMessages)
                }
            }
            
            uiState.error != null -> {
                val errorMessage = uiState.error
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .statusBarsPadding(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Eroare",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = errorMessage ?: "Eroare necunoscută",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Button(
                            onClick = { startQuizGeneration() },
                            modifier = Modifier.padding(top = 8.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Încearcă din nou")
                        }
                    }
                }
            }
            
            uiState.isFinished -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .statusBarsPadding(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            text = "Scor Final",
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Text(
                            text = "${uiState.score} / ${uiState.questions.size}",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.score == uiState.questions.size) {
                                Color(0xFF4CAF50)
                            } else if (uiState.score >= uiState.questions.size * 0.7) {
                                Color(0xFFFF9800)
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                        
                        val percentage = (uiState.score.toFloat() / uiState.questions.size * 100).toInt()
                        Text(
                            text = "$percentage%",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(
                            onClick = onBack,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(
                                text = "FINALIZEAZA",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            
            else -> {
                val currentIndex = uiState.currentQuestionIndex
                val currentQuestion = uiState.questions.getOrNull(currentIndex)
                
                if (currentQuestion != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .statusBarsPadding()
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Întrebare ${currentIndex + 1} din ${uiState.questions.size}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                                if (isExamMode) {
                                    val minutes = remainingExamSeconds / 60
                                    val seconds = remainingExamSeconds % 60
                                    Text(
                                        text = String.format("%02d:%02d", minutes, seconds),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (remainingExamSeconds <= 30) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        }
                                    )
                                }
                            }
                        }
                        
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            QuizQuestionCard(
                                questionIndex = currentIndex,
                                question = currentQuestion,
                                selectedAnswer = uiState.selectedAnswers[currentIndex],
                                onAnswerSelected = { answerIndex ->
                                    viewModel.selectAnswer(currentIndex, answerIndex)
                                },
                                onAddWildcard = { q, a ->
                                    showWildcardDialog = true
                                    wildcardQuestion = q
                                    wildcardAnswer = a
                                }
                            )
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.goToPreviousQuestion() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp),
                                enabled = currentIndex > 0,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    2.dp,
                                    MaterialTheme.colorScheme.outline
                                )
                            ) {
                                Text(
                                    text = "ÎNAPOI",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            if (currentIndex < uiState.questions.size - 1) {
                                Button(
                                    onClick = { viewModel.goToNextQuestion() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Text(
                                        text = "ÎNAINTE",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Button(
                                    onClick = { 
                                        viewModel.calculateScore(
                                            pdfName = pdfName,
                                            pdfUri = pdfUri,
                                            onQuizCompleted = onQuizCompleted
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp),
                                    enabled = uiState.selectedAnswers.size == uiState.questions.size,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.tertiary,
                                        contentColor = MaterialTheme.colorScheme.onTertiary
                                    )
                                ) {
                                    Text(
                                        text = "FINALIZEAZA",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuizQuestionCard(
    questionIndex: Int,
    question: QuizQuestion,
    selectedAnswer: Int?,
    onAnswerSelected: (Int) -> Unit,
    onAddWildcard: ((String, String) -> Unit)? = null
) {
    var xpFlashIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(xpFlashIndex) {
        if (xpFlashIndex != null) {
            kotlinx.coroutines.delay(500)
            xpFlashIndex = null
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Întrebarea ${questionIndex + 1}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = question.question,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))
            
            question.options.forEachIndexed { answerIndex, option ->
                val isSelected = selectedAnswer == answerIndex
                val isCorrect = answerIndex == question.correctIndex

                AnswerOption(
                    label = when (answerIndex) {
                        0 -> "A"
                        1 -> "B"
                        2 -> "C"
                        3 -> "D"
                        else -> ""
                    },
                    text = option,
                    isSelected = isSelected,
                    isCorrect = isCorrect,
                    onClick = {
                        val wasCorrect = answerIndex == question.correctIndex
                        onAnswerSelected(answerIndex)
                        if (wasCorrect) {
                            xpFlashIndex = answerIndex
                        }
                    },
                    xpLabel = if (xpFlashIndex == answerIndex) "+10xp" else null
                )
            }
            
            if (selectedAnswer != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val isCorrectAnswer = selectedAnswer == question.correctIndex
                Text(
                    text = if (isCorrectAnswer) {
                        "✓ Răspuns corect!"
                    } else {
                        "✗ Răspuns greșit. Răspunsul corect este: ${question.options[question.correctIndex]}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isCorrectAnswer) {
                        Color(0xFF4CAF50)
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    fontWeight = FontWeight.Medium
                )
                
                question.explanation?.let { explanation ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Text(
                            text = explanation,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                onAddWildcard?.let { add ->
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            add(question.question, question.options[question.correctIndex])
                        }
                    ) {
                        Text("Adaugă la Cards", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}


