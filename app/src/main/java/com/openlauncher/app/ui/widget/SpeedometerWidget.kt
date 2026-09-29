package com.openlauncher.app.ui.widget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlauncher.app.util.LocationData
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

@Composable
fun SpeedometerWidget(
    location: LocationData?,
    isMetric: Boolean,
    accent: Color,
    isDayMode: Boolean = false,
    digitalOnly: Boolean = false,
    maxKph: Int = 160,
    segmentKph: Int = 20,
    minorTicks: Boolean = true,
    referenceNumbers: Boolean = true,
    modifier: Modifier = Modifier
) {
    val dialMaxKph = maxKph.coerceIn(40, 400)
    val majorStepKph = segmentKph.coerceIn(10, 100)
    val displayFactor = if (isMetric) 1f else 1f / 1.609344f
    val maxSpeed = dialMaxKph * displayFactor
    val speedDisplay = ((location?.speedMps ?: 0f) * if (isMetric) 3.6f else 2.237f).coerceAtLeast(0f)
    val unitLabel    = if (isMetric) "KM/H" else "MPH"
    val trackAlpha   = if (isDayMode) 0.18f else 0.07f
    val tickAlphaMaj = if (isDayMode) 0.50f else 0.28f
    val tickAlphaMin = if (isDayMode) 0.25f else 0.13f

    val contentColor = if (isDayMode) Color(0xFF111111) else MaterialTheme.colorScheme.onBackground
    val subAlpha     = if (isDayMode) 0.55f else 0.32f
    val tickBaseColor = if (isDayMode) Color(0xFF222222) else MaterialTheme.colorScheme.onBackground
    val textMeasurer = rememberTextMeasurer()
    val referenceStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 14.sp,
        color = tickBaseColor.copy(alpha = if (isDayMode) 0.65f else 0.60f)
    )

    Box(
        modifier         = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (digitalOnly) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier            = Modifier.fillMaxSize()
            ) {
                Text(
                    text          = "%.0f".format(speedDisplay),
                    color         = contentColor,
                    fontSize      = 54.sp,
                    fontWeight    = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    letterSpacing = (-1.5).sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text          = unitLabel,
                    color         = contentColor.copy(alpha = subAlpha * 1.5f),
                    fontSize      = 10.sp,
                    fontWeight    = androidx.compose.ui.text.font.FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
        } else {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 24.dp, bottom = 12.dp),
                contentAlignment = Alignment.Center
            ) {
            // The 240-degree arc occupies 2R horizontally and 1.5R vertically;
            // include the rounded stroke caps when fitting it to the widget.
            val radius = minOf(maxWidth / 2.13f, maxHeight / 1.63f)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx    = size.width  / 2f
                val arcR  = radius.toPx()
                val cy    = (size.height + arcR * 0.5f) / 2f
                val trackW = arcR * 0.13f
                val startAngle    = 150f
                val sweepTotal    = 240f
                val progressSweep = (speedDisplay / maxSpeed).coerceIn(0f, 1f) * sweepTotal

                val tl   = Offset(cx - arcR, cy - arcR)
                val sz   = Size(arcR * 2f, arcR * 2f)

                drawArc(
                    color      = contentColor.copy(alpha = trackAlpha),
                    startAngle = startAngle,
                    sweepAngle = sweepTotal,
                    useCenter  = false,
                    topLeft    = tl,
                    size       = sz,
                    style      = Stroke(width = trackW, cap = StrokeCap.Round)
                )

                if (progressSweep > 0.5f) {
                    drawArc(
                        color      = accent,
                        startAngle = startAngle,
                        sweepAngle = progressSweep,
                        useCenter  = false,
                        topLeft    = tl,
                        size       = sz,
                        style      = Stroke(width = trackW, cap = StrokeCap.Round)
                    )
                }

                // Half-interval positions keep minor ticks exactly midway between
                // majors. Always include the scale endpoint, even if the chosen
                // interval does not divide the maximum evenly.
                val halfStepKph = majorStepKph / 2
                val tickSpeeds = (0..dialMaxKph step halfStepKph).toMutableList()
                if (tickSpeeds.last() != dialMaxKph) tickSpeeds.add(dialMaxKph)
                for (tickKph in tickSpeeds) {
                    val isMajor = tickKph % majorStepKph == 0 || tickKph == dialMaxKph
                    if (!isMajor && !minorTicks) continue
                    val angle   = startAngle + tickKph.toFloat() / dialMaxKph * sweepTotal
                    val rad     = Math.toRadians(angle.toDouble())
                    val outerR  = arcR - trackW / 2f - 3.dp.toPx()
                    val innerR  = outerR - if (isMajor) 7.dp.toPx() else 4.dp.toPx()
                    drawLine(
                        color       = tickBaseColor.copy(alpha = if (isMajor) tickAlphaMaj else tickAlphaMin),
                        start       = Offset(cx + (outerR * cos(rad)).toFloat(), cy + (outerR * sin(rad)).toFloat()),
                        end         = Offset(cx + (innerR * cos(rad)).toFloat(), cy + (innerR * sin(rad)).toFloat()),
                        strokeWidth = if (isMajor) 1.5.dp.toPx() else 0.8.dp.toPx()
                    )
                    if (isMajor && referenceNumbers) {
                        val label = textMeasurer.measure(
                            text = (tickKph * displayFactor).roundToInt().toString(),
                            style = referenceStyle
                        )
                        val labelR = innerR - 5.dp.toPx() - label.size.height / 2f
                        drawText(
                            textLayoutResult = label,
                            topLeft = Offset(
                                cx + (labelR * cos(rad)).toFloat() - label.size.width / 2f,
                                cy + (labelR * sin(rad)).toFloat() - label.size.height / 2f
                            )
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier            = Modifier.offset(y = radius / 4f)
            ) {
                Text(
                    text          = "%.0f".format(speedDisplay),
                    color         = contentColor,
                    fontSize      = 34.sp,
                    letterSpacing = (-1).sp
                )
                Text(
                    text          = unitLabel,
                    color         = contentColor.copy(alpha = subAlpha),
                    fontSize      = 8.sp,
                    letterSpacing = 2.sp
                )
            }
            }
        }
    }
}
