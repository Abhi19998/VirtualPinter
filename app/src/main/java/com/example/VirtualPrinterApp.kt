package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.google.firebase.FirebaseApp

class VirtualPrinterApp : Application() {

    companion object {
        const val CHANNEL_ID = "virtual_printer_channel"
        const val CHANNEL_NAME = "Virtual Printer Server"
    }

    override fun onCreate() {
        super.onCreate()
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val options = com.google.firebase.FirebaseOptions.Builder()
                    .setApplicationId("1:319789767715:android:c4ff17c26c882e5b5ea596")
                    .setApiKey("AIzaSyDiE9LERf_bAI4o_Tvv8Ib3DarI8FKO_qc")
                    .setProjectId("virtual-pdf-printer")
                    .setStorageBucket("virtual-pdf-printer.firebasestorage.app")
                    .build()
                FirebaseApp.initializeApp(this, options)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        createNotificationChannel()
        try {
            com.example.utils.StorageHelper.initVirtualPrinterDirectory(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Virtual Printer background server notifications"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
