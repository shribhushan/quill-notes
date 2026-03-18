package com.quillnotes.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quillnotes.data.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: PreferencesRepository
) : ViewModel() {

    val theme: Flow<String> = prefs.theme
    val syncProvider: Flow<String> = prefs.syncProvider
    val autoSync: Flow<Boolean> = prefs.autoSync
    val biometricLock: Flow<Boolean> = prefs.biometricLock
    val fontSize: Flow<String> = prefs.fontSize

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
}
