@file:OptIn(ExperimentalMaterial3Api::class)

package com.quillnotes.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quillnotes.BuildConfig
import com.quillnotes.ui.components.CircleIconButton
import com.quillnotes.ui.components.IconBadge
import com.quillnotes.ui.components.ScreenGutter
import com.quillnotes.ui.components.ScreenHeader
import com.quillnotes.ui.components.SectionLabel
import com.quillnotes.ui.components.bleed
import com.quillnotes.ui.theme.AuroraColors
import com.quillnotes.ui.theme.AuroraScheme
import com.quillnotes.ui.theme.MidnightColors
import com.quillnotes.ui.theme.MidnightScheme
import com.quillnotes.ui.theme.PaperColors
import com.quillnotes.ui.theme.PaperScheme
import com.quillnotes.ui.theme.QuillSerif
import com.quillnotes.ui.theme.QuillTheme
import com.quillnotes.ui.theme.ThemeKeys

data class SettingsUiState(
    val theme: String = ThemeKeys.SYSTEM,
    val fontSize: String = "medium",
    val biometricLock: Boolean = false,
    val autoSync: Boolean = false,
    val versionName: String = BuildConfig.VERSION_NAME
)

class SettingsActions(
    val onBack: () -> Unit = {},
    val onThemeChange: (String) -> Unit = {},
    val onFontSizeChange: (String) -> Unit = {},
    val onBiometricChange: (Boolean) -> Unit = {},
    val onAutoSyncChange: (Boolean) -> Unit = {},
    val onOpenSync: () -> Unit = {},
    val onOpenTrash: () -> Unit = {}
)

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSyncClick: () -> Unit,
    onTrashClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val theme by viewModel.theme.collectAsState(initial = ThemeKeys.SYSTEM)
    val biometricLock by viewModel.biometricLock.collectAsState(initial = false)
    val autoSync by viewModel.autoSync.collectAsState(initial = false)
    val fontSize by viewModel.fontSize.collectAsState(initial = "medium")

    SettingsContent(
        state = SettingsUiState(theme, fontSize, biometricLock, autoSync),
        actions = SettingsActions(
            onBack = onBack,
            onThemeChange = viewModel::setTheme,
            onFontSizeChange = viewModel::setFontSize,
            onBiometricChange = viewModel::setBiometricLock,
            onAutoSyncChange = viewModel::setAutoSync,
            onOpenSync = onSyncClick,
            onOpenTrash = onTrashClick
        )
    )
}

@Composable
fun SettingsContent(state: SettingsUiState, actions: SettingsActions, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxSize()
            .background(scheme.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = ScreenGutter)
            .padding(bottom = 32.dp)
    ) {
        ScreenHeader(
            title = "Settings",
            eyebrow = "Quill Notes",
            leading = {
                CircleIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    onClick = actions.onBack
                )
            }
        )

        SectionLabel("Theme", padding = PaddingValues(top = 22.dp, bottom = 12.dp))
        ThemePicker(selected = state.theme, onSelect = actions.onThemeChange)

        SectionLabel("Text size", padding = PaddingValues(top = 22.dp, bottom = 12.dp))
        FontSizeControl(selected = state.fontSize, onSelect = actions.onFontSizeChange)

        SettingsGroup("Privacy & security") {
            SettingsRow(
                icon = Icons.Rounded.Fingerprint,
                badge = Color(0xFF7B53D6),
                title = "Biometric lock",
                subtitle = "Ask for fingerprint or face unlock to open",
                trailing = { QuillSwitch(state.biometricLock, actions.onBiometricChange) },
                onClick = { actions.onBiometricChange(!state.biometricLock) }
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.Lock,
                badge = Color(0xFF2E7D4F),
                title = "Encrypted on device",
                subtitle = "Every note is sealed with AES-256-GCM"
            )
        }

        SettingsGroup("Sync & storage") {
            SettingsRow(
                icon = Icons.Rounded.CloudSync,
                badge = Color(0xFF3D74DB),
                title = "Cloud sync",
                subtitle = "Google Drive and OneDrive backups",
                onClick = actions.onOpenSync,
                trailing = { Chevron() }
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.Sync,
                badge = Color(0xFF0E9AA7),
                title = "Auto-sync",
                subtitle = "Back up changes automatically",
                trailing = { QuillSwitch(state.autoSync, actions.onAutoSyncChange) },
                onClick = { actions.onAutoSyncChange(!state.autoSync) }
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.DeleteOutline,
                badge = Color(0xFFD9534F),
                title = "Trash",
                subtitle = "Restore notes deleted in the last 30 days",
                onClick = actions.onOpenTrash,
                trailing = { Chevron() }
            )
        }

        SettingsGroup("About") {
            SettingsRow(
                icon = Icons.Rounded.Info,
                badge = scheme.onSurfaceVariant,
                title = "Quill Notes",
                subtitle = "Version ${state.versionName}"
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.Shield,
                badge = Color(0xFFE2741F),
                title = "Your data stays yours",
                subtitle = "No ads, no tracking. Notes leave the device only when you sync."
            )
        }
    }
}

