package com.example.myapp.ui.screens

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapp.ui.components.tech.TechFloatingBackBubble
import com.example.myapp.ui.viewmodel.QuestionsViewModel
import com.example.myapp.ui.viewmodel.QuestionsViewModelFactory
import com.example.myapp.utils.ShakeDetector
import com.example.myapp.utils.SpeechRecognitionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionsScreen(
    pdfUri: android.net.Uri,
    apiKey: String,
    onBack: () -> Unit,
    viewModel: QuestionsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = QuestionsViewModelFactory(
            context = LocalContext.current,
            pdfUri = pdfUri,
            apiKey = apiKey
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val speechRecognitionManager = remember { SpeechRecognitionManager(context) }
    var listeningQuestionIndex by remember { mutableStateOf<Int?>(null) }

    fun findMatchingAnswer(spokenText: String, question: com.example.myapp.data.model.Question): Int? {
        val normalizedSpoken = spokenText.lowercase().trim()

        val letterMap = mapOf(
            "a" to 0, "ă" to 0,
            "b" to 1, "be" to 1,
            "c" to 2, "ce" to 2,
            "d" to 3, "de" to 3
        )

        val firstWord = normalizedSpoken.split(" ").firstOrNull() ?: ""
        letterMap[firstWord]?.let { return it }

        question.answers.forEachIndexed { index, answer ->
            val normalizedAnswer = answer.lowercase().trim()
            if (normalizedSpoken.contains(normalizedAnswer) || 
                normalizedAnswer.contains(normalizedSpoken) ||
                normalizedSpoken == normalizedAnswer) {
                return index
            }
        }

        question.answers.forEachIndexed { index, answer ->
            val answerWords = answer.lowercase().split(" ").take(3).joinToString(" ")
            if (normalizedSpoken.contains(answerWords) || answerWords.contains(normalizedSpoken)) {
                return index
            }
        }
        
        return null
    }
    
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = speechRecognitionManager.parseSpeechResult(result.resultCode, result.data)
            listeningQuestionIndex?.let { questionIndex ->
                if (spokenText != null && questionIndex < uiState.questions.size) {
                    val question = uiState.questions[questionIndex]
                    val matchedAnswerIndex = findMatchingAnswer(spokenText, question)
                    if (matchedAnswerIndex != null) {
                        viewModel.selectAnswer(questionIndex, matchedAnswerIndex)
                    }
                }
            }
        }
        listeningQuestionIndex = null
    }

    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val accelerometer = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    val lastShakeTime = remember { mutableStateOf(0L) }
    val SHAKE_COOLDOWN = 2000L // 2 seconds cooldown

    val canGenerate = remember { mutableStateOf(true) }

    LaunchedEffect(uiState.isLoading, uiState.questions.isEmpty()) {
        canGenerate.value = !uiState.isLoading && uiState.questions.isEmpty()
    }
    
    DisposableEffect(Unit) {
        val shakeDetector = ShakeDetector {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastShakeTime.value > SHAKE_COOLDOWN && canGenerate.value) {
                lastShakeTime.value = currentTime
                viewModel.generateQuestions()
            }
        }
        
        accelerometer?.let {
            sensorManager.registerListener(shakeDetector, it, SensorManager.SENSOR_DELAY_UI)
        }
        
        onDispose {
            sensorManager.unregisterListener(shakeDetector)
        }
    }
    
    fun startVoiceInput(questionIndex: Int) {
        if (speechRecognitionManager.isAvailable()) {
            listeningQuestionIndex = questionIndex
            val intent = speechRecognitionManager.createSpeechIntent()
            speechLauncher.launch(intent)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {}
    ) { paddingValues ->
        Box(Modifier.fillMaxSize()) {
            when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .statusBarsPadding()
                        .padding(top = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Se generează întrebările...",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
            
            uiState.error != null -> {
                val errorMessage = uiState.error
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .statusBarsPadding()
                        .padding(top = 12.dp),
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
                        Text(
                            text = "Scutură telefonul pentru a încerca din nou",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
            
            uiState.questions.isEmpty() -> {
                ShakeToGenerateScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .statusBarsPadding()
                )
            }
            
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .statusBarsPadding()
                        .padding(top = 52.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    uiState.questions.forEachIndexed { questionIndex, question ->
                        QuestionCard(
                            questionIndex = questionIndex,
                            question = question,
                            selectedAnswer = uiState.selectedAnswers[questionIndex],
                            isListening = listeningQuestionIndex == questionIndex,
                            onAnswerSelected = { answerIndex ->
                                viewModel.selectAnswer(questionIndex, answerIndex)
                            },
                            onVoiceInput = {
                                startVoiceInput(questionIndex)
                            }
                        )
                    }
                }
            }
            }
            TechFloatingBackBubble(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart)
            )
        }
    }
}

@Composable
private fun ShakeToGenerateScreen(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "shake_animation")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "SHAKE TO GENERATE",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .scale(scale)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Scutură telefonul pentru a genera întrebări",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
fun QuestionCard(
    questionIndex: Int,
    question: com.example.myapp.data.model.Question,
    selectedAnswer: Int?,
    isListening: Boolean,
    onAnswerSelected: (Int) -> Unit,
    onVoiceInput: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Întrebarea ${questionIndex + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = question.question,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                IconButton(
                    onClick = onVoiceInput,
                    modifier = Modifier.size(48.dp)
                ) {
                    Text(
                        text = "🎤",
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (isListening) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }
            
            if (isListening) {
                Text(
                    text = "🎤 Ascult...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            question.answers.forEachIndexed { answerIndex, answer ->
                val isSelected = selectedAnswer == answerIndex
                val isCorrect = answerIndex == question.correctAnswer
                
                AnswerOption(
                    label = when (answerIndex) {
                        0 -> "A"
                        1 -> "B"
                        2 -> "C"
                        3 -> "D"
                        else -> ""
                    },
                    text = answer,
                    isSelected = isSelected,
                    isCorrect = isCorrect,
                    onClick = { onAnswerSelected(answerIndex) }
                )
            }
            
            if (selectedAnswer != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val isCorrectAnswer = selectedAnswer == question.correctAnswer
                Text(
                    text = if (isCorrectAnswer) {
                        "✓ Răspuns corect!"
                    } else {
                        "✗ Răspuns greșit. Răspunsul corect este: ${question.answers[question.correctAnswer]}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isCorrectAnswer) {
                        Color(0xFF4CAF50)
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun AnswerOption(
    label: String,
    text: String,
    isSelected: Boolean,
    isCorrect: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isSelected && isCorrect -> Color(0xFF4CAF50).copy(alpha = 0.2f)
        isSelected -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    
    val borderColor = when {
        isSelected && isCorrect -> Color(0xFF4CAF50)
        isSelected -> MaterialTheme.colorScheme.error
        else -> Color.Transparent
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        onClick = onClick,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 0.dp,
            color = borderColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "$label.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

