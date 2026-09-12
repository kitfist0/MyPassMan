package my.passman.sync.google

import android.app.PendingIntent
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton

sealed class GoogleDriveAuthResult {
    data class Authorized(
        val accessToken: String,
    ) : GoogleDriveAuthResult()

    data class ConsentRequired(
        val pendingIntent: PendingIntent,
    ) : GoogleDriveAuthResult()

    data class Failed(
        val message: String?,
    ) : GoogleDriveAuthResult()
}

/**
 * Requests access to the app's own hidden Drive folder via the Authorization
 * API (not full Google Sign-In). Play Services matches the app's registered
 * OAuth client by package name + signing certificate, so no client ID needs
 * to be embedded here. A first-time (or scope-change) grant requires
 * launching [GoogleDriveAuthResult.ConsentRequired.pendingIntent] via
 * `ActivityResultContracts.StartIntentSenderForResult`; subsequent calls from
 * anywhere, including a background [android.app.Application] context, return
 * a fresh token silently as long as the grant is still valid.
 */
@Singleton
class GoogleDriveAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val authorizationClient = Identity.getAuthorizationClient(context)

    suspend fun authorize(): GoogleDriveAuthResult {
        val request =
            AuthorizationRequest
                .builder()
                .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
                .build()
        return try {
            val result = authorizationClient.authorize(request).await()
            val pendingIntent = result.pendingIntent
            when {
                result.hasResolution() && pendingIntent != null ->
                    GoogleDriveAuthResult.ConsentRequired(pendingIntent)

                result.accessToken != null ->
                    GoogleDriveAuthResult.Authorized(result.accessToken!!)

                else ->
                    GoogleDriveAuthResult.Failed("No access token returned")
            }
        } catch (e: Exception) {
            GoogleDriveAuthResult.Failed(e.message)
        }
    }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { continuation.resumeWith(Result.success(it)) }
            addOnFailureListener { continuation.resumeWith(Result.failure(it)) }
            addOnCanceledListener { continuation.cancel() }
        }

    companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    }
}
