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
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.quillnotes.CrashReporter
import com.quillnotes.R
import com.quillnotes.ui.components.PassphraseDialog
import com.quillnotes.ui.components.QuillTimePickerDialog
import com.quillnotes.ui.components.rememberNotificationPermissionRequest
import com.quillnotes.ui.util.timeOfDay
import com.quillnotes.ui.util.withTimeOfDay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    val journalReminder: Boolean = false,
    val journalReminderMinutes: Int = 21 * 60,
    val working: Boolean = false,
    val result: DataResult? = null,
    val hasCrashReport: Boolean = false,
    val versionName: String = BuildConfig.VERSION_NAME
)

class SettingsActions(
    val onBack: () -> Unit = {},
    val onThemeChange: (String) -> Unit = {},
    val onFontSizeChange: (String) -> Unit = {},
    val onBiometricChange: (Boolean) -> Unit = {},
    val onAutoSyncChange: (Boolean) -> Unit = {},
    val onJournalReminderChange: (Boolean) -> Unit = {},
    val onJournalReminderTime: (Int) -> Unit = {},
    val onOpenSync: () -> Unit = {},
    val onOpenTrash: () -> Unit = {},
    val onExportMarkdown: () -> Unit = {},
    val onCreateBackup: () -> Unit = {},
    val onRestoreBackup: () -> Unit = {},
    val onDismissResult: () -> Unit = {},
    val onShowPrivacy: () -> Unit = {},
    val onSendCrashReport: () -> Unit = {}
)

/** Which passphrase prompt is open. */
private enum class PassphraseFor { NONE, NEW_BACKUP, RESTORE }

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
    val journalReminder by viewModel.journalReminder.collectAsState(initial = false)
    val reminderMinutes by viewModel.journalReminderMinutes.collectAsState(initial = 21 * 60)
    val working by viewModel.working.collectAsState()
    val result by viewModel.result.collectAsState()
    val context = LocalContext.current
    val requestNotifications = rememberNotificationPermissionRequest()

    var passphraseFor by rememberSaveable { mutableStateOf(PassphraseFor.NONE) }
    var restoreUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var pendingPassphrase by remember { mutableStateOf<String?>(null) } // never saved: it is a secret
    var showPrivacy by rememberSaveable { mutableStateOf(false) }
    var hasCrash by remember { mutableStateOf(CrashReporter.pending(context) != null) }
    val stamp = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }

    val markdownPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) viewModel.exportMarkdown(uri)
    }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val passphrase = pendingPassphrase
        pendingPassphrase = null
        if (uri != null && passphrase != null) viewModel.exportBackup(uri, passphrase)
    }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            restoreUri = uri
            passphraseFor = PassphraseFor.RESTORE
        }
    }

    // A finished restore or a damaged file closes the prompt; a wrong passphrase leaves it open to retry.
    LaunchedEffect(result) {
        when (result) {
            is DataResult.Restored, DataResult.NotABackup, DataResult.Failed -> if (passphraseFor == PassphraseFor.RESTORE) passphraseFor = PassphraseFor.NONE
            else -> Unit
        }
    }

    SettingsContent(
        state = SettingsUiState(
            theme = theme, fontSize = fontSize, biometricLock = biometricLock, autoSync = autoSync,
            journalReminder = journalReminder, journalReminderMinutes = reminderMinutes,
            working = working, result = result, hasCrashReport = hasCrash
        ),
        actions = SettingsActions(
            onBack = onBack,
            onThemeChange = viewModel::setTheme,
            onFontSizeChange = viewModel::setFontSize,
            onBiometricChange = viewModel::setBiometricLock,
            onAutoSyncChange = viewModel::setAutoSync,
            onJournalReminderChange = { enabled ->
                if (enabled) requestNotifications()
                viewModel.setJournalReminder(enabled)
            },
            onJournalReminderTime = viewModel::setJournalReminderTime,
            onOpenSync = onSyncClick,
            onOpenTrash = onTrashClick,
            onExportMarkdown = { markdownPicker.launch("quill-notes-$stamp.zip") },
            onCreateBackup = { passphraseFor = PassphraseFor.NEW_BACKUP },
            onRestoreBackup = { restorePicker.launch(arrayOf("*/*")) },
            onDismissResult = viewModel::clearResult,
            onShowPrivacy = { showPrivacy = true },
            onSendCrashReport = {
                CrashReporter.pending(context)?.let { report ->
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.crash_report_subject))
                        putExtra(Intent.EXTRA_TEXT, report)
                    }
                    context.startActivity(Intent.createChooser(send, null))
                    CrashReporter.clear(context)
                    hasCrash = false
                }
            }
        )
    )

    when (passphraseFor) {
        PassphraseFor.NEW_BACKUP -> PassphraseDialog(
            title = stringResource(R.string.backup_create_title),
            message = stringResource(R.string.backup_create_message),
            confirmLabel = stringResource(R.string.backup_choose_location),
            creating = true,
            onSubmit = { passphrase ->
                pendingPassphrase = passphrase
                passphraseFor = PassphraseFor.NONE
                backupPicker.launch("quill-backup-$stamp.qbackup")
            },
            onDismiss = { passphraseFor = PassphraseFor.NONE }
        )
        PassphraseFor.RESTORE -> PassphraseDialog(
            title = stringResource(R.string.backup_restore_title),
            message = stringResource(R.string.backup_restore_message),
            confirmLabel = stringResource(R.string.backup_restore_action),
            creating = false,
            onSubmit = { passphrase -> restoreUri?.let { viewModel.importBackup(it, passphrase) } },
            onDismiss = { passphraseFor = PassphraseFor.NONE },
            error = if (result == DataResult.WrongPassphrase) stringResource(R.string.backup_wrong_passphrase) else null,
            busy = working
        )
        PassphraseFor.NONE -> Unit
    }

    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text(stringResource(R.string.privacy_title)) },
            text = {
                Text(
                    stringResource(R.string.privacy_body),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text(stringResource(R.string.close)) } }
        )
    }
}

