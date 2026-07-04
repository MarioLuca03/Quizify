package com.example.myapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapp.data.local.OfflineLlmModelCatalog
import com.example.myapp.data.model.AnswerEvaluation
import com.example.myapp.data.model.ExamSubjectItem
import com.example.myapp.data.model.OfflineQuizItem
import com.example.myapp.data.model.OfflineSubiectePhase
import com.example.myapp.ui.components.tech.PdfAiActionButtons
import com.example.myapp.ui.components.tech.PdfAiCenteredColumn
import com.example.myapp.ui.components.tech.PdfAiDomainPanel
import com.example.myapp.ui.components.tech.PdfAiElevatedContentCard
import com.example.myapp.ui.components.tech.PdfAiErrorCard
import com.example.myapp.ui.components.tech.PdfAiHeroTitle
import com.example.myapp.ui.components.tech.PdfAiModelDownloadOverlay
import com.example.myapp.ui.components.tech.PdfAiOfflineModelDownload
import com.example.myapp.ui.components.tech.PdfAiOfflineStatus
import com.example.myapp.ui.components.tech.PdfAiPdfSelector
import com.example.myapp.ui.components.tech.PdfFlowLoadingAnimation
import com.example.myapp.ui.components.tech.TechPdfScaffold
import com.example.myapp.ui.components.tech.TechPrimaryButton
import com.example.myapp.ui.viewmodel.PdfItem
import com.example.myapp.ui.viewmodel.SubiecteViewModel

