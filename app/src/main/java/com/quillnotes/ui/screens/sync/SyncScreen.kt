@file:OptIn(ExperimentalMaterial3Api::class)

package com.quillnotes.ui.screens.sync

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quillnotes.data.sync.SyncProvider
import com.quillnotes.ui.components.CircleIconButton
import com.quillnotes.ui.components.IconBadge
import com.quillnotes.ui.components.ScreenGutter
import com.quillnotes.ui.components.ScreenHeader
import com.quillnotes.ui.components.SectionLabel
import com.quillnotes.ui.components.heroBrush
import com.quillnotes.ui.theme.QuillTheme
import com.quillnotes.ui.util.relativePhrase
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import com.quillnotes.R
import com.quillnotes.ui.components.PassphraseDialog
import com.quillnotes.ui.util.UiText
import com.quillnotes.ui.util.asString
import com.quillnotes.ui.util.LocalTimeText

class SyncActions(
    val onBack: () -> Unit = {},
    val onSyncNow: () -> Unit = {},
    val onDisconnect: () -> Unit = {},
    val onConnect: (SyncProvider) -> Unit = {},
    val onSubmitPassphrase: (String) -> Unit = {},
    val onDismissPassphrase: () -> Unit = {}
)

private val SyncProvider.brand: Color
    get() = when (this) {
        SyncProvider.GOOGLE_DRIVE -> Color(0xFF1FA463)
        SyncProvider.ONEDRIVE -> Color(0xFF0F6CBD)
        SyncProvider.NONE -> Color(0xFF6A6358)
    }

@Composable
fun SyncScreen(
    onBack: () -> Unit,
    viewModel: SyncViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onGoogleSignInResult(result.data)
    }

    // Auto-clear transient messages after a few seconds
    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(4000)
            viewModel.clearMessage()
        }
    }

    SyncContent(
        state = state,
        actions = SyncActions(
            onBack = onBack,
            onSyncNow = viewModel::syncNow,
            onDisconnect = viewModel::disconnect,
            onSubmitPassphrase = viewModel::submitPassphrase,
            onDismissPassphrase = viewModel::dismissPassphrasePrompt,
            onConnect = { provider ->
                if (provider == SyncProvider.GOOGLE_DRIVE) {
                    googleSignInLauncher.launch(viewModel.getGoogleSignInIntent())
                } else {
                    viewModel.connectProvider(provider)
                }
            }
        )
    )
}

@Composable
fun SyncContent(
    state: SyncUiState,
    actions: SyncActions,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis()
) {
    val scheme = MaterialTheme.colorScheme
    if (state.passphrasePrompt != PassphrasePrompt.NONE) {
        val setup = state.passphrasePrompt == PassphrasePrompt.SETUP
        PassphraseDialog(
            title = stringResource(if (setup) R.string.sync_passphrase_setup_title else R.string.sync_passphrase_unlock_title),
            message = stringResource(if (setup) R.string.sync_passphrase_setup_message else R.string.sync_passphrase_unlock_message),
            confirmLabel = stringResource(if (setup) R.string.sync_passphrase_create else R.string.sync_passphrase_restore),
            creating = setup,
            onSubmit = actions.onSubmitPassphrase,
            onDismiss = actions.onDismissPassphrase,
            error = state.passphraseError?.asString(),
            busy = state.isSyncing
        )
    }
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
            title = stringResource(R.string.sync_screen_title),
            eyebrow = stringResource(R.string.sync_eyebrow),
            leading = {
                CircleIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    onClick = actions.onBack
                )
            }
        )
        Spacer(Modifier.height(18.dp))

        AnimatedVisibility(
            visible = state.message != null || state.error != null,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically()
        ) {
            StatusBanner(error = state.error, message = state.message)
        }

        StatusHero(state, actions, now)

        SectionLabel(
            text = stringResource(if (state.isConnected) R.string.sync_switch_provider else R.string.sync_choose_provider),
            padding = PaddingValues(top = 26.dp, bottom = 10.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SyncProvider.entries.filter { it != SyncProvider.NONE }.forEach { provider ->
                ProviderRow(
                    provider = provider,
                    connected = state.isConnected && state.provider == provider,
                    busy = state.isSyncing && state.provider == provider,
                    onConnect = { actions.onConnect(provider) }
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(scheme.surfaceVariant)
                .padding(16.dp)
        ) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.sync_encryption_footer),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusBanner(error: UiText?, message: UiText?) {
    val scheme = MaterialTheme.colorScheme
    val quill = QuillTheme.colors
    val isError = error != null
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(bottom = 14.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(if (isError) scheme.errorContainer else quill.successContainer)
            .padding(16.dp)
    ) {
        Icon(
            if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = if (isError) scheme.onErrorContainer else quill.success,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = (error ?: message)?.asString().orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) scheme.onErrorContainer else scheme.onSurface
        )
    }
}

@Composable
private fun StatusHero(state: SyncUiState, actions: SyncActions, now: Long) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(heroBrush())
            .padding(20.dp)
    ) {
        Box(
            modifier = Modifier.size(56.dp).clip(CircleShape).background(scheme.surface.copy(alpha = 0.8f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (state.isConnected) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = if (state.isConnected) scheme.primary else scheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (state.isConnected) stringResource(R.string.sync_backed_up_to, state.provider.displayName) else stringResource(R.string.sync_not_backed_up),
            style = MaterialTheme.typography.headlineSmall,
            color = scheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = when {
                !state.isConnected -> stringResource(R.string.sync_connect_hint)
                state.lastSyncTime != null -> stringResource(R.string.sync_last_synced, relativePhrase(state.lastSyncTime, now, LocalTimeText.current))
                else -> stringResource(R.string.sync_connected_first)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurface.copy(alpha = 0.72f)
        )
        if (state.isConnected) {
            if (state.unsyncedCount > 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (state.unsyncedCount == 1) stringResource(R.string.sync_waiting_one) else stringResource(R.string.sync_waiting_many, state.unsyncedCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurface,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(scheme.surface.copy(alpha = 0.7f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = actions.onSyncNow,
                    enabled = !state.isSyncing,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.onBackground,
                        contentColor = scheme.background
                    ),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    if (state.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = scheme.background
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.sync_syncing))
                    } else {
                        Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.sync_now_action))
                    }
                }
                OutlinedButton(
                    onClick = actions.onDisconnect,
                    enabled = !state.isSyncing,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, scheme.onSurface.copy(alpha = 0.25f)),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(stringResource(R.string.sync_disconnect_action), color = scheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun ProviderRow(
    provider: SyncProvider,
    connected: Boolean,
    busy: Boolean,
    onConnect: () -> Unit
) {
    val quill = QuillTheme.colors
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onConnect,
        enabled = !connected && !busy,
        shape = MaterialTheme.shapes.large,
        color = quill.card,
        border = BorderStroke(if (connected) 2.dp else 1.dp, if (connected) scheme.primary else quill.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(
                icon = provider.icon,
                background = provider.brand.copy(alpha = 0.14f),
                tint = provider.brand,
                size = 46.dp
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(provider.displayName, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                Text(stringResource(provider.descriptionRes), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(10.dp))
            when {
                busy -> CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                connected -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.sync_provider_connected), style = MaterialTheme.typography.labelLarge, color = scheme.primary)
                }
                else -> Text(
                    text = stringResource(R.string.sync_provider_connect),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.background,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(scheme.onBackground)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}
