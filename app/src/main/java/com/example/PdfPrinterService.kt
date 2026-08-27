package com.example

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintAttributes.Resolution
import android.print.PrinterCapabilitiesInfo
import android.print.PrinterId
import android.print.PrinterInfo
import android.printservice.PrintJob
import android.printservice.PrintService
import android.printservice.PrinterDiscoverySession
import android.provider.MediaStore
import android.widget.Toast
import com.example.utils.StorageHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

class PdfPrinterService : PrintService() {

    override fun onCreatePrinterDiscoverySession(): PrinterDiscoverySession {
        return object : PrinterDiscoverySession() {
            override fun onStartPrinterDiscovery(priorityList: MutableList<PrinterId>) {
                try {
                    val printerId = generatePrinterId("virtual_pdf_printer")
                    
                    val capabilities = PrinterCapabilitiesInfo.Builder(printerId)
                        .addMediaSize(PrintAttributes.MediaSize.ISO_A4, true)
                        .addMediaSize(PrintAttributes.MediaSize.NA_LETTER, false)
                        .addResolution(Resolution("res1", "300 DPI", 300, 300), true)
                        .setColorModes(
                            PrintAttributes.COLOR_MODE_COLOR or PrintAttributes.COLOR_MODE_MONOCHROME,
                            PrintAttributes.COLOR_MODE_COLOR
                        )
                        .build()

                    val printerInfo = PrinterInfo.Builder(printerId, "Virtual PDF Printer", PrinterInfo.STATUS_IDLE)
                        .setDescription("Saves printed documents as PDF to Downloads")
                        .setCapabilities(capabilities)
                        .build()
                        
                    addPrinters(listOf(printerInfo))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onStopPrinterDiscovery() {}
            override fun onValidatePrinters(printerIds: MutableList<PrinterId>) {}
            override fun onStartPrinterStateTracking(printerId: PrinterId) {}
            override fun onStopPrinterStateTracking(printerId: PrinterId) {}
            override fun onDestroy() {}
        }
    }

    override fun onRequestCancelPrintJob(printJob: PrintJob) {
        printJob.cancel()
    }

    override fun onPrintJobQueued(printJob: PrintJob) {
        printJob.start()
        val document = printJob.document
        if (document != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val pfd = document.data
                    if (pfd != null) {
                        FileInputStream(pfd.fileDescriptor).use { input ->
                            savePdfToDownloads(input, printJob.info.label ?: "document")
                        }
                        printJob.complete()
                    } else {
                        printJob.fail("No document data")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    printJob.fail("Failed to save: ${e.message}")
                }
            }
        } else {
            printJob.fail("No document")
        }
    }

    private suspend fun savePdfToDownloads(inputStream: InputStream, jobName: String) {
        val safeJobName = jobName.replace(Regex("[^a-zA-Z0-9.-]"), "_").ifBlank { "PrintDocument" }
        val fileName = "${safeJobName}_${System.currentTimeMillis()}.pdf"
        val tempFile = File(cacheDir, fileName)
        FileOutputStream(tempFile).use { output ->
            inputStream.copyTo(output)
        }

        val savedEntity = StorageHelper.saveAndRegisterPdf(
            context = this@PdfPrinterService,
            pdfFile = tempFile,
            baseName = safeJobName,
            originalFormat = "Android Print Spooler",
            clientIp = "Local Print Spooler",
            pageCount = 1,
            customFileName = fileName
        )

        com.example.server.NetworkPrinterServer.notifyJobReceived(savedEntity)

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this@PdfPrinterService, "📥 Saved '$fileName' to VirtualPrinter", Toast.LENGTH_LONG).show()
        }
    }
}
