package com.quillnotes.ui.components

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.ui.draw.clip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.quillnotes.ui.screens.settings.SettingsViewModel
import androidx.compose.ui.res.stringResource
import com.quillnotes.R

@Composable
fun BiometricGate(
    content: @Composable () -> Unit
) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    // null until the preference loads, so the gate never flashes content before it knows.
    val biometricLock by settingsViewModel.biometricLock
        .collectAsState(initial = null as Boolean?)

    var isAuthenticated by remember { mutableStateOf(false) }
    var backgroundedAt by remember { mutableStateOf(0L) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(biometricLock) {
        when (biometricLock) {
            false -> isAuthenticated = true
            true -> if (!isAuthenticated) {
                triggerBiometricPrompt(context, onSuccess = { isAuthenticated = true })
            }
            null -> Unit
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> backgroundedAt = System.currentTimeMillis()
                Lifecycle.Event.ON_RESUME -> {
                    if (biometricLock == true && isAuthenticated) {
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

    when {
        biometricLock == null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        biometricLock == false || isAuthenticated -> content()
        else -> LockedScreen(
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
    val activity = context as? FragmentActivity ?: return
    val executor = ContextCompat.getMainExecutor(context)

    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onSuccess()
        }
    }

    val prompt = BiometricPrompt(activity, executor, callback)
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(context.getString(R.string.biometric_prompt_title))
        .setSubtitle(context.getString(R.string.applock_prompt_subtitle))
        .setNegativeButtonText(context.getString(R.string.cancel))
        .build()

    prompt.authenticate(info)
}

/**
 * True if this phone can check who is asking: a fingerprint/face is enrolled or a screen lock
 * (PIN, pattern, password) is set. Without one a "locked" note could not actually be locked.
 */
fun canAuthenticate(context: android.content.Context): Boolean =
    BiometricManager.from(context).canAuthenticate(NOTE_LOCK_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

private const val NOTE_LOCK_AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

/**
 * Asks the user to prove it is them before a locked note opens: biometrics, or the screen lock
 * as a fallback. [onSuccess] runs only on success; [onFailure] if they cancel or it is unavailable.
 */
fun authenticateForNote(
    context: android.content.Context,
    title: String,
    subtitle: String,
    onSuccess: () -> Unit,
    onFailure: () -> Unit = {}
) {
    val activity = context as? FragmentActivity
    if (activity == null || !canAuthenticate(context)) {
        onFailure()
        return
    }
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFailure()
    }
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(NOTE_LOCK_AUTHENTICATORS)
        .build()
    BiometricPrompt(activity, ContextCompat.getMainExecutor(context), callback).authenticate(info)
}

/** Shown while the app is locked; also used directly by screenshot tests. */
@Composable
fun LockedScreen(onUnlock: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .systemBarsPadding()
            .padding(32.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(132.dp).clip(CircleShape).background(heroBrush()),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.size(76.dp).clip(CircleShape).background(scheme.surface.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(34.dp))
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                stringResource(R.string.locked_title),
                style = MaterialTheme.typography.displaySmall,
                color = scheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.locked_message),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Button(
            onClick = onUnlock,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = scheme.onBackground,
                contentColor = scheme.background
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Icon(Icons.Rounded.Fingerprint, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.locked_note_unlock), style = MaterialTheme.typography.titleMedium)
        }
    }
}
