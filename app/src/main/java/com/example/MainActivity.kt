package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.server.PrinterServerService
import com.example.ui.LoginScreen
import com.example.ui.MpinMode
import com.example.ui.MpinScreen
import com.example.ui.PdfPreviewScreen
import com.example.ui.PostConversionPrompt
import com.example.ui.PreviewDocumentSource
import com.example.ui.PrinterDashboardScreen
import com.example.ui.PrinterViewModel
import com.example.ui.WebShareScreen
import com.example.ui.WelcomeScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.utils.StorageHelper

class MainActivity : FragmentActivity() {

    private val viewModel: PrinterViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Permission result handled
    }

    private val requestMultiplePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Permissions result handled
        viewModel.syncStorageWithDatabase()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val permissionsToRequest = mutableListOf<String>()

        // Request notification permission on Android 13+ (TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_MEDIA_IMAGES
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestMultiplePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }

        handleIntentExtras(intent)

        setContent {
            MyApplicationTheme {
                MainContent(
                    viewModel = viewModel,
                    onRequestBiometric = { onSuccess, onError ->
                        showBiometricPrompt(onSuccess, onError)
                    }
                )
            }
        }
    }

    fun showBiometricPrompt(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val biometricManager = BiometricManager.from(this)
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            onError("Biometric authentication not configured on this device")
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_CANCELED
                    ) {
                        onError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError("Fingerprint not recognized. Try again or use MPIN.")
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Virtual PDF Printer")
            .setSubtitle("Touch fingerprint sensor to unlock")
            .setNegativeButtonText("Use MPIN")
            .build()

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            onError(e.localizedMessage ?: "Biometric prompt failed")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentExtras(intent)
    }

    private fun handleIntentExtras(intent: Intent?) {
        if (intent == null) return
        if ((intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) {
            return
        }
        val openPdfName = intent.getStringExtra("open_pdf")
        if (!openPdfName.isNullOrBlank()) {
            intent.removeExtra("open_pdf")
            viewModel.openPdfFromFileName(openPdfName)
        }
        val convertedPsName = intent.getStringExtra("converted_ps")
        if (!convertedPsName.isNullOrBlank()) {
            intent.removeExtra("converted_ps")
            viewModel.markPendingConversion(convertedPsName)
            viewModel.checkPendingConversionPsCleanup(convertedPsName)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshNetworkInfo()
        viewModel.syncFilesFromDisk(showMessage = false)
        viewModel.checkUserSessionValidity()
        handleIntentExtras(intent)
    }
}

@Composable
fun MainContent(
    viewModel: PrinterViewModel,
    onRequestBiometric: (onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit = { _, _ -> }
) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val previewDocument by viewModel.previewDocument.collectAsState()
    val postConversionPrompt by viewModel.postConversionPrompt.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val userEmail by viewModel.userEmail.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val context = LocalContext.current

    // Dialog asking user to delete .ps file if PDF is perfect after conversion
    postConversionPrompt?.let { prompt ->
        PostConversionCleanupDialog(
            prompt = prompt,
            onOpenPdf = {
                StorageHelper.openDirectFile(context, prompt.pdfFile, "Open '${prompt.pdfFileName}' with...")
            },
            onDeletePs = {
                viewModel.confirmDeleteOriginalPsFile(prompt.psFileName)
            },
            onKeepBoth = {
                viewModel.dismissPostConversionPrompt()
            }
        )
    }

    // Handle system back button when preview is open
    BackHandler(enabled = previewDocument != null) {
        viewModel.closePreview()
    }

    // Handle system back button when on Web Share screen (Screen 2)
    BackHandler(enabled = previewDocument == null && currentScreen == 2) {
        viewModel.navigateToScreen(1)
    }

    // Handle system back button when on Login screen while already logged in
    BackHandler(enabled = previewDocument == null && currentScreen == 3 && isLoggedIn) {
        viewModel.navigateToScreen(1)
    }

    AnimatedContent(
        targetState = previewDocument,
        transitionSpec = {
            (slideInVertically { height -> height } + fadeIn()) togetherWith
                    (slideOutVertically { height -> height } + fadeOut())
        },
        label = "preview_modal_transition"
    ) { previewSource ->
        if (previewSource != null) {
            PdfPreviewScreen(
                source = previewSource,
                onSaveToDownloads = if (previewSource is PreviewDocumentSource.Pending) {
                    { customName ->
                        viewModel.savePendingPrintJob(previewSource.pendingJob, customName)
                    }
                } else null,
                onDismissOrBack = {
                    if (previewSource is PreviewDocumentSource.Pending) {
                        viewModel.dismissPendingPrintJob(previewSource.pendingJob)
                    } else {
                        viewModel.closePreview()
                    }
                },
                onShare = {
                    when (previewSource) {
                        is PreviewDocumentSource.Pending -> {
                            // Can share temp file or save
                        }
                        is PreviewDocumentSource.Saved -> {
                            viewModel.sharePdf(previewSource.job)
                        }
                    }
                },
                onOpenExternal = if (previewSource is PreviewDocumentSource.Saved) {
                    { viewModel.openPdf(previewSource.job) }
                } else null,
                onRename = if (previewSource is PreviewDocumentSource.Saved) {
                    { viewModel.promptRenameJob(previewSource.job) }
                } else null,
                onDeleteJob = { job ->
                    viewModel.deleteJob(job)
                },
                onDeleteOriginalPs = { fileName ->
                    viewModel.deleteMatchingPsFile(fileName)
                },
                onMarkPendingConversion = { fileName ->
                    viewModel.markPendingConversion(fileName)
                }
            )
        } else {
            Crossfade(targetState = currentScreen, label = "screen_transition") { screen ->
                when (screen) {
                    0 -> {
                        WelcomeScreen(
                            onGetStarted = { viewModel.navigateToScreen(1) }
                        )
                    }
                    1 -> {
                        PrinterDashboardScreen(
                            viewModel = viewModel,
                            onNavigateToWelcome = { viewModel.navigateToScreen(0) },
                            onNavigateToWebShare = {
                                viewModel.navigateToScreen(2)
                            },
                            onNavigateToLogin = {
                                viewModel.navigateToScreen(3)
                            }
                        )
                    }
                    2 -> {
                        WebShareScreen(
                            viewModel = viewModel,
                            onNavigateBack = { viewModel.navigateToScreen(1) }
                        )
                    }
                    3 -> {
                        LoginScreen(
                            viewModel = viewModel,
                            onLoginSuccess = { email ->
                                if (viewModel.securityAuthManager.isMpinSet()) {
                                    viewModel.navigateToScreen(1)
                                } else {
                                    viewModel.navigateToScreen(5) // Setup MPIN
                                }
                            },
                            onBack = if (isLoggedIn) {
                                { viewModel.navigateToScreen(1) }
                            } else null
                        )
                    }
                    4 -> {
                        // Quick Unlock via MPIN or Fingerprint
                        MpinScreen(
                            mode = MpinMode.UNLOCK,
                            userName = userName,
                            userEmail = userEmail,
                            isBiometricAvailable = viewModel.isBiometricEnabled(),
                            onVerifyPin = { pin ->
                                viewModel.verifyMpin(pin)
                            },
                            onSaveNewPin = { _, _ -> },
                            onRequestBiometric = {
                                onRequestBiometric(
                                    { viewModel.onMpinUnlocked() },
                                    { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
                                )
                            },
                            onSwitchToPasswordLogin = {
                                viewModel.navigateToScreen(3)
                            },
                            onUnlockSuccess = {
                                viewModel.onMpinUnlocked()
                            }
                        )
                    }
                    5 -> {
                        // Setup MPIN Screen
                        MpinScreen(
                            mode = MpinMode.SETUP,
                            userName = userName,
                            userEmail = userEmail,
                            isBiometricAvailable = true,
                            onVerifyPin = { true },
                            onSaveNewPin = { pin, enableBiometric ->
                                viewModel.saveMpin(pin, enableBiometric)
                            },
                            onRequestBiometric = { },
                            onSwitchToPasswordLogin = {
                                viewModel.signOut()
                            },
                            onUnlockSuccess = {
                                viewModel.navigateToScreen(1)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PostConversionCleanupDialog(
    prompt: PostConversionPrompt,
    onOpenPdf: () -> Unit,
    onDeletePs: () -> Unit,
    onKeepBoth: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onKeepBoth,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFDCFCE7)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF16A34A),
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Text(
                text = "PDF Converted!",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "If your PDF file is perfect, you can delete the original .ps file to save device storage space.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = prompt.pdfFileName,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )
                            Text(
                                text = StorageHelper.formatFileSize(prompt.pdfFile.length()),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF16A34A),
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = prompt.psFileName,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )
                            Text(
                                text = StorageHelper.formatFileSize(prompt.psFile.length()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = onOpenPdf,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Check Converted PDF")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDeletePs,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Delete .ps File", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onKeepBoth,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Keep Both")
            }
        }
    )
}
