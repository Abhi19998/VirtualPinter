package com.example.data

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

sealed class AuthResult<out T> {
    data class Success<out T>(val data: T) : AuthResult<T>()
    data class Error(val message: String, val exception: Exception? = null) : AuthResult<Nothing>()
}

class FirebaseAuthService(
    private val authProvider: () -> FirebaseAuth? = {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
) {
    private val auth: FirebaseAuth?
        get() = authProvider()

    val currentUser: FirebaseUser?
        get() = auth?.currentUser

    val authState: Flow<FirebaseUser?> = callbackFlow {
        val currentAuth = auth
        if (currentAuth == null) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        currentAuth.addAuthStateListener(listener)
        awaitClose { currentAuth.removeAuthStateListener(listener) }
    }

    suspend fun signInWithEmail(email: String, password: String): AuthResult<FirebaseUser> {
        val currentAuth = auth ?: return AuthResult.Error("Authentication service is unavailable. Please check your internet connection or Google Play Services.")
        return try {
            val result = currentAuth.signInWithEmailAndPassword(email.trim(), password).await()
            val user = result.user ?: return AuthResult.Error("Sign in succeeded but user is null")
            AuthResult.Success(user)
        } catch (e: Exception) {
            val message = when {
                e.message?.contains("no user record", ignoreCase = true) == true -> "No account found with this email."
                e.message?.contains("password is invalid", ignoreCase = true) == true ||
                e.message?.contains("wrong-password", ignoreCase = true) == true -> "Incorrect password. Please try again."
                e.message?.contains("network", ignoreCase = true) == true -> "Network error. Please check your internet connection."
                e.message?.contains("blocked", ignoreCase = true) == true -> "Too many failed attempts. Try again later."
                else -> e.localizedMessage ?: "Failed to sign in. Please verify your credentials."
            }
            AuthResult.Error(message, e)
        }
    }

    suspend fun signUpWithEmail(name: String, email: String, password: String): AuthResult<FirebaseUser> {
        val currentAuth = auth ?: return AuthResult.Error("Authentication service is unavailable. Please check your internet connection or Google Play Services.")
        return try {
            val result = currentAuth.createUserWithEmailAndPassword(email.trim(), password).await()
            val user = result.user ?: return AuthResult.Error("Registration succeeded but user is null")
            if (name.isNotBlank()) {
                val profileUpdate = UserProfileChangeRequest.Builder()
                    .setDisplayName(name.trim())
                    .build()
                user.updateProfile(profileUpdate).await()
            }
            AuthResult.Success(user)
        } catch (e: Exception) {
            val message = when {
                e.message?.contains("already in use", ignoreCase = true) == true -> "An account with this email already exists."
                e.message?.contains("weak-password", ignoreCase = true) == true -> "Password is too weak. Must be at least 6 characters."
                e.message?.contains("network", ignoreCase = true) == true -> "Network error. Please check your internet connection."
                else -> e.localizedMessage ?: "Failed to create account."
            }
            AuthResult.Error(message, e)
        }
    }

    suspend fun signInWithGoogle(context: Context): AuthResult<FirebaseUser> {
        val currentAuth = auth ?: return AuthResult.Error("Authentication service is unavailable. Please check your internet connection.")

        val webClientId = try {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resId != 0) context.getString(resId) else null
        } catch (e: Exception) {
            null
        }

        if (webClientId.isNullOrBlank()) {
            return AuthResult.Error("Google Sign-In is not enabled yet in Firebase Console. Please enable Google Sign-In in Firebase Authentication > Sign-in method, or sign in using Email & Password above.")
        }

        return try {
            val credentialManager = CredentialManager.create(context)
            val googleIdOption = GetSignInWithGoogleOption.Builder(webClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = context
            )

            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = currentAuth.signInWithCredential(authCredential).await()
                val user = authResult.user ?: return AuthResult.Error("Google Sign-In succeeded but user profile was not found.")
                AuthResult.Success(user)
            } else {
                AuthResult.Error("Received unexpected credential type from Google.")
            }
        } catch (e: GetCredentialCancellationException) {
            AuthResult.Error("Google Sign-In was cancelled.")
        } catch (e: NoCredentialException) {
            AuthResult.Error("No Google account found on this device or Google Sign-In is unavailable.")
        } catch (e: Exception) {
            AuthResult.Error(e.localizedMessage ?: "Google Sign-In failed. Please try again or use Email / Password.", e)
        }
    }

    suspend fun sendPasswordResetEmail(email: String): AuthResult<Unit> {
        val currentAuth = auth ?: return AuthResult.Error("Authentication service is unavailable.")
        return try {
            currentAuth.sendPasswordResetEmail(email.trim()).await()
            AuthResult.Success(Unit)
        } catch (e: Exception) {
            AuthResult.Error(e.localizedMessage ?: "Failed to send password reset email.", e)
        }
    }

    suspend fun validateCurrentUser(): Boolean {
        val currentAuth = auth ?: return false
        val user = currentAuth.currentUser ?: return false
        return try {
            user.reload().await()
            val reloadedUser = currentAuth.currentUser
            reloadedUser != null
        } catch (e: Exception) {
            val msg = e.message?.lowercase(java.util.Locale.ROOT) ?: ""
            if (e is com.google.firebase.auth.FirebaseAuthInvalidUserException ||
                msg.contains("no user record") ||
                msg.contains("user-not-found") ||
                msg.contains("user_not_found") ||
                msg.contains("user has been deleted") ||
                msg.contains("user disabled") ||
                msg.contains("token has been revoked") ||
                msg.contains("invalid-user-token") ||
                msg.contains("user_token_expired")
            ) {
                // User was deleted or disabled on Firebase backend
                try {
                    currentAuth.signOut()
                } catch (_: Exception) {}
                false
            } else {
                // Keep session on transient offline network states
                currentAuth.currentUser != null
            }
        }
    }

    fun signOut() {
        auth?.signOut()
    }
}
