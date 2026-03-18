package com.quillnotes.data.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Microsoft OneDrive implementation of CloudSyncService.
 *
 * Uses Microsoft Graph API (https://graph.microsoft.com/v1.0/me/drive).
 * Authentication is handled via MSAL (Microsoft Authentication Library).
 *
 * Setup required (see publishing guide):
 *  1. Register an app in Azure Portal → App registrations.
 *  2. Set the redirect URI to: msauth://<packageName>/<base64-SHA1>
 *  3. Add MS Graph permission: Files.ReadWrite
 *  4. Paste the CLIENT_ID below.
 *
 * Notes are stored under /drive/special/approot/QuillNotes/
 */
@Singleton
class OneDriveSyncService @Inject constructor(
    @ApplicationContext private val context: Context
) : CloudSyncService {

    companion object {
        // Replace with your Azure app registration client ID
        private const val CLIENT_ID = "YOUR_AZURE_CLIENT_ID"
        private const val GRAPH_BASE = "https://graph.microsoft.com/v1.0/me/drive/special/approot"
        private const val FOLDER = "QuillNotes"
        private val JSON_MEDIA = "application/json".toMediaType()
        private val OCTET_MEDIA = "application/octet-stream".toMediaType()
    }

    private val http = OkHttpClient()
    private var accessToken: String? = null

    // ── Authentication ─────────────────────────────────────────

    override suspend fun isAuthenticated(): Boolean = accessToken != null

    override suspend fun signIn(): Boolean = withContext(Dispatchers.IO) {
        // In a real app, launch the MSAL interactive auth flow here and
        // store the resulting IAuthenticationResult.accessToken.
        // Pseudocode:
        //
        // val msalApp = PublicClientApplication.create(context, R.raw.msal_config)
        // val result = msalApp.acquireToken(activity, arrayOf("Files.ReadWrite"))
        // accessToken = result.accessToken
        //
        // For the purposes of this template, we return false until wired up.
        false
    }

    override suspend fun signOut() {
        accessToken = null
    }

    // ── File Operations ────────────────────────────────────────

    override suspend fun uploadFile(
        fileId: String?,
        name: String,
        data: ByteArray
    ): String? = withContext(Dispatchers.IO) {
        val token = accessToken ?: return@withContext null
        // PUT to /approot:/<FOLDER>/<name>:/content  (simple upload, ≤4 MB)
        val url = "$GRAPH_BASE:/$FOLDER/$name:/content"
        val request = Request.Builder()
            .url(url)
            .put(data.toRequestBody(OCTET_MEDIA))
            .header("Authorization", "Bearer $token")
            .build()
        try {
            val response = http.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                json.optString("id").takeIf { it.isNotBlank() }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun downloadFile(fileId: String): ByteArray? = withContext(Dispatchers.IO) {
        val token = accessToken ?: return@withContext null
        val url = "https://graph.microsoft.com/v1.0/me/drive/items/$fileId/content"
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer $token")
            .build()
        try {
            val response = http.newCall(request).execute()
            if (response.isSuccessful) response.body?.bytes() else null
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun deleteFile(fileId: String) = withContext(Dispatchers.IO) {
        val token = accessToken ?: return@withContext
        val request = Request.Builder()
            .url("https://graph.microsoft.com/v1.0/me/drive/items/$fileId")
            .delete()
            .header("Authorization", "Bearer $token")
            .build()
        try { http.newCall(request).execute() } catch (_: Exception) {}
    }

    override suspend fun listFiles(): Map<String, String> = withContext(Dispatchers.IO) {
        val token = accessToken ?: return@withContext emptyMap()
        val url = "$GRAPH_BASE:/$FOLDER:/children?\$select=id,name&\$filter=endswith(name,'.qnote')"
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer $token")
            .build()
        try {
            val response = http.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyMap()
            val items = JSONObject(body).getJSONArray("value")
            buildMap {
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    put(item.getString("id"), item.getString("name"))
                }
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
