package com.openlauncher.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Compose dialogs live in a separate Android window, whose platform-provided
 * density can replace the launcher's scaled density. Capture the caller's
 * density and explicitly restore it inside the dialog composition so both dp
 * dimensions and sp text follow the launcher UI/text scale settings.
 */
@Composable
fun ScaledDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) {
    val launcherDensity = LocalDensity.current
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        CompositionLocalProvider(LocalDensity provides launcherDensity) {
            content()
        }
    }
}
