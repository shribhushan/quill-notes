package com.quillnotes.ui.screens.planner

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quillnotes.ui.components.EmptyState
import com.quillnotes.ui.components.NoteCard
import com.quillnotes.ui.screens.home.SwipeBackground
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerScreen(
    onTaskClick: (Long) -> Unit,
    onNewTask: () -> Unit,
    viewModel: PlannerViewModel = hiltViewModel()
) {
    val tasks by viewModel.tasks.collectAsState()
    val taskCount by viewModel.taskCount.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.deleteEvents.collect { deletedId ->
            val result = snackbarHostState.showSnackbar(
                message = "Task deleted",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete(deletedId)
            }
        }
    }

    val pendingTasks = tasks.filter { !it.isCompleted }
    val completedTasks = tasks.filter { it.isCompleted }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Planner", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "${pendingTasks.size} pending · ${completedTasks.size} done",
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
                onClick = onNewTask,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Rounded.Add, "New Task")
            }
        }
    ) { padding ->
        if (tasks.isEmpty()) {
            EmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                title = "No tasks yet",
                subtitle = "Plan your day by adding tasks"
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (pendingTasks.isNotEmpty()) {
                    item {
                        Text(
                            "To Do",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                        )
                    }

                    items(pendingTasks, key = { it.id }) { task ->
                        val dueLabel = task.dueDate?.let { date ->
                            " · Due ${SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(date))}"
                        } ?: ""

                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                when (value) {
                                    SwipeToDismissBoxValue.EndToStart -> {
                                        viewModel.deleteTask(task.id)
                                        true
                                    }
                                    SwipeToDismissBoxValue.StartToEnd -> {
                                        scope.launch {
                                            viewModel.toggleComplete(task.id, false)
                                            dismissState.reset()
                                        }
                                        false
                                    }
                                    else -> false
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                SwipeBackground(
                                    direction = dismissState.dismissDirection,
                                    startIcon = Icons.Outlined.Done,
                                    startLabel = "Complete",
                                    startColor = MaterialTheme.colorScheme.primaryContainer,
                                    endIcon = Icons.Outlined.Delete,
                                    endLabel = "Delete",
                                    endColor = MaterialTheme.colorScheme.errorContainer
                                )
                            }
                        ) {
                            NoteCard(
                                title = task.title,
                                content = task.content + dueLabel,
                                updatedAt = task.updatedAt,
                                isPinned = task.isPinned,
                                isCompleted = false,
                                showCheckbox = true,
                                onClick = { onTaskClick(task.id) },
                                onPin = { viewModel.togglePin(task.id, task.isPinned) },
                                onDelete = { viewModel.deleteTask(task.id) },
                                onToggleComplete = { viewModel.toggleComplete(task.id, false) }
                            )
                        }
                    }
                }

                if (completedTasks.isNotEmpty()) {
                    item {
                        Text(
                            "Completed",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                        )
                    }

                    items(completedTasks, key = { it.id }) { task ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                when (value) {
                                    SwipeToDismissBoxValue.EndToStart -> {
                                        viewModel.deleteTask(task.id)
                                        true
                                    }
                                    SwipeToDismissBoxValue.StartToEnd -> {
                                        scope.launch {
                                            viewModel.toggleComplete(task.id, true)
                                            dismissState.reset()
                                        }
                                        false
                                    }
                                    else -> false
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                SwipeBackground(
                                    direction = dismissState.dismissDirection,
                                    startIcon = Icons.Outlined.PushPin,
                                    startLabel = "Undo",
                                    startColor = MaterialTheme.colorScheme.secondaryContainer,
                                    endIcon = Icons.Outlined.Delete,
                                    endLabel = "Delete",
                                    endColor = MaterialTheme.colorScheme.errorContainer
                                )
                            }
                        ) {
                            NoteCard(
                                title = task.title,
                                content = task.content,
                                updatedAt = task.updatedAt,
                                isPinned = task.isPinned,
                                isCompleted = true,
                                showCheckbox = true,
                                onClick = { onTaskClick(task.id) },
                                onPin = { viewModel.togglePin(task.id, task.isPinned) },
                                onDelete = { viewModel.deleteTask(task.id) },
                                onToggleComplete = { viewModel.toggleComplete(task.id, true) }
                            )
                        }
                    }
                }
            }
        }
    }
}
