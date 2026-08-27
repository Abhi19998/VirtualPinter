package com.example.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FirestoreUserService(
    private val firestoreProvider: () -> FirebaseFirestore? = {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
) {
    private val firestore: FirebaseFirestore?
        get() = firestoreProvider()

    private fun getCurrentIsoDate(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    /**
     * Create or update user profile document on registration
     */
    suspend fun createUserProfile(uid: String, name: String, email: String) {
        val db = firestore ?: return
        try {
            val userMap = hashMapOf<String, Any>(
                "uid" to uid,
                "name" to name.trim(),
                "email" to email.trim(),
                "createdAt" to getCurrentIsoDate(),
                "lastLoginAt" to getCurrentIsoDate(),
                "isPro" to false,
                "plan" to "free",
                "receivedPrintsCount" to 0,
                "conversionsCount" to 0,
                "totalFilesProcessed" to 0,
                "totalFilesSaved" to 0
            )
            db.collection("users").document(uid)
                .set(userMap, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Record login and ensure name/email exist in Firestore
     */
    suspend fun recordUserLogin(uid: String, name: String?, email: String?) {
        val db = firestore ?: return
        try {
            val userMap = hashMapOf<String, Any>(
                "uid" to uid,
                "lastLoginAt" to getCurrentIsoDate()
            )
            if (!name.isNullOrBlank()) {
                userMap["name"] = name.trim()
            }
            if (!email.isNullOrBlank()) {
                userMap["email"] = email.trim()
            }
            db.collection("users").document(uid)
                .set(userMap, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Increment file processing count when a user prints or creates a PDF file
     */
    suspend fun incrementFileUsage(uid: String, fileName: String? = null, pageCount: Int = 1) {
        val db = firestore ?: return
        try {
            val updateMap = hashMapOf<String, Any>(
                "totalFilesProcessed" to FieldValue.increment(1),
                "lastFileProcessedAt" to getCurrentIsoDate()
            )
            if (!fileName.isNullOrBlank()) {
                updateMap["lastFileName"] = fileName
            }
            db.collection("users").document(uid)
                .set(updateMap, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Sync the total count of files saved locally with the user document
     */
    suspend fun syncTotalFilesCount(uid: String, count: Int) {
        val db = firestore ?: return
        try {
            val updateMap = hashMapOf<String, Any>(
                "totalFilesSaved" to count,
                "lastSyncAt" to getCurrentIsoDate()
            )
            db.collection("users").document(uid)
                .set(updateMap, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
