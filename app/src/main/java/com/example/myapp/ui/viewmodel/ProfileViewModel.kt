package com.example.myapp.ui.viewmodel

import android.app.Application
import java.io.File
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapp.data.model.CompletedQuiz
import com.example.myapp.data.repository.CardsRepository
import com.example.myapp.data.repository.CompletedQuizRepository
import com.example.myapp.data.repository.SummariesRepository
import com.example.myapp.data.repository.SummaryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CardItem(
    val id: String,
    val question: String,
    val answer: String,
    val folder: String
)

data class UserStats(
    val level: Int,
    val xp: Int,
    val totalCorrect: Int,
    val totalQuestions: Int,
    val totalQuizzes: Int,
    val currentStreakDays: Int,
    val maxStreakDays: Int,
    val perfectQuizzes: Int,
    val differentSubjects: Int,
    val differentPdfs: Int
)

data class Achievement(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val unlocked: Boolean
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val cardsRepository = CardsRepository(application.applicationContext)
    private val completedQuizRepository = CompletedQuizRepository(application.applicationContext)
    private val summariesRepository = SummariesRepository(application.applicationContext)

    private val _completedQuizzes = MutableStateFlow<List<CompletedQuiz>>(emptyList())
    val completedQuizzes: StateFlow<List<CompletedQuiz>> = _completedQuizzes.asStateFlow()

    private val _cards = MutableStateFlow<List<CardItem>>(emptyList())
    val cards: StateFlow<List<CardItem>> = _cards.asStateFlow()

    private val _userStats = MutableStateFlow<UserStats?>(null)
    val userStats: StateFlow<UserStats?> = _userStats.asStateFlow()

    private val _achievements = MutableStateFlow<List<Achievement>>(emptyList())
    val achievements: StateFlow<List<Achievement>> = _achievements.asStateFlow()

    private val _summaries = MutableStateFlow<List<SummaryEntry>>(emptyList())
    val summaries: StateFlow<List<SummaryEntry>> = _summaries.asStateFlow()

    data class GamificationEvent(val type: Type, val title: String, val message: String) {
        enum class Type { LEVEL_UP, ACHIEVEMENT_UNLOCKED }
    }

    private val _events = MutableSharedFlow<GamificationEvent>(replay = 4)
    val events: SharedFlow<GamificationEvent> = _events.asSharedFlow()

    private var lastStats: UserStats? = null
    private var lastUnlockedAchievementIds: Set<String> = emptySet()
    private var gamificationInitialized: Boolean = false

    init {
        loadCards()
        loadCompletedQuizzes()
        loadSummaries()
    }

    fun loadCards() {
        viewModelScope.launch {
            val entries = cardsRepository.loadCards()
            _cards.value = entries.map { e ->
                CardItem(id = e.id, question = e.question, answer = e.answer, folder = e.folder)
            }
        }
    }

    private fun loadCompletedQuizzes() {
        viewModelScope.launch {
            val quizzes = completedQuizRepository.loadCompletedQuizzes()
            val filtered = filterQuizzesByExistingPdfs(quizzes)
            if (filtered.size != quizzes.size) {
                completedQuizRepository.saveCompletedQuizzes(filtered)
            }
            _completedQuizzes.value = filtered
            recalculateStatsAndAchievements(filtered)
        }
    }

    private fun loadSummaries() {
        viewModelScope.launch {
            _summaries.value = summariesRepository.loadSummaries()
        }
    }

    fun addCompletedQuiz(quiz: CompletedQuiz) {
        val updated = filterQuizzesByExistingPdfs(listOf(quiz) + _completedQuizzes.value)
        _completedQuizzes.value = updated
        completedQuizRepository.saveCompletedQuizzes(updated)
        recalculateStatsAndAchievements(updated)
    }

    fun removeCard(id: String) {
        cardsRepository.removeCard(id)
        _cards.value = _cards.value.filter { it.id != id }
    }

    fun clearQuizzes() {
        _completedQuizzes.value = emptyList()
        completedQuizRepository.clearAll()
        _userStats.value = null
        _achievements.value = emptyList()
        lastStats = null
        lastUnlockedAchievementIds = emptySet()
    }

    fun addSummary(pdfName: String, pdfUri: android.net.Uri?, content: String) {
        val entry = SummaryEntry(
            id = java.util.UUID.randomUUID().toString(),
            pdfName = pdfName,
            pdfUriString = pdfUri?.toString(),
            content = content,
            createdAt = System.currentTimeMillis()
        )
        _summaries.value = listOf(entry) + _summaries.value
        summariesRepository.addSummary(entry)
    }

    fun removeSummary(id: String) {
        _summaries.value = _summaries.value.filterNot { it.id == id }
        summariesRepository.removeSummary(id)
    }

    private fun filterQuizzesByExistingPdfs(quizzes: List<CompletedQuiz>): List<CompletedQuiz> {
        return quizzes.filter { quiz ->
            val uri = quiz.pdfUri ?: return@filter false
            when (uri.scheme) {
                "file" -> {
                    val path = uri.path ?: return@filter false
                    File(path).exists()
                }
                else -> true
            }
        }
    }

    private fun recalculateStatsAndAchievements(quizzes: List<CompletedQuiz>) {
        if (quizzes.isEmpty()) {
            _userStats.value = null
            _achievements.value = emptyList()
            return
        }

        val totalQuizzes = quizzes.size
        val totalCorrect = quizzes.sumOf { it.score }
        val totalQuestions = quizzes.sumOf { it.totalQuestions }
        val perfectQuizzes = quizzes.count { it.score == it.totalQuestions && it.totalQuestions > 0 }
        val differentSubjects = quizzes.map { it.subject }.toSet().size
        val differentPdfs = quizzes.mapNotNull { it.pdfName }.toSet().size

        // XP simplu: 10 XP per răspuns corect + bonus pentru quiz-uri perfecte
        val xpFromCorrect = totalCorrect * 10
        val xpFromPerfect = perfectQuizzes * 50
        val xp = xpFromCorrect + xpFromPerfect

        // Nivel: crește la fiecare 200 XP (minim nivel 1)
        val level = (xp / 200) + 1

        // Streak-uri de zile consecutive
        val daysWithQuiz = quizzes
            .map { java.util.Date(it.completedAt) }
            .map {
                val cal = java.util.Calendar.getInstance().apply { time = it }
                java.time.LocalDate.of(
                    cal.get(java.util.Calendar.YEAR),
                    cal.get(java.util.Calendar.MONTH) + 1,
                    cal.get(java.util.Calendar.DAY_OF_MONTH)
                )
            }
            .toSet()
            .sorted()

        var currentStreak = 0
        var maxStreak = 0
        if (daysWithQuiz.isNotEmpty()) {
            currentStreak = 1
            maxStreak = 1
            for (i in 1 until daysWithQuiz.size) {
                val prev = daysWithQuiz[i - 1]
                val cur = daysWithQuiz[i]
                val diff = java.time.Period.between(prev, cur).days
                if (diff == 1) {
                    currentStreak += 1
                } else if (diff > 1) {
                    maxStreak = maxOf(maxStreak, currentStreak)
                    currentStreak = 1
                }
            }
            maxStreak = maxOf(maxStreak, currentStreak)
        }

        val stats = UserStats(
            level = level,
            xp = xp,
            totalCorrect = totalCorrect,
            totalQuestions = totalQuestions,
            totalQuizzes = totalQuizzes,
            currentStreakDays = currentStreak,
            maxStreakDays = maxStreak,
            perfectQuizzes = perfectQuizzes,
            differentSubjects = differentSubjects,
            differentPdfs = differentPdfs
        )

        val newAchievements = computeAchievements(quizzes, stats)

        val currentlyUnlocked = newAchievements.filter { it.unlocked }.map { it.id }.toSet()

        // Prima inițializare: doar setăm starea, fără evenimente vizuale
        if (!gamificationInitialized) {
            _userStats.value = stats
            _achievements.value = newAchievements
            lastStats = stats
            lastUnlockedAchievementIds = currentlyUnlocked
            gamificationInitialized = true
            return
        }

        val oldStats = lastStats

        _userStats.value = stats
        _achievements.value = newAchievements

        // Detect level-up
        if (oldStats != null && stats.level > oldStats.level) {
            viewModelScope.launch {
                _events.emit(
                    GamificationEvent(
                        type = GamificationEvent.Type.LEVEL_UP,
                        title = "Level up!",
                        message = "Ai ajuns la nivelul ${stats.level}. Bravo!"
                    )
                )
            }
        }

        // Detect newly unlocked achievements
        val newlyUnlocked = currentlyUnlocked - lastUnlockedAchievementIds
        if (newlyUnlocked.isNotEmpty()) {
            val byId = newAchievements.associateBy { it.id }
            viewModelScope.launch {
                newlyUnlocked.forEach { id ->
                    val ach = byId[id]
                    if (ach != null) {
                        _events.emit(
                            GamificationEvent(
                                type = GamificationEvent.Type.ACHIEVEMENT_UNLOCKED,
                                title = "Achievement deblocat!",
                                message = "${ach.icon} ${ach.name} – ${ach.description}"
                            )
                        )
                    }
                }
            }
        }

        lastStats = stats
        lastUnlockedAchievementIds = currentlyUnlocked
    }

    private fun computeAchievements(
        quizzes: List<CompletedQuiz>,
        stats: UserStats
    ): List<Achievement> {
        val achievements = mutableListOf<Achievement>()

        fun add(id: String, name: String, desc: String, icon: String, unlocked: Boolean) {
            achievements.add(Achievement(id = id, name = name, description = desc, icon = icon, unlocked = unlocked))
        }

        val percentages = quizzes.map { it.percentage }

        // Helperi pentru condiții mai complexe
        val quizzesByDay = quizzes.groupBy { quiz ->
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = quiz.completedAt }
            java.time.LocalDate.of(
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH) + 1,
                cal.get(java.util.Calendar.DAY_OF_MONTH)
            )
        }

        val hasMarathonDay = quizzesByDay.values.any { it.size >= 5 }

        val hasIntenseSession = run {
            val sorted = quizzes.sortedBy { it.completedAt }
            var ok = false
            for (i in sorted.indices) {
                val start = sorted[i].completedAt
                var count = 1
                for (j in i + 1 until sorted.size) {
                    val diffMinutes = (sorted[j].completedAt - start) / (1000 * 60)
                    if (diffMinutes <= 60) {
                        count++
                        if (count >= 3) {
                            ok = true
                            break
                        }
                    } else break
                }
                if (ok) break
            }
            ok
        }

        val hasEveningSprint = quizzes.any {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = it.completedAt }
            cal.get(java.util.Calendar.HOUR_OF_DAY) >= 22
        }

        val hasMorningLearner = quizzes.any {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = it.completedAt }
            cal.get(java.util.Calendar.HOUR_OF_DAY) < 8
        }

        // Revenire spectaculoasă & Nu renunța – pe același subiect+pdf
        var hasBigComeback = false
        var hasDontGiveUp = false
        val bySubjectPdf = quizzes.groupBy { it.subject to (it.pdfName ?: "") }
        bySubjectPdf.values.forEach { list ->
            val sorted = list.sortedBy { it.completedAt }
            for (i in 1 until sorted.size) {
                val prev = sorted[i - 1]
                val cur = sorted[i]
                val diff = cur.percentage - prev.percentage
                if (diff >= 30) hasBigComeback = true
                if (prev.percentage < 50 && cur.percentage >= 70) hasDontGiveUp = true
            }
        }

        // Memorie de elefant – aceeași întrebare corectă de 3 ori
        val questionCorrectCounts = mutableMapOf<String, Int>()
        quizzes.forEach { quiz ->
            quiz.questions.forEachIndexed { index, q ->
                val selected = quiz.selectedAnswers[index]
                if (selected != null && selected == q.correctIndex) {
                    val key = q.question.trim().lowercase()
                    questionCorrectCounts[key] = (questionCorrectCounts[key] ?: 0) + 1
                }
            }
        }
        val hasElephantMemory = questionCorrectCounts.values.any { it >= 3 }

        val heavyPdfQuizCount = quizzes.count { it.totalQuestions >= 20 }

        // 1. Primii pași
        add(
            id = "first_quiz",
            name = "Primii pași",
            desc = "Completează primul tău quiz.",
            icon = "🥉",
            unlocked = stats.totalQuizzes >= 1
        )

        // 2. Începător curios
        add(
            id = "curious_beginner",
            name = "Începător curios",
            desc = "Răspunde corect la 20 de întrebări.",
            icon = "🔍",
            unlocked = stats.totalCorrect >= 20
        )

        // 3. Student dedicat
        add(
            id = "dedicated_student",
            name = "Student dedicat",
            desc = "Răspunde corect la 100 de întrebări.",
            icon = "📜",
            unlocked = stats.totalCorrect >= 100
        )

        // 4. Maestru al grilelor
        add(
            id = "quiz_master",
            name = "Maestru al grilelor",
            desc = "Răspunde corect la 500 de întrebări.",
            icon = "👑",
            unlocked = stats.totalCorrect >= 500
        )

        // 5. Perfect Score
        add(
            id = "perfect_score_once",
            name = "Perfect Score",
            desc = "Obține 100% la un quiz.",
            icon = "💯",
            unlocked = stats.perfectQuizzes >= 1
        )

        // 6. Perfecționist
        add(
            id = "perfectionist",
            name = "Perfecționist",
            desc = "Obține 100% la 5 quiz-uri.",
            icon = "⭐",
            unlocked = stats.perfectQuizzes >= 5
        )

        // 7. Maratonist de quiz-uri
        add(
            id = "quiz_marathon",
            name = "Maratonist de quiz-uri",
            desc = "Completează 5 quiz-uri într-o zi.",
            icon = "🏃",
            unlocked = hasMarathonDay
        )

        // 8. Sesiune intensă
        add(
            id = "intense_session",
            name = "Sesiune intensă",
            desc = "Completează 3 quiz-uri în mai puțin de 60 de minute.",
            icon = "⏱️",
            unlocked = hasIntenseSession
        )

        // 9. Explorator de subiecte
        add(
            id = "subject_explorer",
            name = "Explorator de subiecte",
            desc = "Completează quiz-uri din 5 subiecte diferite.",
            icon = "🌍",
            unlocked = stats.differentSubjects >= 5
        )

        // 10. Bibliotecar digital
        add(
            id = "digital_librarian",
            name = "Bibliotecar digital",
            desc = "Fă quiz-uri din 5 PDF-uri diferite.",
            icon = "📚",
            unlocked = stats.differentPdfs >= 5
        )

        // 11. Enciclopedist
        add(
            id = "encyclopedist",
            name = "Enciclopedist",
            desc = "Fă quiz-uri din 10 PDF-uri diferite.",
            icon = "📖",
            unlocked = stats.differentPdfs >= 10
        )

        // 12. 10 zile la rând
        add(
            id = "streak_10_days",
            name = "10 zile la rând",
            desc = "Învață 10 zile consecutive.",
            icon = "📅",
            unlocked = stats.maxStreakDays >= 10
        )

        // 13. Lună de disciplină
        add(
            id = "streak_30_days",
            name = "Lună de disciplină",
            desc = "Învață 30 de zile consecutive.",
            icon = "🌙",
            unlocked = stats.maxStreakDays >= 30
        )

        // 14. Revenire spectaculoasă
        add(
            id = "big_comeback",
            name = "Revenire spectaculoasă",
            desc = "Crește scorul cu 30 de puncte procentuale pe același subiect/PDF.",
            icon = "📈",
            unlocked = hasBigComeback
        )

        // 15. Nu renunța!
        add(
            id = "dont_give_up",
            name = "Nu renunța!",
            desc = "După un scor <50%, ajungi la ≥70% pe același subiect/PDF.",
            icon = "💪",
            unlocked = hasDontGiveUp
        )

        // 16. Memorie de elefant
        add(
            id = "elephant_memory",
            name = "Memorie de elefant",
            desc = "Răspunzi corect de 3 ori la aceeași întrebare în quiz-uri diferite.",
            icon = "🐘",
            unlocked = hasElephantMemory
        )

        // 17. Sprint de seară
        add(
            id = "evening_sprint",
            name = "Sprint de seară",
            desc = "Completezi un quiz după ora 22:00.",
            icon = "🌜",
            unlocked = hasEveningSprint
        )

        // 18. Matinal studioș
        add(
            id = "morning_learner",
            name = "Matinal studioș",
            desc = "Completezi un quiz înainte de ora 8:00.",
            icon = "🌅",
            unlocked = hasMorningLearner
        )

        // 19. Prietenul PDF-urilor grele
        add(
            id = "heavy_pdf_friend",
            name = "Prietenul PDF-urilor grele",
            desc = "Completezi 3 quiz-uri din PDF-uri \"grele\" (minim 20 de întrebări).",
            icon = "🏋️",
            unlocked = heavyPdfQuizCount >= 3
        )

        // 20. Geniu în devenire
        add(
            id = "genius_in_making",
            name = "Geniu în devenire",
            desc = "Atingi nivelul 10.",
            icon = "💡",
            unlocked = stats.level >= 10
        )

        return achievements
    }
}
