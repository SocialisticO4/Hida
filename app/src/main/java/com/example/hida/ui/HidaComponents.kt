package com.example.hida.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.hida.data.MediaRepository
import com.example.hida.data.PreferencesManager
import com.example.hida.ui.theme.*

val LocalMediaRepository = compositionLocalOf<MediaRepository> {
    error("No MediaRepository provided")
}

val LocalPreferencesManager = compositionLocalOf<PreferencesManager> {
    error("No PreferencesManager provided")
}

@Composable
fun HidaConfirmDialog(
    title: String,
    text: String,
    confirmText: String = "Delete",
    dismissText: String = "Cancel",
    confirmColor: Color = md3_dark_error,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = T30,
        title = { Text(title, color = OnSurfaceHigh) },
        text = { Text(text, color = OnSurfaceMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = confirmColor)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText, color = OnSurfaceHigh)
            }
        }
    )
}
