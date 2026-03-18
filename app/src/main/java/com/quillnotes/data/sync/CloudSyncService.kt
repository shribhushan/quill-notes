package com.quillnotes.data.sync

/**
 * Common interface all cloud providers must implement.
 * Notes are always passed as encrypted bytes — the service only
 * handles transport and storage, never plaintext content.
 */
interface CloudSyncService {

    /** Returns true if the user is currently authenticated. */
    suspend fun isAuthenticated(): Boolean

    /** Initiates the OAuth / sign-in flow. Returns true on success. */
    suspend fun signIn(): Boolean

    /** Revokes credentials and clears local tokens. */
    suspend fun signOut()

    /**
     * Uploads a single encrypted note file.
     * @param fileId  Existing remote file ID (for updates) or null for a new upload.
     * @param name    File name, e.g. "note_42.qnote"
     * @param data    Pre-encrypted bytes to store.
     * @return        Remote file ID that can be stored locally for future updates.
     */
    suspend fun uploadFile(fileId: String?, name: String, data: ByteArray): String?

    /**
     * Downloads a previously uploaded file.
     * @return  The raw encrypted bytes as originally uploaded, or null on error.
     */
    suspend fun downloadFile(fileId: String): ByteArray?

    /**
     * Deletes a file from the cloud.
     */
    suspend fun deleteFile(fileId: String)

    /**
     * Lists all Quill Notes files currently stored in the cloud folder.
     * @return Map of remote file ID → file name.
     */
    suspend fun listFiles(): Map<String, String>
}
