package com.example.myapp.navigation

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.myapp.ui.screens.HomeScreen
import com.example.myapp.ui.screens.LoginScreen
import com.example.myapp.ui.screens.PdfStackScreen
import com.example.myapp.ui.screens.QuizInputScreen
import com.example.myapp.ui.screens.QuizScreen
import com.example.myapp.ui.screens.RegisterScreen
import com.example.myapp.ui.screens.ChangePasswordScreen
import com.example.myapp.ui.screens.DeleteAccountScreen
import com.example.myapp.ui.screens.ProfileScreen
import com.example.myapp.ui.screens.CompletedQuizReviewScreen
import com.example.myapp.ui.screens.FastSummaryScreen
import com.example.myapp.ui.screens.SubiecteScreen
import com.example.myapp.ui.screens.PdfViewerScreen
import com.example.myapp.BuildConfig
import com.example.myapp.ui.viewmodel.FastSummaryViewModel
import com.example.myapp.ui.viewmodel.FastSummaryViewModelFactory
import com.example.myapp.ui.viewmodel.SubiecteViewModel
import com.example.myapp.ui.viewmodel.SubiecteViewModelFactory
import com.example.myapp.ui.viewmodel.PdfStackViewModel
import com.example.myapp.ui.viewmodel.PdfStackViewModelFactory
import com.example.myapp.ui.viewmodel.ProfileViewModel
import com.example.myapp.ui.viewmodel.ProfileViewModelFactory
import com.example.myapp.data.repository.SessionPreferences
import com.google.firebase.auth.FirebaseAuth

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object Home : Screen("home")
    object PdfStack : Screen("pdf_stack")
    object Subiecte : Screen("subiecte")
    object FastSummary : Screen("fast_summary")
    object Profile : Screen("profile")
    object ChangePassword : Screen("change_password")
    object DeleteAccount : Screen("delete_account")
    object QuizInput : Screen("quiz_input")
    object Quiz : Screen("quiz/{subject}/{numQuestions}") {
        fun createRoute(subject: String, numQuestions: Int) =
"quiz/${android.net.Uri.encode(subject)}/$numQuestions"
    }
    object CompletedQuizReview : Screen("completed_quiz_review?quizId={quizId}") {
        fun createRoute(quizId: String) = "completed_quiz_review?quizId=${android.net.Uri.encode(quizId)}"
    }
    object PdfViewer : Screen("pdf_viewer?uri={uri}&title={title}") {
        fun createRoute(uri: android.net.Uri, title: String?) =
            "pdf_viewer?uri=${android.net.Uri.encode(uri.toString())}&title=${title?.let { android.net.Uri.encode(it) } ?: ""}"
    }
}

