package com.quillnotes.data.sync

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Drive implementation of CloudSyncService.
 *
 * Notes are stored inside the hidden app folder (`appDataFolder` scope) so
 * they never appear in the user's visible Drive. Sign-in is interactive and
 * driven from the UI: [getSignInIntent] returns the account-picker / consent
 * Intent to launch via the Activity Result API, and [completeSignIn] takes
 * the returned Intent data to finish authentication. A previously granted
 * session is restored silently by [isAuthenticated].
 *
 * Setup required (see publishing guide):
 *  1. Enable Drive API in Google Cloud Console.
 *  2. Add OAuth 2.0 client ID (Android) with your package name + SHA-1.
 *  3. No server-side token exchange needed for appDataFolder scope.
 */
@Singleton
class GoogleDriveSyncService @Inject constructor(
    @ApplicationContext private val context: Context
) : CloudSyncService {

    companion object {
        private const val APP_NAME = "QuillNotes"
        private const val MIME_TYPE = "application/octet-stream"
    }

    private val driveScope = Scope(DriveScopes.DRIVE_APPDATA)

    private val signInClient: GoogleSignInClient by lazy {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestScopes(driveScope)
            .build()
        GoogleSignIn.getClient(context, options)
    }

    private var driveService: Drive? = null

    // ── Authentication ─────────────────────────────────────────

    /** Intent to launch (via the Activity Result API) to show the account picker / consent screen. */
    fun getSignInIntent(): Intent = signInClient.signInIntent

    /** Completes sign-in using the Intent data returned from [getSignInIntent]'s activity result. */
    suspend fun completeSignIn(data: Intent?): Boolean = withContext(Dispatchers.IO) {
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data)
                .getResult(ApiException::class.java)
            buildDriveService(account)
            true
        } catch (e: Exception) {
            driveService = null
            false
        }
    }

    override suspend fun isAuthenticated(): Boolean = withContext(Dispatchers.IO) {
        if (driveService != null) return@withContext true
        // Restore a previously granted session (e.g. after process death) silently.
        try {
            val account = GoogleSignIn.getLastSignedInAccount(context)
            if (account != null && GoogleSignIn.hasPermissions(account, driveScope)) {
                buildDriveService(account)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            driveService = null
            false
        }
    }

    override suspend fun signIn(): Boolean {
        // Interactive sign-in needs a foreground Activity to show the account
        // picker; that flow is driven from the UI via getSignInIntent() /
        // completeSignIn(). This only attempts a silent restore.
        return isAuthenticated()
    }

    override suspend fun signOut() = withContext(Dispatchers.IO) {
        driveService = null
        try {
            signInClient.signOut()
        } catch (e: Exception) {
            // Best effort — local state is already cleared above.
        }
        Unit
    }

    private fun buildDriveService(account: GoogleSignInAccount) {
        val credential = GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE_APPDATA))
        credential.selectedAccount = account.account
            ?: error("Signed-in Google account has no associated Account handle")
        val transport = NetHttpTransport()
        val jsonFactory = GsonFactory.getDefaultInstance()
        driveService = Drive.Builder(transport, jsonFactory, credential)
            .setApplicationName(APP_NAME)
            .build()
    }

    // ── File Operations ────────────────────────────────────────
    // All operations use the "appDataFolder" alias, matching the
    // DRIVE_APPDATA scope requested above — no visible-Drive folder needed.

    override suspend fun uploadFile(
        fileId: String?,
        name: String,
        data: ByteArray
    ): String? = withContext(Dispatchers.IO) {
        val service = driveService ?: return@withContext null
        val content = ByteArrayContent(MIME_TYPE, data)

        try {
            if (fileId != null) {
                // Update existing file
                service.files()
                    .update(fileId, File(), content)
                    .execute()
                fileId
            } else {
                // Create new file in the hidden app folder
                val meta = File().apply {
                    this.name = name
                    parents = listOf("appDataFolder")
                }
                val created = service.files()
                    .create(meta, content)
                    .setFields("id")
                    .execute()
                created.id
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun downloadFile(fileId: String): ByteArray? = withContext(Dispatchers.IO) {
        val service = driveService ?: return@withContext null
        try {
            service.files().get(fileId).executeMediaAsInputStream().readBytes()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun deleteFile(fileId: String): Unit = withContext(Dispatchers.IO) {
        try {
            driveService?.files()?.delete(fileId)?.execute()
        } catch (e: Exception) {
            // Swallow — best effort
        }
        Unit
    }

    override suspend fun listFiles(): Map<String, String> = withContext(Dispatchers.IO) {
        val service = driveService ?: return@withContext emptyMap()
        try {
            val result = service.files().list()
                .setSpaces("appDataFolder")
                .setFields("files(id, name)")
                .setQ("name contains '.qnote'")
                .execute()
            result.files.associate { it.id to it.name }
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