@Composable
fun SubiecteScreen(
    onBack: () -> Unit,
    pdfItems: List<PdfItem>,
    viewModel: SubiecteViewModel
) {
    val pickedPdf by viewModel.pickedPdf.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val examPack by viewModel.examPack.collectAsState()
    val error by viewModel.error.collectAsState()
    val pdfPageCount by viewModel.pdfPageCount.collectAsState()
    val useFullDocument by viewModel.useFullDocument.collectAsState()
    val rangeFrom by viewModel.rangeFrom.collectAsState()
    val rangeTo by viewModel.rangeTo.collectAsState()
    val offlineModelReady by viewModel.offlineModelReady.collectAsState()
    val selectedOfflineModelId by viewModel.selectedOfflineModelId.collectAsState()
    val isDownloadingModel by viewModel.isDownloadingModel.collectAsState()
    val modelDownloadProgress by viewModel.modelDownloadProgress.collectAsState()
    val isOfflineMode by viewModel.isOfflineMode.collectAsState()
    val offlinePhase by viewModel.offlinePhase.collectAsState()
    val currentQuizItem by viewModel.currentQuizItem.collectAsState()
    val loadingStatus by viewModel.loadingStatus.collectAsState()
    val isModelWarmingUp by viewModel.isModelWarmingUp.collectAsState()
    val isFinalizingSession by viewModel.isFinalizingSession.collectAsState()
    val sessionNotice by viewModel.sessionNotice.collectAsState()
    val usedLocalAi by viewModel.usedLocalAi.collectAsState()
    val selectedModel = OfflineLlmModelCatalog.byId(selectedOfflineModelId)

    val canFinalizeOffline = isOfflineMode && when (offlinePhase) {
        OfflineSubiectePhase.LoadingQuestion,
        OfflineSubiectePhase.GenerationFailed,
        OfflineSubiectePhase.Question,
        OfflineSubiectePhase.Feedback -> true
        OfflineSubiectePhase.Idle -> isLoading && usedLocalAi
        else -> false
    }

    val showLoadingOverlay = when {
        isFinalizingSession -> true
        offlinePhase == OfflineSubiectePhase.GenerationFailed -> true
        isLoading &&
            (offlinePhase == OfflineSubiectePhase.LoadingQuestion ||
                examPack == null && offlinePhase == OfflineSubiectePhase.Idle) -> true
        else -> false
    }

    val loadingMessages = remember(isOfflineMode, offlinePhase, loadingStatus) {
        when {
            loadingStatus.isNotBlank() -> listOf(loadingStatus, "Model local…", "Aproape gata…")
            isOfflineMode -> listOf(
                "Filtrez fragmentele valide…",
                "Generez intrebarea…",
                "Model local…"
            )
            else -> listOf(
                "Citesc documentul…",
                "Generez subiectele…",
                "Aproape gata…"
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.onSubiecteScreenVisible()
        viewModel.refreshConnectivity()
    }

    LaunchedEffect(pickedPdf) {
        viewModel.refreshConnectivity()
    }

    LaunchedEffect(pdfItems) {
        if (pickedPdf == null && pdfItems.size == 1) {
            viewModel.pickPdf(pdfItems.first())
        }
    }

    TechPdfScaffold(
        floatingBack = true,
        showFloatingBack = !showLoadingOverlay,
        onNavigateBack = {
            when {
                offlinePhase == OfflineSubiectePhase.GenerationFailed ->
                    viewModel.dismissGenerationFailure()
                examPack != null || offlinePhase != OfflineSubiectePhase.Idle ->
                    viewModel.resetToPicker()
                else -> onBack()
            }
        }
    ) { paddingValues ->
        val bg = Brush.verticalGradient(
            listOf(
                MaterialTheme.colorScheme.background,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                MaterialTheme.colorScheme.surfaceContainerLowest,
                MaterialTheme.colorScheme.background
            )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bg)
                .padding(paddingValues)
        ) {
            if (showLoadingOverlay) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (offlinePhase == OfflineSubiectePhase.GenerationFailed && !isFinalizingSession) {
                        OfflineGenerationFailedOverlay(
                            message = error ?: loadingStatus,
                            canTryNextFragment = viewModel.canSkipToNextOfflineChunk(),
                            canFinalize = canFinalizeOffline && !isFinalizingSession,
                            onNextFragment = viewModel::skipCurrentOfflineChunk,
                            onBack = viewModel::dismissGenerationFailure,
                            onFinalize = viewModel::finalizeOfflineSession
                        )
                    } else {
                        OfflineLoadingOverlay(
                            messages = loadingMessages,
                            isFinalizing = isFinalizingSession,
                            canFinalize = canFinalizeOffline && !isFinalizingSession,
                            onFinalize = viewModel::finalizeOfflineSession
                        )
                    }
                }
            } else {
                PdfAiCenteredColumn {
                    PdfAiHeroTitle(
                        icon = Icons.Outlined.Quiz,
                        title = "Subiecte examen"
                    )

                    PdfAiPdfSelector(
                        pdfItems = pdfItems,
                        selected = pickedPdf,
                        onSelect = { viewModel.pickPdf(it) }
                    )

                    if (isOfflineMode && isModelWarmingUp) {
                        Text(
                            text = "Incarc modelul local…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (offlinePhase == OfflineSubiectePhase.Idle) {
                        if (isOfflineMode) {
                            PdfAiOfflineStatus(isOfflineMode = true)
                        }
                        PdfAiOfflineModelDownload(
                            models = OfflineLlmModelCatalog.all,
                            selectedModelId = selectedOfflineModelId,
                            offlineModelReady = offlineModelReady,
                            isDownloadingModel = isDownloadingModel,
                            modelDownloadProgress = modelDownloadProgress,
                            isLoading = isLoading,
                            isModelInstalled = viewModel::isOfflineModelInstalled,
                            onSelectModel = viewModel::setSelectedOfflineModel,
                            onDownloadModel = viewModel::downloadOfflineModel
                        )
                    }

                    when {
                        examPack != null && pickedPdf != null -> {
                            SubiecteOnlineResultBlock(
                                subjects = examPack!!.subjects,
                                viewModel = viewModel,
                                onRegenerate = { viewModel.generate() },
                                onNewRange = { viewModel.resetToPicker() }
                            )
                        }

                        offlinePhase == OfflineSubiectePhase.Question &&
                            currentQuizItem != null -> {
                            OfflineQuestionBlock(
                                item = currentQuizItem!!,
                                onSubmit = viewModel::submitOfflineAnswer,
                                onFinalize = viewModel::finalizeOfflineSession
                            )
                        }

                        offlinePhase == OfflineSubiectePhase.Feedback &&
                            currentQuizItem != null -> {
                            OfflineFeedbackBlock(
                                item = currentQuizItem!!,
                                hasMoreQuestions = viewModel.hasMoreOfflineQuestions(),
                                onContinue = viewModel::continueOfflineQuiz,
                                onFinalize = viewModel::finalizeOfflineSession
                            )
                        }

                        offlinePhase == OfflineSubiectePhase.Exhausted -> {
                            OfflineExhaustedBlock(
                                onRestart = { viewModel.resetToPicker() }
                            )
                        }

                        pickedPdf != null && offlinePhase == OfflineSubiectePhase.Idle -> {
                            PdfAiDomainPanel(
                                useFullDocument = useFullDocument,
                                isOfflineMode = isOfflineMode,
                                rangeFrom = rangeFrom,
                                rangeTo = rangeTo,
                                pdfPageCount = pdfPageCount,
                                onUseFullDocument = viewModel::setUseFullDocument,
                                onRangeFrom = viewModel::setRangeFrom,
                                onRangeTo = viewModel::setRangeTo
                            )
                            error?.let { PdfAiErrorCard(it) }
                            sessionNotice?.let { notice ->
                                Text(
                                    text = notice,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            TechPrimaryButton(
                                text = when {
                                    isOfflineMode && viewModel.hasUnusedOfflineChunks() ->
                                        "Genereaza intrebarea"
                                    isOfflineMode -> "Incepe recapitularea"
                                    else -> "Genereaza subiecte"
                                },
                                onClick = { viewModel.generate() },
                                enabled = !isDownloadingModel &&
                                    (!isOfflineMode || offlineModelReady) &&
                                    (useFullDocument || pdfPageCount != null),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            PdfAiModelDownloadOverlay(
                visible = isDownloadingModel,
                progress = modelDownloadProgress,
                modelLabel = selectedModel.displayName
            )
        }
    }
}

@Composable
private fun OfflineLoadingOverlay(
    messages: List<String>,
    isFinalizing: Boolean,
    canFinalize: Boolean,
    onFinalize: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isFinalizing) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Se opreste modelul…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            PdfFlowLoadingAnimation(messages = messages)
        }
        if (canFinalize) {
            Spacer(Modifier.height(24.dp))
            OfflineFinalizeSessionButton(onClick = onFinalize)
        }
    }
}

@Composable
private fun OfflineFinalizeSessionButton(onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Finalizeaza sesiunea")
    }
}

@Composable
private fun OfflineGenerationFailedOverlay(
    message: String,
    canTryNextFragment: Boolean,
    canFinalize: Boolean,
    onNextFragment: () -> Unit,
    onBack: () -> Unit,
    onFinalize: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        if (canTryNextFragment) {
            Text(
                text = "Poti incerca un alt fragment din PDF.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp))
            TechPrimaryButton(
                text = "Urmatorul fragment",
                onClick = onNextFragment,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Inapoi")
            }
            if (canFinalize) {
                OfflineFinalizeSessionButton(onClick = onFinalize)
            }
        } else {
            Text(
                text = "Nu mai sunt alte fragmente de incercat in acest PDF.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp))
            TechPrimaryButton(
                text = "Inapoi",
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            )
            if (canFinalize) {
                Spacer(Modifier.height(8.dp))
                OfflineFinalizeSessionButton(onClick = onFinalize)
            }
        }
    }
}

@Composable
private fun OfflineQuestionBlock(
    item: OfflineQuizItem,
    onSubmit: (String) -> Unit,
    onFinalize: () -> Unit
) {
    var answer by remember(item.pageNumber, item.question) { mutableStateOf("") }

    PdfAiElevatedContentCard(sectionLabel = "Intrebare") {
        Text(
            item.question,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Raspunsul il poti gasi la pagina ${item.pageNumber}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = answer,
        onValueChange = { answer = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Raspunsul tau") },
        minLines = 3
    )
    Spacer(Modifier.height(12.dp))
    TechPrimaryButton(
        text = "Verifica",
        onClick = { onSubmit(answer) },
        enabled = answer.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    OfflineFinalizeSessionButton(onClick = onFinalize)
}

@Composable
private fun OfflineFeedbackBlock(
    item: OfflineQuizItem,
    hasMoreQuestions: Boolean,
    onContinue: () -> Unit,
    onFinalize: () -> Unit
) {
    val eval = item.evaluation ?: return
    PdfAiElevatedContentCard(sectionLabel = "Rezultat") {
        Text(
            item.question,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Text(
            item.userAnswer.orEmpty(),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(12.dp))
        OfflineEvaluationSummary(eval = eval, pageNumber = item.pageNumber)
    }
    Spacer(Modifier.height(12.dp))
    if (hasMoreQuestions) {
        TechPrimaryButton(
            text = "Intrebarea urmatoare",
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        Text(
            "Nu mai sunt fragmente noi in acest PDF.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
    }
    Spacer(Modifier.height(8.dp))
    OfflineFinalizeSessionButton(onClick = onFinalize)
}

@Composable
private fun OfflineExhaustedBlock(onRestart: () -> Unit) {
    Text(
        "Nu s-au putut genera mai multe intrebari din fragmentele disponibile.",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(12.dp))
    TechPrimaryButton(
        text = "Alt PDF / interval",
        onClick = onRestart,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun OfflineEvaluationSummary(eval: AnswerEvaluation, pageNumber: Int) {
    val verdict = when (eval.corect) {
        "da" -> "Raspuns corect"
        "partial" -> "Raspuns partial corect"
        else -> "Raspuns incorect"
    }
    Text(
        verdict,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Raspunsul il poti gasi la pagina $pageNumber.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun EvaluationSummary(eval: AnswerEvaluation) {
    Text(
        "Scor: ${eval.scor}% · ${eval.corect.replaceFirstChar { it.uppercase() }}",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(6.dp))
    Text(eval.feedback, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun SubiecteOnlineResultBlock(
    subjects: List<ExamSubjectItem>,
    viewModel: SubiecteViewModel,
    onRegenerate: () -> Unit,
    onNewRange: () -> Unit
) {
    val scope = rememberCoroutineScope()

    subjects.forEachIndexed { index, subj ->
        OnlineSubjectCard(
            index = index,
            subject = subj,
            onVerify = { answer, onResult ->
                scope.launch {
                    onResult(
                        viewModel.evaluateOnlineAnswer(
                            question = subj.question,
                            expectedSolution = subj.solution,
                            userAnswer = answer
                        )
                    )
                }
            }
        )
        Spacer(Modifier.height(8.dp))
    }
    Spacer(Modifier.height(4.dp))
    PdfAiActionButtons(
        primaryText = "Regenereaza",
        onPrimary = onRegenerate,
        secondaryText = "Schimba intervalul",
        onSecondary = onNewRange
    )
}

@Composable
private fun OnlineSubjectCard(
    index: Int,
    subject: ExamSubjectItem,
    onVerify: (String, (Result<AnswerEvaluation>) -> Unit) -> Unit
) {
    var answer by remember(subject.question) { mutableStateOf("") }
    var showSolution by remember(subject.question) { mutableStateOf(false) }
    var evaluation by remember(subject.question) { mutableStateOf<AnswerEvaluation?>(null) }
    var isEvaluating by remember(subject.question) { mutableStateOf(false) }
    var verifyError by remember(subject.question) { mutableStateOf<String?>(null) }

    PdfAiElevatedContentCard(sectionLabel = "Subiect ${index + 1}") {
        Text(
            subject.question,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        if (!showSolution) {
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { showSolution = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Afiseaza raspunsul")
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Text(
                "Raspuns",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text(subject.solution, style = MaterialTheme.typography.bodyMedium)
        }
    }
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = answer,
        onValueChange = {
            answer = it
            if (evaluation != null) {
                evaluation = null
                verifyError = null
            }
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Raspunsul tau") },
        minLines = 3,
        enabled = !isEvaluating
    )
    Spacer(Modifier.height(12.dp))
    if (evaluation != null) {
        EvaluationSummary(evaluation!!)
    } else {
        verifyError?.let { msg ->
            Text(
                msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        if (isEvaluating) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            TechPrimaryButton(
                text = "Verifica",
                onClick = {
                    if (answer.isBlank()) return@TechPrimaryButton
                    isEvaluating = true
                    verifyError = null
                    onVerify(answer) { result ->
                        isEvaluating = false
                        result.fold(
                            onSuccess = { evaluation = it },
                            onFailure = {
                                verifyError = it.message ?: "Evaluarea a esuat. Incearca din nou."
                            }
                        )
                    }
                },
                enabled = answer.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
