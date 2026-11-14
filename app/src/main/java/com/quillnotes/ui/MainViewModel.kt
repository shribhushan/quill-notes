package com.quillnotes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quillnotes.data.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val prefs: PreferencesRepository
) : ViewModel() {

    /** Null until the preference has loaded, so the first screen never flashes the wrong thing. */
    val onboardingDone: StateFlow<Boolean?> = prefs.onboardingDone
        .map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun finishOnboarding() {
        viewModelScope.launch { prefs.setOnboardingDone(true) }
    }
}
