package com.quillnotes.data.sync

sealed class SyncResult {
    data class Success(val count: Int) : SyncResult()
    data class Failure(val reason: String) : SyncResult()
}
