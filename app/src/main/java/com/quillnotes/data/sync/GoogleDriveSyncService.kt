package com.quillnotes.data.sync

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
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
 * Notes are stored inside a hidden app folder (appDataFolder scope) so they
 * do not pollute the user's visible Drive root. The user authorises via
 * GoogleSignIn using a GoogleAccountCredential; no API key is needed.
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
        private const val FOLDER_NAME = "QuillNotes"
    }

    private var driveService: Drive? = null
    private var folderId: String? = null

    // ── Authentication ─────────────────────────────────────────

    override suspend fun isAuthenticated(): Boolean = withContext(Dispatchers.IO) {
        driveService != null
    }

    override suspend fun signIn(): Boolean = withContext(Dispatchers.IO) {
        try {
            // In a real app this is driven by the Activity result from
            // GoogleSignIn.getClient(context, gso).startActivityForResult().
            // Here we wire up the credential assuming a signed-in account exists.
            val credential = GoogleAccountCredential.usingOAuth2(
                context,
                listOf(DriveScopes.DRIVE_APPDATA)
            )
            // credential.selectedAccount would be set from the sign-in result.

            val transport = com.google.api.client.extensions.android.http.AndroidHttp.newCompatibleTransport()
            val jsonFactory = GsonFactory.getDefaultInstance()

            driveService = Drive.Builder(transport, jsonFactory, credential)
                .setApplicationName(APP_NAME)
                .build()

            ensureAppFolder()
            true
        } catch (e: Exception) {
            driveService = null
            false
        }
    }

    override suspend fun signOut() {
        driveService = null
        folderId = null
    }

    // ── File Operations ────────────────────────────────────────

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
                // Create new file in app folder
                val meta = File().apply {
                    this.name = name
                    parents = listOf(folderId ?: "appDataFolder")
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

    override suspend fun deleteFile(fileId: String) = withContext(Dispatchers.IO) {
        try {
            driveService?.files()?.delete(fileId)?.execute()
        } catch (e: Exception) {
            // Swallow — best effort
        }
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

    // ── Helpers ────────────────────────────────────────────────

    private suspend fun ensureAppFolder() = withContext(Dispatchers.IO) {
        if (folderId != null) return@withContext
        // appDataFolder is a virtual space; no real folder creation needed.
        // For DRIVE scope (user-visible), create a folder like this:
        val service = driveService ?: return@withContext
        val existing = service.files().list()
            .setQ("mimeType='application/vnd.google-apps.folder' and name='$FOLDER_NAME' and trashed=false")
            .setSpaces("drive")
            .setFields("files(id)")
            .execute()
            .files
        folderId = if (existing.isNotEmpty()) {
            existing[0].id
        } else {
            service.files().create(
                File().apply {
                    name = FOLDER_NAME
                    mimeType = "application/vnd.google-apps.folder"
                }
            ).setFields("id").execute().id
        }
    }
}
