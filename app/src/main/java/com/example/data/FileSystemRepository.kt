package com.example.data

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.utils.StorageHelper
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * FileSystemRepository handles scanning, monitoring, and synchronizing files
 * from the `/storage/emulated/0/VirtualPrinter` directory.
 *
 * Uses both Android FileObserver (for instant OS filesystem notifications)
 * and recursive interval polling (to reliably catch updates from Termux,
 * PC network transfers, MTP, or ContentResolver writes).
 */
class FileSystemRepository private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db = AppDatabase.getDatabase(context)
    private val printJobDao = db.printJobDao()

    private val _virtualPrinterDir = StorageHelper.getVirtualPrinterFolder()
    val virtualPrinterDirPath: String = _virtualPrinterDir.absolutePath

    private val _selectedFolderPath = MutableStateFlow(StorageHelper.getSelectedFolderPathDisplay(context))
    val selectedFolderPath: StateFlow<String> = _selectedFolderPath.asStateFlow()

    private val _isCustomFolder = MutableStateFlow(StorageHelper.isCustomFolderSelected(context))
    val isCustomFolder: StateFlow<Boolean> = _isCustomFolder.asStateFlow()

    private val _diskFiles = MutableStateFlow<List<File>>(emptyList())
    val diskFiles: StateFlow<List<File>> = _diskFiles.asStateFlow()

    val printJobs: StateFlow<List<PrintJobEntity>> = printJobDao.getAllJobs()
        .map { list ->
            list.filter { job ->
                val name = job.fileName.lowercase(java.util.Locale.ROOT)
                (name.endsWith(".ps") || name.endsWith(".pdf")) &&
                !name.startsWith(".")
            }.sortedByDescending { it.receivedTimestamp }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private var fileObserver: FileObserver? = null
    private var pollingJob: Job? = null
    private var lastScanSignature: String = ""

    init {
        startMonitoring()
    }

    /**
     * Starts dual FileObserver and interval polling for /storage/emulated/0/VirtualPrinter
     */
    @Synchronized
    fun startMonitoring() {
        if (_isMonitoring.value) return
        _isMonitoring.value = true

        ensureDirectoriesExist()

        // 1. Setup FileObserver
        setupFileObserver()

        // 2. Setup recursive polling ticker
        startPolling()

        // 3. Initial Scan
        scope.launch {
            refreshFiles()
        }
    }

    @Synchronized
    fun stopMonitoring() {
        _isMonitoring.value = false
        try {
            fileObserver?.stopWatching()
            fileObserver = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun ensureDirectoriesExist() {
        try {
            if (!_virtualPrinterDir.exists()) {
                _virtualPrinterDir.mkdirs()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupFileObserver() {
        try {
            val mask = FileObserver.CREATE or
                    FileObserver.CLOSE_WRITE or
                    FileObserver.MOVED_TO or
                    FileObserver.DELETE or
                    FileObserver.MODIFY or
                    FileObserver.MOVED_FROM

            val targetPath = _virtualPrinterDir.absolutePath

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                fileObserver = object : FileObserver(_virtualPrinterDir, mask) {
                    override fun onEvent(event: Int, path: String?) {
                        handleFileObserverEvent(event, path)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                fileObserver = object : FileObserver(targetPath, mask) {
                    override fun onEvent(event: Int, path: String?) {
                        handleFileObserverEvent(event, path)
                    }
                }
            }
            fileObserver?.startWatching()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleFileObserverEvent(event: Int, path: String?) {
        // Trigger a refresh when files are created, written, moved, or deleted
        scope.launch {
            delay(150) // Short debounce for file write completion
            refreshFiles()
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                try {
                    val currentSignature = computeDirectorySignature()
                    if (currentSignature != lastScanSignature) {
                        lastScanSignature = currentSignature
                        refreshFilesInternal()
                    }
                } catch (e: Exception) {
                    // Ignore scan errors
                }
                delay(2000) // Poll every 2 seconds
            }
        }
    }

    private fun computeDirectorySignature(): String {
        val files = getCandidateFiles()
        val sb = StringBuilder()
        sb.append(files.size).append(":")
        for (f in files) {
            sb.append(f.name).append("=").append(f.length()).append("@").append(f.lastModified()).append(";")
        }
        return sb.toString()
    }

    private fun getCandidateFiles(): List<File> {
        val result = mutableListOf<File>()
        val folders = StorageHelper.getAllCandidateFolders(context)
        for (folder in folders) {
            try {
                if (folder.exists() && folder.isDirectory) {
                    folder.listFiles()?.forEach { file ->
                        val name = file.name.lowercase(java.util.Locale.ROOT)
                        if (file.isFile && file.length() > 0 &&
                            (name.endsWith(".ps") || name.endsWith(".pdf")) &&
                            !name.startsWith(".")
                        ) {
                            if (result.none { it.name.equals(file.name, ignoreCase = true) }) {
                                result.add(file)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // directory access error
            }
        }
        return result.sortedByDescending { it.lastModified() }
    }

    suspend fun refreshFiles(): Int = withContext(Dispatchers.IO) {
        refreshFilesInternal()
    }

    suspend fun selectCustomFolder(uri: Uri): Int = withContext(Dispatchers.IO) {
        val display = StorageHelper.setSelectedFolderUri(context, uri)
        _selectedFolderPath.value = display
        _isCustomFolder.value = true
        refreshFilesInternal()
    }

    suspend fun resetToDefaultFolder(): Int = withContext(Dispatchers.IO) {
        StorageHelper.resetToDefaultFolder(context)
        _selectedFolderPath.value = StorageHelper.getSelectedFolderPathDisplay(context)
        _isCustomFolder.value = false
        refreshFilesInternal()
    }

    private suspend fun refreshFilesInternal(): Int {
        val added = StorageHelper.syncStorageWithDatabase(context)
        _selectedFolderPath.value = StorageHelper.getSelectedFolderPathDisplay(context)
        _isCustomFolder.value = StorageHelper.isCustomFolderSelected(context)
        val files = getCandidateFiles()
        _diskFiles.value = files
        return added
    }

    suspend fun importFiles(uris: List<Uri>): List<PrintJobEntity> = withContext(Dispatchers.IO) {
        val imported = mutableListOf<PrintJobEntity>()
        for (uri in uris) {
            val entity = StorageHelper.importUriToVirtualPrinter(context, uri)
            if (entity != null) {
                imported.add(entity)
            }
        }
        refreshFilesInternal()
        imported
    }

    suspend fun deleteFile(job: PrintJobEntity): Boolean = withContext(Dispatchers.IO) {
        StorageHelper.markFileClearedFromUi(context, job.fileName)
        try {
            db.printJobDao().deleteJob(job)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        refreshFilesInternal()
        true
    }

    suspend fun clearAllFiles() = withContext(Dispatchers.IO) {
        val allCurrentJobs = printJobDao.getAllJobsList()
        StorageHelper.markAllFilesClearedFromUi(context, allCurrentJobs.map { it.fileName })
        db.printJobDao().deleteAllJobs()
        refreshFilesInternal()
    }

    companion object {
        @Volatile
        private var INSTANCE: FileSystemRepository? = null

        fun getInstance(context: Context): FileSystemRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FileSystemRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
}
