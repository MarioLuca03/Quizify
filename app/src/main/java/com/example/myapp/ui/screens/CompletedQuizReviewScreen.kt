package com.example.myapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapp.data.model.CompletedQuiz
import com.example.myapp.ui.components.AnswerOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompletedQuizReviewScreen(
    quiz: CompletedQuiz,
    onBack: () -> Unit
) {
    var currentQuestionIndex by remember { mutableStateOf(0) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = quiz.subject,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Scor: ${quiz.score}/${quiz.totalQuestions} (${quiz.percentage}%)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Înapoi"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        val currentQuestion = quiz.questions.getOrNull(currentQuestionIndex)
        
        if (currentQuestion != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Text(
                    text = "Întrebare ${currentQuestionIndex + 1} din ${quiz.questions.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    val selectedAnswer = quiz.selectedAnswers[currentQuestionIndex]
                    val isCorrect = selectedAnswer == currentQuestion.correctIndex
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = currentQuestion.question,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            
                            currentQuestion.options.forEachIndexed { index, option ->
                                val isSelected = selectedAnswer == index
                                val isCorrectOption = index == currentQuestion.correctIndex
                                
                                val optionLabel = when (index) {
                                    0 -> "A"
                                    1 -> "B"
                                    2 -> "C"
                                    3 -> "D"
                                    else -> "${index + 1}"
                                }
                                
                                AnswerOption(
                                    label = optionLabel,
                                    text = option,
                                    isSelected = isSelected,
                                    isCorrect = isCorrectOption,
                                    onClick = { }
                                )
                                
                                if (isSelected || isCorrectOption) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, top = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isCorrectOption && isSelected) {
                                                "✓ Răspuns corect"
                                            } else if (isSelected) {
                                                "✗ Răspuns greșit"
                                            } else {
                                                "Răspuns corect (nu ai selectat)"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = when {
                                                isCorrectOption && isSelected -> Color(0xFF4CAF50)
                                                isSelected -> MaterialTheme.colorScheme.error
                                                else -> Color(0xFF4CAF50)
                                            }
                                        )
                                    }
                                }
                            }
                            
                            currentQuestion.explanation?.let { explanation ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp)
                                    ) {
                                        Text(
                                            text = "Explicație:",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = explanation,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (currentQuestionIndex > 0) {
                                currentQuestionIndex--
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        enabled = currentQuestionIndex > 0,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "ÎNAPOI",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Button(
                        onClick = {
                            if (currentQuestionIndex < quiz.questions.size - 1) {
                                currentQuestionIndex++
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        enabled = currentQuestionIndex < quiz.questions.size - 1,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = if (currentQuestionIndex < quiz.questions.size - 1) {
                                "ÎNAINTE"
                            } else {
                                "FINAL"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

