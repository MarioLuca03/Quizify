package com.example.myapp.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.app.Application
import com.example.myapp.data.model.CompletedQuiz
import com.example.myapp.data.repository.SummaryEntry
import com.example.myapp.ui.viewmodel.ProfileViewModelFactory
import com.example.myapp.ui.viewmodel.CardItem
import com.example.myapp.ui.viewmodel.ProfileViewModel
import java.text.SimpleDateFormat
import java.util.*

private enum class ProfileSection {
    SETTINGS,
    CARDS,
    QUIZ_HISTORY,
    ACHIEVEMENTS,
    SUMMARIES
}

private sealed class ProfileRow {
    data class Header(val name: String) : ProfileRow()
    data class CardRow(val card: CardItem) : ProfileRow()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onChangePassword: () -> Unit,
    onDeleteAccount: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
    onFisiereleMele: () -> Unit,
    onQuizClick: (CompletedQuiz) -> Unit = {},
    viewModel: ProfileViewModel = viewModel(
        factory = ProfileViewModelFactory(LocalContext.current.applicationContext as Application)
    )
) {
    val completedQuizzes by viewModel.completedQuizzes.collectAsState()
    val cards by viewModel.cards.collectAsState()
    val userStats by viewModel.userStats.collectAsState()
    val achievements by viewModel.achievements.collectAsState()
    val summaries by viewModel.summaries.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedSection by remember { mutableStateOf<ProfileSection?>(null) }

    val grouped = remember(cards) {
        cards.groupBy { it.folder.ifBlank { "Altele" } }
    }
    val flattenedCards = remember(grouped) {
        grouped.entries.flatMap { (folder, list) ->
            listOf(ProfileRow.Header(folder)) + list.map { ProfileRow.CardRow(it) }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadCards()
    }

    val topBarTitle = when (selectedSection) {
        ProfileSection.SETTINGS -> "Setări cont"
        ProfileSection.CARDS -> "Flashcards"
        ProfileSection.QUIZ_HISTORY -> "Istoric si Evolutie"
        ProfileSection.ACHIEVEMENTS -> "Achievements & Level"
        ProfileSection.SUMMARIES -> "Rezumate"
        null -> "Profil"
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            snackbarHostState.showSnackbar(
                message = event.message,
                withDismissAction = true
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = topBarTitle,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedSection != null) selectedSection = null
                        else onBack()
                    }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Înapoi",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                SnackbarHost(hostState = snackbarHostState)
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedSection) {
                null -> ProfileMainContent(
                    modifier = Modifier.padding(24.dp),
                    onFisiereleMele = onFisiereleMele,
                    onSettingsClick = { selectedSection = ProfileSection.SETTINGS },
                    onCardsClick = { selectedSection = ProfileSection.CARDS },
                    onQuizHistoryClick = { selectedSection = ProfileSection.QUIZ_HISTORY },
                    onAchievementsClick = { selectedSection = ProfileSection.ACHIEVEMENTS }
                )
                ProfileSection.SETTINGS -> ProfileSettingsContent(
                    modifier = Modifier.padding(16.dp),
                    onChangePassword = onChangePassword,
                    onDeleteAccount = onDeleteAccount,
                    onLogout = onLogout
                )
                ProfileSection.CARDS -> ProfileCardsContent(
                    modifier = Modifier.padding(16.dp),
                    flattenedCards = flattenedCards,
                    onDeleteCard = { viewModel.removeCard(it) }
                )
                ProfileSection.QUIZ_HISTORY -> ProfileQuizHistoryContent(
                    modifier = Modifier.padding(16.dp),
                    completedQuizzes = completedQuizzes,
                    onQuizClick = onQuizClick
                )
                ProfileSection.ACHIEVEMENTS -> ProfileAchievementsContent(
                    modifier = Modifier.padding(16.dp),
                    userStats = userStats,
                    achievements = achievements
                )
                ProfileSection.SUMMARIES -> ProfileSummariesContent(
                    modifier = Modifier.padding(16.dp),
                    summaries = summaries,
                    onDeleteSummary = { id -> viewModel.removeSummary(id) }
                )
            }
        }
    }
}

