package io.github.sj14.brabbely.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.sj14.brabbely.R

@Composable
fun UnsubscribeDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.unsubscribe_title)) },
        text = { Text(stringResource(R.string.unsubscribe_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.unsubscribe)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
