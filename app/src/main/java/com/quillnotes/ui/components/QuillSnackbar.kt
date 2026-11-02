package com.quillnotes.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.runtime.Composable

@Composable
fun QuillSnackbar(data: SnackbarData) {
    val scheme = MaterialTheme.colorScheme
    Snackbar(
        snackbarData = data,
        shape = MaterialTheme.shapes.medium,
        containerColor = scheme.inverseSurface,
        contentColor = scheme.inverseOnSurface,
        actionColor = scheme.inversePrimary
    )
}

@Composable
fun trashSwipeAction(): SwipeAction {
    val scheme = MaterialTheme.colorScheme
    return SwipeAction(
        icon = Icons.Rounded.DeleteOutline,
        label = "Trash",
        container = scheme.errorContainer,
        content = scheme.onErrorContainer
    )
}