@Composable
fun NavGraph(navController: NavHostController) {
    val context = LocalContext.current
    val groqApiKey = BuildConfig.GROQ_API_KEY
    val sessionPrefs = remember(context) { SessionPreferences(context.applicationContext) }
    val auth = remember { FirebaseAuth.getInstance() }

    LaunchedEffect(Unit) {
        val rememberMe = sessionPrefs.isRememberMeEnabled()
        if (!rememberMe && auth.currentUser != null) {
            auth.signOut()
        }
        if (rememberMe && auth.currentUser != null) {
            navController.navigate(Screen.Home.route) {
                popUpTo(Screen.Login.route) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Login.route
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onBackToLogin = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onGenerateQuiz = {
                    navController.navigate(Screen.QuizInput.route)
                },
                onSubiecte = {
                    navController.navigate(Screen.Subiecte.route)
                },
                onFastSummary = {
                    navController.navigate(Screen.FastSummary.route)
                },
                onProfil = {
                    navController.navigate(Screen.Profile.route)
                }
            )
        }

        composable(Screen.FastSummary.route) {
            val context = LocalContext.current
            val homeEntry = remember(navController) {
                navController.getBackStackEntry(Screen.Home.route)
            }
            val pdfStackViewModel: PdfStackViewModel = viewModel(
                homeEntry,
                factory = PdfStackViewModelFactory(context.applicationContext as Application)
            )
            val pdfItems by pdfStackViewModel.pdfItems.collectAsState()
            val fastSummaryViewModel: FastSummaryViewModel = viewModel(
                factory = FastSummaryViewModelFactory(
                    context.applicationContext as Application,
                    groqApiKey
                )
            )
            FastSummaryScreen(
                onBack = { navController.popBackStack() },
                pdfItems = pdfItems,
                viewModel = fastSummaryViewModel
            )
        }

        composable(Screen.Subiecte.route) {
            val context = LocalContext.current
            val homeEntry = remember(navController) {
                navController.getBackStackEntry(Screen.Home.route)
            }
            val pdfStackViewModel: PdfStackViewModel = viewModel(
                homeEntry,
                factory = PdfStackViewModelFactory(context.applicationContext as Application)
            )
            val pdfItems by pdfStackViewModel.pdfItems.collectAsState()
            val subiecteViewModel: SubiecteViewModel = viewModel(
                factory = SubiecteViewModelFactory(
                    context.applicationContext as Application,
                    groqApiKey
                )
            )
            SubiecteScreen(
                onBack = { navController.popBackStack() },
                pdfItems = pdfItems,
                viewModel = subiecteViewModel
            )
        }

        composable(Screen.PdfStack.route) {
            val context = LocalContext.current
            val homeEntry = remember(navController) {
                navController.getBackStackEntry(Screen.Home.route)
            }
            val viewModel: PdfStackViewModel = viewModel(
                homeEntry,
                factory = PdfStackViewModelFactory(context.applicationContext as Application)
            )

            PdfStackScreen(
                onBack = { navController.popBackStack() },
                onOpenPdf = { uri, title ->
                    navController.navigate(Screen.PdfViewer.createRoute(uri, title))
                },
                viewModel = viewModel
            )
        }

        composable(
            route = Screen.PdfViewer.route,
            arguments = listOf(
                navArgument("uri") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val uriStr = backStackEntry.arguments?.getString("uri") ?: ""
            val title = backStackEntry.arguments?.getString("title")?.takeIf { it.isNotEmpty() }
            val pdfUri = android.net.Uri.parse(android.net.Uri.decode(uriStr))
            PdfViewerScreen(
                pdfUri = pdfUri,
                title = title,
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Profile.route) {
            val context = LocalContext.current
            val homeEntry = remember(navController) {
                navController.getBackStackEntry(Screen.Home.route)
            }
            val profileViewModel: ProfileViewModel = viewModel(
                homeEntry,
                factory = ProfileViewModelFactory(context.applicationContext as Application)
            )

            ProfileScreen(
                onChangePassword = {
                    navController.navigate(Screen.ChangePassword.route)
                },
                onDeleteAccount = {
                    navController.navigate(Screen.DeleteAccount.route)
                },
                onLogout = {
                    FirebaseAuth.getInstance().signOut()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onBack = {
                    navController.popBackStack()
                },
                onFisiereleMele = {
                    navController.navigate(Screen.PdfStack.route)
                },
                onQuizClick = { quiz ->
                    navController.navigate(Screen.CompletedQuizReview.createRoute(quiz.id))
                },
                viewModel = profileViewModel
            )
        }
        
        composable(
            route = Screen.CompletedQuizReview.route,
            arguments = listOf(
                navArgument("quizId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val quizId = backStackEntry.arguments?.getString("quizId") ?: ""

            val homeEntry = remember(navController) {
                navController.getBackStackEntry(Screen.Home.route)
            }
            val profileViewModel: ProfileViewModel = viewModel(
                homeEntry,
                factory = ProfileViewModelFactory(LocalContext.current.applicationContext as Application)
            )
            val completedQuizzes by profileViewModel.completedQuizzes.collectAsState()
            
            val quiz = completedQuizzes.find { it.id == quizId }
            
            if (quiz != null) {
                CompletedQuizReviewScreen(
                    quiz = quiz,
                    onBack = {
                        navController.popBackStack()
                    }
                )
            } else {
                LaunchedEffect(Unit) {
                    navController.popBackStack()
                }
            }
        }

        composable(Screen.ChangePassword.route) {
            ChangePasswordScreen(
                onPasswordChanged = {
                    navController.popBackStack()
                },
                onCancel = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.DeleteAccount.route) {
            DeleteAccountScreen(
                onAccountDeleted = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onCancel = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.QuizInput.route) {
            val context = LocalContext.current
            val homeEntry = remember(navController) {
                navController.getBackStackEntry(Screen.Home.route)
            }
            val viewModel: PdfStackViewModel = viewModel(
                homeEntry,
                factory = PdfStackViewModelFactory(context.applicationContext as Application)
            )
            val pdfItems by viewModel.pdfItems.collectAsState()
            
            QuizInputScreen(
                apiKey = groqApiKey,
                pdfItems = pdfItems,
                onBack = {
                    navController.popBackStack()
                },
                onGenerateQuiz = { subject, level, numQuestions ->
                    navController.navigate(Screen.Quiz.createRoute(subject, numQuestions))
                },
                onGenerateQuizFromPdf = { pdfUri, numQuestions, isExamMode ->
                    navController.navigate(
                        "quiz_from_pdf?pdfUri=${android.net.Uri.encode(pdfUri.toString())}" +
                            "&numQuestions=$numQuestions&examMode=$isExamMode"
                    )
                }
            )
        }

        composable(
            route = "quiz_from_pdf?pdfUri={pdfUri}&numQuestions={numQuestions}&examMode={examMode}",
            arguments = listOf(
                navArgument("pdfUri") { type = NavType.StringType },
                navArgument("numQuestions") { type = NavType.IntType; defaultValue = 15 },
                navArgument("examMode") { type = NavType.BoolType; defaultValue = true }
            )
        ) { backStackEntry ->
            val context = LocalContext.current
            val pdfUriString = backStackEntry.arguments?.getString("pdfUri") ?: ""
            val numQuestions = backStackEntry.arguments?.getInt("numQuestions") ?: 15
            val isExamMode = backStackEntry.arguments?.getBoolean("examMode") ?: true
            val pdfUri = android.net.Uri.parse(android.net.Uri.decode(pdfUriString))
            
            val homeEntry = remember(navController) {
                navController.getBackStackEntry(Screen.Home.route)
            }
            val pdfStackViewModel: PdfStackViewModel = viewModel(
                homeEntry,
                factory = PdfStackViewModelFactory(context.applicationContext as Application)
            )
            val pdfName = pdfStackViewModel.getPdfName(pdfUri)
            
            val profileViewModel: ProfileViewModel = viewModel(
                homeEntry,
                factory = ProfileViewModelFactory(context.applicationContext as Application)
            )
            val completedQuizzes by profileViewModel.completedQuizzes.collectAsState()

            QuizScreen(
                apiKey = groqApiKey,
                subject = "Quiz din PDF",
                numQuestions = numQuestions,
                isExamMode = isExamMode,
                pdfUri = pdfUri,
                pdfName = pdfName,
                completedQuizzes = completedQuizzes,
                onQuizCompleted = { completedQuiz ->
                    profileViewModel.addCompletedQuiz(completedQuiz)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.Quiz.route,
            arguments = listOf(
                navArgument("subject") { type = NavType.StringType },
                navArgument("numQuestions") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val subject = android.net.Uri.decode(backStackEntry.arguments?.getString("subject") ?: "")
            val numQuestions = backStackEntry.arguments?.getInt("numQuestions") ?: 15

            val context = LocalContext.current
            val homeEntry = remember(navController) {
                navController.getBackStackEntry(Screen.Home.route)
            }
            val profileViewModel: ProfileViewModel = viewModel(
                homeEntry,
                factory = ProfileViewModelFactory(context.applicationContext as Application)
            )
            val completedQuizzes by profileViewModel.completedQuizzes.collectAsState()

            QuizScreen(
                apiKey = groqApiKey,
                subject = subject,
                numQuestions = numQuestions,
                isExamMode = true,
                pdfUri = null,
                pdfName = null,
                completedQuizzes = completedQuizzes,
                onQuizCompleted = { completedQuiz ->
                    profileViewModel.addCompletedQuiz(completedQuiz)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

