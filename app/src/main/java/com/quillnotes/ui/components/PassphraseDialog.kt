package com.quillnotes.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.quillnotes.R

/**
 * Asks for a passphrase: once when opening something encrypted, twice (with a length rule) when
 * choosing a new one. Never stores or logs what is typed.
 */
@Composable
fun PassphraseDialog(
    title: String,
    message: String,
    confirmLabel: String,
    creating: Boolean,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
    error: String? = null,
    busy: Boolean = false
) {
    var passphrase by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val mismatch = creating && confirm.isNotEmpty() && confirm != passphrase
    val tooShort = creating && passphrase.isNotEmpty() && passphrase.length < MIN_LENGTH
    val canSubmit = !busy && passphrase.isNotEmpty() && (!creating || (passphrase.length >= MIN_LENGTH && passphrase == confirm))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(R.string.passphrase)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = tooShort,
                    supportingText = if (tooShort) ({ Text(stringResource(R.string.passphrase_too_short, MIN_LENGTH)) }) else null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (creating) {
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it },
                        label = { Text(stringResource(R.string.passphrase_confirm)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = mismatch,
                        supportingText = if (mismatch) ({ Text(stringResource(R.string.passphrase_mismatch)) }) else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(passphrase) }, enabled = canSubmit) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

const val MIN_LENGTH = 8
