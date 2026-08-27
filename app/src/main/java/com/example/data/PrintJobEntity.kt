package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "print_jobs")
data class PrintJobEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,
    val originalFormat: String,
    val fileSizeBytes: Long,
    val filePath: String,
    val savedLocation: String = "VirtualPrinter",
    val contentUri: String? = null,
    val receivedTimestamp: Long = System.currentTimeMillis(),
    val clientIp: String = "127.0.0.1",
    val pageCount: Int = 1,
    val status: String = "Converted to PDF"
)