@Composable
private fun ProfileMainContent(
    modifier: Modifier = Modifier,
    onFisiereleMele: () -> Unit,
    onSettingsClick: () -> Unit,
    onCardsClick: () -> Unit,
    onQuizHistoryClick: () -> Unit,
    onAchievementsClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Profil",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            ProfileMenuCard(
                title = "Fișierele mele",
                onClick = onFisiereleMele,
                icon = Icons.Default.Description
            )
            ProfileMenuCard(
                title = "Setări cont",
                onClick = onSettingsClick,
                icon = Icons.Default.Settings
            )
            ProfileMenuCard(
                title = "Cards",
                onClick = onCardsClick,
                icon = Icons.Outlined.Style
            )
            ProfileMenuCard(
                title = "Istoric Quiz",
                onClick = onQuizHistoryClick,
                icon = Icons.Default.History
            )
            ProfileMenuCard(
                title = "Achievements & Level",
                onClick = onAchievementsClick,
                icon = Icons.Outlined.Style
            )
        }
    }
}

@Composable
private fun ProfileMenuCard(
    title: String,
    onClick: () -> Unit,
    icon: ImageVector
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp
            )
        }
    }
}

@Composable
private fun ProfileSettingsContent(
    modifier: Modifier = Modifier,
    onChangePassword: () -> Unit,
    onDeleteAccount: () -> Unit,
    onLogout: () -> Unit
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            onClick = onChangePassword
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Schimbă parola", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(text = "→", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            onClick = onLogout
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Deconectare",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "→",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            onClick = onDeleteAccount
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Șterge cont", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onErrorContainer)
                Text(text = "→", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun ProfileCardsContent(
    modifier: Modifier = Modifier,
    flattenedCards: List<ProfileRow>,
    onDeleteCard: (String) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (flattenedCards.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        text = "Nu ai carduri. Adaugă wildcard din quiz (după ce răspunzi la o întrebare).",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(
                count = flattenedCards.size,
                key = { index -> when (val r = flattenedCards[index]) { is ProfileRow.Header -> "h_${r.name}"; is ProfileRow.CardRow -> "c_${r.card.id}" } }
            ) { index ->
                when (val row = flattenedCards[index]) {
                    is ProfileRow.Header -> {
                        Text(
                            text = row.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    is ProfileRow.CardRow -> {
                        FlashcardItem(card = row.card, onDelete = { onDeleteCard(row.card.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileQuizHistoryContent(
    modifier: Modifier = Modifier,
    completedQuizzes: List<CompletedQuiz>,
    onQuizClick: (CompletedQuiz) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (completedQuizzes.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = "Nu ai completat niciun quiz încă.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Column
        }

        // Rezumat progres general (toate quiz-urile, indiferent de fișier)
        ProgressSummaryCard(completedQuizzes = completedQuizzes)

        // Grafic general de progres
        ProgressChart(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            completedQuizzes = completedQuizzes
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Grupare pe fișiere PDF (sau pe quiz-uri fără PDF)
        val groupedByFile = completedQuizzes.groupBy { quiz ->
            quiz.pdfName ?: "Quiz fără PDF asociat"
        }

        groupedByFile.forEach { (fileName, quizzesForFile) ->
            PdfQuizHistorySection(
                fileName = fileName,
                quizzes = quizzesForFile,
                onQuizClick = onQuizClick
            )
        }
    }
}

@Composable
private fun ProgressSummaryCard(completedQuizzes: List<CompletedQuiz>) {
    val percentages = completedQuizzes.map { it.percentage }
    val avg = percentages.average().toInt()
    val last = percentages.firstOrNull() ?: 0
    val best = percentages.maxOrNull() ?: 0

    val avgText = "$avg%"
    val lastText = "$last%"
    val bestText = "$best%"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Progres general",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ProgressSummaryItem(label = "Ultimul quiz", value = lastText)
                ProgressSummaryItem(label = "Media", value = avgText)
                ProgressSummaryItem(label = "Cel mai bun", value = bestText)
            }
        }
    }
}

@Composable
private fun ProgressSummaryItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun PdfQuizHistorySection(
    fileName: String,
    quizzes: List<CompletedQuiz>,
    onQuizClick: (CompletedQuiz) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = fileName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Grafic de progres specific acestui fișier / grup
        ProgressChart(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            completedQuizzes = quizzes
        )

        quizzes.forEach { quiz ->
            CompletedQuizCard(
                quiz = quiz,
                onClick = { onQuizClick(quiz) }
            )
        }
    }
}

@Composable
private fun ProgressChart(
    modifier: Modifier = Modifier,
    completedQuizzes: List<CompletedQuiz>
) {
    val quizzes = completedQuizzes
        .sortedBy { it.completedAt }
        .takeLast(15) // ultimele 15 pentru claritate

    if (quizzes.isEmpty()) return

    val points = quizzes.mapIndexed { index, quiz ->
        index.toFloat() to quiz.percentage.toFloat()
    }

    val maxX = (points.maxOfOrNull { it.first } ?: 1f).coerceAtLeast(1f)
    val maxY = (points.maxOfOrNull { it.second } ?: 100f).coerceAtLeast(100f)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Text(
                text = "Evoluția scorului (%)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val primaryColor = MaterialTheme.colorScheme.primary

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val padding = 24.dp.toPx()
                val width = size.width - padding * 2
                val height = size.height - padding * 2

                if (width <= 0f || height <= 0f) return@Canvas

                val path = androidx.compose.ui.graphics.Path()
                points.forEachIndexed { index, (x, y) ->
                    val px = padding + (x / maxX) * width
                    val py = padding + height - (y / maxY) * height
                    if (index == 0) {
                        path.moveTo(px, py)
                    } else {
                        path.lineTo(px, py)
                    }
                }

                // Linie de progres
                drawPath(
                    path = path,
                    color = primaryColor,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 4.dp.toPx()
                    )
                )

                // Puncte pe grafic
                points.forEach { (x, y) ->
                    val px = padding + (x / maxX) * width
                    val py = padding + height - (y / maxY) * height
                    drawCircle(
                        color = primaryColor,
                        radius = 5.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(px, py)
                    )
                }
            }

            Text(
                text = "Ultimele ${points.size} quiz-uri",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
private fun ProfileSummariesContent(
    modifier: Modifier = Modifier,
    summaries: List<SummaryEntry>,
    onDeleteSummary: (String) -> Unit
) {
    if (summaries.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Nu ai încă niciun rezumat salvat.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(summaries, key = { it.id }) { entry ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = entry.pdfName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        val preview = entry.content.lines().take(3).joinToString("\n")
                        Text(
                            text = preview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { onDeleteSummary(entry.id) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Șterge rezumat",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileAchievementsContent(
    modifier: Modifier = Modifier,
    userStats: com.example.myapp.ui.viewmodel.UserStats?,
    achievements: List<com.example.myapp.ui.viewmodel.Achievement>
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (userStats == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = "Nu ai suficiente date încă. Completează câteva quiz-uri pentru a începe să deblochezi achievements!",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Column
        }

        // Card cu Level & XP
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Nivelul tău",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Level ${userStats.level}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                val xpForCurrentLevel = (userStats.level - 1) * 200
                val xpIntoLevel = (userStats.xp - xpForCurrentLevel).coerceAtLeast(0)
                val xpNeeded = 200
                val progress = (xpIntoLevel.toFloat() / xpNeeded).coerceIn(0f, 1f)

                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                Text(
                    text = "${userStats.xp} XP total · ${xpIntoLevel}/${xpNeeded} XP spre nivelul următor",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Total întrebări corecte: ${userStats.totalCorrect}/${userStats.totalQuestions}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Quiz-uri completate: ${userStats.totalQuizzes}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Streak maxim: ${userStats.maxStreakDays} zile",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Lista de achievements
        Text(
            text = "Achievements",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        achievements.forEach { achievement ->
            val isUnlocked = achievement.unlocked
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isUnlocked) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isUnlocked) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (isUnlocked) achievement.icon else "🔒",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = achievement.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isUnlocked) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Text(
                            text = achievement.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FlashcardItem(
    card: CardItem,
    onDelete: () -> Unit
) {
    var isFlipped by remember { mutableStateOf(false) }
    val rotationY by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = 200f
        ),
        label = "flip"
    )
    val density = LocalDensity.current
    val cameraDistance = with(density) { 12.dp.toPx() }
    val scaleX = remember(rotationY) {
        abs(cos(rotationY * PI / 180f)).toFloat().coerceAtLeast(0.01f)
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    this.rotationY = rotationY
                    this.cameraDistance = cameraDistance
                    this.scaleX = scaleX
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                }
                .clip(RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            onClick = { isFlipped = !isFlipped }
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                if (rotationY < 90f) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Întrebare",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = card.question,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Start,
                                maxLines = 4
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                            .graphicsLayer { this.rotationY = 180f },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("A", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Răspuns",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = card.answer,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Start,
                                maxLines = 4
                            )
                        }
                    }
                }
            }
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Șterge card",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun CompletedQuizCard(
    quiz: CompletedQuiz,
    onClick: () -> Unit = {}
) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    val dateString = dateFormat.format(Date(quiz.completedAt))
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = quiz.subject,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (quiz.pdfName != null) {
                        Text(
                            text = "📄 ${quiz.pdfName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
                
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "${quiz.score}/${quiz.totalQuestions}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            quiz.percentage >= 70 -> MaterialTheme.colorScheme.primary
                            quiz.percentage >= 50 -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
                    Text(
                        text = "${quiz.percentage}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            
            Text(
                text = dateString,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

