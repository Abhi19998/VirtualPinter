package com.example.server

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.example.data.AppDatabase
import com.example.data.PendingPrintJob
import com.example.data.PrintJobEntity
import com.example.data.ProLicenseManager
import com.example.data.SharedFileItem
import com.example.utils.StorageHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NetworkPrinterServer {

    private const val TAG = "NetworkPrinterServer"
    const val PORT = 9100
    const val HTTP_PORT = 8080

    private var serverSocket: ServerSocket? = null
    private var httpServer: ServerSocket? = null
    private var serverJob: Job? = null
    private var httpJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _lastJobReceived = MutableSharedFlow<PrintJobEntity>(replay = 0, extraBufferCapacity = 64)
    val lastJobReceived: SharedFlow<PrintJobEntity> = _lastJobReceived.asSharedFlow()

    private val _pendingJobForSave = MutableStateFlow<PendingPrintJob?>(null)
    val pendingJobForSave: StateFlow<PendingPrintJob?> = _pendingJobForSave.asStateFlow()

    private val _serverStatusMessage = MutableStateFlow("Stopped")
    val serverStatusMessage: StateFlow<String> = _serverStatusMessage.asStateFlow()

    // Files staged from phone to send back to PC
    private val _sharedFilesForPc = MutableStateFlow<List<SharedFileItem>>(emptyList())
    val sharedFilesForPc: StateFlow<List<SharedFileItem>> = _sharedFilesForPc.asStateFlow()

    fun addSharedFileForPc(item: SharedFileItem) {
        val current = _sharedFilesForPc.value.toMutableList()
        current.removeAll { it.fileName == item.fileName }
        current.add(0, item)
        _sharedFilesForPc.value = current
    }

    fun removeSharedFileForPc(id: String) {
        val current = _sharedFilesForPc.value.toMutableList()
        val item = current.find { it.id == id }
        if (item != null) {
            try {
                File(item.filePath).delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            current.remove(item)
            _sharedFilesForPc.value = current
        }
    }

    fun clearSharedFilesForPc() {
        val current = _sharedFilesForPc.value
        current.forEach { item ->
            try { File(item.filePath).delete() } catch (e: Exception) {}
        }
        _sharedFilesForPc.value = emptyList()
    }

    fun notifyJobReceived(entity: PrintJobEntity) {
        _lastJobReceived.tryEmit(entity)
    }

    fun postPendingJob(job: PendingPrintJob) {
        _pendingJobForSave.value = job
    }

    fun dismissPendingJob(job: PendingPrintJob? = null) {
        if (job == null || _pendingJobForSave.value == job) {
            _pendingJobForSave.value = null
        }
    }

    suspend fun savePendingJob(
        context: Context,
        pendingJob: PendingPrintJob,
        customName: String
    ): PrintJobEntity {
        val entity = StorageHelper.saveAndRegisterPdf(
            context = context,
            pdfFile = pendingJob.tempFile,
            baseName = pendingJob.defaultName,
            originalFormat = pendingJob.originalFormat,
            clientIp = pendingJob.clientIp,
            pageCount = pendingJob.pageCount,
            customFileName = customName
        )
        _lastJobReceived.tryEmit(entity)
        if (_pendingJobForSave.value == pendingJob) {
            _pendingJobForSave.value = null
        }
        return entity
    }

    fun start(context: Context) {
        if (_isRunning.value) return

        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(PORT).apply {
                    soTimeout = 3000 // 3 sec timeout to allow checking isActive
                    reuseAddress = true
                }
                
                // Start Web UI Server alongside
                httpJob = scope.launch(Dispatchers.IO) { startHttpServer(context) }
                
                _isRunning.value = true
                _serverStatusMessage.value = "Running on Port $PORT"
                Log.i(TAG, "Virtual Printer Server started on port $PORT")

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Virtual Printer & Web Share started on Ports $PORT / $HTTP_PORT", Toast.LENGTH_SHORT).show()
                }

                while (isActive && _isRunning.value) {
                    try {
                        val clientSocket = serverSocket?.accept()
                        if (clientSocket != null) {
                            launch(Dispatchers.IO) {
                                handleClientConnection(context, clientSocket)
                            }
                        }
                    } catch (e: SocketTimeoutException) {
                        // Regular timeout to loop and check isActive
                    } catch (e: Exception) {
                        if (isActive && _isRunning.value && serverSocket?.isClosed == false) {
                            Log.e(TAG, "Error accepting client: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                if (isActive && _isRunning.value) {
                    Log.e(TAG, "Server socket error: ${e.message}", e)
                    _serverStatusMessage.value = "Error: ${e.message}"
                    _isRunning.value = false
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Cannot start server: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            } finally {
                stopInternal()
            }
        }
    }

    fun stop(context: Context? = null) {
        _isRunning.value = false
        stopInternal()
        serverJob?.cancel()
        _serverStatusMessage.value = "Stopped"
        if (context != null) {
            Toast.makeText(context, "Virtual Printer Server stopped", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopInternal() {
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // Ignored on close
        }
        try {
            httpServer?.close()
        } catch (e: Exception) {
            // Ignored on close
        }
        serverSocket = null
        httpServer = null
        httpJob?.cancel()
        _isRunning.value = false
    }
    
    private suspend fun CoroutineScope.startHttpServer(context: Context) {
        try {
            httpServer = ServerSocket(HTTP_PORT).apply {
                soTimeout = 3000
                reuseAddress = true
            }
            while (isActive && _isRunning.value) {
                try {
                    val client = httpServer?.accept()
                    if (client != null) {
                        launch(Dispatchers.IO) {
                            handleHttpConnection(context, client)
                        }
                    }
                } catch (e: SocketTimeoutException) {
                    // Normal timeout, continue loop
                } catch (e: Exception) {
                    if (isActive && _isRunning.value && httpServer?.isClosed == false) {
                        Log.e(TAG, "HTTP Server error: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            if (isActive && _isRunning.value) {
                Log.w(TAG, "HTTP web server on port $HTTP_PORT could not be started: ${e.message}")
            }
        }
    }
    
    private suspend fun handleHttpConnection(context: Context, socket: Socket) {
        val clientIp = socket.inetAddress?.hostAddress ?: "Unknown"
        try {
            socket.soTimeout = 15000
            val input = socket.getInputStream()
            val out = socket.getOutputStream()
            
            // Read HTTP request header lines
            val headerBytes = ByteArrayOutputStream()
            var prev1 = -1
            var prev2 = -1
            var prev3 = -1
            var b: Int
            while (true) {
                b = input.read()
                if (b == -1) break
                headerBytes.write(b)
                if (prev3 == 13 && prev2 == 10 && prev1 == 13 && b == 10) {
                    // Reached \r\n\r\n
                    break
                }
                prev3 = prev2
                prev2 = prev1
                prev1 = b
            }
            
            val headerText = headerBytes.toString(Charsets.UTF_8.name())
            val headerLines = headerText.lines()
            val requestLine = headerLines.firstOrNull() ?: return
            
            var contentLength = 0
            var boundary = ""
            for (line in headerLines) {
                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line.substring(15).trim().toIntOrNull() ?: 0
                } else if (line.startsWith("Content-Type: multipart/form-data", ignoreCase = true)) {
                    val parts = line.split("boundary=")
                    if (parts.size > 1) {
                        boundary = parts[1].trim().trim('"')
                    }
                }
            }
            
            when {
                requestLine.startsWith("GET / ") || requestLine.startsWith("GET /index.html") -> {
                    serveWebHubHtml(context, out)
                }
                requestLine.startsWith("GET /download?") -> {
                    val query = requestLine.substringAfter("GET /download?").substringBefore(" HTTP/")
                    val params = parseQueryParams(query)
                    val fileId = params["id"]
                    val fileName = params["file"]
                    
                    val sharedItem = _sharedFilesForPc.value.find { it.id == fileId || it.fileName == fileName }
                    if (sharedItem != null && File(sharedItem.filePath).exists()) {
                        serveFileDownload(File(sharedItem.filePath), sharedItem.fileName, sharedItem.mimeType, out)
                    } else {
                        serveNotFound(out, "Requested shared file not found or has been removed.")
                    }
                }
                requestLine.startsWith("GET /download-saved?") -> {
                    val query = requestLine.substringAfter("GET /download-saved?").substringBefore(" HTTP/")
                    val params = parseQueryParams(query)
                    val jobIdStr = params["id"]
                    val fileName = params["file"]
                    
                    val db = AppDatabase.getDatabase(context)
                    val allJobs = db.printJobDao().getAllJobs().firstOrNull() ?: emptyList()
                    val targetJob = allJobs.find { it.id.toString() == jobIdStr || it.fileName == fileName }
                    
                    if (targetJob != null && File(targetJob.filePath).exists()) {
                        val file = File(targetJob.filePath)
                        serveFileDownload(file, targetJob.fileName, StorageHelper.getMimeType(targetJob.fileName), out)
                    } else {
                        serveNotFound(out, "Saved document file not found on device.")
                    }
                }
                requestLine.startsWith("GET /api/shared-files") -> {
                    val items = _sharedFilesForPc.value
                    val json = StringBuilder("[")
                    items.forEachIndexed { index, item ->
                        if (index > 0) json.append(",")
                        json.append("{\"id\":\"${item.id}\",\"fileName\":\"${escapeJson(item.fileName)}\",\"fileSizeBytes\":${item.fileSizeBytes},\"mimeType\":\"${item.mimeType}\",\"timestamp\":${item.timestamp}}")
                    }
                    json.append("]")
                    val response = "HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${json.toString().toByteArray().size}\r\nConnection: close\r\n\r\n$json"
                    out.write(response.toByteArray())
                    out.flush()
                }
                requestLine.startsWith("POST /upload") && contentLength > 0 -> {
                    val proManager = ProLicenseManager.getInstance(context)
                    if (!proManager.canReceivePrint()) {
                        val limitHtml = """
                            <!DOCTYPE html>
                            <html>
                            <head>
                                <meta charset="utf-8">
                                <meta name="viewport" content="width=device-width, initial-scale=1">
                                <title>Free Limit Reached - Virtual PDF Printer PRO</title>
                                <style>
                                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #0f172a; color: #f8fafc; display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; padding: 20px; box-sizing: border-box; }
                                    .card { background: #1e293b; border: 1px solid #f59e0b; border-radius: 16px; padding: 32px; max-width: 480px; width: 100%; text-align: center; box-shadow: 0 10px 25px rgba(0,0,0,0.5); }
                                    .icon { font-size: 54px; margin-bottom: 16px; }
                                    h2 { color: #f59e0b; margin: 0 0 10px; }
                                    p { color: #94a3b8; line-height: 1.5; font-size: 15px; }
                                    .btn { display: inline-block; background: #f59e0b; color: #000; text-decoration: none; padding: 12px 24px; border-radius: 8px; font-weight: bold; margin-top: 20px; transition: 0.2s; }
                                    .btn:hover { background: #d97706; }
                                </style>
                            </head>
                            <body>
                                <div class="card">
                                    <div class="icon">👑</div>
                                    <h2>Free Plan Limit Reached (10 Files)</h2>
                                    <p>You have reached the maximum of 10 received files on the Free Plan.</p>
                                    <p>Please enter your <b>PRO Activation Key</b> in the Virtual PDF Printer app on your phone to unlock unlimited printing and conversions!</p>
                                    <a href="/" class="btn">Back to Web Hub</a>
                                </div>
                            </body>
                            </html>
                        """.trimIndent()
                        val response = "HTTP/1.1 403 Forbidden\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${limitHtml.toByteArray().size}\r\nConnection: close\r\n\r\n$limitHtml"
                        out.write(response.toByteArray())
                        out.flush()
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(context, "⚠️ Free Limit reached (10 files). Upgrade to PRO for unlimited printing!", Toast.LENGTH_LONG).show()
                        }
                        return
                    }

                    // Read full body bytes
                    val body = ByteArray(contentLength)
                    var totalRead = 0
                    while (totalRead < contentLength) {
                        val read = input.read(body, totalRead, contentLength - totalRead)
                        if (read == -1) break
                        totalRead += read
                    }
                    
                    var payloadStart = 0
                    var payloadEnd = body.size
                    var originalFileName = ""

                    if (boundary.isNotEmpty()) {
                        // Find end of multipart headers: \r\n\r\n
                        val headerEndMarker = byteArrayOf(13, 10, 13, 10)
                        for (i in 0..body.size - headerEndMarker.size) {
                            var found = true
                            for (j in headerEndMarker.indices) {
                                if (body[i + j] != headerEndMarker[j]) { found = false; break }
                            }
                            if (found) {
                                payloadStart = i + 4
                                val partHeaderBytes = body.copyOfRange(0, i)
                                val partHeaderStr = String(partHeaderBytes, Charsets.UTF_8)
                                // Extract filename="something.xlsx"
                                val regex = Regex("filename=\"?([^\";\\r\\n]+)\"?", RegexOption.IGNORE_CASE)
                                val match = regex.find(partHeaderStr)
                                if (match != null && match.groupValues.size > 1) {
                                    originalFileName = match.groupValues[1].trim()
                                }
                                break
                            }
                        }
                        
                        // Find start of trailing boundary: \r\n--$boundary
                        val boundaryBytes = ("\r\n--$boundary").toByteArray()
                        for (i in payloadStart..body.size - boundaryBytes.size) {
                            var found = true
                            for (j in boundaryBytes.indices) {
                                if (body[i + j] != boundaryBytes[j]) { found = false; break }
                            }
                            if (found) {
                                payloadEnd = i
                                break
                            }
                        }
                    }
                    
                    val finalPayload = body.copyOfRange(payloadStart, payloadEnd)
                    
                    if (originalFileName.isBlank()) {
                        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                        originalFileName = "WebUpload_$ts.bin"
                    }
                    
                    // SAVE IN ORIGINAL FORMAT - NO FORCED CONVERSION TO PDF!
                    val savedEntity = StorageHelper.saveAndRegisterRawFile(
                        context = context,
                        fileBytes = finalPayload,
                        originalFileName = originalFileName,
                        clientIp = "$clientIp (Web Share)"
                    )
                    _lastJobReceived.tryEmit(savedEntity)

                    val destFolder = StorageHelper.getSelectedFolderPathDisplay(context)

                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            context,
                            "📥 Received '${savedEntity.fileName}' in original format, saved to $destFolder",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    
                    val sizeFormatted = formatFileSize(finalPayload.size.toLong())
                    val successHtml = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta charset="utf-8">
                            <meta name="viewport" content="width=device-width, initial-scale=1">
                            <title>Uploaded Successfully - Virtual Printer</title>
                            <style>
                                body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #0f172a; color: #f8fafc; display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; padding: 20px; box-sizing: border-box; }
                                .card { background: #1e293b; border: 1px solid #334155; border-radius: 16px; padding: 32px; max-width: 480px; width: 100%; text-align: center; box-shadow: 0 10px 25px rgba(0,0,0,0.5); }
                                .icon { font-size: 54px; margin-bottom: 16px; }
                                h2 { color: #38bdf8; margin: 0 0 10px; }
                                .file-tag { background: #0284c7; color: #fff; padding: 6px 14px; border-radius: 20px; font-weight: bold; display: inline-block; margin: 12px 0; word-break: break-all; }
                                p { color: #94a3b8; line-height: 1.5; font-size: 15px; }
                                .btn { display: inline-block; background: #2563eb; color: #fff; text-decoration: none; padding: 12px 24px; border-radius: 8px; font-weight: bold; margin-top: 20px; transition: 0.2s; }
                                .btn:hover { background: #1d4ed8; }
                            </style>
                        </head>
                        <body>
                            <div class="card">
                                <div class="icon">✅</div>
                                <h2>File Uploaded to Phone!</h2>
                                <div class="file-tag">${savedEntity.fileName} ($sizeFormatted)</div>
                                <p>Saved in <b>original format</b> directly to your phone's <code>$destFolder</code> folder.</p>
                                <a href="/" class="btn">Back to Web Hub</a>
                                <script>setTimeout(() => { window.location.href = '/'; }, 3000);</script>
                            </div>
                        </body>
                        </html>
                    """.trimIndent()
                    
                    val response = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${successHtml.toByteArray().size}\r\nConnection: close\r\n\r\n$successHtml"
                    out.write(response.toByteArray())
                    out.flush()
                }
                else -> {
                    serveNotFound(out, "Page not found.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "HTTP connection error: ${e.message}")
        } finally {
            try { socket.close() } catch (e: Exception) {}
        }
    }

    private suspend fun serveWebHubHtml(context: Context, out: OutputStream) {
        val sharedList = _sharedFilesForPc.value
        val db = AppDatabase.getDatabase(context)
        val allSavedJobs = db.printJobDao().getAllJobs().firstOrNull() ?: emptyList()
        val currentDestFolder = StorageHelper.getSelectedFolderPathDisplay(context)

        val sharedRowsHtml = if (sharedList.isEmpty()) {
            """<tr><td colspan="4" style="text-align:center;padding:30px;color:#94a3b8;">
                <div style="font-size:32px;margin-bottom:8px">📭</div>
                <b>No files shared from phone yet</b><br>
                <span style="font-size:13px">Tap <b>'Web Share' ➔ 'Send Files to PC'</b> in the phone app to send photos, docs, or spreadsheets here!</span>
            </td></tr>"""
        } else {
            sharedList.joinToString("") { item ->
                val size = formatFileSize(item.fileSizeBytes)
                val ext = item.fileName.substringAfterLast('.', "").uppercase().ifEmpty { "FILE" }
                val time = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(item.timestamp))
                """
                <tr>
                    <td>
                        <span class="badge badge-ext">$ext</span>
                        <b style="color:#f8fafc;word-break:break-all">${item.fileName}</b>
                    </td>
                    <td><span class="badge badge-size">$size</span></td>
                    <td style="color:#94a3b8;font-size:13px">$time</td>
                    <td>
                        <a href="/download?id=${item.id}" class="btn-download" download="${item.fileName}">⬇️ Download</a>
                    </td>
                </tr>
                """.trimIndent()
            }
        }

        val savedRowsHtml = if (allSavedJobs.isEmpty()) {
            """<tr><td colspan="4" style="text-align:center;padding:20px;color:#94a3b8;">No documents received on phone yet</td></tr>"""
        } else {
            allSavedJobs.take(8).joinToString("") { job ->
                val size = formatFileSize(job.fileSizeBytes)
                val ext = job.fileName.substringAfterLast('.', "").uppercase().ifEmpty { "DOC" }
                val time = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(job.receivedTimestamp))
                """
                <tr>
                    <td>
                        <span class="badge badge-ext">$ext</span>
                        <b style="color:#f8fafc;word-break:break-all">${job.fileName}</b>
                    </td>
                    <td><span class="badge badge-size">$size</span></td>
                    <td style="color:#94a3b8;font-size:13px">$time</td>
                    <td>
                        <a href="/download-saved?id=${job.id}" class="btn-download" download="${job.fileName}">⬇️ Download</a>
                    </td>
                </tr>
                """.trimIndent()
            }
        }

        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Virtual Printer & Web Share Hub</title>
                <link rel="preconnect" href="https://fonts.googleapis.com">
                <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&family=JetBrains+Mono:wght@500;700&display=swap" rel="stylesheet">
                <style>
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body { font-family: 'Inter', sans-serif; background: #0b1120; color: #f1f5f9; min-height: 100vh; padding: 24px 16px; }
                    .container { max-width: 900px; margin: 0 auto; display: flex; flex-direction: column; gap: 24px; }
                    
                    .header { background: linear-gradient(135deg, #1e293b 0%, #0f172a 100%); border: 1px solid #334155; border-radius: 20px; padding: 24px; display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 16px; box-shadow: 0 10px 25px rgba(0,0,0,0.3); }
                    .header-title { display: flex; align-items: center; gap: 14px; }
                    .header-icon { font-size: 36px; background: #0284c7; width: 56px; height: 56px; border-radius: 14px; display: flex; align-items: center; justify-content: center; box-shadow: 0 4px 12px rgba(2,132,199,0.4); }
                    .header h1 { font-size: 22px; font-weight: 800; color: #f8fafc; }
                    .header p { font-size: 13px; color: #94a3b8; margin-top: 2px; }
                    .status-pill { background: rgba(34, 197, 94, 0.15); border: 1px solid #22c55e; color: #4ade80; font-size: 12px; font-weight: 700; padding: 6px 14px; border-radius: 20px; display: flex; align-items: center; gap: 6px; }
                    .status-dot { width: 8px; height: 8px; background: #22c55e; border-radius: 50%; animation: pulse 2s infinite; }
                    
                    .card { background: #1e293b; border: 1px solid #334155; border-radius: 18px; padding: 24px; box-shadow: 0 4px 20px rgba(0,0,0,0.2); }
                    .card-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18px; }
                    .card-title { font-size: 17px; font-weight: 700; display: flex; align-items: center; gap: 10px; color: #38bdf8; }
                    
                    .upload-dropzone { border: 2px dashed #0284c7; background: rgba(2, 132, 199, 0.04); border-radius: 14px; padding: 32px 20px; text-align: center; cursor: pointer; transition: all 0.2s; position: relative; }
                    .upload-dropzone:hover { background: rgba(2, 132, 199, 0.1); border-color: #38bdf8; }
                    .upload-dropzone input[type="file"] { position: absolute; top: 0; left: 0; width: 100%; height: 100%; opacity: 0; cursor: pointer; }
                    .upload-icon { font-size: 42px; margin-bottom: 10px; color: #38bdf8; }
                    .file-name-display { margin-top: 10px; font-weight: 600; color: #f8fafc; font-size: 14px; }
                    
                    .btn-submit { background: linear-gradient(135deg, #0284c7 0%, #2563eb 100%); color: #fff; border: none; padding: 14px 28px; border-radius: 12px; font-size: 15px; font-weight: 700; cursor: pointer; width: 100%; margin-top: 16px; transition: 0.2s; box-shadow: 0 4px 14px rgba(37,99,235,0.3); }
                    .btn-submit:hover { opacity: 0.95; transform: translateY(-1px); }
                    .format-note { background: #0f172a; border: 1px solid #334155; border-radius: 10px; padding: 10px 14px; margin-top: 14px; font-size: 13px; color: #cbd5e1; display: flex; align-items: center; gap: 8px; }
                    
                    table { width: 100%; border-collapse: collapse; margin-top: 8px; }
                    th { text-align: left; padding: 10px 12px; font-size: 12px; color: #94a3b8; text-transform: uppercase; letter-spacing: 0.5px; border-bottom: 1px solid #334155; }
                    td { padding: 12px; border-bottom: 1px solid #334155; vertical-align: middle; font-size: 14px; }
                    tr:last-child td { border-bottom: none; }
                    
                    .badge { display: inline-block; padding: 3px 8px; border-radius: 6px; font-size: 11px; font-weight: 700; margin-right: 6px; }
                    .badge-ext { background: #0284c7; color: #fff; }
                    .badge-size { background: #334155; color: #94a3b8; }
                    
                    .btn-download { background: #10b981; color: #fff; text-decoration: none; padding: 6px 14px; border-radius: 8px; font-size: 13px; font-weight: 600; display: inline-flex; align-items: center; gap: 4px; transition: 0.2s; }
                    .btn-download:hover { background: #059669; }
                    
                    .btn-refresh { background: #334155; color: #f8fafc; border: none; padding: 6px 12px; border-radius: 8px; font-size: 13px; font-weight: 600; cursor: pointer; display: flex; align-items: center; gap: 6px; transition: 0.2s; }
                    .btn-refresh:hover { background: #475569; }
                    
                    .info-box { background: #0f172a; border-left: 4px solid #38bdf8; border-radius: 0 12px 12px 0; padding: 14px 18px; font-size: 13px; line-height: 1.6; color: #94a3b8; }
                    .info-box code { font-family: 'JetBrains Mono', monospace; background: #1e293b; color: #38bdf8; padding: 2px 6px; border-radius: 4px; }
                    
                    @keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.4; } }
                    @media (max-width: 600px) { body { padding: 12px; } .card { padding: 16px; } th:nth-child(3), td:nth-child(3) { display: none; } }
                </style>
            </head>
            <body>
                <div class="container">
                    <!-- Header -->
                    <div class="header">
                        <div class="header-title">
                            <div class="header-icon">🖨️</div>
                            <div>
                                <h1>Virtual Printer & Web Share Hub</h1>
                                <p>Two-Way Local Transfer between Phone & PC</p>
                            </div>
                        </div>
                        <div class="status-pill">
                            <div class="status-dot"></div>
                            Connected to Android Device
                        </div>
                    </div>

                    <!-- 1. Send Files to Phone (PC -> Phone) -->
                    <div class="card">
                        <div class="card-header">
                            <div class="card-title">📤 Send Files from PC to Phone</div>
                            <span style="font-size:12px;color:#94a3b8">Preserves Original Format</span>
                        </div>
                        
                        <form method="POST" action="/upload" enctype="multipart/form-data" id="uploadForm">
                            <div class="upload-dropzone" id="dropzone">
                                <div class="upload-icon">📁</div>
                                <div style="font-weight:700;font-size:16px;color:#f8fafc">Click or Drag & Drop Any File Here</div>
                                <p style="font-size:13px;color:#94a3b8;margin-top:4px">All formats supported: Excel (.xlsx), Word (.docx), Images (.png, .jpg), PDFs, ZIP, Text</p>
                                <div class="file-name-display" id="fileNameDisplay"></div>
                                <input type="file" name="file" id="fileInput" required>
                            </div>
                            
                            <div class="format-note">
                                <span>⚡</span>
                                <span><b>Original format preserved:</b> Uploaded files are saved directly in their authentic format to the phone's <code>$currentDestFolder</code> folder without conversion.</span>
                            </div>
                            
                            <button type="submit" class="btn-submit" id="submitBtn">🚀 Send File to Android Phone</button>
                        </form>
                    </div>

                    <!-- 2. Files Shared from Phone (Phone -> PC) -->
                    <div class="card">
                        <div class="card-header">
                            <div class="card-title">📥 Files Shared from Phone (Download to PC)</div>
                            <button class="btn-refresh" onclick="location.reload()">🔄 Refresh List</button>
                        </div>
                        
                        <div style="overflow-x:auto">
                            <table>
                                <thead>
                                    <tr>
                                        <th>File Name</th>
                                        <th>Size</th>
                                        <th>Shared Time</th>
                                        <th>Action</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    $sharedRowsHtml
                                </tbody>
                            </table>
                        </div>
                    </div>

                    <!-- 3. Stored Files on Phone -->
                    <div class="card">
                        <div class="card-header">
                            <div class="card-title">📱 Stored Documents & Received Files</div>
                            <span style="font-size:12px;color:#94a3b8">$currentDestFolder</span>
                        </div>
                        
                        <div style="overflow-x:auto">
                            <table>
                                <thead>
                                    <tr>
                                        <th>Document Name</th>
                                        <th>Size</th>
                                        <th>Timestamp</th>
                                        <th>Download</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    $savedRowsHtml
                                </tbody>
                            </table>
                        </div>
                    </div>

                    <!-- 4. Virtual RAW Printer Guide -->
                    <div class="card">
                        <div class="card-header">
                            <div class="card-title">🖨️ Windows / Mac Virtual Printer Setup (Port 9100)</div>
                        </div>
                        <div class="info-box">
                            To print directly from desktop applications (Word, Chrome, Excel) as a network printer:<br>
                            1. In Windows: Go to <b>Printers & Scanners</b> ➔ <b>Add Printer</b> ➔ <b>Add using IP address</b><br>
                            2. Set Device type to <code>TCP/IP Device</code>, enter this phone's IP address, Port: <code>9100</code><br>
                            3. Select Driver: <b>Microsoft Print to PDF</b> or <b>Generic / Text Only</b>
                        </div>
                    </div>
                </div>

                <script>
                    const fileInput = document.getElementById('fileInput');
                    const fileNameDisplay = document.getElementById('fileNameDisplay');
                    const submitBtn = document.getElementById('submitBtn');
                    const uploadForm = document.getElementById('uploadForm');

                    fileInput.addEventListener('change', (e) => {
                        if (fileInput.files.length > 0) {
                            const file = fileInput.files[0];
                            const sizeMb = (file.size / (1024 * 1024)).toFixed(2);
                            fileNameDisplay.innerHTML = "Selected: <b>" + file.name + "</b> (" + sizeMb + " MB)";
                        } else {
                            fileNameDisplay.innerHTML = "";
                        }
                    });

                    uploadForm.addEventListener('submit', () => {
                        submitBtn.innerText = "⏳ Uploading to phone...";
                        submitBtn.style.opacity = "0.7";
                        submitBtn.disabled = true;
                    });
                </script>
            </body>
            </html>
        """.trimIndent()

        val response = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${html.toByteArray().size}\r\nConnection: close\r\n\r\n$html"
        out.write(response.toByteArray())
        out.flush()
    }

    private fun serveFileDownload(file: File, fileName: String, mimeType: String, out: OutputStream) {
        val safeName = fileName.replace("\"", "")
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: $mimeType\r\n" +
                "Content-Disposition: attachment; filename=\"$safeName\"\r\n" +
                "Content-Length: ${file.length()}\r\n" +
                "Connection: close\r\n\r\n"
        out.write(header.toByteArray())
        
        FileInputStream(file).use { inStream ->
            val buffer = ByteArray(16384)
            var bytesRead: Int
            while (inStream.read(buffer).also { bytesRead = it } != -1) {
                out.write(buffer, 0, bytesRead)
            }
        }
        out.flush()
    }

    private fun serveNotFound(out: OutputStream, message: String) {
        val html = "<html><body><h2>404 Not Found</h2><p>$message</p><a href=\"/\">Back to Hub</a></body></html>"
        val response = "HTTP/1.1 404 Not Found\r\nContent-Type: text/html\r\nContent-Length: ${html.length}\r\nConnection: close\r\n\r\n$html"
        out.write(response.toByteArray())
        out.flush()
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val pairs = query.split('&')
        for (pair in pairs) {
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                map[key] = value
            }
        }
        return map
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format(Locale.getDefault(), "%.1f MB", mb)
    }

    private suspend fun handleClientConnection(context: Context, socket: Socket) {
        val clientIp = socket.inetAddress?.hostAddress ?: "Unknown"
        Log.i(TAG, "Incoming print connection from $clientIp")

        val proManager = ProLicenseManager.getInstance(context)
        if (!proManager.canReceivePrint()) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    "⚠️ Free Plan Limit reached (10 files). Activate PRO for unlimited printing!",
                    Toast.LENGTH_LONG
                ).show()
            }
            Log.w(TAG, "Print connection rejected: Free limit of 10 files reached")
            try { socket.close() } catch (e: Exception) {}
            return
        }
        
        try {
            socket.soTimeout = 5000 // 5s initial read timeout
            val inputStream: InputStream = socket.getInputStream()
            val buffer = ByteArray(16384)
            val byteAccumulator = ByteArrayOutputStream()
            
            var bytesRead: Int
            var totalBytes = 0L

            while (true) {
                try {
                    bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) break
                    byteAccumulator.write(buffer, 0, bytesRead)
                    totalBytes += bytesRead
                    // Once data stream begins, reduce timeout to 1.5s for fast finish detection
                    socket.soTimeout = 1500
                } catch (e: SocketTimeoutException) {
                    // End of transmission or idle timeout reached
                    break
                }
            }

            val rawBytes = byteAccumulator.toByteArray()

            if (rawBytes.isNotEmpty()) {
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                
                // Detect format from initial header snippet
                val headerSnippet = if (rawBytes.size >= 4096) {
                    String(rawBytes.copyOfRange(0, 4096), Charsets.ISO_8859_1)
                } else {
                    String(rawBytes, Charsets.ISO_8859_1)
                }

                val (detectedFormat, ext) = when {
                    headerSnippet.startsWith("%PDF-", ignoreCase = true) -> Pair("PDF Document", "pdf")
                    headerSnippet.startsWith("8BPS") ||
                    (rawBytes.size >= 4 && rawBytes[0] == 0x38.toByte() && rawBytes[1] == 0x42.toByte() && rawBytes[2] == 0x50.toByte() && rawBytes[3] == 0x53.toByte()) -> Pair("Photoshop / PSD Document", "psd")
                    headerSnippet.contains("%!PS", ignoreCase = true) ||
                    headerSnippet.contains("%%Creator:", ignoreCase = true) ||
                    headerSnippet.contains("%%BoundingBox", ignoreCase = true) ||
                    headerSnippet.contains("/setfont", ignoreCase = true) ||
                    headerSnippet.contains("showpage", ignoreCase = true) -> Pair("PostScript Document", "ps")
                    headerSnippet.contains("\u001b%-12345X") || headerSnippet.contains("\u001bE") -> Pair("PCL Print Stream", "prn")
                    rawBytes.size >= 8 && rawBytes[0] == 0x89.toByte() && rawBytes[1] == 0x50.toByte() -> Pair("PNG Image", "png")
                    rawBytes.size >= 2 && rawBytes[0] == 0xFF.toByte() && rawBytes[1] == 0xD8.toByte() -> Pair("JPEG Image", "jpg")
                    else -> Pair("PostScript Document", "ps") // Default to .ps for virtual print driver streams
                }

                val finalFileName = "Print_$ts.$ext"
                Log.i(TAG, "Received $totalBytes bytes from $clientIp. Keeping original format: $detectedFormat ($finalFileName)")

                // Save raw file directly in its authentic format - NO conversion to PDF
                val savedEntity = StorageHelper.saveAndRegisterRawFile(
                    context = context,
                    fileBytes = rawBytes,
                    originalFileName = finalFileName,
                    clientIp = clientIp
                )
                _pendingJobForSave.value = null
                _lastJobReceived.tryEmit(savedEntity)

                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context,
                        "📥 Saved original '$finalFileName' to VirtualPrinter",
                        Toast.LENGTH_LONG
                    ).show()
                }

                val logBuilder = StringBuilder().apply {
                    appendLine("--- VIRTUAL PRINTER RAW JOB RECEIVED ---")
                    appendLine("Job ID: job_$ts")
                    appendLine("Saved filename: $finalFileName")
                    appendLine("Received byte count: $totalBytes bytes")
                    appendLine("Detected format: $detectedFormat (Kept as authentic .$ext file)")
                    appendLine("Storage destination: VirtualPrinter/$finalFileName")
                    appendLine("----------------------------------------")
                }
                Log.i(TAG, logBuilder.toString())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing print job: ${e.message}", e)
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun simulatePrintJob(context: Context, testType: String = "ps"): PrintJobEntity {
        val proManager = ProLicenseManager.getInstance(context)
        if (!proManager.canReceivePrint()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "⚠️ Free Limit (10 files) reached. Activate PRO for unlimited test prints!", Toast.LENGTH_LONG).show()
            }
            throw IllegalStateException("Free Plan Limit reached (10 files). Please activate Virtual PDF Printer PRO.")
        }

        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val isPsd = testType.equals("psd", ignoreCase = true)
        
        val (fileName, data) = if (isPsd) {
            val psdHeader = byteArrayOf(0x38, 0x42, 0x50, 0x53, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
            val samplePayload = "Sample VirtualPrinter PSD Print Layer Data $ts".toByteArray(Charsets.UTF_8)
            val fullBytes = psdHeader + samplePayload
            Pair("Sample_Design_$ts.psd", fullBytes)
        } else {
            val samplePs = """
                %!PS-Adobe-3.0
                %%Title: Sample_PostScript_Document
                %%Creator: Virtual Printer Driver
                %%CreationDate: ${Date()}
                %%Pages: 1
                %%BoundingBox: 0 0 595 842
                %%EndComments

                /Helvetica-Bold findfont 20 scalefont setfont
                50 780 moveto
                (VIRTUAL PRINTER POSTSCRIPT DOCUMENT) show

                /Helvetica findfont 12 scalefont setfont
                50 740 moveto
                (This .ps file was received directly from the PC printer port 9100.) show
                50 720 moveto
                (The file is stored byte-for-byte in its authentic PostScript format.) show
                50 700 moveto
                (No conversion or lossy transformation applied.) show
                50 660 moveto
                (Saved location: /storage/emulated/0/VirtualPrinter/Sample_Document_$ts.ps) show
                50 630 moveto
                (Status: Original PostScript format preserved.) show

                showpage
                %%EOF
            """.trimIndent()
            Pair("Sample_Document_$ts.ps", samplePs.toByteArray(Charsets.UTF_8))
        }

        val savedEntity = StorageHelper.saveAndRegisterRawFile(
            context = context,
            fileBytes = data,
            originalFileName = fileName,
            clientIp = "127.0.0.1 (Self-Test)"
        )
        _pendingJobForSave.value = null
        _lastJobReceived.tryEmit(savedEntity)
        return savedEntity
    }
}
