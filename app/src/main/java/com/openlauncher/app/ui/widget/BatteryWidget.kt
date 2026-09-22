package com.openlauncher.app.ui.widget

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlauncher.app.model.BatteryState
import java.util.Locale

@Composable
fun BatteryWidget(
    state: BatteryState,
    historyDays: Int,
    isDayMode: Boolean,
    modifier: Modifier = Modifier
) {
    val secondary = if (isDayMode) Color(0xFF6C757D) else Color(0xFF777777)
    val voltageText = if (isDayMode) Color(0xFF555555) else Color(0xFFB8B8B8)
    val outline = if (isDayMode) Color(0xFF495057) else Color(0xFFAAAAAA)
    val fill = if (state.isCharging || state.isSettling) Color(0xFF00CFE8) else Color(0xFF39B54A)
    val percent = state.chargePercent
    val targetFillFraction = if (state.isCharging) 1f else ((percent ?: 0) / 100f).coerceIn(0f, 1f)
    val fillFraction by animateFloatAsState(
        targetValue = targetFillFraction,
        animationSpec = tween(durationMillis = 900),
        label = "batteryFill"
    )

    Row(
        modifier = modifier.padding(start = 14.dp, top = 18.dp, end = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(end = 10.dp),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            StatLabel("HIGHEST · $historyDays DAYS", state.highestVoltage, secondary)
            StatLabel("LOWEST · $historyDays DAYS", state.lowestVoltage, secondary)
        }

        Box(
            modifier = Modifier.fillMaxHeight().aspectRatio(0.64f),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val terminalHeight = size.height * 0.055f
                val terminalWidth = size.width * 0.20f
                val bodyTop = terminalHeight + size.height * 0.035f
                val bodyHeight = size.height - bodyTop
                val radius = size.width * 0.12f
                val bodyPath = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = 1f,
                            top = bodyTop,
                            right = size.width - 1f,
                            bottom = size.height - 1f,
                            cornerRadius = CornerRadius(radius, radius)
                        )
                    )
                }
                val fraction = fillFraction
                clipPath(bodyPath) {
                    val fillTop = bodyTop + bodyHeight * (1f - fraction)
                    drawRect(
                        color = fill.copy(alpha = if (percent == null) 0.12f else 0.82f),
                        topLeft = Offset(1f, fillTop),
                        size = Size(size.width - 2f, size.height - fillTop)
                    )
                }
                drawPath(bodyPath, outline, style = Stroke(width = 2.dp.toPx()))
                val terminalInset = size.width * 0.13f
                drawRoundRect(
                    color = outline,
                    topLeft = Offset(terminalInset, 0f),
                    size = Size(terminalWidth, terminalHeight),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
                drawRoundRect(
                    color = outline,
                    topLeft = Offset(size.width - terminalInset - terminalWidth, 0f),
                    size = Size(terminalWidth, terminalHeight),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            ) {
                Text(
                    text = when {
                        state.isCharging -> "Charging"
                        state.isSettling -> "Settling"
                        else -> percent?.let { "$it%" } ?: "—"
                    },
                    color = if (isDayMode) Color(0xFF111111) else Color.White,
                    fontSize = if (state.isCharging || state.isSettling) 19.sp else 28.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = state.voltage?.let { String.format(Locale.US, "%.2fV", it) }
                        ?: if (state.connected) "SAMPLING…" else "NO MCU",
                    color = voltageText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun StatLabel(label: String, voltage: Float?, color: Color) {
    Column {
        Text(
            text = label,
            color = color.copy(alpha = 0.75f),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 7.sp,
            letterSpacing = 0.7.sp,
            maxLines = 1
        )
        Text(
            text = voltage?.let { String.format(Locale.US, "%.2f V", it) } ?: "—",
            color = color,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
