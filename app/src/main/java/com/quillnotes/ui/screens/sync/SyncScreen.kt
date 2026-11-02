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
import com.quillnotes.ui.util.relativeTime
import kotlinx.coroutines.delay

class SyncActions(
    val onBack: () -> Unit = {},
    val onSyncNow: () -> Unit = {},
    val onDisconnect: () -> Unit = {},
    val onConnect: (SyncProvider) -> Unit = {}
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
            title = "Cloud sync",
            eyebrow = "Backup",
            leading = {
                CircleIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
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
            text = if (state.isConnected) "Switch provider" else "Choose a provider",
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
                "Notes are encrypted with AES-256-GCM before they leave your phone. " +
                    "Your cloud provider can't read them.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusBanner(error: String?, message: String?) {
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
            text = error ?: message.orEmpty(),
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
            text = if (state.isConnected) "Backed up to ${state.provider.displayName}" else "Not backed up yet",
            style = MaterialTheme.typography.headlineSmall,
            color = scheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = when {
                !state.isConnected -> "Connect a provider below to keep an encrypted copy of your notes in the cloud."
                state.lastSyncTime != null -> "Last synced ${relativeTime(state.lastSyncTime, now).lowercase()}"
                else -> "Connected — run your first sync"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurface.copy(alpha = 0.72f)
        )
        if (state.isConnected) {
            if (state.unsyncedCount > 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (state.unsyncedCount == 1) "1 note waiting to sync" else "${state.unsyncedCount} notes waiting to sync",
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
                        Text("Syncing…")
                    } else {
                        Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sync now")
                    }
                }
                OutlinedButton(
                    onClick = actions.onDisconnect,
                    enabled = !state.isSyncing,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, scheme.onSurface.copy(alpha = 0.25f)),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Disconnect", color = scheme.onSurface)
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
                Text(provider.description, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(10.dp))
            when {
                busy -> CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                connected -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Connected", style = MaterialTheme.typography.labelLarge, color = scheme.primary)
                }
                else -> Text(
                    text = "Connect",
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
