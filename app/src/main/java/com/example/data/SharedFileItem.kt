package com.example.data

import java.io.File

data class SharedFileItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val fileName: String,
    val fileSizeBytes: Long,
    val filePath: String,
    val mimeType: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    val file: File get() = File(filePath)
}
