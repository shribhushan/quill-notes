package com.quillnotes.ui.components

import androidx.activity.ComponentActivity
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.quillnotes.ui.screens.settings.SettingsViewModel

@Composable
fun BiometricGate(
    content: @Composable () -> Unit
) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val biometricLock by settingsViewModel.biometricLock.collectAsState(initial = false)

    var isAuthenticated by remember { mutableStateOf(!biometricLock) }
    var backgroundedAt by remember { mutableStateOf(0L) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(biometricLock) {
        if (!biometricLock) {
            isAuthenticated = true
        } else if (!isAuthenticated) {
            triggerBiometricPrompt(context, onSuccess = { isAuthenticated = true })
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> backgroundedAt = System.currentTimeMillis()
                Lifecycle.Event.ON_RESUME -> {
                    if (biometricLock && isAuthenticated) {
                        val elapsed = System.currentTimeMillis() - backgroundedAt
                        if (elapsed > 60_000L) {
                            isAuthenticated = false
                            triggerBiometricPrompt(context, onSuccess = { isAuthenticated = true })
                        }
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!biometricLock || isAuthenticated) {
        content()
    } else {
        LockedScreen(
            onUnlock = {
                triggerBiometricPrompt(context, onSuccess = { isAuthenticated = true })
            }
        )
    }
}

private fun triggerBiometricPrompt(
    context: android.content.Context,
    onSuccess: () -> Unit
) {
    val activity = context as? ComponentActivity ?: return
    val executor = ContextCompat.getMainExecutor(context)

    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onSuccess()
        }
    }

    val prompt = BiometricPrompt(activity, executor, callback)
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock Quill Notes")
        .setSubtitle("Use your biometric credential to access your notes")
        .setNegativeButtonText("Cancel")
        .build()

    prompt.authenticate(info)
}

@Composable
private fun LockedScreen(onUnlock: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Quill Notes is locked",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Authenticate to access your notes",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onUnlock) {
                Icon(
                    Icons.Outlined.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Unlock")
            }
        }
    }
}
