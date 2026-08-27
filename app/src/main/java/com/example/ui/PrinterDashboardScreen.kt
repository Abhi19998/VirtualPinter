package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.DropdownMenu

import androidx.compose.material3.DropdownMenuItem

import androidx.compose.foundation.gestures.detectTapGestures

import androidx.compose.ui.input.pointer.pointerInput

import androidx.compose.foundation.ExperimentalFoundationApi

import androidx.compose.foundation.combinedClickable


import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PendingPrintJob
import com.example.data.PrintJobEntity
import com.example.ui.components.FreePlanBadge
import com.example.ui.components.ProActivationDialog
import com.example.ui.components.ProBadge
import com.example.ui.components.UsageLimitCard
import com.example.utils.StorageHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.example.ui.components.AppBrandLogo
import com.example.ui.components.LivePrinterRadarWave
import com.example.ui.components.AnimatedCheckmarkCelebration
import com.example.ui.components.AnimatedSaveSuccessBanner
import com.example.ui.components.PdfSaveSuccessDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterDashboardScreen(
    viewModel: PrinterViewModel,
    onNavigateToWelcome: () -> Unit,
    onNavigateToWebShare: () -> Unit,
    onNavigateToLogin: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val networkInfo by viewModel.networkInfo.collectAsState()
    val isServerRunning by viewModel.isServerRunning.collectAsState()
    val serverStatusMessage by viewModel.serverStatusMessage.collectAsState()
    val printJobs by viewModel.printJobs.collectAsState()
    val totalCount by viewModel.totalCount.collectAsState()
    val isSimulating by viewModel.isSimulating.collectAsState()
    val selectedFolderPath by viewModel.selectedFolderPath.collectAsState()
    val isCustomFolder by viewModel.isCustomFolder.collectAsState()
    val pendingPrintJob by viewModel.pendingPrintJob.collectAsState()
    val jobToRename by viewModel.jobToRename.collectAsState()
    val saveProgressState by viewModel.saveProgressState.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val userEmail by viewModel.userEmail.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val isPro by viewModel.isPro.collectAsState()
    val appTitle by viewModel.appTitle.collectAsState()
    val receivedPrintsCount by viewModel.receivedPrintsCount.collectAsState()
    val conversionsCount by viewModel.conversionsCount.collectAsState()
    val proActivationKey by viewModel.proActivationKey.collectAsState()
    var showProDialog by remember { mutableStateOf(false) }
    var savedJobForCelebration by remember { mutableStateOf<PrintJobEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(saveProgressState) {
        if (saveProgressState is SaveProgressState.Success) {
            savedJobForCelebration = (saveProgressState as SaveProgressState.Success).job
        }
    }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showUserMenu by remember { mutableStateOf(false) }
    var jobToDelete by remember { mutableStateOf<PrintJobEntity?>(null) }
    var showClearAllConfirmDialog by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.selectCustomFolder(uri)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.saveSnackbarEvents.collect { event ->
            val result = snackbarHostState.showSnackbar(
                message = event.message,
                actionLabel = event.actionLabel,
                withDismissAction = true,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed && event.job != null) {
                viewModel.openPdf(event.job)
            }
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.testTag("save_snackbar_host")
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionColor = MaterialTheme.colorScheme.inversePrimary,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AppBrandLogo(
                            size = 36.dp,
                            isAnimated = false
                        )
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = appTitle,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (isPro) {
                                    ProBadge(isSmall = true)
                                } else {
                                    FreePlanBadge(onClickUpgrade = { showProDialog = true })
                                }
                            }
                            Text(
                                text = if (isServerRunning) "Ports 9100/8080 • Online" else "Ready to connect",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isServerRunning) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = onNavigateToWebShare,
                        modifier = Modifier
                            .testTag("web_share_top_button")
                            .padding(end = 4.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Web Share",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.refreshNetworkInfo()
                            viewModel.syncFilesFromDisk(showMessage = true, forceIncludeAll = true)
                        },
                        modifier = Modifier.testTag("refresh_network_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh & Sync"
                        )
                    }
                    IconButton(
                        onClick = onNavigateToWelcome,
                        modifier = Modifier.testTag("welcome_guide_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = "Guide"
                        )
                    }

                    // Account / Login Menu
                    Box {
                        IconButton(
                            onClick = {
                                if (isLoggedIn) {
                                    showUserMenu = true
                                } else {
                                    onNavigateToLogin()
                                }
                            },
                            modifier = Modifier.testTag("account_menu_button")
                        ) {
                            if (isLoggedIn) {
                                Surface(
                                    modifier = Modifier.size(30.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = userName?.firstOrNull()?.uppercase() ?: "U",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = "Sign In / Account",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showUserMenu,
                            onDismissRequest = { showUserMenu = false }
                        ) {
                            if (isLoggedIn) {
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = userName ?: "User",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = userEmail ?: "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {},
                                    leadingIcon = {
                                        Icon(Icons.Default.Person, contentDescription = null)
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(if (isPro) "PRO Account Active" else "Activate PRO License")
                                            if (isPro) ProBadge(isSmall = true)
                                        }
                                    },
                                    onClick = {
                                        showUserMenu = false
                                        showProDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.WorkspacePremium,
                                            contentDescription = null,
                                            tint = if (isPro) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Set / Change MPIN") },
                                    onClick = {
                                        showUserMenu = false
                                        viewModel.navigateToScreen(5)
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Pin, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Switch Account") },
                                    onClick = {
                                        showUserMenu = false
                                        onNavigateToLogin()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.SwitchAccount, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sign Out", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showUserMenu = false
                                        viewModel.signOut()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    viewModel.refreshNetworkInfo()
                    viewModel.syncFilesFromDisk(showMessage = true, forceIncludeAll = true)
                    delay(1000)
                    isRefreshing = false
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)

        ) {
            LazyColumn(
            modifier = Modifier
                .fillMaxSize()

                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Pro & Limits Usage Status Card
            item {
                UsageLimitCard(
                    isPro = isPro,
                    receivedPrints = receivedPrintsCount,
                    conversions = conversionsCount,
                    onActivateProClick = { showProDialog = true }
                )
            }

            // 1. IP Address & Connection Card at Top
            item {
                IpAddressCard(
                    ipAddress = networkInfo.ipAddress,
                    port = networkInfo.port,
                    connectionType = networkInfo.connectionType,
                    isConnected = networkInfo.isConnected,
                    onCopyIp = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Printer IP", networkInfo.ipAddress)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "IP Address copied: ${networkInfo.ipAddress}", Toast.LENGTH_SHORT).show()
                    },
                    onShowGuide = { showHelpDialog = true },
                    onOpenWebShare = onNavigateToWebShare
                )
            }

            // 2. Start / Stop Printer Server Control Card
            item {
                PrinterControlCard(
                    isRunning = isServerRunning,
                    statusText = serverStatusMessage,
                    isSimulating = isSimulating,
                    onToggleServer = { viewModel.toggleServer() },
                    onSendTestPrint = { viewModel.sendTestPrint("ps") },
                    onOpenPrintSettings = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_PRINT_SETTINGS))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot open print settings", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onOpenWebShare = onNavigateToWebShare
                )
            }

            // 3. Web Share Button
            item {
                WebShareDashboardBannerCard(
                    onOpenWebShare = onNavigateToWebShare
                )
            }

            // 4. Visual Progress Indicator for Saving to Downloads
            item {
                SaveProgressIndicatorBanner(
                    saveState = saveProgressState,
                    onOpenPdf = { job -> viewModel.openPdf(job) }
                )
            }

            // 5. Received Print Jobs Box Header
            item {
                ReceivedJobsBoxHeader(
                    totalCount = totalCount,
                    selectedFolderPath = selectedFolderPath,
                    isCustomFolder = isCustomFolder,
                    onSelectFolder = {
                        folderPickerLauncher.launch(null)
                    },
                    onResetFolder = {
                        viewModel.resetToDefaultFolder()
                    },
                    onOpenFolder = {
                        StorageHelper.openFolderInFileManager(context)
                    },
                    onSyncStorage = {
                        viewModel.syncFilesFromDisk(showMessage = true, forceIncludeAll = true)
                    },
                    onClearAll = {
                        if (totalCount > 0) {
                            showClearAllConfirmDialog = true
                        }
                    }
                )
            }

            // 6. Received Print Jobs List
            if (printJobs.isEmpty()) {
                item {
                    EmptyPrintJobsCard(
                        selectedFolderPath = selectedFolderPath,
                        onSelectFolder = {
                            folderPickerLauncher.launch(null)
                        },
                        onSendTest = { viewModel.sendTestPrint("ps") },
                        onSyncStorage = { viewModel.syncFilesFromDisk(showMessage = true, forceIncludeAll = true) }
                    )
                }
            } else {
                items(printJobs, key = { it.id }) { job ->
                    PrintJobCard(
                        job = job,
                        onPreview = { viewModel.openPreview(PreviewDocumentSource.Saved(job)) },
                        onOpenPdf = { viewModel.openPdf(job) },
                        onShare = { viewModel.sharePdf(job) },
                        onRename = { viewModel.promptRenameJob(job) },
                        onDelete = { jobToDelete = job }
                    )
                }
            }
        }
        }
    }

    // 1. Dialog for Incoming Print Job to choose custom filename
    if (pendingPrintJob != null) {
        val job = pendingPrintJob!!
        SaveCustomFilenameDialog(
            initialFileName = job.defaultName,
            originalFormat = job.originalFormat,
            clientIp = job.clientIp,
            pageCount = job.pageCount,
            onPreview = {
                viewModel.openPreview(PreviewDocumentSource.Pending(job))
            },
            onConfirm = { customName ->
                viewModel.savePendingPrintJob(job, customName)
            },
            onDismiss = {
                viewModel.dismissPendingPrintJob(job)
            }
        )
    }

    // 2. Dialog to rename an existing saved file
    if (jobToRename != null) {
        val job = jobToRename!!
        RenameFilenameDialog(
            job = job,
            onConfirm = { newName ->
                viewModel.confirmRenameJob(job, newName)
            },
            onDismiss = {
                viewModel.promptRenameJob(null)
            }
        )
    }

    // 3. Confirmation Dialog before permanently deleting a file from the list
    if (jobToDelete != null) {
        val job = jobToDelete!!
        DeleteConfirmationDialog(
            fileName = job.fileName,
            fileSizeBytes = job.fileSizeBytes,
            format = job.originalFormat,
            onConfirm = {
                viewModel.deleteJob(job)
                jobToDelete = null
            },
            onDismiss = {
                jobToDelete = null
            }
        )
    }

    // 4. Confirmation Dialog before clearing all print jobs
    if (showClearAllConfirmDialog) {
        ClearAllConfirmationDialog(
            totalCount = totalCount,
            onConfirm = {
                viewModel.clearAllJobs()
                showClearAllConfirmDialog = false
                Toast.makeText(context, "Print history cleared", Toast.LENGTH_SHORT).show()
            },
            onDismiss = {
                showClearAllConfirmDialog = false
            }
        )
    }

    if (showHelpDialog) {
        PcSetupDialog(
            ipAddress = networkInfo.ipAddress,
            port = networkInfo.port,
            onDismiss = { showHelpDialog = false }
        )
    }

    if (showProDialog) {
        ProActivationDialog(
            isPro = isPro,
            currentKey = proActivationKey,
            receivedPrints = receivedPrintsCount,
            conversions = conversionsCount,
            onActivateWithKey = { key ->
                viewModel.activateProWithKey(key)
            },
            onDismiss = { showProDialog = false }
        )
    }

    savedJobForCelebration?.let { job ->
        PdfSaveSuccessDialog(
            fileName = job.fileName,
            filePath = job.filePath,
            fileSizeBytes = job.fileSizeBytes,
            job = job,
            onOpenPdf = {
                viewModel.openPdf(job)
                savedJobForCelebration = null
            },
            onSharePdf = {
                viewModel.sharePdf(job)
                savedJobForCelebration = null
            },
            onDismiss = {
                savedJobForCelebration = null
            }
        )
    }
}

@Composable
fun IpAddressCard(
    ipAddress: String,
    port: Int,
    connectionType: String,
    isConnected: Boolean,
    onCopyIp: () -> Unit,
    onShowGuide: () -> Unit,
    onOpenWebShare: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ip_address_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Connection badge with animated radar wave
                Surface(
                    color = if (isConnected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            LivePrinterRadarWave(
                                isRunning = isConnected,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isConnected) Color(0xFF16A34A) else MaterialTheme.colorScheme.error)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = connectionType,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onShowGuide,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.HelpOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Setup Guide",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "YOUR PHONE'S PRINTER IP ADDRESS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.8.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Highlighted IP Card with Gradient Accent
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp)),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = ipAddress,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "RAW Port: $port",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF8B5CF6).copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "Web: :8080",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF7C3AED),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    FilledIconButton(
                        onClick = onCopyIp,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("copy_ip_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy IP",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PrinterControlCard(
    isRunning: Boolean,
    statusText: String,
    isSimulating: Boolean,
    onToggleServer: () -> Unit,
    onSendTestPrint: () -> Unit,
    onOpenPrintSettings: () -> Unit,
    onOpenWebShare: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("printer_control_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LivePrinterRadarWave(
                            isRunning = isRunning,
                            modifier = Modifier.fillMaxSize()
                        )
                        LivePulsingStatusDot(isRunning = isRunning)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isRunning) "PRINTER SERVER ACTIVE" else "PRINTER SERVER STOPPED",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isRunning) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Start / Stop Button
            Button(
                onClick = onToggleServer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("toggle_server_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isRunning) "Stop Virtual Printer Server" else "Start Virtual Printer Server",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Secondary Action Row: Test Print & Android Print Settings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onSendTestPrint,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("test_print_button"),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isSimulating
                ) {
                    if (isSimulating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Test Print (.ps)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                OutlinedButton(
                    onClick = onOpenPrintSettings,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("system_print_settings_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Print Settings",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun LivePulsingStatusDot(isRunning: Boolean) {
    if (!isRunning) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color.Gray)
        )
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_dot")
    val pulseAlpha = infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_dot_alpha"
    )

    Box(
        modifier = Modifier
            .size(10.dp)
            .graphicsLayer {
                alpha = pulseAlpha.value
            }
            .clip(CircleShape)
            .background(Color(0xFF16A34A))
    )
}

@Composable
fun WebShareDashboardBannerCard(
    ipAddress: String = "",
    isServerRunning: Boolean = true,
    onOpenWebShare: () -> Unit
) {
    Button(
        onClick = onOpenWebShare,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("open_web_share_hub_button"),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary
        )
    ) {
        Icon(
            imageVector = Icons.Default.CloudSync,
            contentDescription = null,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Web Share",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ReceivedJobsBoxHeader(
    totalCount: Int,
    selectedFolderPath: String = "Virtual Printer/",
    isCustomFolder: Boolean = false,
    onSelectFolder: () -> Unit = {},
    onResetFolder: () -> Unit = {},
    onOpenFolder: () -> Unit = {},
    onSyncStorage: () -> Unit = {},
    onClearAll: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("received_jobs_header_box"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FolderSpecial,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Received Print Files",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Text(
                            text = "$totalCount Recv",
                            modifier = Modifier.padding(horizontal = 4.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onSyncStorage,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync from storage",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    if (totalCount > 0) {
                        TextButton(
                            onClick = onClearAll,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text("Clear", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Dedicated Folder Path Badge with "Select Folder" Action
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelectFolder() }
                    .testTag("select_folder_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Folder",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = selectedFolderPath,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (isCustomFolder) {
                        TextButton(
                            onClick = onResetFolder,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text("Default", style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(modifier = Modifier.width(2.dp))
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier.clickable { onSelectFolder() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Select Folder",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PrintJobCard(
    job: PrintJobEntity,
    onPreview: () -> Unit,
    onOpenPdf: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    val dateFormatted = remember(job.receivedTimestamp) {
        SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(Date(job.receivedTimestamp))
    }
    val formattedSize = remember(job.fileSizeBytes) {
        val kb = job.fileSizeBytes / 1024.0
        if (kb >= 1024) {
            String.format(Locale.getDefault(), "%.1f MB", kb / 1024.0)
        } else {
            String.format(Locale.getDefault(), "%.1f KB", kb)
        }
    }

    val isDirectPdf = remember(job.fileName) { job.fileName.endsWith(".pdf", ignoreCase = true) }
    val isPsd = remember(job.fileName) { job.fileName.endsWith(".psd", ignoreCase = true) }
    val isPs = remember(job.fileName) { job.fileName.endsWith(".ps", ignoreCase = true) || job.fileName.endsWith(".eps", ignoreCase = true) }
    val hasPdf = remember(isDirectPdf, job.status, job.originalFormat) {
        isDirectPdf || job.status.contains("PDF", ignoreCase = true) || job.originalFormat.contains("PDF", ignoreCase = true)
    }

    val formatIcon = when {
        isDirectPdf || hasPdf -> Icons.Default.PictureAsPdf
        isPsd -> Icons.Default.Image
        isPs -> Icons.Default.Description
        else -> Icons.Default.InsertDriveFile
    }

    val badgeBg = when {
        isDirectPdf || hasPdf -> MaterialTheme.colorScheme.errorContainer
        isPsd -> MaterialTheme.colorScheme.primaryContainer
        isPs -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val badgeFg = when {
        isDirectPdf || hasPdf -> MaterialTheme.colorScheme.onErrorContainer
        isPsd -> MaterialTheme.colorScheme.onPrimaryContainer
        isPs -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val actionIcon = if (hasPdf) Icons.Default.Preview else Icons.Default.OpenInNew

    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("job_card_${job.id}")
                .combinedClickable(
                    onClick = { onPreview() },
                    onLongClick = { showMenu = true }
                ),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // File Format Badge Icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = formatIcon,
                        contentDescription = if (hasPdf) "PDF Available" else "PostScript",
                        tint = badgeFg,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = job.fileName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = formattedSize,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dateFormatted,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Virtual Printer/",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (showMenu) {
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                DropdownMenuItem(
                    text = { Text("Preview / Open") },
                    onClick = { showMenu = false; onPreview() },
                    leadingIcon = { Icon(actionIcon, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Open in Folder") },
                    onClick = {
                        showMenu = false
                        StorageHelper.openFolderInFileManager(context)
                    },
                    leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Copy File Path") },
                    onClick = {
                        showMenu = false
                        val path = if (job.filePath.isNotEmpty()) job.filePath else "/storage/emulated/0/Virtual Printer/${job.fileName}"
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("File Path", path)
                        clipboard?.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied path: $path", Toast.LENGTH_SHORT).show()
                    },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = { showMenu = false; onRename() },
                    leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Share") },
                    onClick = { showMenu = false; onShare() },
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Clear from UI", color = MaterialTheme.colorScheme.error) },
                    onClick = { showMenu = false; onDelete() },
                    leadingIcon = { Icon(Icons.Default.DeleteOutline, tint = MaterialTheme.colorScheme.error, contentDescription = null) }
                )
            }
        }
    }
}
data class Tuple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

@Composable
fun SaveCustomFilenameDialog(
    initialFileName: String,
    originalFormat: String,
    clientIp: String,
    pageCount: Int,
    onPreview: () -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val fileExt = remember(initialFileName) {
        initialFileName.substringAfterLast('.', "ps").lowercase(Locale.ROOT)
    }

    var fileNameInput by remember(initialFileName) {
        val clean = if (initialFileName.contains('.')) {
            initialFileName.substringBeforeLast('.')
        } else {
            initialFileName
        }
        mutableStateOf(clean)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SaveAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        title = {
            Text(
                text = "Save Print Job",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Info Banner
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Print,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Incoming Print Stream",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "From $clientIp • $originalFormat (Kept as authentic .$fileExt)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Interactive Preview Prompt Button
                OutlinedButton(
                    onClick = onPreview,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_preview_document_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View & Inspect Document", fontWeight = FontWeight.SemiBold)
                }

                Text(
                    text = "Enter a custom filename for saving to your Downloads folder:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = fileNameInput,
                    onValueChange = { fileNameInput = it },
                    label = { Text("Filename") },
                    placeholder = { Text("e.g., PrintJob") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (fileNameInput.isNotEmpty()) {
                            IconButton(onClick = { fileNameInput = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear"
                                )
                            }
                        }
                    },
                    suffix = {
                        Text(
                            text = ".$fileExt",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_filename_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Destination: Virtual Printer/",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = fileNameInput.trim().ifEmpty { initialFileName }
                    val fullName = if (finalName.contains('.')) finalName else "$finalName.$fileExt"
                    onConfirm(fullName)
                },
                modifier = Modifier.testTag("dialog_save_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save to Downloads")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dialog_cancel_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun RenameFilenameDialog(
    job: PrintJobEntity,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val fileExt = remember(job.fileName) {
        job.fileName.substringAfterLast('.', "ps").lowercase(Locale.ROOT)
    }

    var fileNameInput by remember(job.fileName) {
        val clean = if (job.fileName.contains('.')) {
            job.fileName.substringBeforeLast('.')
        } else {
            job.fileName
        }
        mutableStateOf(clean)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DriveFileRenameOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        title = {
            Text(
                text = "Rename & Save As",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Update the filename in your Downloads directory:",
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = fileNameInput,
                    onValueChange = { fileNameInput = it },
                    label = { Text("New Filename") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    suffix = {
                        Text(
                            text = ".$fileExt",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_filename_input"),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = fileNameInput.trim().ifEmpty { job.fileName }
                    val fullName = if (finalName.contains('.')) finalName else "$finalName.$fileExt"
                    onConfirm(fullName)
                },
                modifier = Modifier.testTag("rename_save_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("rename_cancel_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DeleteConfirmationDialog(
    fileName: String,
    fileSizeBytes: Long = 0L,
    format: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val formattedSize = remember(fileSizeBytes) {
        if (fileSizeBytes <= 0L) null
        else {
            val kb = fileSizeBytes / 1024.0
            if (kb >= 1024) String.format(Locale.getDefault(), "%.1f MB", kb / 1024.0)
            else String.format(Locale.getDefault(), "%.1f KB", kb)
        }
    }

    val ext = remember(fileName) {
        format?.uppercase(Locale.ROOT)
            ?: fileName.substringAfterLast('.', "").uppercase(Locale.ROOT).ifEmpty { "FILE" }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("delete_confirmation_dialog"),
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        title = {
            Text(
                text = "Clear from List?",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Are you sure you want to clear this file from the UI list?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = ext.take(4),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = fileName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (formattedSize != null) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Size: $formattedSize",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "The physical file remains safely in your Virtual Printer storage folder.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("confirm_delete_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Clear from UI")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("cancel_delete_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ClearAllConfirmationDialog(
    totalCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("clear_all_confirmation_dialog"),
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        title = {
            Text(
                text = "Clear History from UI?",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Are you sure you want to clear all $totalCount print jobs from the UI display?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "📁 All original files will remain safe in your Virtual Printer storage folder.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("confirm_clear_all_button")
            ) {
                Text("Clear All from UI")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("cancel_clear_all_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SaveProgressIndicatorBanner(
    saveState: SaveProgressState,
    onOpenPdf: (PrintJobEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = saveState !is SaveProgressState.Idle,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        when (saveState) {
            is SaveProgressState.Saving -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("save_progress_indicator_saving"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Saving file to Downloads...",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = saveState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )
                    }
                }
            }
            is SaveProgressState.Success -> {
                AnimatedSaveSuccessBanner(
                    fileName = saveState.job.fileName,
                    message = saveState.message,
                    onOpenPdf = { onOpenPdf(saveState.job) }
                )
            }
            is SaveProgressState.Error -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("save_progress_indicator_error"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Saving Failed",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = saveState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
            is SaveProgressState.Idle -> {}
        }
    }
}

@Composable
fun EmptyPrintJobsCard(
    selectedFolderPath: String = "Virtual Printer/",
    onSelectFolder: () -> Unit = {},
    onSendTest: () -> Unit = {},
    onSyncStorage: () -> Unit = {}
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_jobs_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(68.dp),
                contentAlignment = Alignment.Center
            ) {
                AppBrandLogo(size = 60.dp, isAnimated = true)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "No Received Files Yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Send print jobs from PC, drop files into $selectedFolderPath, or select a folder to load documents into the app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onSelectFolder()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Folder: $selectedFolderPath",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Change",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Select Folder",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSelectFolder,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).testTag("empty_select_folder_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Select Folder", fontWeight = FontWeight.SemiBold)
                }

                FilledTonalButton(
                    onClick = onSyncStorage,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).testTag("empty_sync_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Scan Folder", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            FilledTonalButton(
                onClick = onSendTest,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Print,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Send Test Page", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}


@Composable
fun PcSetupDialog(
    ipAddress: String,
    port: Int,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "How to Connect Your PC",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Windows Setup:",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "1. Ensure PC and Phone are on the same Wi-Fi network.\n" +
                            "2. Open Windows Settings -> Printers & Scanners -> Add Device.\n" +
                            "3. Click 'The printer that I want isn't listed'.\n" +
                            "4. Select 'Add a printer using IP address or hostname'.\n" +
                            "5. Device type: TCP/IP Device.\n" +
                            "6. Hostname or IP address: $ipAddress\n" +
                            "7. Port: $port (Standard TCP/IP / RAW).\n" +
                            "8. For simple text, choose 'Generic / Text Only'.\n" +
                            "9. For graphics/PDFs, use the Web Upload feature below.\n\n" +
                            "🌐 WEB UPLOAD (EASIEST & BEST FOR PDFs):\n" +
                            "Open a browser on your PC and visit:\n" +
                            "http://$ipAddress:8080\n" +
                            "You can directly upload and send PDF documents to your phone from there!",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 6.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Mac Setup:",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "1. System Settings -> Printers & Scanners -> Add Printer.\n" +
                            "2. Click the IP icon at the top.\n" +
                            "3. Address: $ipAddress\n" +
                            "4. Protocol: HP Jetdirect - Socket.\n" +
                            "5. Use: Generic PostScript Printer (Text only) or use Web Upload (http://$ipAddress:8080) for PDFs.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got It")
            }
        }
    )
}
