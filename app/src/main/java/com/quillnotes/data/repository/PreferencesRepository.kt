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
}
