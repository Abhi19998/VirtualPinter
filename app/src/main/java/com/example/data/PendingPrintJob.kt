package com.example.data

import java.io.File

data class PendingPrintJob(
    val tempFile: File,
    val defaultName: String,
    val originalFormat: String,
    val clientIp: String,
    val pageCount: Int,
    val timestamp: Long = System.currentTimeMillis()
)