@Composable
fun SettingsContent(state: SettingsUiState, actions: SettingsActions, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    var showTimePicker by remember { mutableStateOf(false) }
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
            title = stringResource(R.string.settings),
            eyebrow = stringResource(R.string.app_name),
            leading = {
                CircleIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    onClick = actions.onBack
                )
            }
        )

        ResultBanner(state, actions)

        SectionLabel(stringResource(R.string.settings_theme), padding = PaddingValues(top = 22.dp, bottom = 12.dp))
        ThemePicker(selected = state.theme, onSelect = actions.onThemeChange)

        SectionLabel(stringResource(R.string.settings_text_size), padding = PaddingValues(top = 22.dp, bottom = 12.dp))
        FontSizeControl(selected = state.fontSize, onSelect = actions.onFontSizeChange)

        SettingsGroup(stringResource(R.string.settings_privacy_security)) {
            SettingsRow(
                icon = Icons.Rounded.Fingerprint,
                badge = Color(0xFF7B53D6),
                title = stringResource(R.string.settings_biometric_title),
                subtitle = stringResource(R.string.settings_biometric_subtitle),
                trailing = { QuillSwitch(state.biometricLock, actions.onBiometricChange) },
                onClick = { actions.onBiometricChange(!state.biometricLock) }
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.Lock,
                badge = Color(0xFF2E7D4F),
                title = stringResource(R.string.settings_encrypted_title),
                subtitle = stringResource(R.string.settings_encrypted_subtitle)
            )
        }

        SettingsGroup(stringResource(R.string.settings_reminders)) {
            SettingsRow(
                icon = Icons.Rounded.AutoStories,
                badge = Color(0xFFD9534F),
                title = stringResource(R.string.settings_journal_reminder_title),
                subtitle = if (state.journalReminder) stringResource(R.string.settings_journal_reminder_on, formatMinutes(state.journalReminderMinutes))
                else stringResource(R.string.settings_journal_reminder_off),
                trailing = { QuillSwitch(state.journalReminder, actions.onJournalReminderChange) },
                onClick = { actions.onJournalReminderChange(!state.journalReminder) }
            )
            if (state.journalReminder) {
                RowDivider()
                SettingsRow(
                    icon = Icons.Rounded.AccessTime,
                    badge = Color(0xFFE2741F),
                    title = stringResource(R.string.settings_journal_reminder_time),
                    subtitle = formatMinutes(state.journalReminderMinutes),
                    onClick = { showTimePicker = true },
                    trailing = { Chevron() }
                )
            }
        }

        SettingsGroup(stringResource(R.string.settings_sync_storage)) {
            SettingsRow(
                icon = Icons.Rounded.CloudSync,
                badge = Color(0xFF3D74DB),
                title = stringResource(R.string.settings_cloud_sync_title),
                subtitle = stringResource(R.string.settings_cloud_sync_subtitle),
                onClick = actions.onOpenSync,
                trailing = { Chevron() }
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.Sync,
                badge = Color(0xFF0E9AA7),
                title = stringResource(R.string.settings_auto_sync_title),
                subtitle = stringResource(R.string.settings_auto_sync_subtitle),
                trailing = { QuillSwitch(state.autoSync, actions.onAutoSyncChange) },
                onClick = { actions.onAutoSyncChange(!state.autoSync) }
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.DeleteOutline,
                badge = Color(0xFFD9534F),
                title = stringResource(R.string.settings_trash_title),
                subtitle = stringResource(R.string.settings_trash_subtitle),
                onClick = actions.onOpenTrash,
                trailing = { Chevron() }
            )
        }

        SettingsGroup(stringResource(R.string.settings_your_data)) {
            SettingsRow(
                icon = Icons.Rounded.FileDownload,
                badge = Color(0xFF2E7D4F),
                title = stringResource(R.string.settings_export_markdown_title),
                subtitle = stringResource(R.string.settings_export_markdown_subtitle),
                onClick = if (state.working) null else actions.onExportMarkdown,
                trailing = { if (state.working) WorkingIndicator() else Chevron() }
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.Backup,
                badge = Color(0xFF3D74DB),
                title = stringResource(R.string.settings_backup_title),
                subtitle = stringResource(R.string.settings_backup_subtitle),
                onClick = if (state.working) null else actions.onCreateBackup,
                trailing = { Chevron() }
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.Restore,
                badge = Color(0xFF7B53D6),
                title = stringResource(R.string.settings_restore_title),
                subtitle = stringResource(R.string.settings_restore_subtitle),
                onClick = if (state.working) null else actions.onRestoreBackup,
                trailing = { Chevron() }
            )
        }

        SettingsGroup(stringResource(R.string.settings_about)) {
            SettingsRow(
                icon = Icons.Rounded.Info,
                badge = scheme.onSurfaceVariant,
                title = stringResource(R.string.app_name),
                subtitle = stringResource(R.string.settings_version, state.versionName)
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.Shield,
                badge = Color(0xFFE2741F),
                title = stringResource(R.string.settings_data_yours_title),
                subtitle = stringResource(R.string.settings_data_yours_subtitle),
                onClick = actions.onShowPrivacy,
                trailing = { Chevron() }
            )
            if (state.hasCrashReport) {
                RowDivider()
                SettingsRow(
                    icon = Icons.Rounded.BugReport,
                    badge = Color(0xFFD9534F),
                    title = stringResource(R.string.settings_crash_title),
                    subtitle = stringResource(R.string.settings_crash_subtitle),
                    onClick = actions.onSendCrashReport,
                    trailing = { Chevron() }
                )
            }
        }
    }

    if (showTimePicker) {
        QuillTimePickerDialog(
            title = stringResource(R.string.settings_journal_reminder_time),
            initialHour = state.journalReminderMinutes / 60,
            initialMinute = state.journalReminderMinutes % 60,
            onDismiss = { showTimePicker = false },
            onConfirm = { h, m ->
                showTimePicker = false
                actions.onJournalReminderTime(h * 60 + m)
            }
        )
    }
}

