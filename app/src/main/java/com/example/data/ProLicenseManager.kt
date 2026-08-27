package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProLicenseManager(private val context: Context) {

    companion object {
        const val MAX_FREE_PRINTS = 10
        const val MAX_FREE_CONVERSIONS = 10

        private const val PREFS_NAME = "virtual_pdf_printer_pro_prefs"
        private const val KEY_IS_PRO = "key_is_pro_user"
        private const val KEY_PRO_KEY = "key_pro_activation_key"
        private const val KEY_RECEIVED_PRINTS = "key_received_prints_count"
        private const val KEY_CONVERTED_FILES = "key_converted_files_count"

        @Volatile
        private var instance: ProLicenseManager? = null

        fun getInstance(context: Context): ProLicenseManager {
            return instance ?: synchronized(this) {
                instance ?: ProLicenseManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isPro = MutableStateFlow(prefs.getBoolean(KEY_IS_PRO, false))
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val _receivedPrintsCount =
        MutableStateFlow(prefs.getInt(KEY_RECEIVED_PRINTS, 0))
    val receivedPrintsCount: StateFlow<Int> = _receivedPrintsCount.asStateFlow()

    private val _conversionsCount =
        MutableStateFlow(prefs.getInt(KEY_CONVERTED_FILES, 0))
    val conversionsCount: StateFlow<Int> = _conversionsCount.asStateFlow()

    private val _proKey = MutableStateFlow(prefs.getString(KEY_PRO_KEY, null))
    val proKey: StateFlow<String?> = _proKey.asStateFlow()

    private fun getCurrentIsoDate(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    fun isUserPro(): Boolean = _isPro.value

    fun canReceivePrint(): Boolean {
        if (_isPro.value) return true
        return _receivedPrintsCount.value < MAX_FREE_PRINTS
    }

    fun canConvertFile(): Boolean {
        if (_isPro.value) return true
        return _conversionsCount.value < MAX_FREE_CONVERSIONS
    }

    fun getRemainingPrints(): Int {
        if (_isPro.value) return Int.MAX_VALUE
        return (MAX_FREE_PRINTS - _receivedPrintsCount.value).coerceAtLeast(0)
    }

    fun getRemainingConversions(): Int {
        if (_isPro.value) return Int.MAX_VALUE
        return (MAX_FREE_CONVERSIONS - _conversionsCount.value).coerceAtLeast(0)
    }

    /**
     * Increment received prints counter
     */
    fun recordPrintReceived(fileName: String? = null) {
        val newCount = _receivedPrintsCount.value + 1
        _receivedPrintsCount.value = newCount
        prefs.edit().putInt(KEY_RECEIVED_PRINTS, newCount).apply()

        // Sync with Firestore
        try {
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null) {
                val db = FirebaseFirestore.getInstance()
                val update = hashMapOf<String, Any>(
                    "receivedPrintsCount" to newCount,
                    "lastPrintReceivedAt" to getCurrentIsoDate()
                )
                if (!fileName.isNullOrBlank()) {
                    update["lastPrintFileName"] = fileName
                }
                db.collection("users").document(user.uid)
                    .set(update, SetOptions.merge())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Increment file conversions counter
     */
    fun recordConversion(fileName: String? = null) {
        val newCount = _conversionsCount.value + 1
        _conversionsCount.value = newCount
        prefs.edit().putInt(KEY_CONVERTED_FILES, newCount).apply()

        // Sync with Firestore
        try {
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null) {
                val db = FirebaseFirestore.getInstance()
                val update = hashMapOf<String, Any>(
                    "conversionsCount" to newCount,
                    "lastConvertedAt" to getCurrentIsoDate()
                )
                if (!fileName.isNullOrBlank()) {
                    update["lastConvertedFileName"] = fileName
                }
                db.collection("users").document(user.uid)
                    .set(update, SetOptions.merge())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Validates and activates PRO using a provided key.
     * Supports:
     * 1. Dynamic keys stored in Firestore `activation_keys` or `pro_keys` collections.
     * 2. Algorithmic admin keys (e.g., PRO-2026-VIP, PRO-PDF-PRINT, VIP-PRO-UNLIMITED, or any key starting with PRO- / VIP-).
     */
    suspend fun activateProWithKey(inputKey: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = inputKey.trim().uppercase()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(Exception("Please enter a valid Pro activation key."))
        }

        var isValidKey = false
        var keyDetails = "Pro License Key"

        // 1. Try checking in Firestore collections if online
        try {
            val db = FirebaseFirestore.getInstance()
            val docSnap = db.collection("activation_keys").document(cleanKey).get().await()
            if (docSnap.exists()) {
                val isActive = docSnap.getBoolean("active") ?: true
                if (isActive) {
                    isValidKey = true
                    keyDetails = docSnap.getString("description") ?: "Admin Activation Key"
                }
            } else {
                val proSnap = db.collection("pro_keys").document(cleanKey).get().await()
                if (proSnap.exists()) {
                    val isActive = proSnap.getBoolean("active") ?: true
                    if (isActive) {
                        isValidKey = true
                        keyDetails = proSnap.getString("description") ?: "VIP Pro Key"
                    }
                }
            }
        } catch (e: Exception) {
            // If offline or collection doesn't exist yet, fallback to offline format verification
            e.printStackTrace()
        }

        // 2. Offline master patterns & admin keys provided by you
        if (!isValidKey) {
            val validPrefixes = listOf("PRO-", "VIP-", "PDFPRO-", "MASTER-", "ADMIN-")
            val isKnownPattern = validPrefixes.any { cleanKey.startsWith(it) } && cleanKey.length >= 8
            val isStandardPromo = cleanKey in listOf(
                "PRO-2026-VIP",
                "PRO-PDF-PRINT",
                "VIP-PDF-PRO",
                "PDF-PRO-UNLIMITED",
                "PRO-ADMIN-KEY",
                "PRO-DEVELOPER",
                "VIRTUAL-PRO-2026"
            )
            if (isKnownPattern || isStandardPromo) {
                isValidKey = true
                keyDetails = "Master Pro License"
            }
        }

        if (isValidKey) {
            setProStatus(true, cleanKey)

            // Save to Firestore user profile
            try {
                val user = FirebaseAuth.getInstance().currentUser
                if (user != null) {
                    val db = FirebaseFirestore.getInstance()
                    val userMap = hashMapOf<String, Any>(
                        "isPro" to true,
                        "plan" to "pro",
                        "proActivationKey" to cleanKey,
                        "proActivatedAt" to getCurrentIsoDate()
                    )
                    db.collection("users").document(user.uid)
                        .set(userMap, SetOptions.merge())
                        .await()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            Result.success("PRO activated successfully! Welcome to Virtual PDF Printer PRO.")
        } else {
            Result.failure(Exception("Invalid activation key. Please enter a valid key provided by the admin."))
        }
    }

    private var userSnapshotListener: ListenerRegistration? = null

    /**
     * Direct setter for Pro status
     */
    fun setProStatus(isProUser: Boolean, key: String? = null) {
        _isPro.value = isProUser
        _proKey.value = if (isProUser) key else null
        prefs.edit()
            .putBoolean(KEY_IS_PRO, isProUser)
            .putString(KEY_PRO_KEY, if (isProUser) key else null)
            .apply()
    }

    /**
     * Start listening to user document changes in real-time from Firestore.
     * When admin assigns or revokes Pro plan in Firebase Console, UI updates immediately.
     */
    fun startRealtimeUserSync(uid: String) {
        stopRealtimeUserSync()
        try {
            val db = FirebaseFirestore.getInstance()
            userSnapshotListener = db.collection("users").document(uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        error.printStackTrace()
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        val isProInDb = (snapshot.getBoolean("isPro") == true) ||
                                (snapshot.getString("plan")?.trim()?.lowercase(Locale.ROOT) == "pro") ||
                                (snapshot.getString("subscription")?.trim()?.lowercase(Locale.ROOT) == "pro")
                        val savedKey = snapshot.getString("proActivationKey")
                        setProStatus(isProInDb, if (isProInDb) (savedKey ?: _proKey.value) else null)

                        val dbPrints = snapshot.getLong("receivedPrintsCount")?.toInt()
                        if (dbPrints != null) {
                            _receivedPrintsCount.value = dbPrints
                            prefs.edit().putInt(KEY_RECEIVED_PRINTS, dbPrints).apply()
                        }

                        val dbConversions = snapshot.getLong("conversionsCount")?.toInt()
                        if (dbConversions != null) {
                            _conversionsCount.value = dbConversions
                            prefs.edit().putInt(KEY_CONVERTED_FILES, dbConversions).apply()
                        }
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Stop real-time user sync
     */
    fun stopRealtimeUserSync() {
        userSnapshotListener?.remove()
        userSnapshotListener = null
    }

    /**
     * Sync user's Pro status and counts from their Firestore user document
     */
    suspend fun syncUserFromFirestore(uid: String) = withContext(Dispatchers.IO) {
        try {
            val db = FirebaseFirestore.getInstance()
            val doc = db.collection("users").document(uid).get().await()
            if (doc.exists()) {
                val isProInDb = (doc.getBoolean("isPro") == true) ||
                        (doc.getString("plan")?.trim()?.lowercase(Locale.ROOT) == "pro") ||
                        (doc.getString("subscription")?.trim()?.lowercase(Locale.ROOT) == "pro")
                val savedKey = doc.getString("proActivationKey")

                // If isProInDb is false, explicitly set to free plan!
                setProStatus(isProInDb, if (isProInDb) (savedKey ?: _proKey.value) else null)

                val dbPrints = doc.getLong("receivedPrintsCount")?.toInt()
                if (dbPrints != null) {
                    _receivedPrintsCount.value = dbPrints
                    prefs.edit().putInt(KEY_RECEIVED_PRINTS, dbPrints).apply()
                }

                val dbConversions = doc.getLong("conversionsCount")?.toInt()
                if (dbConversions != null) {
                    _conversionsCount.value = dbConversions
                    prefs.edit().putInt(KEY_CONVERTED_FILES, dbConversions).apply()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun resetCountsForTesting() {
        _receivedPrintsCount.value = 0
        _conversionsCount.value = 0
        prefs.edit()
            .putInt(KEY_RECEIVED_PRINTS, 0)
            .putInt(KEY_CONVERTED_FILES, 0)
            .apply()
    }
}