// ── Theme picker ──────────────────────────────────────────

private data class ThemeSwatch(
    val background: Color,
    val card: Color,
    val ink: Color,
    val accent: Color,
    val dock: Color
)

@Composable
private fun ThemePicker(selected: String, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    val paper = ThemeSwatch(PaperScheme.background, PaperColors.card, PaperScheme.onBackground, PaperScheme.primary, PaperColors.dockContainer)
    val midnight = ThemeSwatch(MidnightScheme.background, MidnightColors.card, MidnightScheme.onBackground, MidnightScheme.primary, MidnightColors.dockContainer)
    val aurora = ThemeSwatch(AuroraScheme.background, AuroraColors.card, AuroraScheme.onBackground, AuroraScheme.primary, AuroraColors.dockContainer)
    val dynamic = if (QuillTheme.colors.isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    val dynamicSwatch = ThemeSwatch(dynamic.background, dynamic.surfaceVariant, dynamic.onBackground, dynamic.primary, dynamic.inverseSurface)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bleed(ScreenGutter)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = ScreenGutter),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ThemeOptionTile("System", selected == ThemeKeys.SYSTEM, { onSelect(ThemeKeys.SYSTEM) }) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight()) { MiniMock(paper, compact = true) }
                Box(Modifier.weight(1f).fillMaxHeight()) { MiniMock(midnight, compact = true) }
            }
        }
        ThemeOptionTile("Paper", selected == ThemeKeys.PAPER, { onSelect(ThemeKeys.PAPER) }) { MiniMock(paper) }
        ThemeOptionTile("Midnight", selected == ThemeKeys.MIDNIGHT, { onSelect(ThemeKeys.MIDNIGHT) }) { MiniMock(midnight) }
        ThemeOptionTile("Aurora", selected == ThemeKeys.AURORA, { onSelect(ThemeKeys.AURORA) }) { MiniMock(aurora) }
        ThemeOptionTile("Wallpaper", selected == ThemeKeys.DYNAMIC, { onSelect(ThemeKeys.DYNAMIC) }) { MiniMock(dynamicSwatch) }
    }
}

@Composable
private fun ThemeOptionTile(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    preview: @Composable BoxScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val ring by animateColorAsState(if (selected) scheme.primary else scheme.outlineVariant, label = "themeRing")
    val ringWidth by animateDpAsState(if (selected) 2.5.dp else 1.dp, label = "themeRingWidth")
    val outer = RoundedCornerShape(22.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(width = 92.dp, height = 124.dp)
                .clip(outer)
                .border(ringWidth, ring, outer)
                .clickable(role = Role.RadioButton, onClickLabel = "Use $label theme", onClick = onClick)
                .padding(5.dp)
                .clip(RoundedCornerShape(17.dp)),
            content = preview
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(3.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) scheme.onSurface else scheme.onSurfaceVariant
            )
        }
    }
}

