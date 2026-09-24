package ps.hakim.phoneagent

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope

/**
 * User-account Google Workspace authorization.
 *
 * - Requests only drive.file.
 * - Access tokens live in memory only and are never persisted by Hakim.
 * - User consent is launched only when Google says a resolution is required.
 * - No embedded WebView and no owner-account credential.
 */
object HakimGoogleWorkspaceAuthorization {
    const val VERSION = "GOOGLE-WORKSPACE-AUTH-2026-09-24-v1"
    const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    const val REQUEST_CODE = 2404

    private const val PREFS = "hakim_google_workspace"
    private const val KEY_EVER_AUTHORIZED = "ever_authorized"
    private const val KEY_LAST_AUTHORIZED_AT = "last_authorized_at"

    @Volatile private var accessToken: String? = null
    @Volatile private var pendingCallback: ((Result<String>) -> Unit)? = null

    fun authorize(activity: Activity, callback: (Result<String>) -> Unit) {
        pendingCallback = callback
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
            .build()

        Identity.getAuthorizationClient(activity)
            .authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pending = result.pendingIntent
                    if (pending == null) {
                        finish(Result.failure(IllegalStateException("تعذر بدء موافقة Google.")))
                        return@addOnSuccessListener
                    }
                    runCatching {
                        activity.startIntentSenderForResult(
                            pending.intentSender,
                            REQUEST_CODE,
                            null,
                            0,
                            0,
                            0
                        )
                    }.onFailure { finish(Result.failure(it)) }
                } else {
                    acceptToken(activity, result.accessToken)
                }
            }
            .addOnFailureListener { finish(Result.failure(it)) }
    }

    fun handleActivityResult(
        activity: Activity,
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ): Boolean {
        if (requestCode != REQUEST_CODE) return false
        if (resultCode != Activity.RESULT_OK || data == null) {
            finish(Result.failure(IllegalStateException("لم تكتمل موافقة Google.")))
            return true
        }
        runCatching {
            Identity.getAuthorizationClient(activity)
                .getAuthorizationResultFromIntent(data)
        }.onSuccess { result ->
            acceptToken(activity, result.accessToken)
        }.onFailure { finish(Result.failure(it)) }
        return true
    }

    fun currentAccessToken(): String? = accessToken?.takeIf { it.isNotBlank() }

    fun wasAuthorizedBefore(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_EVER_AUTHORIZED, false)

    fun clearCurrentToken(activity: Activity, callback: (Boolean) -> Unit = {}) {
        val token = accessToken
        accessToken = null
        pendingCallback = null
        if (token.isNullOrBlank()) {
            callback(true)
            return
        }
        Identity.getAuthorizationClient(activity)
            .clearToken(ClearTokenRequest.builder().setToken(token).build())
            .addOnSuccessListener { callback(true) }
            .addOnFailureListener { callback(false) }
    }

    private fun acceptToken(activity: Activity, token: String?) {
        if (token.isNullOrBlank()) {
            finish(Result.failure(IllegalStateException("لم تُرجع Google رمز وصول صالحًا.")))
            return
        }
        accessToken = token
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_EVER_AUTHORIZED, true)
            .putLong(KEY_LAST_AUTHORIZED_AT, System.currentTimeMillis())
            .apply()
        finish(Result.success(token))
    }

    private fun finish(result: Result<String>) {
        val callback = pendingCallback
        pendingCallback = null
        callback?.invoke(result)
    }
}
