package com.quillnotes.ui.screens.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quillnotes.data.local.entity.NoteEntity
import com.quillnotes.data.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val repository: NoteRepository
) : ViewModel() {

    val deletedNotes: StateFlow<List<NoteEntity>> = repository.getDeletedNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun restoreNote(id: Long) {
        viewModelScope.launch { repository.restoreNote(id) }
    }

    fun deleteForever(id: Long) {
        viewModelScope.launch { repository.permanentlyDeleteNote(id) }
    }

    fun emptyTrash() {
        viewModelScope.launch { repository.purgeAllDeletedNotes() }
    }
}
