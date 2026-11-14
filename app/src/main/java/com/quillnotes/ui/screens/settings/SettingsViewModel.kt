package com.quillnotes.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quillnotes.data.export.BackupManager
import com.quillnotes.data.export.BadPassphraseException
import com.quillnotes.data.export.ImportSummary
import com.quillnotes.data.export.InvalidBackupException
import com.quillnotes.data.repository.PreferencesRepository
import com.quillnotes.notifications.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The outcome of an export or restore, for the screen to word (and translate). */
sealed interface DataResult {
    data class MarkdownExported(val notes: Int, val images: Int) : DataResult
    data class BackupCreated(val notes: Int, val images: Int) : DataResult
    data class Restored(val summary: ImportSummary) : DataResult
    data object WrongPassphrase : DataResult
    data object NotABackup : DataResult
    data object Failed : DataResult
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: PreferencesRepository,
    private val backup: BackupManager,
    private val alarms: AlarmScheduler,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val theme: Flow<String> = prefs.theme
    val syncProvider: Flow<String> = prefs.syncProvider
    val autoSync: Flow<Boolean> = prefs.autoSync
    val biometricLock: Flow<Boolean> = prefs.biometricLock
    val fontSize: Flow<String> = prefs.fontSize
    val journalReminder: Flow<Boolean> = prefs.journalReminderEnabled
    val journalReminderMinutes: Flow<Int> = prefs.journalReminderMinutes

    private val _working = MutableStateFlow(false)
    val working: StateFlow<Boolean> = _working.asStateFlow()

    private val _result = MutableStateFlow<DataResult?>(null)
    val result: StateFlow<DataResult?> = _result.asStateFlow()

    fun setTheme(theme: String) {
        viewModelScope.launch { prefs.setTheme(theme) }
    }

    fun setSyncProvider(provider: String) {
        viewModelScope.launch { prefs.setSyncProvider(provider) }
    }

    fun setAutoSync(enabled: Boolean) {
        viewModelScope.launch { prefs.setAutoSync(enabled) }
    }

    fun setBiometricLock(enabled: Boolean) {
        viewModelScope.launch { prefs.setBiometricLock(enabled) }
    }

    fun setFontSize(size: String) {
        viewModelScope.launch { prefs.setFontSize(size) }
    }

    fun setJournalReminder(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setJournalReminderEnabled(enabled)
            if (enabled) alarms.scheduleJournalReminder(prefs.journalReminderMinutes.first()) else alarms.cancelJournalReminder()
        }
    }

    fun setJournalReminderTime(minutesOfDay: Int) {
        viewModelScope.launch {
            prefs.setJournalReminderMinutes(minutesOfDay)
            if (prefs.journalReminderEnabled.first()) alarms.scheduleJournalReminder(minutesOfDay)
        }
    }

    fun clearResult() {
        _result.value = null
    }

    // ── Export and restore ─────────────────────────────────────

    fun exportMarkdown(uri: Uri) = perform {
        val out = context.contentResolver.openOutputStream(uri) ?: throw java.io.IOException("cannot open $uri")
        val summary = out.use { backup.exportMarkdown(it) }
        DataResult.MarkdownExported(summary.notes, summary.images)
    }

    fun exportBackup(uri: Uri, passphrase: String) = perform {
        val chars = passphrase.toCharArray()
        try {
            val out = context.contentResolver.openOutputStream(uri) ?: throw java.io.IOException("cannot open $uri")
            val manifest = backup.exportBackup(out, chars)
            DataResult.BackupCreated(manifest.noteCount, manifest.attachmentCount)
        } finally {
            chars.fill('\u0000')
        }
    }

    fun importBackup(uri: Uri, passphrase: String) = perform {
        val chars = passphrase.toCharArray()
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw java.io.IOException("cannot open $uri")
            DataResult.Restored(input.use { backup.importBackup(it, chars) })
        } finally {
            chars.fill('\u0000')
        }
    }

    private fun perform(block: suspend () -> DataResult) {
        if (_working.value) return
        _working.value = true
        _result.value = null
        viewModelScope.launch {
            _result.value = try {
                block()
            } catch (e: BadPassphraseException) {
                DataResult.WrongPassphrase
            } catch (e: InvalidBackupException) {
                DataResult.NotABackup
            } catch (e: Exception) {
                DataResult.Failed
            } finally {
                _working.value = false
            }
        }
    }
}
