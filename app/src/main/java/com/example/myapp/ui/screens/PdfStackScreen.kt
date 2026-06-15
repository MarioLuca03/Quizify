package com.example.myapp.ui.screens

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapp.ui.viewmodel.PdfStackViewModel
import com.example.myapp.ui.viewmodel.PdfStackViewModelFactory
import com.example.myapp.utils.PdfStorageManager
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfStackScreen(
    onBack: () -> Unit,
    onOpenPdf: (Uri, String?) -> Unit = { _, _ -> },
    viewModel: PdfStackViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = PdfStackViewModelFactory(LocalContext.current.applicationContext as Application)
    )
) {
    val context = LocalContext.current
    val pdfItems by viewModel.pdfItems.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var showNameDialog by remember { mutableStateOf(false) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pdfName by remember { mutableStateOf("") }
    var selectedFolderIndex by remember { mutableStateOf(0) }
    var newFolderName by remember { mutableStateOf("") }
    var showNewFolderField by remember { mutableStateOf(false) }
    var folderDropdownExpanded by remember { mutableStateOf(false) }

    val foldersToShow = remember(folders, pdfItems) {
        if (folders.isNotEmpty()) folders
        else pdfItems.map { it.category.ifBlank { "Altele" } }.distinct()
    }
    val pdfsInSelectedFolder = remember(pdfItems, selectedFolder) {
        selectedFolder?.let { folder ->
            pdfItems.withIndex().filter { it.value.category.ifBlank { "Altele" } == folder }
                .map { it.index to it.value }
        }.orEmpty()
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            if (pdfItems.size < 5) {
                pendingUri = it
                pdfName = ""
                selectedFolderIndex = 0
                newFolderName = ""
                showNewFolderField = false
                showNameDialog = true
            }
        }
    }

    if (showNameDialog && pendingUri != null) {
        val folderOptions = folders.ifEmpty { listOf("Altele") } + "Folder nou"
        val effectiveFolderIndex = selectedFolderIndex.coerceIn(0, folderOptions.size - 1)
        val isNewFolder = folderOptions.getOrNull(effectiveFolderIndex) == "Folder nou"

        AlertDialog(
            onDismissRequest = {
                showNameDialog = false
                pendingUri = null
                showNewFolderField = false
            },
            title = { Text("Adaugă PDF") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = pdfName,
                        onValueChange = { pdfName = it },
                        label = { Text("Nume PDF") },
                        placeholder = { Text("ex: Curs Istorie") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Folder", style = MaterialTheme.typography.labelMedium)
                    ExposedDropdownMenuBox(
                        expanded = folderDropdownExpanded,
                        onExpandedChange = { expanded -> folderDropdownExpanded = expanded }
                    ) {
                        OutlinedTextField(
                            value = if (isNewFolder) newFolderName.ifEmpty { "Nume folder nou..." } else (folderOptions.getOrNull(effectiveFolderIndex) ?: "Altele"),
                            onValueChange = { value -> if (isNewFolder) newFolderName = value },
                            readOnly = !isNewFolder,
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = folderDropdownExpanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = folderDropdownExpanded,
                            onDismissRequest = { folderDropdownExpanded = false }
                        ) {
                            folderOptions.forEachIndexed { index, label ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        selectedFolderIndex = index
                                        showNewFolderField = (label == "Folder nou")
                                        folderDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    if (isNewFolder) {
                        OutlinedTextField(
                            value = newFolderName,
                            onValueChange = { newFolderName = it },
                            label = { Text("Nume folder nou") },
                            placeholder = { Text("ex: Istorie") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = pdfName.trim().ifBlank { "PDF ${pdfItems.size + 1}" }
                        val folder = if (isNewFolder) newFolderName.trim().ifBlank { "Altele" } else (folderOptions.getOrNull(effectiveFolderIndex)?.takeIf { it != "Folder nou" } ?: "Altele")
                        pendingUri?.let { uri ->
                            var hasPersistablePermission = false
                            try {
                                context.contentResolver.takePersistableUriPermission(
                                    uri,
                                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                                hasPersistablePermission = true
                            } catch (e: Exception) { }
                            coroutineScope.launch {
                                PdfStorageManager.savePdf(context, uri).fold(
                                    onSuccess = { savedFile ->
                                        viewModel.addPdf(Uri.fromFile(savedFile), name, folder)
                                    },
                                    onFailure = {
                                        if (hasPersistablePermission) viewModel.addPdf(uri, name, folder)
                                    }
                                )
                            }
                        }
                        showNameDialog = false
                        pendingUri = null
                        pdfName = ""
                        newFolderName = ""
                    }
                ) { Text("Adaugă") }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false; pendingUri = null }) {
                    Text("Anulează")
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = selectedFolder ?: "Fișierele mele",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedFolder != null) selectedFolder = null
                        else onBack()
                    }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Înapoi",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                        enabled = pdfItems.size < 5
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Adaugă fișier",
                            tint = if (pdfItems.size < 5)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                selectedFolder != null -> {
                    if (pdfsInSelectedFolder.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Nu ai fișiere în acest folder.\nApasă + pentru a adăuga.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = pdfsInSelectedFolder,
                                key = { (index, _) -> "p_$index" }
                            ) { (index, item) ->
                                PdfItemCard(
                                    pdfItem = item,
                                    onOpen = { onOpenPdf(item.uri, item.name) },
                                    onDelete = { viewModel.removePdf(index) }
                                )
                            }
                        }
                    }
                }
                foldersToShow.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Fișierele mele",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )
                        Text(
                            text = "Nu ai încă niciun folder.\nApasă + pentru a adăuga primul fișier.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp)
                    ) {
                        Text(
                            text = "Fișierele mele",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, bottom = 24.dp)
                        )
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(items = foldersToShow, key = { it }) { folderName ->
                                val count = pdfItems.count { it.category.ifBlank { "Altele" } == folderName }
                                FolderCard(
                                    folderName = folderName,
                                    fileCount = count,
                                    onClick = { selectedFolder = folderName }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderCard(
    folderName: String,
    fileCount: Int,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folderName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (fileCount == 1) "1 fișier" else "$fileCount fișiere",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun PdfItemCard(
    pdfItem: com.example.myapp.ui.viewmodel.PdfItem,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        onClick = onOpen
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = pdfItem.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Șterge",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}


