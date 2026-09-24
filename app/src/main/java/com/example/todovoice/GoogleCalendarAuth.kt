package com.example.todovoice

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Google sign-in + read-only Calendar consent via the Identity AuthorizationClient.
 * Needs an Android OAuth client in Google Cloud Console for this package + SHA-1 (see README).
 */
object GoogleCalendarAuth {
    private const val CALENDAR_READONLY = "https://www.googleapis.com/auth/calendar.readonly"

    private fun request(): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(CALENDAR_READONLY)))
            .build()

    /** If the result [AuthorizationResult.hasResolution], the caller must launch its pendingIntent. */
    suspend fun authorize(context: Context): AuthorizationResult =
        Identity.getAuthorizationClient(context).authorize(request()).await()

    fun resultFromIntent(context: Context, data: Intent?): AuthorizationResult =
        Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)

    /** Access token without UI, or null when the user has to reconnect. Throws on network/other errors. */
    suspend fun getTokenSilently(context: Context): String? {
        val result = authorize(context)
        return if (result.hasResolution()) null else result.accessToken
    }

    /** Drops a rejected token from Play services' cache so the next authorize() mints a new one. */
    suspend fun clearToken(context: Context, token: String) = withContext(Dispatchers.IO) {
        try {
            GoogleAuthUtil.clearToken(context, token)
        } catch (_: Exception) {
            // best effort
        }
    }

    fun describeError(e: Exception): String =
        if (e is ApiException) {
            when (e.statusCode) {
                CommonStatusCodes.DEVELOPER_ERROR ->
                    "The Google OAuth client for this app isn't set up (DEVELOPER_ERROR). " +
                        "Check the package name and SHA-1 in Google Cloud Console (see README)."
                CommonStatusCodes.NETWORK_ERROR -> "No internet connection."
                CommonStatusCodes.CANCELED -> "Sign-in was cancelled."
                else -> "Google error ${e.statusCode}: ${e.message}"
            }
        } else {
            e.message ?: e.toString()
        }
}
