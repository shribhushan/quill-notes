package com.quillnotes

import android.content.Intent

/**
 * What the app was asked to do when it was opened: from a notification, a launcher shortcut,
 * the Quick Settings tile, a widget or another app's share sheet.
 */
data class LaunchRequest(
    val kind: Kind = Kind.NONE,
    val noteId: Long? = null,
    val sharedText: String? = null,
    /** Distinguishes two otherwise identical requests so each is handled once. */
    val token: Long = System.nanoTime()
) {
    enum class Kind { NONE, NEW_NOTE, NEW_TASK, NEW_JOURNAL, OPEN_NOTE, OPEN_TASK, SEARCH, SHARED_TEXT }

    companion object {
        const val ACTION_NEW_NOTE = "com.quillnotes.action.NEW_NOTE"
        const val ACTION_NEW_TASK = "com.quillnotes.action.NEW_TASK"
        const val ACTION_NEW_JOURNAL = "com.quillnotes.action.NEW_JOURNAL"
        const val ACTION_OPEN_NOTE = "com.quillnotes.action.OPEN_NOTE"
        const val ACTION_OPEN_TASK = "com.quillnotes.action.OPEN_TASK"
        const val ACTION_SEARCH = "com.quillnotes.action.SEARCH"
        const val EXTRA_NOTE_ID = "note_id"

        val NONE = LaunchRequest()

        fun from(intent: Intent?): LaunchRequest {
            intent ?: return NONE
            return when (intent.action) {
                Intent.ACTION_SEND -> shared(intent.getStringExtra(Intent.EXTRA_TEXT))
                Intent.ACTION_PROCESS_TEXT -> shared(intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString())
                ACTION_NEW_NOTE -> LaunchRequest(Kind.NEW_NOTE)
                ACTION_NEW_TASK -> LaunchRequest(Kind.NEW_TASK)
                ACTION_NEW_JOURNAL -> LaunchRequest(Kind.NEW_JOURNAL)
                ACTION_SEARCH -> LaunchRequest(Kind.SEARCH)
                ACTION_OPEN_NOTE -> intent.getLongExtra(EXTRA_NOTE_ID, -1L).takeIf { it > 0 }
                    ?.let { LaunchRequest(Kind.OPEN_NOTE, noteId = it) } ?: NONE
                ACTION_OPEN_TASK -> intent.getLongExtra(com.quillnotes.notifications.ReminderReceiver.EXTRA_TASK_ID, -1L).takeIf { it > 0 }
                    ?.let { LaunchRequest(Kind.OPEN_TASK, noteId = it) } ?: NONE
                else -> NONE
            }
        }

        private fun shared(text: String?): LaunchRequest =
            if (text.isNullOrBlank()) NONE else LaunchRequest(Kind.SHARED_TEXT, sharedText = text)
    }
}
