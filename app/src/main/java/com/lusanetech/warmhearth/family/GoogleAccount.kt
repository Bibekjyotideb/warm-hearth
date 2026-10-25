package com.lusanetech.warmhearth.family

import android.app.Activity
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.lusanetech.warmhearth.R
import kotlinx.coroutines.tasks.await

/**
 * "Sign in with Google" for family members (the senior's phone never needs an account).
 * The anonymous account the app started with is upgraded in place, so nothing is lost.
 *
 * R.string.default_web_client_id is generated from google-services.json by the Google Services
 * Gradle plugin (enable Google as a sign-in provider in Firebase first, see docs/OPERATIONS.md).
 */
object GoogleAccount {
    sealed interface Result {
        data object Ok : Result
        data object Cancelled : Result
        data class Failed(val message: String?) : Result
    }

    val signedInEmail: String? get() = FirebaseAuth.getInstance().currentUser?.takeIf { !it.isAnonymous }?.email

    suspend fun signIn(activity: Activity): Result {
        val option = GetSignInWithGoogleOption.Builder(activity.getString(R.string.default_web_client_id)).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val response = CredentialManager.create(activity).getCredential(activity, request)
            val cred = response.credential
            if (cred !is CustomCredential || cred.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                return Result.Failed("Unexpected credential")
            }
            val idToken = GoogleIdTokenCredential.createFrom(cred.data).idToken
            val firebaseCred = GoogleAuthProvider.getCredential(idToken, null)
            val auth = FirebaseAuth.getInstance()
            val current = auth.currentUser
            if (current != null && current.isAnonymous) {
                try {
                    current.linkWithCredential(firebaseCred).await()
                } catch (e: FirebaseAuthUserCollisionException) {
                    // This Google account already used WarmHearth before (e.g. reinstall): sign into it.
                    auth.signInWithCredential(firebaseCred).await()
                }
            } else {
                auth.signInWithCredential(firebaseCred).await()
            }
            Result.Ok
        } catch (e: GetCredentialCancellationException) {
            Result.Cancelled
        } catch (e: Exception) {
            Log.w("GoogleAccount", "Sign-in failed", e)
            Result.Failed(e.message)
        }
    }

    suspend fun signOut(activity: Activity) {
        FirebaseAuth.getInstance().signOut()
        runCatching { CredentialManager.create(activity).clearCredentialState(androidx.credentials.ClearCredentialStateRequest()) }
    }
}
