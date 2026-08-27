package com.example.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.AppDatabase
import com.example.data.PrintJobEntity
import com.example.data.SharedFileItem
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object StorageHelper {

    const val DEFAULT_VIRTUAL_PRINTER_DIR = "/storage/emulated/0/Virtual Printer"
    const val VIRTUAL_PRINTER_FOLDER_NAME = "Virtual Printer"
    const val VIRTUAL_PRINTER_LEGACY_FOLDER_NAME = "VirtualPrinter"

    private const val STORAGE_PREFS = "virtual_printer_storage_prefs"
    private const val KEY_CUSTOM_TREE_URI = "custom_folder_tree_uri"
    private const val KEY_CUSTOM_DISPLAY_PATH = "custom_folder_display_path"

    fun getSelectedFolderUri(context: Context): Uri? {
        val prefs = context.getSharedPreferences(STORAGE_PREFS, Context.MODE_PRIVATE)
        val uriStr = prefs.getString(KEY_CUSTOM_TREE_URI, null) ?: return null
        return try {
            Uri.parse(uriStr)
        } catch (e: Exception) {
            null
        }
    }

    fun getSelectedFolderPathDisplay(context: Context): String {
        val prefs = context.getSharedPreferences(STORAGE_PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CUSTOM_DISPLAY_PATH, "Virtual Printer/") ?: "Virtual Printer/"
    }

    fun getPosixFolderDir(context: Context): String {
        val customUri = getSelectedFolderUri(context) ?: return "/storage/emulated/0/VirtualPrinter"
        return try {
            val docId = DocumentsContract.getTreeDocumentId(customUri)
            when {
                docId.startsWith("primary:") -> {
                    val path = docId.removePrefix("primary:")
                    if (path.isEmpty()) "/storage/emulated/0" else "/storage/emulated/0/$path"
                }
                docId.contains(":") -> {
                    val volume = docId.substringBefore(":")
                    val path = docId.substringAfter(":")
                    if (path.isEmpty()) "/storage/$volume" else "/storage/$volume/$path"
                }
                else -> "/storage/emulated/0/VirtualPrinter"
            }
        } catch (e: Exception) {
            "/storage/emulated/0/VirtualPrinter"
        }
    }

    fun isCustomFolderSelected(context: Context): Boolean {
        val prefs = context.getSharedPreferences(STORAGE_PREFS, Context.MODE_PRIVATE)
        return prefs.contains(KEY_CUSTOM_TREE_URI)
    }

    fun setSelectedFolderUri(context: Context, uri: Uri): String {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Format friendly folder display path
        val docId = try {
            DocumentsContract.getTreeDocumentId(uri)
        } catch (e: Exception) {
            uri.lastPathSegment ?: "Selected Folder"
        }
        val displayPath = when {
            docId.startsWith("primary:") -> docId.removePrefix("primary:") + "/"
            docId.contains(":") -> docId.substringAfter(":") + "/"
            else -> docId + "/"
        }

        val prefs = context.getSharedPreferences(STORAGE_PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_CUSTOM_TREE_URI, uri.toString())
            .putString(KEY_CUSTOM_DISPLAY_PATH, displayPath)
            .apply()

        return displayPath
    }

    private const val KEY_CLEARED_FROM_UI_FILES = "key_cleared_from_ui_files"

    fun markFileClearedFromUi(context: Context, fileName: String) {
        try {
            val prefs = context.getSharedPreferences(STORAGE_PREFS, Context.MODE_PRIVATE)
            val set = prefs.getStringSet(KEY_CLEARED_FROM_UI_FILES, emptySet())?.toMutableSet() ?: mutableSetOf()
            set.add(fileName.lowercase(java.util.Locale.ROOT))
            prefs.edit().putStringSet(KEY_CLEARED_FROM_UI_FILES, set).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun markAllFilesClearedFromUi(context: Context, fileNames: Collection<String>) {
        try {
            val prefs = context.getSharedPreferences(STORAGE_PREFS, Context.MODE_PRIVATE)
            val set = prefs.getStringSet(KEY_CLEARED_FROM_UI_FILES, emptySet())?.toMutableSet() ?: mutableSetOf()
            for (name in fileNames) {
                set.add(name.lowercase(java.util.Locale.ROOT))
            }
            prefs.edit().putStringSet(KEY_CLEARED_FROM_UI_FILES, set).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun unmarkFileCleared(context: Context, fileName: String) {
        try {
            val prefs = context.getSharedPreferences(STORAGE_PREFS, Context.MODE_PRIVATE)
            val set = prefs.getStringSet(KEY_CLEARED_FROM_UI_FILES, emptySet())?.toMutableSet() ?: mutableSetOf()
            if (set.remove(fileName.lowercase(java.util.Locale.ROOT))) {
                prefs.edit().putStringSet(KEY_CLEARED_FROM_UI_FILES, set).apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isFileClearedFromUi(context: Context, fileName: String): Boolean {
        return try {
            val prefs = context.getSharedPreferences(STORAGE_PREFS, Context.MODE_PRIVATE)
            val set = prefs.getStringSet(KEY_CLEARED_FROM_UI_FILES, emptySet()) ?: emptySet()
            set.contains(fileName.lowercase(java.util.Locale.ROOT))
        } catch (e: Exception) {
            false
        }
    }

    fun clearAllDismissals(context: Context) {
        try {
            val prefs = context.getSharedPreferences(STORAGE_PREFS, Context.MODE_PRIVATE)
            prefs.edit().remove(KEY_CLEARED_FROM_UI_FILES).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun resetToDefaultFolder(context: Context) {
        val prefs = context.getSharedPreferences(STORAGE_PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_CUSTOM_TREE_URI)
            .putString(KEY_CUSTOM_DISPLAY_PATH, "Virtual Printer/")
            .apply()
    }

    /**
     * Returns all potential storage directories for Virtual Printer files:
     * - /storage/emulated/0/Virtual Printer
     * - /storage/emulated/0/VirtualPrinter
     * - /storage/emulated/0/Documents/Virtual Printer
     * - /storage/emulated/0/Documents/VirtualPrinter
     * - /storage/emulated/0/Download/Virtual Printer
     * - /storage/emulated/0/Download/VirtualPrinter
     * - App-specific external & internal storage
     */
    fun getAllCandidateFolders(context: Context): List<File> {
        val list = mutableListOf<File>()
        val root = Environment.getExternalStorageDirectory()
        list.add(File(root, VIRTUAL_PRINTER_FOLDER_NAME))
        list.add(File(root, VIRTUAL_PRINTER_LEGACY_FOLDER_NAME))

        try {
            val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (docs != null) {
                list.add(File(docs, VIRTUAL_PRINTER_FOLDER_NAME))
                list.add(File(docs, VIRTUAL_PRINTER_LEGACY_FOLDER_NAME))
            }
        } catch (e: Exception) {
            // Ignore
        }

        try {
            val dls = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (dls != null) {
                list.add(File(dls, VIRTUAL_PRINTER_FOLDER_NAME))
                list.add(File(dls, VIRTUAL_PRINTER_LEGACY_FOLDER_NAME))
            }
        } catch (e: Exception) {
            // Ignore
        }

        try {
            val extApp = context.getExternalFilesDir(null)
            if (extApp != null) {
                list.add(File(extApp, VIRTUAL_PRINTER_FOLDER_NAME))
                list.add(File(extApp, VIRTUAL_PRINTER_LEGACY_FOLDER_NAME))
                list.add(extApp)
            }
        } catch (e: Exception) {
            // Ignore
        }

        list.add(File(context.filesDir, VIRTUAL_PRINTER_FOLDER_NAME))
        list.add(File(context.filesDir, VIRTUAL_PRINTER_LEGACY_FOLDER_NAME))
        list.add(context.filesDir)

        return list.distinctBy { it.absolutePath }
    }

    /**
     * Initializes the dedicated Virtual Printer folders on device root storage.
     * Ensures directories are created upon application install / startup.
     */
    fun initVirtualPrinterDirectory(context: Context): File {
        val folders = getAllCandidateFolders(context)
        for (folder in folders) {
            try {
                if (!folder.exists()) {
                    folder.mkdirs()
                }
            } catch (e: Exception) {
                // Ignore individual mkdirs errors
            }
        }

        val root = Environment.getExternalStorageDirectory()
        val pubSpaced = File(root, VIRTUAL_PRINTER_FOLDER_NAME)
        val pubUnspaced = File(root, VIRTUAL_PRINTER_LEGACY_FOLDER_NAME)
        if (pubSpaced.exists() && pubSpaced.canWrite()) return pubSpaced
        if (pubUnspaced.exists() && pubUnspaced.canWrite()) return pubUnspaced

        val docsSpaced = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), VIRTUAL_PRINTER_FOLDER_NAME)
        if (docsSpaced.exists() && docsSpaced.canWrite()) return docsSpaced

        val extApp = File(context.getExternalFilesDir(null) ?: context.filesDir, VIRTUAL_PRINTER_FOLDER_NAME)
        if (extApp.exists()) return extApp

        return pubSpaced
    }

    fun getVirtualPrinterFolder(): File {
        val root = Environment.getExternalStorageDirectory()
        val pubSpaced = File(root, VIRTUAL_PRINTER_FOLDER_NAME)
        val pubUnspaced = File(root, VIRTUAL_PRINTER_LEGACY_FOLDER_NAME)

        if (pubSpaced.exists() && pubSpaced.isDirectory) return pubSpaced
        if (pubUnspaced.exists() && pubUnspaced.isDirectory) return pubUnspaced

        try {
            pubSpaced.mkdirs()
        } catch (e: Exception) {
            // Ignore
        }
        if (pubSpaced.exists()) return pubSpaced

        try {
            pubUnspaced.mkdirs()
        } catch (e: Exception) {
            // Ignore
        }
        if (pubUnspaced.exists()) return pubUnspaced

        val docsSpaced = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), VIRTUAL_PRINTER_FOLDER_NAME)
        try {
            docsSpaced.mkdirs()
        } catch (e: Exception) {}
        if (docsSpaced.exists()) return docsSpaced

        return pubSpaced
    }

    fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return "*/*"
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        if (mime != null) return mime
        return when (ext) {
            "ps", "eps" -> "application/postscript"
            "psd" -> "image/vnd.adobe.photoshop"
            "prn", "pjl" -> "application/octet-stream"
            "pcl" -> "application/vnd.hp-pcl"
            "pdf" -> "application/pdf"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "xls" -> "application/vnd.ms-excel"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "doc" -> "application/msword"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "ppt" -> "application/vnd.ms-powerpoint"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "txt" -> "text/plain"
            "csv" -> "text/csv"
            "json" -> "application/json"
            "zip" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            else -> "*/*"
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val formatted = String.format(java.util.Locale.US, "%.1f", bytes / Math.pow(1024.0, digitGroups.toDouble()))
        return "$formatted ${units[digitGroups]}"
    }

    fun sanitizeFileName(input: String, defaultPrefix: String = "PrintDoc", defaultExt: String = "ps"): String {
        var clean = input.trim()
        val hasExt = clean.contains('.')
        val ext = if (hasExt) clean.substringAfterLast('.') else defaultExt
        val nameWithoutExt = if (hasExt) clean.substringBeforeLast('.') else clean
        val cleanBase = nameWithoutExt.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        val finalBase = if (cleanBase.isEmpty()) "${defaultPrefix}_${System.currentTimeMillis()}" else cleanBase
        return "$finalBase.$ext"
    }

    fun sanitizeOriginalFileName(input: String, defaultPrefix: String = "Upload"): String {
        var clean = input.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
        if (clean.isEmpty()) {
            clean = "${defaultPrefix}_${System.currentTimeMillis()}"
        }
        return clean
    }

    suspend fun saveAndRegisterRawFile(
        context: Context,
        fileBytes: ByteArray,
        originalFileName: String,
        clientIp: String
    ): PrintJobEntity {
        val cleanName = sanitizeOriginalFileName(originalFileName, "UploadedDoc")
        val mimeType = getMimeType(cleanName)
        val ext = cleanName.substringAfterLast('.', "dat").uppercase()

        var savedPath = "$DEFAULT_VIRTUAL_PRINTER_DIR/$cleanName"
        var contentUriStr: String? = null
        var displayFolder = "Virtual Printer/"

        val customUri = getSelectedFolderUri(context)
        if (customUri != null) {
            displayFolder = getSelectedFolderPathDisplay(context)
            try {
                val resolver = context.contentResolver
                val treeDocId = DocumentsContract.getTreeDocumentId(customUri)
                val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(customUri, treeDocId)
                val newDocUri = DocumentsContract.createDocument(resolver, parentDocUri, mimeType, cleanName)
                if (newDocUri != null) {
                    resolver.openOutputStream(newDocUri)?.use { out ->
                        out.write(fileBytes)
                    }
                    contentUriStr = newDocUri.toString()
                    savedPath = "$displayFolder$cleanName"
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            // 1. Save directly to default /storage/emulated/0/VirtualPrinter
            try {
                val directDir = getVirtualPrinterFolder()
                val directFile = File(directDir, cleanName)
                FileOutputStream(directFile).use { it.write(fileBytes) }
                savedPath = directFile.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Also register in MediaStore VirtualPrinter for system gallery/files index
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, cleanName)
                        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "VirtualPrinter")
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        contentUriStr = uri.toString()
                        resolver.openOutputStream(uri)?.use { out ->
                            out.write(fileBytes)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Also keep persistent internal copy for guaranteed access
        val internalFile = File(context.filesDir, cleanName)
        try {
            FileOutputStream(internalFile).use { it.write(fileBytes) }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val fileForUri = if (File(savedPath).exists()) File(savedPath) else if (internalFile.exists()) internalFile else File(savedPath)
        val fileProviderUri = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                fileForUri
            )
        } catch (e: Exception) {
            null
        }

        val entity = PrintJobEntity(
            fileName = cleanName,
            originalFormat = "$ext File",
            fileSizeBytes = fileBytes.size.toLong(),
            filePath = if (customUri != null) (contentUriStr ?: savedPath) else savedPath,
            savedLocation = if (customUri != null) savedPath else savedPath,
            contentUri = if (customUri != null) contentUriStr else (fileProviderUri?.toString() ?: contentUriStr),
            receivedTimestamp = System.currentTimeMillis(),
            clientIp = clientIp,
            pageCount = 1,
            status = "Original Format Preserved"
        )

        unmarkFileCleared(context, entity.fileName)
        val db = AppDatabase.getDatabase(context)
        val id = db.printJobDao().insertJob(entity)
        
        try {
            com.example.data.ProLicenseManager.getInstance(context).recordPrintReceived(cleanName)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return entity.copy(id = id)
    }

    suspend fun saveAndRegisterFile(
        context: Context,
        file: File,
        baseName: String,
        originalFormat: String,
        clientIp: String,
        pageCount: Int = 1,
        customFileName: String? = null
    ): PrintJobEntity {
        val originalExt = file.name.substringAfterLast('.', "ps")
        val fileName = if (!customFileName.isNullOrBlank()) {
            sanitizeFileName(customFileName, baseName, originalExt)
        } else {
            file.name
        }
        val mimeType = getMimeType(fileName)
        var savedPath = "$DEFAULT_VIRTUAL_PRINTER_DIR/$fileName"
        var contentUriStr: String? = null
        var displayFolder = "Virtual Printer/"

        val customUri = getSelectedFolderUri(context)
        if (customUri != null) {
            displayFolder = getSelectedFolderPathDisplay(context)
            try {
                val resolver = context.contentResolver
                val treeDocId = DocumentsContract.getTreeDocumentId(customUri)
                val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(customUri, treeDocId)
                val newDocUri = DocumentsContract.createDocument(resolver, parentDocUri, mimeType, fileName)
                if (newDocUri != null) {
                    resolver.openOutputStream(newDocUri)?.use { out ->
                        FileInputStream(file).use { inStream ->
                            inStream.copyTo(out)
                        }
                    }
                    contentUriStr = newDocUri.toString()
                    savedPath = "$displayFolder$fileName"
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            // 1. Save directly to default /storage/emulated/0/VirtualPrinter
            try {
                val directDir = getVirtualPrinterFolder()
                val targetFile = File(directDir, fileName)
                if (file.absolutePath != targetFile.absolutePath) {
                    FileInputStream(file).use { inStream ->
                        FileOutputStream(targetFile).use { outStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                }
                savedPath = targetFile.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
                savedPath = file.absolutePath
            }

            // 2. Also register in MediaStore VirtualPrinter for external file pickers
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "VirtualPrinter")
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        contentUriStr = uri.toString()
                        resolver.openOutputStream(uri)?.use { out ->
                            FileInputStream(file).use { inStream ->
                                inStream.copyTo(out)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Persistent copy in internal storage
        val internalFile = File(context.filesDir, fileName)
        if (file.absolutePath != internalFile.absolutePath) {
            try {
                FileInputStream(file).use { inStream ->
                    FileOutputStream(internalFile).use { outStream ->
                        inStream.copyTo(outStream)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val fileForUri = if (File(savedPath).exists()) File(savedPath) else if (internalFile.exists()) internalFile else file
        val fileProviderUri = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                fileForUri
            )
        } catch (e: Exception) {
            null
        }

        val entity = PrintJobEntity(
            fileName = fileName,
            originalFormat = originalFormat,
            fileSizeBytes = if (File(savedPath).exists() && File(savedPath).length() > 0) File(savedPath).length() else if (internalFile.exists() && internalFile.length() > 0) internalFile.length() else file.length(),
            filePath = if (customUri != null) (contentUriStr ?: savedPath) else savedPath,
            savedLocation = if (customUri != null) savedPath else savedPath,
            contentUri = if (customUri != null) contentUriStr else (fileProviderUri?.toString() ?: contentUriStr),
            receivedTimestamp = System.currentTimeMillis(),
            clientIp = clientIp,
            pageCount = pageCount,
            status = "Saved to VirtualPrinter"
        )

        unmarkFileCleared(context, entity.fileName)
        val db = AppDatabase.getDatabase(context)
        val id = db.printJobDao().insertJob(entity)

        // Increment file usage count in Firebase Firestore for the logged-in user
        try {
            com.example.data.ProLicenseManager.getInstance(context).recordPrintReceived(fileName)
            val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                com.example.data.FirestoreUserService().incrementFileUsage(currentUser.uid, fileName, pageCount)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return entity.copy(id = id)
    }

    fun hasAllFilesPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestAllFilesPermission(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } else {
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }

    fun ensureInternalCopy(context: Context, fileName: String, sourceFile: File? = null): File {
        val internalFile = File(context.filesDir, fileName)
        val fileToCopy = if (sourceFile != null && sourceFile.exists() && sourceFile.length() > 0) {
            sourceFile
        } else {
            findFileOnDisk(context, fileName, sourceFile?.absolutePath)
        }

        if (fileToCopy != null && fileToCopy.exists() && fileToCopy.absolutePath != internalFile.absolutePath) {
            try {
                if (!internalFile.exists() || internalFile.length() == 0L || fileToCopy.lastModified() > internalFile.lastModified() || fileToCopy.length() != internalFile.length()) {
                    FileInputStream(fileToCopy).use { inStream ->
                        FileOutputStream(internalFile).use { outStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return internalFile
    }

    fun findFileOnDisk(context: Context, fileName: String, preferredPath: String? = null): File? {
        // 1. Check preferredPath directly
        if (!preferredPath.isNullOrBlank()) {
            val f = File(preferredPath)
            if (f.exists() && f.length() > 0) return f
        }

        // 2. Check all candidate Virtual Printer folders
        val candidateFolders = getAllCandidateFolders(context)
        for (folder in candidateFolders) {
            val target = File(folder, fileName)
            if (target.exists() && target.length() > 0) {
                return target
            }
        }

        // 3. Fallback direct storage names
        val directSpaced = File("/storage/emulated/0/Virtual Printer", fileName)
        if (directSpaced.exists() && directSpaced.length() > 0) return directSpaced
        val directUnspaced = File("/storage/emulated/0/VirtualPrinter", fileName)
        if (directUnspaced.exists() && directUnspaced.length() > 0) return directUnspaced

        return null
    }

    suspend fun saveAndRegisterPdf(
        context: Context,
        pdfFile: File,
        baseName: String,
        originalFormat: String,
        clientIp: String,
        pageCount: Int,
        customFileName: String? = null
    ): PrintJobEntity {
        return saveAndRegisterFile(
            context = context,
            file = pdfFile,
            baseName = baseName,
            originalFormat = originalFormat,
            clientIp = clientIp,
            pageCount = pageCount,
            customFileName = customFileName
        )
    }

    fun copyUriToShared(context: Context, uri: Uri): SharedFileItem? {
        try {
            val resolver = context.contentResolver
            var fileName = "Shared_${System.currentTimeMillis()}"
            var fileSize = 0L

            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrBlank()) fileName = name
                    }
                    if (sizeIndex != -1) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }

            fileName = sanitizeOriginalFileName(fileName)
            val sharedDir = File(context.filesDir, "shared_to_pc").apply { mkdirs() }
            val targetFile = File(sharedDir, fileName)

            resolver.openInputStream(uri)?.use { inStream ->
                FileOutputStream(targetFile).use { outStream ->
                    inStream.copyTo(outStream)
                }
            } ?: return null

            val mimeType = getMimeType(fileName)
            return SharedFileItem(
                fileName = fileName,
                fileSizeBytes = if (fileSize > 0) fileSize else targetFile.length(),
                filePath = targetFile.absolutePath,
                mimeType = mimeType
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    suspend fun renameJobFile(
        context: Context,
        job: PrintJobEntity,
        newCustomName: String
    ): PrintJobEntity {
        val hasExt = newCustomName.contains('.')
        val ext = if (hasExt) newCustomName.substringAfterLast('.') else job.fileName.substringAfterLast('.', "pdf")
        val cleanBase = if (hasExt) newCustomName.substringBeforeLast('.') else newCustomName
        val cleanSanitized = cleanBase.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        val newFileName = "$cleanSanitized.$ext"

        val oldFile = File(job.filePath)
        val internalTarget = File(context.filesDir, newFileName)
        
        val pubDir = getVirtualPrinterFolder()
        val pubOld = File(pubDir, job.fileName)
        val pubNew = File(pubDir, newFileName)
        val directOld = File("/storage/emulated/0/VirtualPrinter", job.fileName)
        val directNew = File("/storage/emulated/0/VirtualPrinter", newFileName)
        
        try {
            if (oldFile.exists() && oldFile.absolutePath != internalTarget.absolutePath) {
                FileInputStream(oldFile).use { inStream ->
                    FileOutputStream(internalTarget).use { outStream ->
                        inStream.copyTo(outStream)
                    }
                }
                oldFile.delete()
            }
            if (pubOld.exists()) {
                pubOld.renameTo(pubNew)
            }
            if (directOld.exists()) {
                directOld.renameTo(directNew)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        var contentUriStr: String? = null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, newFileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, getMimeType(newFileName))
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "VirtualPrinter")
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    contentUriStr = uri.toString()
                    resolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(if (internalTarget.exists()) internalTarget else oldFile).use { inStream ->
                            inStream.copyTo(out)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val fileForUri = if (internalTarget.exists()) internalTarget else oldFile
        val fileProviderUri = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                fileForUri
            )
        } catch (e: Exception) {
            null
        }

        val updatedJob = job.copy(
            fileName = newFileName,
            filePath = if (directNew.exists()) directNew.absolutePath else if (internalTarget.exists()) internalTarget.absolutePath else "$DEFAULT_VIRTUAL_PRINTER_DIR/$newFileName",
            savedLocation = "$DEFAULT_VIRTUAL_PRINTER_DIR/$newFileName",
            contentUri = fileProviderUri?.toString() ?: contentUriStr ?: job.contentUri
        )

        val db = AppDatabase.getDatabase(context)
        db.printJobDao().updateJob(updatedJob)
        return updatedJob
    }

    fun findMatchingPdfFile(context: Context, psFileName: String, psFilePath: String? = null): File? {
        val baseName = psFileName.substringBeforeLast('.')
        val cleanBaseName = if (baseName.endsWith(".ps", ignoreCase = true)) baseName.substringBeforeLast('.') else baseName

        // 1. Direct path check from psFilePath
        if (!psFilePath.isNullOrBlank()) {
            val directPdfFromPath = File(psFilePath.substringBeforeLast('.') + ".pdf")
            if (directPdfFromPath.exists() && directPdfFromPath.length() > 0) return directPdfFromPath

            val parent = File(psFilePath).parentFile
            if (parent != null && parent.exists()) {
                val candidate1 = File(parent, "$cleanBaseName.pdf")
                if (candidate1.exists() && candidate1.length() > 0) return candidate1
                val candidate2 = File(parent, "$baseName.pdf")
                if (candidate2.exists() && candidate2.length() > 0) return candidate2
            }
        }

        // 2. Check canonical VirtualPrinter directory directly
        val vpFolder = getVirtualPrinterFolder()
        val direct1 = File(vpFolder, "$cleanBaseName.pdf")
        if (direct1.exists() && direct1.length() > 0) return direct1

        val direct2 = File(vpFolder, "$baseName.pdf")
        if (direct2.exists() && direct2.length() > 0) return direct2

        val direct3 = File(vpFolder, "$psFileName.pdf")
        if (direct3.exists() && direct3.length() > 0) return direct3

        // 3. Check App internal fallback
        val internalFile = File(context.filesDir, "$cleanBaseName.pdf")
        if (internalFile.exists() && internalFile.length() > 0) return internalFile

        return null
    }

    fun openDirectFile(context: Context, file: File, title: String? = null) {
        try {
            if (!file.exists() || file.length() == 0L) {
                Toast.makeText(context, "File does not exist or is empty", Toast.LENGTH_SHORT).show()
                return
            }

            val isPdf = file.name.endsWith(".pdf", ignoreCase = true)
            val mimeType = if (isPdf) "application/pdf" else getMimeType(file.name)

            // Ensure a valid, accessible copy exists in internal storage
            val fileToServe = ensureInternalCopy(context, file.name, file)
            val fileForProvider = if (fileToServe.exists() && fileToServe.length() > 0) fileToServe else file

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                fileForProvider
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            // Grant read permission to all matching activities
            val pm = context.packageManager
            val resInfoList = pm.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            val allInfoList = if (resInfoList.isEmpty()) pm.queryIntentActivities(intent, 0) else resInfoList

            if (allInfoList.isEmpty()) {
                Toast.makeText(
                    context,
                    "No external PDF viewer app installed (e.g. Adobe Acrobat, Google PDF Viewer).",
                    Toast.LENGTH_LONG
                ).show()
            }

            for (resolveInfo in allInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                try {
                    context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                } catch (e: Exception) {
                    // Ignore
                }
            }

            // Standard implicit intent launches Android's native Resolver displaying "Just once" and "Always"
            try {
                context.startActivity(intent)
            } catch (e: android.content.ActivityNotFoundException) {
                val chooser = Intent.createChooser(intent, title ?: "Open '${file.name}' with...").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
                context.startActivity(chooser)
            }
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(
                context,
                "No app found to open file. Please install a compatible app.",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Unable to open file: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun openFile(context: Context, job: PrintJobEntity) {
        try {
            val isPs = job.fileName.endsWith(".ps", ignoreCase = true) || job.fileName.endsWith(".eps", ignoreCase = true)
            val isPdf = job.fileName.endsWith(".pdf", ignoreCase = true)

            if (isPs) {
                // For PostScript, search for the converted PDF
                val matchingPdf = findMatchingPdfFile(context, job.fileName, job.filePath)
                if (matchingPdf != null && matchingPdf.exists() && matchingPdf.length() > 0) {
                    openDirectFile(context, matchingPdf, "Open '${matchingPdf.name}' with...")
                    return
                } else {
                    // Do not attempt to open raw PostScript in external PDF apps
                    Toast.makeText(
                        context,
                        "PDF not found for '${job.fileName}'. Tap 'Convert with Termux' to create the PDF.",
                        Toast.LENGTH_LONG
                    ).show()
                    return
                }
            }

            if (isPdf) {
                val directFile = File(DEFAULT_VIRTUAL_PRINTER_DIR, job.fileName)
                val internalFile = File(context.filesDir, job.fileName)
                val externalFile = File(job.filePath)

                val fileToOpen = when {
                    directFile.exists() && directFile.length() > 0 -> directFile
                    internalFile.exists() && internalFile.length() > 0 -> internalFile
                    externalFile.exists() && externalFile.length() > 0 -> externalFile
                    else -> null
                }

                if (fileToOpen != null) {
                    openDirectFile(context, fileToOpen, "Open '${fileToOpen.name}' with...")
                    return
                }
            }

            val internalFile = File(context.filesDir, job.fileName)
            val externalFile = File(job.filePath)

            val fileToServe = when {
                internalFile.exists() && internalFile.length() > 0 -> internalFile
                externalFile.exists() && externalFile.length() > 0 -> {
                    ensureInternalCopy(context, job.fileName, externalFile)
                }
                else -> null
            }

            if (fileToServe != null && fileToServe.exists()) {
                openDirectFile(context, fileToServe, "Open '${job.fileName}' with...")
            } else if (!job.contentUri.isNullOrEmpty()) {
                val uri = Uri.parse(job.contentUri)
                val mimeType = getMimeType(job.fileName)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val chooser = Intent.createChooser(intent, "Open '${job.fileName}' with...").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(chooser)
                }
            } else {
                Toast.makeText(context, "File not found on disk", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Unable to open file: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun openPdf(context: Context, job: PrintJobEntity) {
        openFile(context, job)
    }

    fun shareFile(context: Context, job: PrintJobEntity) {
        try {
            val internalFile = File(context.filesDir, job.fileName)
            val externalFile = File(job.filePath)

            val fileToServe = when {
                internalFile.exists() && internalFile.length() > 0 -> internalFile
                externalFile.exists() && externalFile.length() > 0 -> {
                    ensureInternalCopy(context, job.fileName, externalFile)
                }
                else -> null
            }

            val uri: Uri = if (fileToServe != null && fileToServe.exists()) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    fileToServe
                )
            } else if (!job.contentUri.isNullOrEmpty()) {
                Uri.parse(job.contentUri)
            } else {
                Toast.makeText(context, "File not available for sharing", Toast.LENGTH_SHORT).show()
                return
            }

            val mimeType = getMimeType(job.fileName)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, job.fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val resInfoList = context.packageManager.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                try {
                    context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {
                    // Ignore
                }
            }

            val chooser = Intent.createChooser(intent, "Share '${job.fileName}' via...").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot share file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun sharePdf(context: Context, job: PrintJobEntity) {
        shareFile(context, job)
    }

    fun generateTermuxCommand(context: Context, fileName: String, packageName: String = "com.aistudio.pdfprinter.zxvpq"): String {
        val baseName = if (fileName.contains('.')) fileName.substringBeforeLast('.') else fileName
        val ext = fileName.substringAfterLast('.', "ps").lowercase(java.util.Locale.ROOT)
        val targetName = if (fileName.contains('.')) fileName else "$fileName.ps"
        val pdfFileName = "$baseName.pdf"
        val workingDir = getPosixFolderDir(context)
        // 1. Ghostscript converts .ps to .pdf, ImageMagick / convert supports .psd to .pdf in selected folder
        // 2. Uses termux-open / am start to open the generated PDF
        // 3. Exits Termux session automatically once completed
        return if (ext == "psd") {
            "cd \"$workingDir\" && (magick \"$targetName\" \"$pdfFileName\" || convert \"$targetName\" \"$pdfFileName\" || gs -dBATCH -dNOPAUSE -sDEVICE=pdfwrite -sOutputFile=\"$pdfFileName\" \"$targetName\") && (termux-open --choose \"$pdfFileName\" || termux-open \"$pdfFileName\" || am start -a android.intent.action.VIEW -d \"file://$workingDir/$pdfFileName\" -t \"application/pdf\"); exit"
        } else {
            "cd \"$workingDir\" && gs -dBATCH -dNOPAUSE -sDEVICE=pdfwrite -sOutputFile=\"$pdfFileName\" \"$targetName\" && (termux-open --choose \"$pdfFileName\" || termux-open \"$pdfFileName\" || am start -a android.intent.action.VIEW -d \"file://$workingDir/$pdfFileName\" -t \"application/pdf\"); exit"
        }
    }

    suspend fun syncFilesFromTreeUri(context: Context, treeUri: Uri): Int {
        var addedCount = 0
        try {
            val db = AppDatabase.getDatabase(context)
            val existingJobs = db.printJobDao().getAllJobsList()
            val existingNames = existingJobs.associateBy { it.fileName.lowercase(java.util.Locale.ROOT) }.toMutableMap()

            val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)

            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )

            val cursor = context.contentResolver.query(childrenUri, projection, null, null, null)
            cursor?.use { c ->
                val idCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val dateCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (c.moveToNext()) {
                    val docId = if (idCol >= 0) c.getString(idCol) else null
                    val name = if (nameCol >= 0) c.getString(nameCol) else null
                    val mime = if (mimeCol >= 0) c.getString(mimeCol) else null
                    val size = if (sizeCol >= 0) c.getLong(sizeCol) else 0L
                    val dateMod = if (dateCol >= 0) c.getLong(dateCol) else System.currentTimeMillis()

                    if (!name.isNullOrBlank() && !name.startsWith(".") && mime != DocumentsContract.Document.MIME_TYPE_DIR) {
                        val ext = name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
                        if (ext != "ps" && ext != "pdf") {
                            continue
                        }

                        val childDocUri = if (docId != null) {
                            DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        } else null

                        val lower = name.lowercase(java.util.Locale.ROOT)
                        val localCopy = File(context.filesDir, name)

                        // Copy to app storage so preview, conversions and file operations work reliably
                        try {
                            if (childDocUri != null && (!localCopy.exists() || localCopy.length() != size)) {
                                context.contentResolver.openInputStream(childDocUri)?.use { input ->
                                    FileOutputStream(localCopy).use { output ->
                                        input.copyTo(output)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        val format = when (ext) {
                            "pdf" -> "PDF Document"
                            "ps" -> "PostScript Document"
                            else -> "Document"
                        }

                        val displayFolder = getSelectedFolderPathDisplay(context)
                        val entity = PrintJobEntity(
                            fileName = name,
                            originalFormat = format,
                            fileSizeBytes = if (size > 0) size else localCopy.length(),
                            filePath = localCopy.absolutePath,
                            savedLocation = "$displayFolder$name",
                            contentUri = childDocUri?.toString(),
                            receivedTimestamp = if (dateMod > 0) dateMod else System.currentTimeMillis(),
                            clientIp = "Selected Folder",
                            pageCount = 1,
                            status = if (ext == "pdf") "PDF Document Ready" else "Selected from $displayFolder"
                        )

                        if (!existingNames.containsKey(lower)) {
                            db.printJobDao().insertJob(entity)
                            existingNames[lower] = entity
                            addedCount++
                        } else {
                            val existing = existingNames[lower]!!
                            db.printJobDao().updateJob(
                                existing.copy(
                                    fileSizeBytes = if (size > 0) size else localCopy.length(),
                                    filePath = localCopy.absolutePath,
                                    savedLocation = "$displayFolder$name",
                                    contentUri = childDocUri?.toString() ?: existing.contentUri
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return addedCount
    }

    fun openFolderInFileManager(context: Context, directoryPath: String = DEFAULT_VIRTUAL_PRINTER_DIR): Boolean {
        try {
            
            getVirtualPrinterFolder().mkdirs()
            File(DEFAULT_VIRTUAL_PRINTER_DIR).mkdirs()
        } catch (e: Exception) {
            // Ignore
        }

        val customUri = getSelectedFolderUri(context)
        val pm = context.packageManager
        val folderDocId = "primary:VirtualPrinter"
        val treeUri = customUri ?: DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents", folderDocId)
        val documentUri = if (customUri != null) {
            try {
                DocumentsContract.buildDocumentUriUsingTree(customUri, DocumentsContract.getTreeDocumentId(customUri))
            } catch (e: Exception) {
                DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", folderDocId)
            }
        } else {
            DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", folderDocId)
        }

        // Generate tailored folder intents for every known file manager app
        fun createFolderIntentForPackage(pkg: String): Intent? {
            return try {
                when (pkg) {
                    "com.sec.android.app.myfiles" -> {
                        Intent("com.sec.android.app.myfiles.OPEN_DIR").apply {
                            setPackage("com.sec.android.app.myfiles")
                            putExtra("samsung.myfiles.intent.extra.PATH", DEFAULT_VIRTUAL_PRINTER_DIR)
                            putExtra("samsung.myfiles.intent.extra.START_PATH", DEFAULT_VIRTUAL_PRINTER_DIR)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                    "com.google.android.apps.nbu.files" -> {
                        Intent(Intent.ACTION_VIEW).apply {
                            setPackage("com.google.android.apps.nbu.files")
                            setDataAndType(documentUri, "vnd.android.document/directory")
                            putExtra(DocumentsContract.EXTRA_INITIAL_URI, treeUri)
                            putExtra("android.provider.extra.INITIAL_URI", treeUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                    "com.alphainventor.filemanager" -> {
                        Intent(Intent.ACTION_VIEW).apply {
                            setPackage("com.alphainventor.filemanager")
                            setDataAndType(Uri.parse("file://$DEFAULT_VIRTUAL_PRINTER_DIR"), "resource/folder")
                            putExtra("current_path", DEFAULT_VIRTUAL_PRINTER_DIR)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                    "com.cxinventor.file.extractor" -> {
                        Intent(Intent.ACTION_VIEW).apply {
                            setPackage("com.cxinventor.file.extractor")
                            setDataAndType(Uri.parse("file://$DEFAULT_VIRTUAL_PRINTER_DIR"), "resource/folder")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                    "pl.solidexplorer2" -> {
                        Intent(Intent.ACTION_VIEW).apply {
                            setPackage("pl.solidexplorer2")
                            setDataAndType(Uri.parse("file://$DEFAULT_VIRTUAL_PRINTER_DIR"), "vnd.android.document/directory")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                    "com.mi.android.globalFileexplorer", "com.android.fileexplorer" -> {
                        Intent(Intent.ACTION_VIEW).apply {
                            setPackage(pkg)
                            setDataAndType(Uri.parse("file://$DEFAULT_VIRTUAL_PRINTER_DIR"), "vnd.android.document/directory")
                            putExtra("root_path", DEFAULT_VIRTUAL_PRINTER_DIR)
                            putExtra("path", DEFAULT_VIRTUAL_PRINTER_DIR)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                    "com.coloros.filemanager", "com.oplus.filemanager", "com.oneplus.filemanager" -> {
                        Intent(Intent.ACTION_VIEW).apply {
                            setPackage(pkg)
                            setDataAndType(Uri.parse("file://$DEFAULT_VIRTUAL_PRINTER_DIR"), "vnd.android.document/directory")
                            putExtra("file_path", DEFAULT_VIRTUAL_PRINTER_DIR)
                            putExtra("path", DEFAULT_VIRTUAL_PRINTER_DIR)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                    "com.android.documentsui", "com.google.android.documentsui" -> {
                        Intent(Intent.ACTION_VIEW).apply {
                            setPackage(pkg)
                            setDataAndType(documentUri, DocumentsContract.Document.MIME_TYPE_DIR)
                            putExtra(DocumentsContract.EXTRA_INITIAL_URI, treeUri)
                            putExtra("android.provider.extra.INITIAL_URI", treeUri)
                            putExtra("android.content.extra.SHOW_ADVANCED", true)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                    else -> {
                        Intent(Intent.ACTION_VIEW).apply {
                            setPackage(pkg)
                            setDataAndType(documentUri, "vnd.android.document/directory")
                            putExtra(DocumentsContract.EXTRA_INITIAL_URI, treeUri)
                            putExtra("android.provider.extra.INITIAL_URI", treeUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                }
            } catch (e: Exception) {
                null
            }
        }

        val fileManagerPackages = listOf(
            "com.sec.android.app.myfiles",
            "com.google.android.apps.nbu.files",
            "com.android.documentsui",
            "com.google.android.documentsui",
            "com.mi.android.globalFileexplorer",
            "com.android.fileexplorer",
            "com.coloros.filemanager",
            "com.oplus.filemanager",
            "com.oneplus.filemanager",
            "com.motorola.filemanager",
            "com.alphainventor.filemanager",
            "com.cxinventor.file.extractor",
            "pl.solidexplorer2"
        )

        // Find installed apps and create folder-specific intents
        val targetedIntents = fileManagerPackages.mapNotNull { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                createFolderIntentForPackage(pkg)
            } catch (e: Exception) {
                null
            }
        }.filter { intent ->
            try {
                pm.queryIntentActivities(intent, 0).isNotEmpty()
            } catch (e: Exception) {
                false
            }
        }

        // Generic implicit directory intent for Android system resolver
        val genericFolderIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(documentUri, DocumentsContract.Document.MIME_TYPE_DIR)
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, treeUri)
            putExtra("android.provider.extra.INITIAL_URI", treeUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (targetedIntents.isNotEmpty()) {
            if (targetedIntents.size == 1) {
                try {
                    context.startActivity(targetedIntents.first())
                    Toast.makeText(context, "Opening Virtual Printer", Toast.LENGTH_SHORT).show()
                    return true
                } catch (e: Exception) {
                    // Fall back to chooser
                }
            }

            val chooser = Intent.createChooser(targetedIntents.first(), "Open Virtual Printer folder with...").apply {
                if (targetedIntents.size > 1) {
                    val additional = targetedIntents.drop(1).toTypedArray()
                    putExtra(Intent.EXTRA_INITIAL_INTENTS, additional)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(chooser)
                return true
            } catch (e: Exception) {
                // Try fallback
            }
        }

        // Fallback: Launch standard generic folder intent
        try {
            if (pm.queryIntentActivities(genericFolderIntent, 0).isNotEmpty()) {
                context.startActivity(genericFolderIntent)
                return true
            }
        } catch (e: Exception) {
            // Ignore
        }

        Toast.makeText(
            context,
            "Folder location: $DEFAULT_VIRTUAL_PRINTER_DIR",
            Toast.LENGTH_LONG
        ).show()
        return false
    }

    fun openFileLocation(context: Context, fileName: String, filePath: String? = null): Boolean {
        val file = findFileOnDisk(context, fileName, filePath)
        if (file != null && file.exists()) {
            Toast.makeText(context, "File location: ${file.parentFile?.name ?: "Virtual Printer"}/${file.name}", Toast.LENGTH_SHORT).show()
        }
        return openFolderInFileManager(context)
    }

    fun findMatchingPsFile(context: Context, targetFileName: String): File? {
        val baseName = if (targetFileName.contains('.')) targetFileName.substringBeforeLast('.') else targetFileName
        val candidateNames = listOf("$baseName.ps", "$baseName.psd", "$baseName.eps")
        
        for (name in candidateNames) {
            val f = findFileOnDisk(context, name)
            if (f != null && f.exists() && f.length() > 0) return f
        }

        return null
    }

    suspend fun deleteMatchingPsFile(context: Context, targetFileName: String): Boolean {
        val baseName = if (targetFileName.contains('.')) targetFileName.substringBeforeLast('.') else targetFileName
        val candidateNames = listOf("$baseName.ps", "$baseName.psd", "$baseName.eps")
        var anyDeleted = false

        for (name in candidateNames) {
            val candidateFolders = getAllCandidateFolders(context)
            for (folder in candidateFolders) {
                val f = File(folder, name)
                if (f.exists()) {
                    if (f.delete()) anyDeleted = true
                }
            }
            val directSpaced = File("/storage/emulated/0/Virtual Printer", name)
            if (directSpaced.exists() && directSpaced.delete()) anyDeleted = true
            val directUnspaced = File("/storage/emulated/0/VirtualPrinter", name)
            if (directUnspaced.exists() && directUnspaced.delete()) anyDeleted = true
        }

        // Remove corresponding PS / PSD records from Room Database
        try {
            val db = AppDatabase.getDatabase(context)
            val allJobs = db.printJobDao().getAllJobsList()
            allJobs.filter {
                it.fileName.equals("$baseName.ps", ignoreCase = true) ||
                it.fileName.equals("$baseName.psd", ignoreCase = true) ||
                it.fileName.equals("$baseName.eps", ignoreCase = true) ||
                (it.fileName.substringBeforeLast('.').equals(baseName, ignoreCase = true) && (it.fileName.endsWith(".ps", ignoreCase = true) || it.fileName.endsWith(".psd", ignoreCase = true)))
            }.forEach {
                db.printJobDao().deleteJob(it)
                anyDeleted = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return anyDeleted
    }

    suspend fun syncStorageWithDatabase(context: Context): Int {
        var addedCount = 0
        try {
            initVirtualPrinterDirectory(context)
            val db = AppDatabase.getDatabase(context)
            val existingJobs = db.printJobDao().getAllJobsList()
            val existingJobMapByName = existingJobs.associateBy { it.fileName.lowercase(java.util.Locale.ROOT) }.toMutableMap()
            val existingJobMapByPath = existingJobs.associateBy { it.filePath.lowercase(java.util.Locale.ROOT) }.toMutableMap()

            val filesToScan = mutableListOf<File>()
            val customUri = getSelectedFolderUri(context)

            if (customUri != null) {
                // 1. Check if custom SAF tree uri is selected and scan only it
                try {
                    syncFilesFromTreeUri(context, customUri)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                // 2. Scan all candidate Virtual Printer folders ONLY if no custom folder selected
                val candidateFolders = getAllCandidateFolders(context)
                for (folder in candidateFolders) {
                    try {
                        if (folder.exists() && folder.isDirectory) {
                            folder.listFiles()?.forEach { file ->
                                val nameLower = file.name.lowercase(java.util.Locale.ROOT)
                                val ext = nameLower.substringAfterLast('.', "")
                                if (file.isFile && file.length() > 0 &&
                                    (ext == "ps" || ext == "pdf") &&
                                    !nameLower.startsWith(".") &&
                                    !nameLower.endsWith(".tmp") &&
                                    !nameLower.endsWith(".bin")
                                ) {
                                    if (filesToScan.none { it.name.equals(file.name, ignoreCase = true) }) {
                                        filesToScan.add(file)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore folder listing error
                    }
                }
            }

            for (file in filesToScan) {
                val lowerName = file.name.lowercase(java.util.Locale.ROOT)
                val lowerPath = file.absolutePath.lowercase(java.util.Locale.ROOT)

                // Skip re-adding files that the user cleared from the UI
                if (isFileClearedFromUi(context, file.name)) {
                    continue
                }

                // If already in DB, update size / path if needed
                val existingByName = existingJobMapByName[lowerName]
                val existingByPath = existingJobMapByPath[lowerPath]
                val existing = existingByName ?: existingByPath

                if (existing != null) {
                    if (existing.fileSizeBytes != file.length() || existing.filePath != file.absolutePath) {
                        val updated = existing.copy(
                            fileSizeBytes = file.length(),
                            filePath = file.absolutePath
                        )
                        db.printJobDao().updateJob(updated)
                    }
                    continue
                }

                val ext = file.name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
                val format = when (ext) {
                    "pdf" -> "PDF Document"
                    "ps" -> "PostScript Document"
                    else -> "Document"
                }
                val status = when (ext) {
                    "pdf" -> "PDF Document Ready"
                    "ps" -> "PostScript Document"
                    else -> "Saved in Virtual Printer"
                }

                val fileProviderUri = try {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                } catch (e: Exception) {
                    null
                }

                val entity = PrintJobEntity(
                    fileName = file.name,
                    originalFormat = format,
                    fileSizeBytes = file.length(),
                    filePath = file.absolutePath,
                    savedLocation = file.absolutePath,
                    contentUri = fileProviderUri?.toString(),
                    receivedTimestamp = if (file.lastModified() > 0) file.lastModified() else System.currentTimeMillis(),
                    clientIp = "Virtual Printer Storage",
                    pageCount = 1,
                    status = status
                )

                db.printJobDao().insertJob(entity)
                existingJobMapByName[lowerName] = entity
                existingJobMapByPath[lowerPath] = entity
                addedCount++
            }

            // Prune jobs: keep only .ps and .pdf files and remove any physically missing files
            val currentJobs = db.printJobDao().getAllJobsList()
            val customActiveUri = getSelectedFolderUri(context)
            val currentDisplayFolder = getSelectedFolderPathDisplay(context)
            
            for (job in currentJobs) {
                val ext = job.fileName.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
                if (ext != "ps" && ext != "pdf") {
                    db.printJobDao().deleteJob(job)
                    continue
                }
                
                // Enforce current active folder isolation
                if (customActiveUri != null) {
                    if (!job.savedLocation.startsWith(currentDisplayFolder)) {
                        db.printJobDao().deleteJob(job)
                        continue
                    }
                } else {
                    if (!job.savedLocation.startsWith("/") && !job.savedLocation.startsWith("Virtual Printer")) {
                        db.printJobDao().deleteJob(job)
                        continue
                    }
                }

                if (job.filePath.startsWith("content://")) {
                    try {
                        val uri = android.net.Uri.parse(job.filePath)
                        var docExists = false
                        context.contentResolver.query(
                            uri, 
                            arrayOf(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID), 
                            null, null, null
                        )?.use { c ->
                            if (c.moveToFirst()) docExists = true
                        }
                        if (!docExists) {
                            db.printJobDao().deleteJob(job)
                        }
                    } catch (e: Exception) {
                        // ignore or treat as missing if exception (like SecurityException or deleted)
                        db.printJobDao().deleteJob(job)
                    }
                } else {
                    val foundFile = findFileOnDisk(context, job.fileName, job.filePath)
                    val exists = foundFile != null && foundFile.exists() && foundFile.length() > 0
                    val hasValidInternal = java.io.File(context.filesDir, job.fileName).exists()

                    if (!exists && !hasValidInternal) {
                        db.printJobDao().deleteJob(job)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return addedCount
    }

    suspend fun importUriToVirtualPrinter(context: Context, uri: Uri): PrintJobEntity? {
        return try {
            val resolver = context.contentResolver
            var displayName = "Imported_${System.currentTimeMillis()}"
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrBlank()) {
                            displayName = name
                        }
                    }
                }
            }

            val cleanName = sanitizeOriginalFileName(displayName)
            val directFolder = getVirtualPrinterFolder()
            val directFile = File(directFolder, cleanName)

            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(directFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Also keep internal copy
            val internalFile = File(context.filesDir, cleanName)
            try {
                if (directFile.exists() && directFile.length() > 0) {
                    directFile.inputStream().use { input ->
                        FileOutputStream(internalFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val ext = cleanName.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
            val format = when (ext) {
                "pdf" -> "PDF Document"
                "ps", "eps" -> "PostScript Document"
                "psd" -> "PSD Document"
                "prn", "pcl", "raw" -> "RAW Print Stream"
                else -> "${ext.uppercase()} Document"
            }

            val fileProviderUri = try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    if (directFile.exists()) directFile else internalFile
                )
            } catch (e: Exception) {
                null
            }

            val entity = PrintJobEntity(
                fileName = cleanName,
                originalFormat = format,
                fileSizeBytes = if (directFile.exists()) directFile.length() else internalFile.length(),
                filePath = directFile.absolutePath,
                savedLocation = "$DEFAULT_VIRTUAL_PRINTER_DIR/$cleanName",
                contentUri = fileProviderUri?.toString() ?: uri.toString(),
                receivedTimestamp = System.currentTimeMillis(),
                clientIp = "Imported File",
                pageCount = 1,
                status = if (ext == "pdf") "PDF Document Ready" else "Saved in VirtualPrinter"
            )

            val db = AppDatabase.getDatabase(context)
            db.printJobDao().insertJob(entity)
            
            try {
                com.example.data.ProLicenseManager.getInstance(context).recordPrintReceived(cleanName)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            entity
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun registerExistingPdfFromDisk(context: Context, pdfFileName: String): PrintJobEntity? {
        val cleanName = if (pdfFileName.endsWith(".pdf", ignoreCase = true)) pdfFileName else "$pdfFileName.pdf"
        
        // Locate the file on disk
        val internalFile = File(context.filesDir, cleanName)
        
        val extFile = File(getVirtualPrinterFolder(), cleanName)
        val directPath = File("/storage/emulated/0/VirtualPrinter", cleanName)

        val targetFile = when {
            extFile.exists() && extFile.length() > 0 -> extFile
            directPath.exists() && directPath.length() > 0 -> directPath
            internalFile.exists() && internalFile.length() > 0 -> internalFile
            else -> null
        } ?: return null

        // Safely ensure internal copy exists for 100% permission-free rendering & sharing
        val safeInternal = ensureInternalCopy(context, cleanName, targetFile)

        // Read page count from safe internal copy
        var pageCount = 1
        try {
            val fileForCount = if (safeInternal.exists() && safeInternal.length() > 0) safeInternal else targetFile
            val pfd = android.os.ParcelFileDescriptor.open(fileForCount, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = android.graphics.pdf.PdfRenderer(pfd)
            pageCount = renderer.pageCount
            renderer.close()
            pfd.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val fileProviderUri = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                if (safeInternal.exists() && safeInternal.length() > 0) safeInternal else targetFile
            )
        } catch (e: Exception) {
            null
        }

        val db = AppDatabase.getDatabase(context)
        val existingJob = db.printJobDao().getAllJobsList().find {
            it.fileName.equals(cleanName, ignoreCase = true)
        }

        val entity = if (existingJob != null) {
            val updated = existingJob.copy(
                originalFormat = "Termux PDF",
                fileSizeBytes = if (safeInternal.exists() && safeInternal.length() > 0) safeInternal.length() else targetFile.length(),
                filePath = if (targetFile.exists()) targetFile.absolutePath else if (safeInternal.exists() && safeInternal.length() > 0) safeInternal.absolutePath else "$DEFAULT_VIRTUAL_PRINTER_DIR/$cleanName",
                savedLocation = "$DEFAULT_VIRTUAL_PRINTER_DIR/$cleanName",
                contentUri = fileProviderUri?.toString() ?: existingJob.contentUri,
                pageCount = pageCount,
                status = "Converted PDF (Termux)"
            )
            db.printJobDao().updateJob(updated)
            updated
        } else {
            val newEntity = PrintJobEntity(
                fileName = cleanName,
                originalFormat = "Termux PDF",
                fileSizeBytes = if (safeInternal.exists() && safeInternal.length() > 0) safeInternal.length() else targetFile.length(),
                filePath = if (targetFile.exists()) targetFile.absolutePath else if (safeInternal.exists() && safeInternal.length() > 0) safeInternal.absolutePath else "$DEFAULT_VIRTUAL_PRINTER_DIR/$cleanName",
                savedLocation = "$DEFAULT_VIRTUAL_PRINTER_DIR/$cleanName",
                contentUri = fileProviderUri?.toString(),
                receivedTimestamp = System.currentTimeMillis(),
                clientIp = "Termux Ghostscript",
                pageCount = pageCount,
                status = "Converted PDF (Termux)"
            )
            val id = db.printJobDao().insertJob(newEntity)
            newEntity.copy(id = id)
        }

        return entity
    }

    fun generateTermuxBatchScriptContent(context: Context): String {
        val workingDir = getPosixFolderDir(context)
        return """
            #!/data/data/com.termux/files/usr/bin/bash
            # VirtualPrinter Auto PS & PSD to PDF Converter Script
            cd "$workingDir" || exit 1
            echo "Searching for PostScript (.ps) and PSD files to convert in $workingDir..."
            count=0
            for file in *.[pP][sS] *.[pP][sS][dD] *.[eE][pP][sS]; do
                [ -f "${'$'}file" ] || continue
                ext="${'$'}{file##*.}"
                ext_lower="${'$'}(echo "${'$'}ext" | tr '[:upper:]' '[:lower:]')"
                base="${'$'}{file%.*}"
                echo "Converting: ${'$'}file -> ${'$'}base.pdf"
                if [ "${'$'}ext_lower" = "psd" ]; then
                    (magick "${'$'}file" "${'$'}base.pdf" || convert "${'$'}file" "${'$'}base.pdf" || gs -dBATCH -dNOPAUSE -sDEVICE=pdfwrite -sOutputFile="${'$'}base.pdf" "${'$'}file")
                else
                    gs -dBATCH -dNOPAUSE -sDEVICE=pdfwrite -sOutputFile="${'$'}base.pdf" "${'$'}file"
                fi
                count=${'$'}((count+1))
            done
            if [ ${'$'}count -eq 0 ]; then
                echo "No .ps or .psd files found in $workingDir"
            else
                echo "Successfully converted ${'$'}count file(s) to PDF in $workingDir!"
            fi
            exit 0
        """.trimIndent()
    }

    suspend fun createOrUpdateTermuxBatchScript(context: Context): Boolean {
        return try {
            val scriptContent = generateTermuxBatchScriptContent(context).toByteArray(Charsets.UTF_8)
            val fileName = "convert_all.sh"
            val workingDir = getPosixFolderDir(context)
            val targetDir = File(workingDir)
            if (!targetDir.exists()) targetDir.mkdirs()
            val targetFile = File(targetDir, fileName)
            FileOutputStream(targetFile).use { it.write(scriptContent) }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun executeInTermux(
        context: Context,
        command: String,
        inBackground: Boolean = false
    ): Boolean {
        try {
            // 1. Always copy command to clipboard first so user can paste immediately if needed
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("Termux Command", command))

            // 2. Dispatch the Termux RUN_COMMAND intent to RunCommandService
            val workingDir = getPosixFolderDir(context)
            val intent = Intent().apply {
                setClassName("com.termux", "com.termux.app.RunCommandService")
                action = "com.termux.RUN_COMMAND"
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
                putExtra("com.termux.RUN_COMMAND_WORKDIR", workingDir)
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", inBackground)
                putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0") // 0 = opens/creates foreground terminal session
            }

            var serviceStarted = false
            try {
                context.startService(intent)
                serviceStarted = true
            } catch (serviceEx: Exception) {
                // Fallback attempt with foreground service or activity if direct startService has background restriction
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                        serviceStarted = true
                    }
                } catch (ignored: Exception) {
                    serviceEx.printStackTrace()
                }
            }

            // 3. Bring Termux to foreground so user sees execution / terminal window
            val packageManager = context.packageManager
            val launchIntent = packageManager.getLaunchIntentForPackage("com.termux")
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            }

            if (serviceStarted) {
                Toast.makeText(context, "⚡ Dispatched to Termux! (Command copied to clipboard)", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Termux opened! Long-press & Paste to run (copied to clipboard)", Toast.LENGTH_LONG).show()
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Termux not installed or cannot be opened", Toast.LENGTH_SHORT).show()
        }
        return false
    }

    fun launchTermux(context: Context): Boolean {
        try {
            val packageManager = context.packageManager
            val intent = packageManager.getLaunchIntentForPackage("com.termux")
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    fun shareSharedFileItem(context: Context, item: SharedFileItem) {
        try {
            val file = File(item.filePath)
            if (!file.exists()) {
                Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show()
                return
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = item.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, item.fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share '${item.fileName}' via...").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot share file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun openSharedFileItem(context: Context, item: SharedFileItem) {
        try {
            val file = File(item.filePath)
            if (!file.exists()) {
                Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show()
                return
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, item.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                val chooser = Intent.createChooser(intent, "Open '${item.fileName}' with...").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