/** "9:30 PM" for a time of day given as minutes after midnight. */
@Composable
private fun formatMinutes(minutes: Int): String = timeOfDay(withTimeOfDay(System.currentTimeMillis(), minutes / 60, minutes % 60))

@Composable
private fun WorkingIndicator() {
    androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
}

/** The outcome of the last export or restore, until dismissed. */
@Composable
private fun ResultBanner(state: SettingsUiState, actions: SettingsActions) {
    val result = state.result ?: return
    val scheme = MaterialTheme.colorScheme
    val quill = QuillTheme.colors
    val isError = result is DataResult.WrongPassphrase || result is DataResult.NotABackup || result is DataResult.Failed
    val text = when (result) {
        is DataResult.MarkdownExported -> stringResource(R.string.result_markdown_exported, result.notes, result.images)
        is DataResult.BackupCreated -> stringResource(R.string.result_backup_created, result.notes, result.images)
        is DataResult.Restored -> stringResource(
            R.string.result_restored, result.summary.added, result.summary.updated, result.summary.skipped, result.summary.images
        )
        DataResult.WrongPassphrase -> stringResource(R.string.backup_wrong_passphrase)
        DataResult.NotABackup -> stringResource(R.string.result_not_a_backup)
        DataResult.Failed -> stringResource(R.string.result_failed)
    }
    // A wrong passphrase is shown inside the prompt instead.
    if (result is DataResult.WrongPassphrase) return
    androidx.compose.runtime.LaunchedEffect(result) {
        if (!isError) {
            kotlinx.coroutines.delay(6_000)
            actions.onDismissResult()
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 14.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(if (isError) scheme.errorContainer else quill.successContainer)
            .clickable(onClick = actions.onDismissResult)
            .padding(16.dp)
    ) {
        Icon(
            if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = if (isError) scheme.onErrorContainer else quill.success,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = if (isError) scheme.onErrorContainer else scheme.onSurface)
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
        ThemeOptionTile(stringResource(R.string.theme_system), selected == ThemeKeys.SYSTEM, { onSelect(ThemeKeys.SYSTEM) }) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight()) { MiniMock(paper, compact = true) }
                Box(Modifier.weight(1f).fillMaxHeight()) { MiniMock(midnight, compact = true) }
            }
        }
        ThemeOptionTile(stringResource(R.string.theme_paper), selected == ThemeKeys.PAPER, { onSelect(ThemeKeys.PAPER) }) { MiniMock(paper) }
        ThemeOptionTile(stringResource(R.string.theme_midnight), selected == ThemeKeys.MIDNIGHT, { onSelect(ThemeKeys.MIDNIGHT) }) { MiniMock(midnight) }
        ThemeOptionTile(stringResource(R.string.theme_aurora), selected == ThemeKeys.AURORA, { onSelect(ThemeKeys.AURORA) }) { MiniMock(aurora) }
        ThemeOptionTile(stringResource(R.string.theme_wallpaper), selected == ThemeKeys.DYNAMIC, { onSelect(ThemeKeys.DYNAMIC) }) { MiniMock(dynamicSwatch) }
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
                .clickable(role = Role.RadioButton, onClickLabel = stringResource(R.string.theme_use, label), onClick = onClick)
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
                Triple("small", R.string.text_size_small, 15.sp),
                Triple("medium", R.string.text_size_default, 19.sp),
                Triple("large", R.string.text_size_large, 24.sp)
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
                    Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = fg)
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
