package com.example.myapp.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import com.github.barteksc.pdfviewer.util.FitPolicy
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    pdfUri: Uri,
    title: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var pdfFile by remember(pdfUri) { mutableStateOf<File?>(null) }

    LaunchedEffect(pdfUri) {
        pdfFile = when (pdfUri.scheme) {
            "file" -> pdfUri.path?.let { File(it).takeIf { f -> f.exists() } }
            else -> {
                val file = File(context.cacheDir, "view_${System.currentTimeMillis()}.pdf")
                context.contentResolver.openInputStream(pdfUri)?.use { input ->
                    FileOutputStream(file).use { output -> input.copyTo(output) }
                }
                file.takeIf { it.exists() }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title ?: "PDF", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Înapoi")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (pdfFile != null) {
                AndroidView(
                    factory = { ctx ->
                        PDFView(ctx, null).apply {
                            fromFile(pdfFile!!)
                                .defaultPage(0)
                                .enableSwipe(true)
                                .swipeHorizontal(false)
                                .enableDoubletap(true)
                                .scrollHandle(DefaultScrollHandle(ctx))
                                .spacing(10)
                                .pageFitPolicy(FitPolicy.WIDTH)
                                .load()
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(0.dp))
                        .background(Color.White)
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier
                        .wrapContentSize()
                        .padding(24.dp)
                )
            }
        }
    }
}
