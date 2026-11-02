package com.quillnotes.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.quillnotes.ui.theme.MoodAnxious
import com.quillnotes.ui.theme.MoodCalm
import com.quillnotes.ui.theme.MoodHappy
import com.quillnotes.ui.theme.MoodNeutral
import com.quillnotes.ui.theme.MoodSad

data class MoodOption(
    val label: String,
    val emoji: String,
    val key: String,
    val color: Color
)

val moods = listOf(
    MoodOption("Happy", "😊", "happy", MoodHappy),
    MoodOption("Calm", "🧘", "calm", MoodCalm),
    MoodOption("Neutral", "😐", "neutral", MoodNeutral),
    MoodOption("Anxious", "😟", "anxious", MoodAnxious),
    MoodOption("Sad", "😔", "sad", MoodSad)
)

fun moodFor(key: String?): MoodOption? = moods.find { it.key == key }

/** Row of emoji bubbles; the selected mood grows and gains a coloured halo. */
@Composable
fun MoodSelector(
    selectedMood: String?,
    onMoodSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = "How are you feeling?"
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            moods.forEach { mood ->
                MoodBubble(
                    mood = mood,
                    selected = selectedMood == mood.key,
                    dimmed = selectedMood != null && selectedMood != mood.key,
                    onClick = { onMoodSelected(mood.key) }
                )
            }
        }
    }
}

@Composable
private fun MoodBubble(mood: MoodOption, selected: Boolean, dimmed: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "moodScale"
    )
    val bg by animateColorAsState(
        if (selected) mood.color.copy(alpha = 0.22f) else scheme.surfaceVariant,
        label = "moodBg"
    )
    val ring by animateColorAsState(
        if (selected) mood.color else Color.Transparent,
        label = "moodRing"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .graphicsLayer { alpha = if (dimmed) 0.55f else 1f }
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(bg)
                .border(2.dp, ring, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(mood.emoji, style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = mood.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) scheme.onSurface else scheme.onSurfaceVariant
        )
    }
}
