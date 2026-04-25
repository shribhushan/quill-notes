package com.quillnotes.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSyncClick: () -> Unit,
    onTrashClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val theme by viewModel.theme.collectAsState(initial = "system")
    val biometricLock by viewModel.biometricLock.collectAsState(initial = false)
    val autoSync by viewModel.autoSync.collectAsState(initial = false)
    val fontSize by viewModel.fontSize.collectAsState(initial = "medium")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // ── Appearance ─────────────────────────────────────
            SettingsSection("Appearance") {
                Text(
                    "Theme",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "system" to "System",
                        "light" to "Light",
                        "dark" to "Dark",
                        "colorful" to "Colorful"
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = theme == key,
                            onClick = { viewModel.setTheme(key) },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }

                Text(
                    "Font Size",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("small" to "Small", "medium" to "Medium", "large" to "Large").forEach { (key, label) ->
                        FilterChip(
                            selected = fontSize == key,
                            onClick = { viewModel.setFontSize(key) },
                            label = { Text(label) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Security ───────────────────────────────────────
            SettingsSection("Security") {
                SettingsToggle(
                    icon = Icons.Outlined.Fingerprint,
                    title = "Biometric Lock",
                    subtitle = "Require fingerprint or face to open app",
                    checked = biometricLock,
                    onCheckedChange = viewModel::setBiometricLock
                )

                SettingsItem(
                    icon = Icons.Outlined.Lock,
                    title = "Encryption",
                    subtitle = "AES-256-GCM · All notes encrypted at rest",
                    onClick = {}
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Notes ─────────────────────────────────────────
            SettingsSection("Notes") {
                SettingsItem(
                    icon = Icons.Outlined.Delete,
                    title = "Trash",
                    subtitle = "View and restore deleted notes",
                    onClick = onTrashClick
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Cloud Sync ─────────────────────────────────────
            SettingsSection("Cloud Sync") {
                SettingsItem(
                    icon = Icons.Outlined.Cloud,
                    title = "Sync Settings",
                    subtitle = "Connect Google Drive, OneDrive",
                    onClick = onSyncClick
                )

                SettingsToggle(
                    icon = Icons.Outlined.Sync,
                    title = "Auto-Sync",
                    subtitle = "Sync notes automatically when changed",
                    checked = autoSync,
                    onCheckedChange = viewModel::setAutoSync
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── About ──────────────────────────────────────────
            SettingsSection("About") {
                SettingsItem(
                    icon = Icons.Outlined.Info,
                    title = "Quill Notes",
                    subtitle = "Version 1.0.0",
                    onClick = {}
                )
                SettingsItem(
                    icon = Icons.Outlined.Description,
                    title = "Privacy Policy",
                    subtitle = "How we protect your data",
                    onClick = {}
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
            )
            content()
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
