@file:OptIn(ExperimentalMaterial3Api::class)

package com.quillnotes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DismissDirection
import androidx.compose.material3.DismissValue
import androidx.compose.material3.SwipeToDismiss
import androidx.compose.material3.rememberDismissState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** One side of a swipe gesture: what it does and how it looks. */
data class SwipeAction(
    val icon: ImageVector,
    val label: String,
    val container: Color,
    val content: Color
)

/** Background revealed behind a swiped card; shows the action for the active direction. */
@Composable
fun SwipeActionBackground(
    direction: DismissDirection?,
    startToEnd: SwipeAction?,
    endToStart: SwipeAction?,
    modifier: Modifier = Modifier
) {
    val action = when (direction) {
        DismissDirection.StartToEnd -> startToEnd
        DismissDirection.EndToStart -> endToStart
        null -> null
    } ?: return
    val fromStart = direction == DismissDirection.StartToEnd
    Row(
        modifier = modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.medium)
            .background(action.container)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (fromStart) Arrangement.Start else Arrangement.End
    ) {
        Icon(action.icon, contentDescription = null, tint = action.content, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(action.label, style = MaterialTheme.typography.labelLarge, color = action.content)
    }
}

/**
 * Swipeable wrapper used by list rows.
 * Swiping toward the end runs [onSwipeToEnd] and springs back (e.g. pin, complete);
 * swiping toward the start runs [onSwipeToStart] and lets the row leave (e.g. trash).
 */
@Composable
fun QuillSwipeBox(
    modifier: Modifier = Modifier,
    toEndAction: SwipeAction? = null,
    onSwipeToEnd: (() -> Unit)? = null,
    toStartAction: SwipeAction? = null,
    onSwipeToStart: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val currentToEnd by rememberUpdatedState(onSwipeToEnd)
    val currentToStart by rememberUpdatedState(onSwipeToStart)
    val dismissState = rememberDismissState(
        confirmValueChange = { value ->
            when (value) {
                DismissValue.DismissedToStart -> {
                    currentToStart?.invoke()
                    currentToStart != null
                }
                DismissValue.DismissedToEnd -> {
                    currentToEnd?.invoke()
                    false
                }
                else -> false
            }
        }
    )
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == DismissValue.DismissedToEnd) dismissState.reset()
    }
    val directions = buildSet {
        if (onSwipeToEnd != null) add(DismissDirection.StartToEnd)
        if (onSwipeToStart != null) add(DismissDirection.EndToStart)
    }
    SwipeToDismiss(
        state = dismissState,
        modifier = modifier,
        directions = directions,
        background = {
            SwipeActionBackground(
                direction = dismissState.dismissDirection,
                startToEnd = toEndAction,
                endToStart = toStartAction
            )
        },
        dismissContent = { content() }
    )
}
