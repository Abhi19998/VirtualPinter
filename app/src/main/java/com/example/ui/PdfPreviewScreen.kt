package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PendingPrintJob
import com.example.data.PrintJobEntity
import com.example.data.ProLicenseManager
import com.example.ui.components.ProActivationDialog
import com.example.ui.components.ProBadge
import com.example.utils.StorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

sealed class PreviewDocumentSource {
    data class Pending(val pendingJob: PendingPrintJob) : PreviewDocumentSource()
    data class Saved(val job: PrintJobEntity) : PreviewDocumentSource()

    val file: File
        get() = when (this) {
            is Pending -> pendingJob.tempFile
            is Saved -> File(job.filePath)
        }

    val defaultFileName: String
        get() = when (this) {
            is Pending -> pendingJob.defaultName
            is Saved -> job.fileName
        }

    val originalFormat: String
        get() = when (this) {
            is Pending -> pendingJob.originalFormat
            is Saved -> job.originalFormat
        }

    val clientIp: String
        get() = when (this) {
            is Pending -> pendingJob.clientIp
            is Saved -> job.clientIp
        }
}

sealed class DocumentContent {
    data class Pdf(val pages: List<Bitmap>) : DocumentContent()
    data class PostScript(val totalBytes: Long) : DocumentContent()
    data class SingleImage(val bitmap: Bitmap) : DocumentContent()
    data class RawBinary(val formatName: String, val sizeBytes: Long, val hexSnippet: String) : DocumentContent()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPreviewScreen(
    source: PreviewDocumentSource,
    onSaveToDownloads: ((String) -> Unit)? = null,
    onDismissOrBack: () -> Unit,
    onShare: (() -> Unit)? = null,
    onOpenExternal: (() -> Unit)? = null,
    onRename: (() -> Unit)? = null,
    onDeleteJob: ((PrintJobEntity) -> Unit)? = null,
    onDeleteOriginalPs: ((String) -> Unit)? = null,
    onMarkPendingConversion: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var documentContent by remember { mutableStateOf<DocumentContent?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val fileExtension = remember(source.defaultFileName) {
        source.defaultFileName.substringAfterLast('.', "ps").lowercase(Locale.ROOT)
    }

    var matchingPsFile by remember(source.defaultFileName) {
        mutableStateOf(StorageHelper.findMatchingPsFile(context, source.defaultFileName))
    }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Pro License & Limits
    val proLicenseManager = remember { ProLicenseManager.getInstance(context) }
    val isPro by proLicenseManager.isPro.collectAsState()
    val receivedPrints by proLicenseManager.receivedPrintsCount.collectAsState()
    val conversions by proLicenseManager.conversionsCount.collectAsState()
    val proKey by proLicenseManager.proKey.collectAsState()
    var showProLimitDialog by remember { mutableStateOf(false) }

    // Filename input state for saving
    var customFileName by remember(source.defaultFileName) {
        val raw = source.defaultFileName
        val clean = if (raw.contains('.')) raw.substringBeforeLast('.') else raw
        mutableStateOf(clean)
    }

    // Zoom and Pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val listState = rememberLazyListState()

    // Inspect and load document in background
    LaunchedEffect(source.file) {
        isLoading = true
        errorMessage = null
        try {
            val content = withContext(Dispatchers.IO) {
                loadDocumentContent(context, source.file, fileExtension)
            }
            documentContent = content
            matchingPsFile = StorageHelper.findMatchingPsFile(context, source.defaultFileName)
            isLoading = false
        } catch (e: Exception) {
            e.printStackTrace()
            errorMessage = e.localizedMessage ?: "Failed to read document"
            isLoading = false
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(if (fileExtension in listOf("ps", "eps")) "Delete PostScript File?" else "Delete Original .PS File?") },
            text = {
                Text(
                    if (fileExtension in listOf("ps", "eps")) {
                        "Are you sure you want to delete '${source.defaultFileName}' from storage?"
                    } else {
                        "Are you sure you want to delete the original PostScript file '${matchingPsFile?.name ?: ""}'? Your converted PDF will remain safe."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        if (fileExtension in listOf("ps", "eps")) {
                            if (source is PreviewDocumentSource.Saved && onDeleteJob != null) {
                                onDeleteJob(source.job)
                            } else {
                                onDeleteOriginalPs?.invoke(source.defaultFileName)
                                onDismissOrBack()
                            }
                        } else {
                            onDeleteOriginalPs?.invoke(source.defaultFileName)
                            matchingPsFile = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = source.defaultFileName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${source.originalFormat} • From ${source.clientIp}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onDismissOrBack,
                        modifier = Modifier.testTag("preview_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (scale != 1f || offset != Offset.Zero) {
                        IconButton(onClick = {
                            scale = 1f
                            offset = Offset.Zero
                        }) {
                            Icon(
                                imageVector = Icons.Default.ZoomOutMap,
                                contentDescription = "Reset Zoom"
                            )
                        }
                    }

                    // Delete button in top bar
                    if (fileExtension in listOf("ps", "eps") || matchingPsFile != null) {
                        IconButton(
                            onClick = { showDeleteConfirmDialog = true },
                            modifier = Modifier.testTag("preview_top_delete_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete File",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // Direct Quick-Save / Download Button in Top Bar
                    if (source is PreviewDocumentSource.Pending && onSaveToDownloads != null) {
                        IconButton(
                            onClick = {
                                val finalName = customFileName.trim().ifEmpty { source.defaultFileName }
                                val fullName = if (finalName.contains('.')) finalName else "$finalName.$fileExtension"
                                onSaveToDownloads(fullName)
                            },
                            modifier = Modifier.testTag("preview_top_save_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SaveAlt,
                                contentDescription = "Save to Downloads",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (onShare != null) {
                        IconButton(
                            onClick = onShare,
                            modifier = Modifier.testTag("preview_share_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share Document"
                            )
                        }
                    }
                    if (onOpenExternal != null && fileExtension !in listOf("ps", "eps")) {
                        IconButton(
                            onClick = onOpenExternal,
                            modifier = Modifier.testTag("preview_open_external_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = "Open in External App"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                if (source is PreviewDocumentSource.Pending && onSaveToDownloads != null) {
                    // Pending save controls: Custom Filename + Save to Downloads button
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .navigationBarsPadding()
                            .imePadding(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = customFileName,
                            onValueChange = { customFileName = it },
                            label = { Text("Save Filename in Downloads") },
                            placeholder = { Text("e.g., MyPrintJob") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = {
                                if (customFileName.isNotEmpty()) {
                                    IconButton(onClick = { customFileName = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear"
                                        )
                                    }
                                }
                            },
                            suffix = {
                                Text(
                                    text = ".$fileExtension",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("preview_filename_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = onDismissOrBack,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("preview_discard_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Discard")
                            }

                            Button(
                                onClick = {
                                    val finalName = customFileName.trim().ifEmpty { source.defaultFileName }
                                    val fullName = if (finalName.contains('.')) finalName else "$finalName.$fileExtension"
                                    onSaveToDownloads(fullName)
                                },
                                modifier = Modifier
                                    .weight(1.5f)
                                    .testTag("preview_confirm_save_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SaveAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Download / Save", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else if (source is PreviewDocumentSource.Saved) {
                    // Saved document bottom controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .navigationBarsPadding(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (fileExtension in listOf("ps", "eps")) {
                            val matchingConvertedPdf = remember(source.defaultFileName) {
                                StorageHelper.findMatchingPdfFile(context, source.defaultFileName)
                            }
                            val hasConverted = matchingConvertedPdf != null && matchingConvertedPdf.exists() && matchingConvertedPdf.length() > 0

                            if (hasConverted && matchingConvertedPdf != null) {
                                Button(
                                    onClick = {
                                        StorageHelper.openDirectFile(context, matchingConvertedPdf, "Open '${matchingConvertedPdf.name}' with...")
                                    },
                                    modifier = Modifier.weight(1.2f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFDC2626),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PictureAsPdf,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open PDF", maxLines = 1, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        if (!proLicenseManager.canConvertFile()) {
                                            showProLimitDialog = true
                                        } else {
                                            proLicenseManager.recordConversion(source.defaultFileName)
                                            onMarkPendingConversion?.invoke(source.defaultFileName)
                                            val termuxCmd = StorageHelper.generateTermuxCommand(context, source.defaultFileName, context.packageName)
                                            StorageHelper.executeInTermux(context, termuxCmd)
                                        }
                                    },
                                    modifier = Modifier.weight(1.2f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFD97706),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Convert in Termux", maxLines = 1, fontWeight = FontWeight.Bold)
                                }
                            }

                            FilledTonalButton(
                                onClick = { showDeleteConfirmDialog = true },
                                modifier = Modifier.weight(0.8f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete", maxLines = 1)
                            }
                        } else {
                            if (matchingPsFile != null) {
                                FilledTonalButton(
                                    onClick = { showDeleteConfirmDialog = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Delete .PS", maxLines = 1)
                                }
                            }

                            if (onRename != null) {
                                OutlinedButton(
                                    onClick = onRename,
                                    modifier = Modifier.weight(0.85f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DriveFileRenameOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Rename", maxLines = 1)
                                }
                            }

                            if (onOpenExternal != null) {
                                val isPdf = fileExtension == "pdf"
                                val openLabel = if (isPdf) "Open PDF" else "Open File"
                                val openIcon = if (isPdf) Icons.Default.PictureAsPdf else Icons.Default.OpenInNew
                                val buttonColor = if (isPdf) Color(0xFFDC2626) else MaterialTheme.colorScheme.primary
                                Button(
                                    onClick = onOpenExternal,
                                    modifier = Modifier.weight(1.2f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = buttonColor
                                    )
                                ) {
                                    Icon(
                                        imageVector = openIcon,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(openLabel, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        ) {
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Loading & Rendering Document...",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Could not preview document",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage ?: "Unknown error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        
                        if (errorMessage?.contains("permission", ignoreCase = true) == true ||
                            errorMessage?.contains("denied", ignoreCase = true) == true ||
                            !StorageHelper.hasAllFilesPermission(context)
                        ) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    StorageHelper.requestAllFilesPermission(context)
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderSpecial,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Grant Storage Access Permission", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                documentContent != null -> {
                    when (val content = documentContent!!) {
                        is DocumentContent.Pdf -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            scale = (scale * zoom).coerceIn(1f, 4f)
                                            if (scale > 1f) {
                                                offset = Offset(
                                                    x = (offset.x + pan.x).coerceIn(-500f * (scale - 1), 500f * (scale - 1)),
                                                    y = (offset.y + pan.y).coerceIn(-800f * (scale - 1), 800f * (scale - 1))
                                                )
                                            } else {
                                                offset = Offset.Zero
                                            }
                                        }
                                    }
                            ) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                            translationX = offset.x
                                            translationY = offset.y
                                        }
                                        .padding(horizontal = 16.dp, vertical = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    if (matchingPsFile != null) {
                                        item {
                                            Card(
                                                shape = RoundedCornerShape(14.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                                                ),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(14.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                                                        modifier = Modifier.size(36.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(
                                                                imageVector = Icons.Default.Description,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.tertiary,
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = "Original PostScript file found",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                                        )
                                                        Text(
                                                            text = "${matchingPsFile?.name} (${matchingPsFile?.length() ?: 0} B)",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Button(
                                                        onClick = { showDeleteConfirmDialog = true },
                                                        shape = RoundedCornerShape(10.dp),
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = MaterialTheme.colorScheme.error
                                                        ),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.DeleteOutline,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Delete .PS", fontSize = 12.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    itemsIndexed(content.pages, key = { index, _ -> "pdf_page_$index" }) { index, bitmap ->
                                        PdfPageCard(
                                            pageNumber = index + 1,
                                            totalCount = content.pages.size,
                                            bitmap = bitmap
                                        )
                                    }
                                }

                                val currentPageNumber by remember {
                                    derivedStateOf { listState.firstVisibleItemIndex + 1 }
                                }

                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 8.dp)
                                        .shadow(4.dp, shape = CircleShape),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FilterFrames,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Page $currentPageNumber of ${content.pages.size}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                        is DocumentContent.PostScript -> {
                            TermuxConverterTabContent(
                                fileName = source.defaultFileName,
                                fileSizeBytes = content.totalBytes,
                                clientIp = source.clientIp,
                                onDeleteThisPs = { showDeleteConfirmDialog = true },
                                onGenerateBatchScript = {
                                    coroutineScope.launch {
                                        val ok = StorageHelper.createOrUpdateTermuxBatchScript(context)
                                        if (ok) {
                                            Toast.makeText(context, "Created convert_all.sh in VirtualPrinter!", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "Could not write script file", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }
                        is DocumentContent.SingleImage -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = content.bitmap.asImageBitmap(),
                                    contentDescription = "Image preview",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                        is DocumentContent.RawBinary -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(20.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Print,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(32.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = content.formatName,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "${content.sizeBytes} bytes",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "Raw Byte Inspection:",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Surface(
                                            color = Color(0xFF1E1E1E),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = content.hexSnippet,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = Color(0xFF4ADE80),
                                                modifier = Modifier.padding(12.dp)
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
    }

    if (showProLimitDialog) {
        ProActivationDialog(
            isPro = isPro,
            currentKey = proKey,
            receivedPrints = receivedPrints,
            conversions = conversions,
            onActivateWithKey = { key ->
                proLicenseManager.activateProWithKey(key)
            },
            onDismiss = { showProLimitDialog = false }
        )
    }
}

@Composable
fun TermuxConverterTabContent(
    fileName: String,
    fileSizeBytes: Long = 0L,
    clientIp: String = "",
    onDeleteThisPs: (() -> Unit)? = null,
    onGenerateBatchScript: () -> Unit
) {
    val context = LocalContext.current
    val proLicenseManager = remember { ProLicenseManager.getInstance(context) }
    var showProDialogInTab by remember { mutableStateOf(false) }
    val isPro by proLicenseManager.isPro.collectAsState()
    val receivedPrints by proLicenseManager.receivedPrintsCount.collectAsState()
    val conversions by proLicenseManager.conversionsCount.collectAsState()
    val proKey by proLicenseManager.proKey.collectAsState()

    val termuxCommand = remember(fileName) {
        StorageHelper.generateTermuxCommand(context, fileName, context.packageName)
    }
    val matchingPdf = remember(fileName) {
        StorageHelper.findMatchingPdfFile(context, fileName)
    }
    val hasPdf = matchingPdf != null && matchingPdf.exists() && matchingPdf.length() > 0

    val isPsd = remember(fileName) {
        fileName.endsWith(".psd", ignoreCase = true)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (hasPdf && matchingPdf != null) {
            // Converted PDF ready card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7)),
                    border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "PDF Converted & Ready",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color(0xFF14532D)
                                )
                                Text(
                                    text = matchingPdf.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF166534)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                StorageHelper.openDirectFile(context, matchingPdf, "Open '${matchingPdf.name}' with...")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFDC2626)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open PDF", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Document Header Summary Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isPsd) Color(0xFF4338CA).copy(alpha = 0.15f) else Color(0xFFD97706).copy(alpha = 0.15f),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPsd) Icons.Default.Brush else Icons.Default.Terminal,
                                contentDescription = null,
                                tint = if (isPsd) Color(0xFF4338CA) else Color(0xFFD97706),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = fileName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isPsd) Color(0xFFE0E7FF) else Color(0xFFFEF3C7)
                            ) {
                                Text(
                                    text = if (isPsd) "Photoshop (.psd)" else "PostScript (.ps)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPsd) Color(0xFF3730A3) else Color(0xFF92400E),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            if (fileSizeBytes > 0) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = android.text.format.Formatter.formatFileSize(context, fileSizeBytes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (clientIp.isNotEmpty() && clientIp != "Local") {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "• $clientIp",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Primary Action: Instant 1-Tap Convert in Termux
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isPsd) "Convert PSD to PDF with Termux" else "Convert to PDF with Termux",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isPsd) "Converts with ImageMagick / Ghostscript in /storage/emulated/0/VirtualPrinter" else "Converts with full vector & image fidelity via Ghostscript",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (!proLicenseManager.canConvertFile()) {
                                showProDialogInTab = true
                            } else {
                                proLicenseManager.recordConversion(fileName)
                                StorageHelper.executeInTermux(context, termuxCommand)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD97706)
                        ),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "⚡ Convert with Termux (1-Tap)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Monospace Command Preview Box with Copy
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF18181B),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "BASH COMMAND",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF59E0B)
                                )
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Termux Command", termuxCommand))
                                        Toast.makeText(context, "Copied command to clipboard!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        tint = Color(0xFFF59E0B),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = termuxCommand,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = Color(0xFF4ADE80),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }

        // Setup & Automation Instructions Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SettingsSuggest,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "One-Time Termux Setup",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        val isTermuxInstalled = remember {
                            try {
                                context.packageManager.getLaunchIntentForPackage("com.termux") != null
                            } catch (e: Exception) {
                                false
                            }
                        }
                        if (isTermuxInstalled) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.15f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Installed",
                                        color = Color(0xFF10B981),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = "Not Installed",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "To allow Virtual Printer to automatically trigger commands in Termux, run this setup once in Termux:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    val setupScript = "pkg install -y ghostscript && termux-setup-storage && mkdir -p ~/.termux && echo \"allow-external-apps = true\" >> ~/.termux/termux.properties && termux-reload-settings"

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF18181B),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = setupScript,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color(0xFF38BDF8),
                                lineHeight = 15.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Termux Setup Script", setupScript))
                            Toast.makeText(context, "Copied setup command! Paste once in Termux.", Toast.LENGTH_SHORT).show()
                            val opened = StorageHelper.launchTermux(context)
                            if (!opened) {
                                Toast.makeText(context, "Command copied! Open Termux to paste.", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy Setup Command & Open Termux")
                    }
                }
            }
        }

        // Batch script helper card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BatchPrediction,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Batch Convert Script (All .PS Files)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Convert all PS files in VirtualPrinter in one go with a 'convert_all.sh' script.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = onGenerateBatchScript,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create 'convert_all.sh' Script", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Delete PS File Card if requested
        if (onDeleteThisPs != null) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Delete PostScript File",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "Remove original '$fileName' when done",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = onDeleteThisPs,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete .PS File", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showProDialogInTab) {
        ProActivationDialog(
            isPro = isPro,
            currentKey = proKey,
            receivedPrints = receivedPrints,
            conversions = conversions,
            onActivateWithKey = { key ->
                proLicenseManager.activateProWithKey(key)
            },
            onDismiss = { showProDialogInTab = false }
        )
    }
}

@Composable
fun PdfPageCard(
    pageNumber: Int,
    totalCount: Int,
    bitmap: Bitmap,
    modifier: Modifier = Modifier
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val aspectRatio = remember(bitmap) {
        bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
    }
    val dimensionText = remember(bitmap) { "${bitmap.width} × ${bitmap.height} px" }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, shape = RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "Document Page $pageNumber of $totalCount",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio),
                contentScale = ContentScale.FillWidth
            )
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Page $pageNumber / $totalCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = dimensionText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

private suspend fun loadDocumentContent(context: Context, file: File, ext: String): DocumentContent {
    val resolvedDiskFile = StorageHelper.findFileOnDisk(context, file.name, file.absolutePath) ?: file
    val safeFile = StorageHelper.ensureInternalCopy(context, file.name, resolvedDiskFile)
    val actualFile = if (safeFile.exists() && safeFile.length() > 0) safeFile else resolvedDiskFile

    if (!actualFile.exists() || actualFile.length() == 0L) {
        throw IllegalArgumentException("Document file '${file.name}' is empty or not found in VirtualPrinter.")
    }

    if (ext == "pdf") {
        val pfd = try {
            ParcelFileDescriptor.open(actualFile, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (e: Exception) {
            if (file.exists() && file.absolutePath != actualFile.absolutePath) {
                try {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                } catch (e2: Exception) {
                    throw IllegalStateException("Storage permission denied for '${file.name}'. Please grant All Files Access in Settings.", e2)
                }
            } else {
                throw IllegalStateException("Storage permission denied for '${file.name}'. Please grant All Files Access in Settings.", e)
            }
        }
        val renderer = PdfRenderer(pfd)
        val pageCount = renderer.pageCount
        val bitmaps = mutableListOf<Bitmap>()

        val displayMetrics = context.resources.displayMetrics
        val densityScale = displayMetrics.density.coerceIn(1.5f, 3.0f)

        for (i in 0 until pageCount) {
            val page = renderer.openPage(i)
            val targetWidth = (page.width * densityScale).toInt().coerceIn(400, 2400)
            val targetHeight = (page.height * densityScale).toInt().coerceIn(600, 3200)

            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            bitmaps.add(bitmap)
        }

        renderer.close()
        pfd.close()
        return DocumentContent.Pdf(bitmaps)
    }

    if (ext in listOf("png", "jpg", "jpeg", "webp", "gif")) {
        val bitmap = BitmapFactory.decodeFile(actualFile.absolutePath)
        if (bitmap != null) {
            return DocumentContent.SingleImage(bitmap)
        }
    }

    // PostScript (.ps, .eps) - instant, zero extra load
    if (ext in listOf("ps", "eps") || actualFile.name.endsWith(".ps", ignoreCase = true)) {
        return DocumentContent.PostScript(totalBytes = actualFile.length())
    }

    // Fallback binary stream inspection
    val bytes = try { actualFile.readBytes() } catch (e: Exception) { ByteArray(0) }
    val hex = bytes.take(64).joinToString(" ") { String.format("%02X", it) }
    return DocumentContent.RawBinary(
        formatName = "${ext.uppercase()} Document",
        sizeBytes = actualFile.length(),
        hexSnippet = hex
    )
}
