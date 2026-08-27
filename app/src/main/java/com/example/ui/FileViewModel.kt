package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FileSystemRepository
import com.example.data.PrintJobEntity
import com.example.utils.StorageHelper
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * FileViewModel manages UI state for monitoring and displaying files
 * from `/storage/emulated/0/VirtualPrinter`.
 */
class FileViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FileSystemRepository.getInstance(application)

    val diskFiles: StateFlow<List<File>> = repository.diskFiles
    val printJobs: StateFlow<List<PrintJobEntity>> = repository.printJobs
    val isMonitoring: StateFlow<Boolean> = repository.isMonitoring
    val virtualPrinterDirPath: String = repository.virtualPrinterDirPath

    private val _userFeedbackEvents = MutableSharedFlow<String>()
    val userFeedbackEvents: SharedFlow<String> = _userFeedbackEvents.asSharedFlow()

    init {
        repository.startMonitoring()
    }

    fun refreshFiles(showMessage: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val added = repository.refreshFiles()
            if (showMessage) {
                _userFeedbackEvents.emit(
                    if (added > 0) "Synced $added new file(s) from VirtualPrinter folder"
                    else "Folder is up to date"
                )
            }
        }
    }

    fun importFiles(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val imported = repository.importFiles(uris)
            if (imported.isNotEmpty()) {
                _userFeedbackEvents.emit("Imported ${imported.size} file(s) into VirtualPrinter")
            }
        }
    }

    fun deleteFile(job: PrintJobEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteFile(job)
            _userFeedbackEvents.emit("Deleted ${job.fileName}")
        }
    }

    fun clearAllFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAllFiles()
            _userFeedbackEvents.emit("Cleared all print jobs")
        }
    }

    fun openFile(context: Context, job: PrintJobEntity) {
        StorageHelper.openFile(context, job)
    }

    fun openFolder(context: Context) {
        StorageHelper.openFolderInFileManager(context)
    }

    override fun onCleared() {
        super.onCleared()
        // ViewModel cleared
    }
}
