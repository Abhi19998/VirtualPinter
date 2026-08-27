package com.example.ui

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AuthResult
import com.example.data.FirebaseAuthService
import com.example.data.FileSystemRepository
import com.example.data.FirestoreUserService
import com.example.data.PendingPrintJob
import com.example.data.PrintJobEntity
import com.example.data.ProLicenseManager
import com.example.data.SecurityAuthManager
import com.example.data.SharedFileItem
import com.example.server.NetworkPrinterServer
import com.example.server.PrinterServerService
import com.example.utils.NetworkUtils
import com.example.utils.StorageHelper
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class SaveProgressState {
    object Idle : SaveProgressState()
    data class Saving(val fileName: String, val message: String) : SaveProgressState()
    data class Success(val job: PrintJobEntity, val message: String) : SaveProgressState()
    data class Error(val message: String) : SaveProgressState()
}

data class PostConversionPrompt(
    val psFileName: String,
    val pdfFileName: String,
    val baseName: String,
    val pdfFile: File,
    val psFile: File
)

data class SaveSnackbarEvent(
    val message: String,
    val actionLabel: String? = null,
    val job: PrintJobEntity? = null
)

class PrinterViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val printJobDao = db.printJobDao()
    private val fileSystemRepository = FileSystemRepository.getInstance(application)

    // Show all print jobs and synced storage documents in the received files list
    val printJobs: StateFlow<List<PrintJobEntity>> = fileSystemRepository.printJobs
    val diskFiles: StateFlow<List<File>> = fileSystemRepository.diskFiles
    val isStorageMonitoring: StateFlow<Boolean> = fileSystemRepository.isMonitoring
    val selectedFolderPath: StateFlow<String> = fileSystemRepository.selectedFolderPath
    val isCustomFolder: StateFlow<Boolean> = fileSystemRepository.isCustomFolder

    val totalCount: StateFlow<Int> = printJobs
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // Pending job prompting user for custom filename
    val pendingPrintJob: StateFlow<PendingPrintJob?> = NetworkPrinterServer.pendingJobForSave

    // Files staged from phone to send back to PC
    val sharedFilesForPc: StateFlow<List<SharedFileItem>> = NetworkPrinterServer.sharedFilesForPc

    // Document Preview State (either pending job or saved job)
    private val _previewDocument = MutableStateFlow<PreviewDocumentSource?>(null)
    val previewDocument: StateFlow<PreviewDocumentSource?> = _previewDocument.asStateFlow()

    // Job to rename / save as
    private val _jobToRename = MutableStateFlow<PrintJobEntity?>(null)
    val jobToRename: StateFlow<PrintJobEntity?> = _jobToRename.asStateFlow()

    // Save Progress State (for visual indicators & progress bars)
    private val _saveProgressState = MutableStateFlow<SaveProgressState>(SaveProgressState.Idle)
    val saveProgressState: StateFlow<SaveProgressState> = _saveProgressState.asStateFlow()

    // Snackbar events
    private val _saveSnackbarEvents = MutableSharedFlow<SaveSnackbarEvent>()
    val saveSnackbarEvents: SharedFlow<SaveSnackbarEvent> = _saveSnackbarEvents.asSharedFlow()

    // Current Network Info
    private val _networkInfo = MutableStateFlow(NetworkUtils.getNetworkInfo(application))
    val networkInfo: StateFlow<NetworkUtils.NetworkInfo> = _networkInfo.asStateFlow()

    // Server State
    val isServerRunning: StateFlow<Boolean> = NetworkPrinterServer.isRunning
    val serverStatusMessage: StateFlow<String> = NetworkPrinterServer.serverStatusMessage

    private val firebaseAuthService = FirebaseAuthService()
    private val firestoreUserService = FirestoreUserService()
    val securityAuthManager = SecurityAuthManager(application)
    val proLicenseManager = ProLicenseManager.getInstance(application)

    // Pro User State & Limits
    val isPro: StateFlow<Boolean> = proLicenseManager.isPro
    val receivedPrintsCount: StateFlow<Int> = proLicenseManager.receivedPrintsCount
    val conversionsCount: StateFlow<Int> = proLicenseManager.conversionsCount
    val proActivationKey: StateFlow<String?> = proLicenseManager.proKey

    val appTitle: StateFlow<String> = proLicenseManager.isPro
        .map { if (it) "Virtual PDF Printer PRO" else "Virtual PDF Printer" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, if (proLicenseManager.isUserPro()) "Virtual PDF Printer PRO" else "Virtual PDF Printer")

    private val _themeMode = MutableStateFlow(
        try {
            val prefs = application.getSharedPreferences("app_settings_theme", android.content.Context.MODE_PRIVATE)
            val savedTheme = prefs.getString("theme_mode", com.example.ui.theme.AppThemeMode.OBSIDIAN_DARK.name)
            com.example.ui.theme.AppThemeMode.valueOf(savedTheme ?: com.example.ui.theme.AppThemeMode.OBSIDIAN_DARK.name)
        } catch (e: Exception) {
            com.example.ui.theme.AppThemeMode.OBSIDIAN_DARK
        }
    )
    val themeMode: StateFlow<com.example.ui.theme.AppThemeMode> = _themeMode.asStateFlow()

    fun toggleTheme() {
        val next = if (_themeMode.value == com.example.ui.theme.AppThemeMode.OBSIDIAN_DARK) {
            com.example.ui.theme.AppThemeMode.NORDIC_LIGHT
        } else {
            com.example.ui.theme.AppThemeMode.OBSIDIAN_DARK
        }
        setThemeMode(next)
    }

    fun setThemeMode(mode: com.example.ui.theme.AppThemeMode) {
        _themeMode.value = mode
        try {
            val app = getApplication<Application>()
            val prefs = app.getSharedPreferences("app_settings_theme", android.content.Context.MODE_PRIVATE)
            prefs.edit().putString("theme_mode", mode.name).apply()
        } catch (e: Exception) {
            // Ignore
        }
    }

    // Server Telemetry / Simulated Load
    val simulatedLoad: StateFlow<Int> = MutableStateFlow(24).asStateFlow()
    val accentWeightPercent: Int = 90
    val cornerRadiusPx: Int = 6

    // Active Screen: 0 = Welcome/Instructions, 1 = Printer Server Dashboard, 2 = Web Share & Local Transfer, 3 = Login Screen, 4 = MPIN Unlock, 5 = MPIN Setup
    private val _currentScreen = MutableStateFlow(
        when {
            securityAuthManager.isMpinSet() -> 4 // MPIN set -> Quick Unlock with Fingerprint / MPIN
            firebaseAuthService.currentUser != null -> 1 // Logged in -> Dashboard
            else -> 3 // Mandatory authentication: start at Login Screen (Guest Mode removed)
        }
    )
    val currentScreen: StateFlow<Int> = _currentScreen.asStateFlow()

    // Authentication State (Firebase Auth / Email session)
    private val _isLoggedIn = MutableStateFlow(firebaseAuthService.currentUser != null)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _userEmail = MutableStateFlow<String?>(firebaseAuthService.currentUser?.email)
    val userEmail: StateFlow<String?> = _userEmail.asStateFlow()

    private val _userName = MutableStateFlow<String?>(
        firebaseAuthService.currentUser?.displayName ?: firebaseAuthService.currentUser?.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
    )
    val userName: StateFlow<String?> = _userName.asStateFlow()

    fun verifyMpin(pin: String): Boolean {
        return securityAuthManager.verifyMpin(pin)
    }

    fun saveMpin(pin: String, enableBiometric: Boolean) {
        securityAuthManager.saveMpin(pin)
        securityAuthManager.setBiometricEnabled(enableBiometric)
        _currentScreen.value = 1
    }

    fun onMpinUnlocked() {
        viewModelScope.launch(Dispatchers.IO) {
            val user = firebaseAuthService.currentUser
            if (user == null) {
                withContext(Dispatchers.Main) {
                    signOut()
                    _currentScreen.value = 3
                    Toast.makeText(getApplication(), "Session expired. Please sign in.", Toast.LENGTH_LONG).show()
                }
                return@launch
            }
            val isValid = firebaseAuthService.validateCurrentUser()
            if (!isValid) {
                withContext(Dispatchers.Main) {
                    signOut()
                    _currentScreen.value = 3
                    Toast.makeText(getApplication(), "Account blocked. Please sign in again.", Toast.LENGTH_LONG).show()
                }
            } else {
                withContext(Dispatchers.Main) {
                    _currentScreen.value = 1
                }
                proLicenseManager.startRealtimeUserSync(user.uid)
                proLicenseManager.syncUserFromFirestore(user.uid)
                firestoreUserService.syncTotalFilesCount(user.uid, printJobs.value.size)
            }
        }
    }

    fun checkUserSessionValidity() {
        viewModelScope.launch(Dispatchers.IO) {
            val user = firebaseAuthService.currentUser
            if (user != null) {
                val isValid = firebaseAuthService.validateCurrentUser()
                if (!isValid) {
                    withContext(Dispatchers.Main) {
                        signOut()
                        _currentScreen.value = 3
                        Toast.makeText(
                            getApplication(),
                            "Account blocked. Please sign in again.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    fun isBiometricEnabled(): Boolean {
        return securityAuthManager.isBiometricEnabled()
    }

    suspend fun signInWithEmail(email: String, password: String): AuthResult<String> {
        val result = firebaseAuthService.signInWithEmail(email, password)
        return when (result) {
            is AuthResult.Success -> {
                _isLoggedIn.value = true
                _userEmail.value = result.data.email
                val name = result.data.displayName ?: result.data.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
                _userName.value = name

                // Record user profile in Firestore and sync Pro license
                viewModelScope.launch(Dispatchers.IO) {
                    firestoreUserService.recordUserLogin(result.data.uid, name, result.data.email)
                    firestoreUserService.syncTotalFilesCount(result.data.uid, printJobs.value.size)
                    proLicenseManager.startRealtimeUserSync(result.data.uid)
                    proLicenseManager.syncUserFromFirestore(result.data.uid)
                }

                if (securityAuthManager.isMpinSet()) {
                    _currentScreen.value = 1
                } else {
                    _currentScreen.value = 5 // Setup MPIN for fast unlock
                }
                AuthResult.Success(result.data.email ?: email)
            }
            is AuthResult.Error -> {
                AuthResult.Error(result.message, result.exception)
            }
        }
    }

    suspend fun signUpWithEmail(name: String, email: String, password: String): AuthResult<String> {
        val result = firebaseAuthService.signUpWithEmail(name, email, password)
        return when (result) {
            is AuthResult.Success -> {
                _isLoggedIn.value = true
                _userEmail.value = result.data.email
                val effectiveName = if (name.isNotBlank()) name.trim() else result.data.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
                _userName.value = effectiveName

                // Store new user in Firestore
                viewModelScope.launch(Dispatchers.IO) {
                    firestoreUserService.createUserProfile(result.data.uid, effectiveName ?: "User", result.data.email ?: email)
                    firestoreUserService.syncTotalFilesCount(result.data.uid, printJobs.value.size)
                    proLicenseManager.startRealtimeUserSync(result.data.uid)
                    proLicenseManager.syncUserFromFirestore(result.data.uid)
                }

                _currentScreen.value = 5 // Prompt to set 4-digit MPIN
                AuthResult.Success(result.data.email ?: email)
            }
            is AuthResult.Error -> {
                AuthResult.Error(result.message, result.exception)
            }
        }
    }

    suspend fun signInWithGoogle(context: android.content.Context): AuthResult<String> {
        val result = firebaseAuthService.signInWithGoogle(context)
        return when (result) {
            is AuthResult.Success -> {
                _isLoggedIn.value = true
                _userEmail.value = result.data.email
                val name = result.data.displayName ?: result.data.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
                _userName.value = name

                // Record in Firestore and sync Pro
                viewModelScope.launch(Dispatchers.IO) {
                    firestoreUserService.recordUserLogin(result.data.uid, name, result.data.email)
                    firestoreUserService.syncTotalFilesCount(result.data.uid, printJobs.value.size)
                    proLicenseManager.startRealtimeUserSync(result.data.uid)
                    proLicenseManager.syncUserFromFirestore(result.data.uid)
                }

                if (securityAuthManager.isMpinSet()) {
                    _currentScreen.value = 1
                } else {
                    _currentScreen.value = 5 // Setup MPIN for fast unlock
                }
                AuthResult.Success(result.data.email ?: "Google User")
            }
            is AuthResult.Error -> {
                AuthResult.Error(result.message, result.exception)
            }
        }
    }

    suspend fun sendPasswordReset(email: String): AuthResult<Unit> {
        return firebaseAuthService.sendPasswordResetEmail(email)
    }

    fun signOut() {
        proLicenseManager.stopRealtimeUserSync()
        proLicenseManager.setProStatus(false, null)
        firebaseAuthService.signOut()
        securityAuthManager.clearSecurity()
        _isLoggedIn.value = false
        _userEmail.value = null
        _userName.value = null
        _currentScreen.value = 3 // Return to login screen
    }

    private val _isSimulating = MutableStateFlow(false)
    val isSimulating: StateFlow<Boolean> = _isSimulating.asStateFlow()

    // Prompt user when returning to app after PDF conversion to delete original .ps file if PDF is perfect
    private val _postConversionPrompt = MutableStateFlow<PostConversionPrompt?>(null)
    val postConversionPrompt: StateFlow<PostConversionPrompt?> = _postConversionPrompt.asStateFlow()
    private val dismissedPsConversions = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    init {
        refreshNetworkInfo()
        syncFilesFromDisk()

        // Validate Firebase user existence on launch (handles user deleted from Firebase console)
        viewModelScope.launch(Dispatchers.IO) {
            val user = firebaseAuthService.currentUser
            if (user != null) {
                val isValid = firebaseAuthService.validateCurrentUser()
                if (!isValid) {
                    withContext(Dispatchers.Main) {
                        signOut()
                        _currentScreen.value = 3
                        Toast.makeText(
                            getApplication(),
                            "Account blocked. Please sign in again.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    proLicenseManager.startRealtimeUserSync(user.uid)
                    proLicenseManager.syncUserFromFirestore(user.uid)
                }
            }
        }

        // When a new print job or file is received in real-time:
        viewModelScope.launch {
            NetworkPrinterServer.lastJobReceived.collect { savedJob ->
                val fileName = savedJob.fileName
                val isPs = fileName.endsWith(".ps", ignoreCase = true) || fileName.endsWith(".eps", ignoreCase = true)
                val isPdf = fileName.endsWith(".pdf", ignoreCase = true)

                if (isPs) {
                    val matchingPdf = StorageHelper.findMatchingPdfFile(getApplication(), savedJob.fileName, savedJob.filePath)
                    if (matchingPdf != null && matchingPdf.exists() && matchingPdf.length() > 0) {
                        _previewDocument.value = null
                        StorageHelper.openDirectFile(getApplication(), matchingPdf, "Open '${matchingPdf.name}' with...")
                    } else {
                        _previewDocument.value = PreviewDocumentSource.Saved(savedJob)
                    }
                } else if (isPdf) {
                    _previewDocument.value = null
                    openFile(savedJob)
                } else {
                    // For Web Share / Local Transfer of ANY generic file (Excel, Word, Images, Audio, Video, ZIP, APK, etc.)
                    // Automatically open with its matching app on phone without forcing PDF preview
                    _previewDocument.value = null
                    openFile(savedJob)
                }
                if (_currentScreen.value == 0) {
                    _currentScreen.value = 1
                }
                syncFilesFromDisk()
            }
        }

        // FileSystemRepository handles dual FileObserver + periodic recursive polling
        fileSystemRepository.startMonitoring()

        // Sync files count and Pro status with Firestore when user is logged in
        viewModelScope.launch {
            val uid = firebaseAuthService.currentUser?.uid
            if (uid != null) {
                proLicenseManager.syncUserFromFirestore(uid)
            }
            printJobs.collect { jobs ->
                val currentUid = firebaseAuthService.currentUser?.uid
                if (currentUid != null) {
                    firestoreUserService.syncTotalFilesCount(currentUid, jobs.size)
                }
            }
        }
    }

    suspend fun activateProWithKey(key: String): Result<String> {
        val result = proLicenseManager.activateProWithKey(key)
        if (result.isSuccess) {
            val uid = firebaseAuthService.currentUser?.uid
            if (uid != null) {
                proLicenseManager.syncUserFromFirestore(uid)
            }
        }
        return result
    }

    fun canReceivePrint(): Boolean = proLicenseManager.canReceivePrint()

    fun canConvertFile(): Boolean = proLicenseManager.canConvertFile()

    fun recordConversion(fileName: String? = null) {
        proLicenseManager.recordConversion(fileName)
    }

    fun getRemainingPrints(): Int = proLicenseManager.getRemainingPrints()

    fun getRemainingConversions(): Int = proLicenseManager.getRemainingConversions()

    fun navigateToScreen(screenIndex: Int) {
        if (!_isLoggedIn.value && screenIndex != 3 && screenIndex != 4 && screenIndex != 5) {
            _currentScreen.value = 3
            return
        }
        _currentScreen.value = screenIndex
    }

    fun openPreview(source: PreviewDocumentSource) {
        val fileName = when (source) {
            is PreviewDocumentSource.Saved -> source.job.fileName
            is PreviewDocumentSource.Pending -> source.pendingJob.defaultName
        }
        val filePath = when (source) {
            is PreviewDocumentSource.Saved -> source.job.filePath
            is PreviewDocumentSource.Pending -> source.pendingJob.tempFile.absolutePath
        }

        // If matching PDF exists in the same folder, open only the PDF via external app chooser
        val matchingPdf = StorageHelper.findMatchingPdfFile(getApplication(), fileName, filePath)
        if (matchingPdf != null && matchingPdf.exists() && matchingPdf.length() > 0) {
            _previewDocument.value = null
            StorageHelper.openDirectFile(getApplication(), matchingPdf, "Open '${matchingPdf.name}' with...")
            return
        }

        val isPs = fileName.endsWith(".ps", ignoreCase = true) || fileName.endsWith(".eps", ignoreCase = true)
        if (isPs) {
            _previewDocument.value = source
        } else {
            // For all generic files (PDF, XLSX, DOCX, Images, ZIP, Audio, etc.)
            when (source) {
                is PreviewDocumentSource.Saved -> {
                    _previewDocument.value = null
                    openFile(source.job)
                }
                is PreviewDocumentSource.Pending -> {
                    viewModelScope.launch(Dispatchers.IO) {
                        val entity = NetworkPrinterServer.savePendingJob(
                            context = getApplication(),
                            pendingJob = source.pendingJob,
                            customName = source.pendingJob.defaultName
                        )
                        _previewDocument.value = null
                        openFile(entity)
                    }
                }
            }
        }
    }

    fun closePreview() {
        _previewDocument.value = null
        syncFilesFromDisk()
    }

    fun syncFilesFromDisk(showMessage: Boolean = false, forceIncludeAll: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            if (forceIncludeAll) {
                StorageHelper.clearAllDismissals(getApplication())
            }
            val added = StorageHelper.syncStorageWithDatabase(getApplication())
            fileSystemRepository.refreshFiles()
            val total = printJobDao.getAllJobsList().size
            if (showMessage) {
                val msg = if (added > 0) {
                    "🔄 Synced $added new file(s) from Virtual Printer ($total total)"
                } else {
                    "✅ Virtual Printer folder synced ($total file(s) available)"
                }
                _saveSnackbarEvents.emit(
                    SaveSnackbarEvent(
                        message = msg
                    )
                )
            }
        }
    }

    fun refreshNetworkInfo() {
        try {
            _networkInfo.value = NetworkUtils.getNetworkInfo(getApplication())
            syncFilesFromDisk(showMessage = false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun toggleServer() {
        try {
            val context = getApplication<Application>()
            if (isServerRunning.value) {
                PrinterServerService.stopService(context)
            } else {
                PrinterServerService.startService(context)
            }
            refreshNetworkInfo()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun ensureServerRunning() {
        if (!isServerRunning.value) {
            val context = getApplication<Application>()
            PrinterServerService.startService(context)
            refreshNetworkInfo()
        }
    }

    fun shareFilesFromPhoneToPc(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            var addedCount = 0
            for (uri in uris) {
                val item = StorageHelper.copyUriToShared(context, uri)
                if (item != null) {
                    NetworkPrinterServer.addSharedFileForPc(item)
                    addedCount++
                }
            }
            if (addedCount > 0) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Added $addedCount file(s) to Web Share! Downloadable on PC now.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                _saveSnackbarEvents.emit(
                    SaveSnackbarEvent(
                        message = "Added $addedCount file(s) to Web Share for PC download"
                    )
                )
            }
        }
    }

    fun removeSharedFileForPc(id: String) {
        NetworkPrinterServer.removeSharedFileForPc(id)
    }

    fun clearAllSharedFilesForPc() {
        NetworkPrinterServer.clearSharedFilesForPc()
    }

    fun openSharedFile(item: SharedFileItem) {
        StorageHelper.openSharedFileItem(getApplication(), item)
    }

    fun shareSharedFile(item: SharedFileItem) {
        StorageHelper.shareSharedFileItem(getApplication(), item)
    }

    fun savePendingPrintJob(pendingJob: PendingPrintJob, customName: String) {
        if (_previewDocument.value is PreviewDocumentSource.Pending) {
            _previewDocument.value = null
        }
        viewModelScope.launch(Dispatchers.IO) {
            val originalExt = pendingJob.tempFile.name.substringAfterLast('.', "ps")
            val cleanName = StorageHelper.sanitizeFileName(customName, pendingJob.defaultName, originalExt)
            _saveProgressState.value = SaveProgressState.Saving(
                fileName = cleanName,
                message = "Saving '$cleanName' to VirtualPrinter..."
            )
            try {
                delay(300)
                val entity = NetworkPrinterServer.savePendingJob(
                    context = getApplication(),
                    pendingJob = pendingJob,
                    customName = customName
                )
                _saveProgressState.value = SaveProgressState.Success(
                    job = entity,
                    message = "Successfully saved '${entity.fileName}' as PDF!"
                )
                _saveSnackbarEvents.emit(
                    SaveSnackbarEvent(
                        message = "Saved ${entity.fileName} to VirtualPrinter",
                        actionLabel = "Open PDF",
                        job = entity
                    )
                )
                delay(3500)
                _saveProgressState.value = SaveProgressState.Idle
            } catch (e: Exception) {
                e.printStackTrace()
                _saveProgressState.value = SaveProgressState.Error(
                    message = "Failed to save file: ${e.message}"
                )
                _saveSnackbarEvents.emit(
                    SaveSnackbarEvent(
                        message = "Error saving file: ${e.message}"
                    )
                )
                delay(2500)
                _saveProgressState.value = SaveProgressState.Idle
            }
        }
    }

    fun dismissPendingPrintJob(pendingJob: PendingPrintJob? = null) {
        if (_previewDocument.value is PreviewDocumentSource.Pending) {
            _previewDocument.value = null
        }
        NetworkPrinterServer.dismissPendingJob(pendingJob)
    }

    fun promptRenameJob(job: PrintJobEntity?) {
        _jobToRename.value = job
    }

    fun confirmRenameJob(job: PrintJobEntity, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val originalExt = job.fileName.substringAfterLast('.', "ps")
            val cleanName = StorageHelper.sanitizeFileName(newName, "Doc", originalExt)
            _saveProgressState.value = SaveProgressState.Saving(
                fileName = cleanName,
                message = "Updating '$cleanName' in Downloads..."
            )
            try {
                delay(300)
                val updatedJob = StorageHelper.renameJobFile(getApplication(), job, newName)
                _jobToRename.value = null
                if (_previewDocument.value is PreviewDocumentSource.Saved) {
                    _previewDocument.value = PreviewDocumentSource.Saved(updatedJob)
                }
                _saveProgressState.value = SaveProgressState.Success(
                    job = updatedJob,
                    message = "Renamed to '${updatedJob.fileName}'"
                )
                _saveSnackbarEvents.emit(
                    SaveSnackbarEvent(
                        message = "Renamed to ${updatedJob.fileName}",
                        actionLabel = "Open",
                        job = updatedJob
                    )
                )
                delay(2000)
                _saveProgressState.value = SaveProgressState.Idle
            } catch (e: Exception) {
                e.printStackTrace()
                _saveProgressState.value = SaveProgressState.Error(
                    message = "Failed to rename: ${e.message}"
                )
                _saveSnackbarEvents.emit(
                    SaveSnackbarEvent(
                        message = "Error renaming file: ${e.message}"
                    )
                )
                delay(2500)
                _saveProgressState.value = SaveProgressState.Idle
            }
        }
    }

    fun selectCustomFolder(uri: Uri) {
        viewModelScope.launch {
            _saveProgressState.value = SaveProgressState.Saving("Folder", "Loading files from selected folder...")
            val added = fileSystemRepository.selectCustomFolder(uri)
            val path = fileSystemRepository.selectedFolderPath.value
            _saveProgressState.value = SaveProgressState.Idle
            _saveSnackbarEvents.emit(
                SaveSnackbarEvent(
                    message = "Folder selected: $path ($added new items loaded)"
                )
            )
        }
    }

    fun resetToDefaultFolder() {
        viewModelScope.launch {
            fileSystemRepository.resetToDefaultFolder()
            _saveSnackbarEvents.emit(
                SaveSnackbarEvent(
                    message = "Reset folder to VirtualPrinter/"
                )
            )
        }
    }

    fun openFile(job: PrintJobEntity) {
        StorageHelper.openFile(getApplication(), job)
    }

    fun shareFile(job: PrintJobEntity) {
        StorageHelper.shareFile(getApplication(), job)
    }

    fun openPdf(job: PrintJobEntity) {
        openFile(job)
    }

    fun sharePdf(job: PrintJobEntity) {
        shareFile(job)
    }

    fun openFileLocation(job: PrintJobEntity) {
        val success = StorageHelper.openFileLocation(getApplication(), job.fileName, job.filePath)
        if (!success) {
            viewModelScope.launch {
                _saveSnackbarEvents.emit(SaveSnackbarEvent(message = "Failed to open file location. File manager not found."))
            }
        }
    }

    fun deleteJob(job: PrintJobEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_previewDocument.value is PreviewDocumentSource.Saved) {
                val current = (_previewDocument.value as PreviewDocumentSource.Saved).job
                if (current.id == job.id) {
                    _previewDocument.value = null
                }
            }
            // Mark as cleared from UI so background sync won't immediately re-add it
            StorageHelper.markFileClearedFromUi(getApplication(), job.fileName)
            printJobDao.deleteJob(job)
            syncFilesFromDisk()
            _saveSnackbarEvents.emit(
                SaveSnackbarEvent(
                    message = "Cleared '${job.fileName}' from list (file kept in storage)"
                )
            )
        }
    }

    fun deleteMatchingPsFile(targetFileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val baseName = if (targetFileName.contains('.')) targetFileName.substringBeforeLast('.') else targetFileName
            val psName = "$baseName.ps"
            val deleted = StorageHelper.deleteMatchingPsFile(getApplication(), targetFileName)
            _saveSnackbarEvents.emit(
                SaveSnackbarEvent(
                    message = if (deleted) "🗑️ Deleted original '$psName'" else "Removed '$psName'"
                )
            )
        }
    }

    fun markPendingConversion(psFileName: String) {
        val base = psFileName.substringBeforeLast('.')
        dismissedPsConversions.remove(base.lowercase(java.util.Locale.ROOT))
    }

    fun dismissPostConversionPrompt() {
        val current = _postConversionPrompt.value
        if (current != null) {
            dismissedPsConversions.add(current.baseName.lowercase(java.util.Locale.ROOT))
        }
        _postConversionPrompt.value = null
    }

    fun confirmDeleteOriginalPsFile(psFileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val baseName = psFileName.substringBeforeLast('.')
            dismissedPsConversions.add(baseName.lowercase(java.util.Locale.ROOT))
            _postConversionPrompt.value = null
            val deleted = StorageHelper.deleteMatchingPsFile(getApplication(), psFileName)
            syncFilesFromDisk()
            _saveSnackbarEvents.emit(
                SaveSnackbarEvent(
                    message = if (deleted) "🗑️ Deleted original '$psFileName'. PDF saved!" else "Original file removed."
                )
            )
        }
    }

    fun checkPendingConversionPsCleanup(targetPsName: String? = null) {
        if (targetPsName.isNullOrBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            syncFilesFromDisk()
            val context = getApplication<Application>()
            val vpFolder = StorageHelper.getVirtualPrinterFolder()
            val dirs = listOfNotNull(vpFolder, context.filesDir)

            for (d in dirs) {
                if (!d.exists() || !d.isDirectory) continue
                val files = d.listFiles() ?: continue
                val psFiles = files.filter { 
                    it.isFile && it.name.equals(targetPsName, ignoreCase = true) && it.length() > 0 
                }
                for (ps in psFiles) {
                    val base = ps.name.substringBeforeLast('.')
                    if (dismissedPsConversions.contains(base.lowercase(java.util.Locale.ROOT))) continue
                    val matchingPdf = StorageHelper.findMatchingPdfFile(context, ps.name, ps.absolutePath)
                    if (matchingPdf != null && matchingPdf.exists() && matchingPdf.length() > 0) {
                        _previewDocument.value = null
                        _currentScreen.value = 1
                        _postConversionPrompt.value = PostConversionPrompt(
                            psFileName = ps.name,
                            pdfFileName = matchingPdf.name,
                            baseName = base,
                            pdfFile = matchingPdf,
                            psFile = ps
                        )
                        return@launch
                    }
                }
            }
        }
    }

    fun openPdfFromFileName(pdfFileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = StorageHelper.registerExistingPdfFromDisk(getApplication(), pdfFileName)
            _saveSnackbarEvents.emit(
                SaveSnackbarEvent(
                    message = "📄 PDF Converted: ${entity?.fileName ?: pdfFileName}",
                    actionLabel = "Open PDF",
                    job = entity
                )
            )
            syncFilesFromDisk()
        }
    }

    fun syncStorageWithDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            StorageHelper.syncStorageWithDatabase(getApplication())
        }
    }

    fun clearAllJobs() {
        viewModelScope.launch(Dispatchers.IO) {
            _previewDocument.value = null
            val currentJobs = printJobDao.getAllJobsList()
            StorageHelper.markAllFilesClearedFromUi(getApplication(), currentJobs.map { it.fileName })
            val vpFiles = StorageHelper.getVirtualPrinterFolder().listFiles()?.filter { it.isFile }?.map { it.name } ?: emptyList()
            StorageHelper.markAllFilesClearedFromUi(getApplication(), vpFiles)
            printJobDao.deleteAllJobs()
            syncFilesFromDisk()
            _saveSnackbarEvents.emit(
                SaveSnackbarEvent(
                    message = "Cleared all print records from list (files kept in storage)"
                )
            )
        }
    }

    fun sendTestPrint(format: String = "ps") {
        if (_isSimulating.value) return
        _isSimulating.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                NetworkPrinterServer.simulatePrintJob(getApplication(), format)
            } finally {
                _isSimulating.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        proLicenseManager.stopRealtimeUserSync()
    }
}
