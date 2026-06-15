package com.example.myapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.example.myapp.data.model.PdfSmartSummaryResult
import com.example.myapp.ui.components.tech.PdfAiActionButtons
import com.example.myapp.ui.components.tech.PdfAiBulletList
import com.example.myapp.ui.components.tech.PdfAiCenteredColumn
import com.example.myapp.ui.components.tech.PdfAiDomainPanel
import com.example.myapp.ui.components.tech.PdfAiElevatedContentCard
import com.example.myapp.ui.components.tech.PdfAiErrorCard
import com.example.myapp.ui.components.tech.PdfAiHeroTitle
import com.example.myapp.ui.components.tech.PdfAiPdfSelector
import com.example.myapp.ui.components.tech.PdfFlowLoadingAnimation
import com.example.myapp.ui.components.tech.TechPdfScaffold
import com.example.myapp.ui.components.tech.TechPrimaryButton
import com.example.myapp.ui.viewmodel.FastSummaryViewModel
import com.example.myapp.ui.viewmodel.PdfItem

@Composable
fun FastSummaryScreen(
    onBack: () -> Unit,
    pdfItems: List<PdfItem>,
    viewModel: FastSummaryViewModel
) {
    val pickedPdf by viewModel.pickedPdf.collectAsState()
    val smartResult by viewModel.smartResult.collectAsState()
    val fromCache by viewModel.fromCache.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val pdfPageCount by viewModel.pdfPageCount.collectAsState()
    val useFullDocument by viewModel.useFullDocument.collectAsState()
    val rangeFrom by viewModel.rangeFrom.collectAsState()
    val rangeTo by viewModel.rangeTo.collectAsState()
    val usedLocalAi by viewModel.usedLocalAi.collectAsState()
    val isOfflineMode by viewModel.isOfflineMode.collectAsState()

    val loadingMessages = remember(useFullDocument) {
        when {
            useFullDocument -> listOf(
                "Citesc tot documentul…",
                "Analizez continutul PDF-ului…",
                "Generez rezumatul…",
                "Aproape gata…"
            )
            else -> listOf(
                "Citesc paginile selectate…",
                "Analizez continutul ales…",
                "Generez rezumatul…",
                "Aproape gata…"
            )
        }
    }

    LaunchedEffect(Unit) {
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
        showFloatingBack = !isLoading,
        onNavigateBack = {
            when {
                smartResult != null -> viewModel.resetToPicker()
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
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    PdfFlowLoadingAnimation(messages = loadingMessages)
                }
            } else {
                PdfAiCenteredColumn {
                    PdfAiHeroTitle(
                        icon = Icons.Outlined.AutoAwesome,
                        title = "Sumarizare PDF"
                    )

                    PdfAiPdfSelector(
                        pdfItems = pdfItems,
                        selected = pickedPdf,
                        onSelect = {
                            viewModel.pickPdf(it)
                            viewModel.resetToPicker()
                        }
                    )

                    if (isOfflineMode && pickedPdf != null && smartResult == null) {
                        PdfAiErrorCard(
                            "Esti offline. Rezumatul rapid necesita internet. " +
                                "Foloseste Subiecte pentru recapitulare cu modelul local."
                        )
                    }

                    if (smartResult != null && pickedPdf != null) {
                        FastSummaryResultBlock(
                            result = smartResult!!,
                            usedLocalAi = usedLocalAi || smartResult!!.mode == FastSummaryViewModel.MODE_LOCAL,
                            fromCache = fromCache,
                            onRegenerate = { viewModel.summarize(forceRefresh = true) },
                            onNewRange = { viewModel.resetToPicker() }
                        )
                    } else if (pickedPdf != null) {
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
                        TechPrimaryButton(
                            text = "Genereaza sumar",
                            onClick = { viewModel.summarize(forceRefresh = false) },
                            enabled = !isOfflineMode && (useFullDocument || pdfPageCount != null),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FastSummaryResultBlock(
    result: PdfSmartSummaryResult,
    usedLocalAi: Boolean,
    fromCache: Boolean,
    onRegenerate: () -> Unit,
    onNewRange: () -> Unit
) {
    val scopeLine = when {
        result.scopeAll -> "Tot documentul"
        result.scopeFrom != null && result.scopeTo != null ->
            "Pagini ${result.scopeFrom}–${result.scopeTo}"
        else -> ""
    }
    val displaySummary = result.summary
    val subtitle = buildString {
        if (scopeLine.isNotBlank()) append(scopeLine)
        if (fromCache) {
            if (isNotEmpty()) append(" · ")
            append("din cache")
        }
    }
    PdfAiElevatedContentCard(
        sectionLabel = if (subtitle.isBlank()) "Rezumat" else "Rezumat · $subtitle"
    ) {
        PdfAiBulletList(displaySummary)
    }
    Spacer(Modifier.height(4.dp))
    PdfAiActionButtons(
        primaryText = "Regenereaza",
        onPrimary = onRegenerate,
        secondaryText = "Schimba intervalul",
        onSecondary = onNewRange
    )
}
