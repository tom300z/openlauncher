package com.openlauncher.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.openlauncher.app.data.AppSettings

@Composable
fun SpeedometerConfigDialog(
    settings: AppSettings,
    accent: Color,
    isDayMode: Boolean,
    onDismiss: () -> Unit,
    onSave: (Int, Int, Boolean, Boolean) -> Unit
) {
    var maxKph by remember { mutableIntStateOf(settings.speedometerMaxKph) }
    var segmentKph by remember { mutableIntStateOf(settings.speedometerSegmentKph) }
    var minorTicks by remember { mutableStateOf(settings.speedometerMinorTicks) }
    var numbers by remember { mutableStateOf(settings.speedometerReferenceNumbers) }
    val text = if (isDayMode) Color(0xFF111111) else Color.White
    val secondary = if (isDayMode) Color(0xFF6C757D) else Color(0xFF888888)
    ScrollableSettingsDialog(
        title = "SPEEDOMETER SETTINGS",
        isDayMode = isDayMode,
        onDismiss = onDismiss,
        actions = {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("CANCEL", color = secondary) }
            TextButton(onClick = { onSave(maxKph, segmentKph, minorTicks, numbers) }) {
                Text("SAVE", color = accent)
            }
        }
    ) {
        SpeedValueControl("Maximum speed", maxKph, 40, 400, text, accent) { maxKph = it }
        Text("Dial range only; the digital readout still displays higher speeds.", color = secondary)
        Spacer(Modifier.height(16.dp))
        SpeedValueControl("Segment size", segmentKph, 10, 100, text, accent) { segmentKph = it }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Minor tick marks", color = text)
                Text("Half a segment (${segmentKph / 2} km/h)", color = secondary)
            }
            Switch(checked = minorTicks, onCheckedChange = { minorTicks = it },
                colors = SwitchDefaults.colors(checkedTrackColor = accent, checkedThumbColor = Color.Black))
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Reference numbers", color = text, modifier = Modifier.weight(1f))
            Switch(checked = numbers, onCheckedChange = { numbers = it },
                colors = SwitchDefaults.colors(checkedTrackColor = accent, checkedThumbColor = Color.Black))
        }
    }
}

@Composable
private fun SpeedValueControl(
    label: String, value: Int, minimum: Int, maximum: Int,
    text: Color, accent: Color, onChange: (Int) -> Unit
) {
    Text(label, color = text)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center) {
        IconButton(enabled = value > minimum, onClick = { onChange((value - 10).coerceAtLeast(minimum)) }) {
            Icon(Icons.Default.Remove, "Decrease $label", tint = accent)
        }
        Text("$value km/h", color = text, modifier = Modifier.padding(horizontal = 12.dp))
        IconButton(enabled = value < maximum, onClick = { onChange((value + 10).coerceAtMost(maximum)) }) {
            Icon(Icons.Default.Add, "Increase $label", tint = accent)
        }
    }
}
