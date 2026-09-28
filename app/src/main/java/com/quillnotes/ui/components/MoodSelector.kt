package com.quillnotes.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quillnotes.ui.theme.*

data class MoodOption(
    val label: String,
    val emoji: String,
    val key: String,
    val color: androidx.compose.ui.graphics.Color
)

val moods = listOf(
    MoodOption("Happy", "\uD83D\uDE0A", "happy", MoodHappy),
    MoodOption("Calm", "\uD83E\uDDD8", "calm", MoodCalm),
    MoodOption("Neutral", "\uD83D\uDE10", "neutral", MoodNeutral),
    MoodOption("Anxious", "\uD83D\uDE1F", "anxious", MoodAnxious),
    MoodOption("Sad", "\uD83D\uDE14", "sad", MoodSad)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodSelector(
    selectedMood: String?,
    onMoodSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            "How are you feeling?",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            moods.forEach { mood ->
                val isSelected = selectedMood == mood.key
                val containerColor by animateColorAsState(
                    targetValue = if (isSelected) mood.color.copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    label = "mood_color"
                )

                FilterChip(
                    selected = isSelected,
                    onClick = { onMoodSelected(mood.key) },
                    label = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(mood.emoji, style = MaterialTheme.typography.titleMedium)
                            Text(mood.label, style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = containerColor,
                        containerColor = containerColor
                    )
                )
            }
        }
    }
}
