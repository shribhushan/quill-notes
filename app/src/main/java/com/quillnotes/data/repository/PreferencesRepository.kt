package com.quillnotes.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "quill_settings")

@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val THEME_KEY = stringPreferencesKey("theme")
        val SYNC_PROVIDER_KEY = stringPreferencesKey("sync_provider")
        val AUTO_SYNC_KEY = booleanPreferencesKey("auto_sync")
        val BIOMETRIC_LOCK_KEY = booleanPreferencesKey("biometric_lock")
        val SORT_ORDER_KEY = stringPreferencesKey("sort_order")
        val FONT_SIZE_KEY = stringPreferencesKey("font_size")
        val NOTES_VIEW_MODE_KEY = stringPreferencesKey("notes_view_mode")
        val ONBOARDING_DONE_KEY = booleanPreferencesKey("onboarding_done")
        val JOURNAL_REMINDER_KEY = booleanPreferencesKey("journal_reminder")
        val JOURNAL_REMINDER_MINUTES_KEY = intPreferencesKey("journal_reminder_minutes")
        val NOTEBOOKS_KEY = stringSetPreferencesKey("notebooks")
        val PLANNER_VIEW_KEY = stringPreferencesKey("planner_view")
        val JOURNAL_VIEW_KEY = stringPreferencesKey("journal_view")
        const val DEFAULT_JOURNAL_REMINDER_MINUTES = 21 * 60
    }

    // ── Theme ──────────────────────────────────────────────────
    val theme: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[THEME_KEY] ?: "system"
    }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { it[THEME_KEY] = theme }
    }

    // ── Sync ───────────────────────────────────────────────────
    val syncProvider: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SYNC_PROVIDER_KEY] ?: "none"
    }

    suspend fun setSyncProvider(provider: String) {
        context.dataStore.edit { it[SYNC_PROVIDER_KEY] = provider }
    }

    val autoSync: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_SYNC_KEY] ?: false
    }

    suspend fun setAutoSync(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_SYNC_KEY] = enabled }
    }

    // ── Security ───────────────────────────────────────────────
    val biometricLock: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[BIOMETRIC_LOCK_KEY] ?: false
    }

    suspend fun setBiometricLock(enabled: Boolean) {
        context.dataStore.edit { it[BIOMETRIC_LOCK_KEY] = enabled }
    }

    // ── Display ────────────────────────────────────────────────
    val sortOrder: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SORT_ORDER_KEY] ?: "updated"
    }

    suspend fun setSortOrder(order: String) {
        context.dataStore.edit { it[SORT_ORDER_KEY] = order }
    }

    val fontSize: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[FONT_SIZE_KEY] ?: "medium"
    }

    suspend fun setFontSize(size: String) {
        context.dataStore.edit { it[FONT_SIZE_KEY] = size }
    }

    // ── Notes View Mode (list / grid) ─────────────────────────────
    val notesViewMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[NOTES_VIEW_MODE_KEY] ?: "grid"
    }

    suspend fun setNotesViewMode(mode: String) {
        context.dataStore.edit { it[NOTES_VIEW_MODE_KEY] = mode }
    }

    // ── Onboarding ─────────────────────────────────────────────
    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { it[ONBOARDING_DONE_KEY] ?: false }

    suspend fun setOnboardingDone(done: Boolean) {
        context.dataStore.edit { it[ONBOARDING_DONE_KEY] = done }
    }

    // ── Journal reminder ───────────────────────────────────────
    val journalReminderEnabled: Flow<Boolean> = context.dataStore.data.map { it[JOURNAL_REMINDER_KEY] ?: false }

    /** Minutes after midnight, local time. */
    val journalReminderMinutes: Flow<Int> =
        context.dataStore.data.map { it[JOURNAL_REMINDER_MINUTES_KEY] ?: DEFAULT_JOURNAL_REMINDER_MINUTES }

    suspend fun setJournalReminderEnabled(enabled: Boolean) {
        context.dataStore.edit { it[JOURNAL_REMINDER_KEY] = enabled }
    }

    suspend fun setJournalReminderMinutes(minutes: Int) {
        context.dataStore.edit { it[JOURNAL_REMINDER_MINUTES_KEY] = minutes.coerceIn(0, 24 * 60 - 1) }
    }

    // ── Notebooks created by the user (so empty ones persist) ──
    val notebookNames: Flow<Set<String>> = context.dataStore.data.map { it[NOTEBOOKS_KEY] ?: emptySet() }

    suspend fun addNotebook(name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        context.dataStore.edit { prefs ->
            val current = prefs[NOTEBOOKS_KEY] ?: emptySet()
            if (current.none { it.equals(n, ignoreCase = true) }) prefs[NOTEBOOKS_KEY] = current + n
        }
    }

    suspend fun removeNotebook(name: String) {
        context.dataStore.edit { prefs ->
            prefs[NOTEBOOKS_KEY] = (prefs[NOTEBOOKS_KEY] ?: emptySet()).filterNot { it.equals(name, ignoreCase = true) }.toSet()
        }
    }

    suspend fun renameNotebook(from: String, to: String) {
        val target = to.trim()
        if (target.isEmpty()) return
        context.dataStore.edit { prefs ->
            val current = (prefs[NOTEBOOKS_KEY] ?: emptySet()).filterNot { it.equals(from, ignoreCase = true) }.toMutableSet()
            if (current.none { it.equals(target, ignoreCase = true) }) current += target
            prefs[NOTEBOOKS_KEY] = current
        }
    }

    // ── Remembered view choices ────────────────────────────────
    val plannerView: Flow<String> = context.dataStore.data.map { it[PLANNER_VIEW_KEY] ?: "list" }
    suspend fun setPlannerView(view: String) { context.dataStore.edit { it[PLANNER_VIEW_KEY] = view } }

    val journalView: Flow<String> = context.dataStore.data.map { it[JOURNAL_VIEW_KEY] ?: "timeline" }
    suspend fun setJournalView(view: String) { context.dataStore.edit { it[JOURNAL_VIEW_KEY] = view } }
}
