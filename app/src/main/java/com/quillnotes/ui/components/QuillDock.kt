@file:OptIn(ExperimentalMaterial3Api::class)

package com.quillnotes.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.quillnotes.ui.theme.QuillTheme

data class DockDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
)

/**
 * Floating ink-coloured navigation pill with a separate create button.
 * The selected destination expands to show its label.
 */
@Composable
fun QuillDock(
    destinations: List<DockDestination>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onCreate: () -> Unit,
    createLabel: String,
    modifier: Modifier = Modifier
) {
    val quill = QuillTheme.colors
    Row(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = quill.dockContainer,
            contentColor = quill.dockContent,
            shadowElevation = 10.dp
        ) {
            Row(
                modifier = Modifier.padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                destinations.forEach { destination ->
                    DockItem(
                        destination = destination,
                        selected = destination.route == currentRoute,
                        onClick = { onNavigate(destination.route) }
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        CreateButton(label = createLabel, onClick = onCreate)
    }
}

@Composable
private fun DockItem(
    destination: DockDestination,
    selected: Boolean,
    onClick: () -> Unit
) {
    val quill = QuillTheme.colors
    val container by animateColorAsState(
        if (selected) quill.dockSelectedContainer else Color.Transparent,
        label = "dockItemContainer"
    )
    val content by animateColorAsState(
        if (selected) quill.dockSelectedContent else quill.dockContent,
        label = "dockItemContent"
    )
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        contentColor = content,
        interactionSource = interaction,
        modifier = Modifier
            .height(52.dp)
            .pressScale(interaction, 0.92f)
            .semantics {
                this.selected = selected
                role = Role.Tab
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (selected) 18.dp else 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (selected) destination.selectedIcon else destination.icon,
                contentDescription = if (selected) null else destination.label,
                modifier = Modifier.size(22.dp)
            )
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Text(
                    text = destination.label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun CreateButton(label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = scheme.primary,
        contentColor = scheme.onPrimary,
        shadowElevation = 10.dp,
        interactionSource = interaction,
        modifier = Modifier.size(64.dp).pressScale(interaction, 0.9f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, contentDescription = label, modifier = Modifier.size(28.dp))
        }
    }
}
