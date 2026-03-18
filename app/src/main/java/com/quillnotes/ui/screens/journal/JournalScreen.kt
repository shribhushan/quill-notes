package com.quillnotes.ui.screens.journal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quillnotes.ui.components.EmptyState
import com.quillnotes.ui.components.NoteCard
import com.quillnotes.ui.components.moods
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    onEntryClick: (Long) -> Unit,
    onNewEntry: () -> Unit,
    viewModel: JournalViewModel = hiltViewModel()
) {
    val entries by viewModel.entries.collectAsState()
    val entryCount by viewModel.entryCount.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Journal", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "$entryCount entries",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewEntry,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Rounded.Add, "New Journal Entry")
            }
        }
    ) { padding ->
        if (entries.isEmpty()) {
            EmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                title = "No journal entries",
                subtitle = "Start journaling to track your thoughts and moods"
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Group entries by date
                val grouped = entries.groupBy { entry ->
                    SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
                        .format(Date(entry.createdAt))
                }

                grouped.forEach { (date, dateEntries) ->
                    item {
                        Text(
                            text = date,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                        )
                    }

                    items(dateEntries, key = { it.id }) { entry ->
                        val moodEmoji = moods.find { it.key == entry.mood }?.emoji ?: ""
                        NoteCard(
                            title = "$moodEmoji ${entry.title}".trim(),
                            content = entry.content,
                            updatedAt = entry.updatedAt,
                            isPinned = entry.isPinned,
                            onClick = { onEntryClick(entry.id) },
                            onPin = { viewModel.togglePin(entry.id, entry.isPinned) },
                            onDelete = { viewModel.deleteEntry(entry.id) }
                        )
                    }
                }
            }
        }
    }
}