/** A tiny drawing of the app in a given palette: title, two note cards, the dock. */
@Composable
private fun MiniMock(swatch: ThemeSwatch, compact: Boolean = false) {
    Box(Modifier.fillMaxSize().background(swatch.background)) {
        Column(Modifier.padding(if (compact) 6.dp else 9.dp)) {
            Box(Modifier.width(if (compact) 22.dp else 40.dp).height(7.dp).clip(CircleShape).background(swatch.ink.copy(alpha = 0.85f)))
            Spacer(Modifier.height(4.dp))
            Box(Modifier.width(if (compact) 16.dp else 26.dp).height(4.dp).clip(CircleShape).background(swatch.ink.copy(alpha = 0.3f)))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MiniCard(swatch.card, swatch.ink, 30.dp)
                    MiniCard(swatch.accent.copy(alpha = 0.22f), swatch.ink, 18.dp)
                }
                if (!compact) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        MiniCard(swatch.accent.copy(alpha = 0.16f), swatch.ink, 20.dp)
                        MiniCard(swatch.card, swatch.ink, 28.dp)
                    }
                }
            }
        }
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(if (compact) 18.dp else 34.dp).height(10.dp).clip(CircleShape).background(swatch.dock))
            if (!compact) {
                Spacer(Modifier.width(3.dp))
                Box(Modifier.size(10.dp).clip(CircleShape).background(swatch.accent))
            }
        }
    }
}

@Composable
private fun MiniCard(color: Color, ink: Color, height: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(5.dp))
            .background(color)
            .border(0.5.dp, ink.copy(alpha = 0.08f), RoundedCornerShape(5.dp))
            .padding(4.dp)
    ) {
        Box(Modifier.fillMaxWidth(0.7f).height(3.dp).clip(CircleShape).background(ink.copy(alpha = 0.45f)))
    }
}

// ── Font size ─────────────────────────────────────────────

@Composable
private fun FontSizeControl(selected: String, onSelect: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = scheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().height(60.dp)
    ) {
        Row(Modifier.padding(5.dp)) {
            listOf(
                Triple("small", "Small", 15.sp),
                Triple("medium", "Default", 19.sp),
                Triple("large", "Large", 24.sp)
            ).forEach { (key, label, sample) ->
                val isSelected = key == selected
                val bg by animateColorAsState(if (isSelected) scheme.onBackground else Color.Transparent, label = "fontSeg")
                val fg by animateColorAsState(if (isSelected) scheme.background else scheme.onSurfaceVariant, label = "fontSegFg")
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(bg)
                        .clickable(role = Role.RadioButton, onClick = { onSelect(key) }),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Aa", fontFamily = QuillSerif, fontSize = sample, color = fg)
                    Spacer(Modifier.width(6.dp))
                    Text(label, style = MaterialTheme.typography.labelMedium, color = fg)
                }
            }
        }
    }
}

// ── Rows ──────────────────────────────────────────────────

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    val quill = QuillTheme.colors
    SectionLabel(title, padding = PaddingValues(top = 26.dp, bottom = 10.dp))
    Surface(
        shape = MaterialTheme.shapes.large,
        color = quill.card,
        border = BorderStroke(1.dp, quill.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    badge: Color,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon = icon, background = badge.copy(alpha = 0.14f), tint = badge)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
private fun RowDivider() {
    Box(
        Modifier
            .padding(start = 68.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(QuillTheme.colors.cardBorder)
    )
}

@Composable
private fun Chevron() {
    Icon(
        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.outline
    )
}

@Composable
private fun QuillSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        thumbContent = if (checked) {
            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
        } else null,
        colors = SwitchDefaults.colors(
            checkedTrackColor = scheme.primary,
            checkedThumbColor = scheme.onPrimary,
            checkedIconColor = scheme.primary
        )
    )
}
