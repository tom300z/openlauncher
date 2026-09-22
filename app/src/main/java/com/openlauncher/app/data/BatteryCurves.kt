package com.openlauncher.app.data

data class VoltagePoint(val volts: Float, val percent: Int)

data class BatteryCurveDefinition(
    val type: BatteryCurve,
    val displayName: String,
    val points: List<VoltagePoint>
)

val BATTERY_CURVES = listOf(
    BatteryCurveDefinition(
        BatteryCurve.FLOODED,
        "Flooded lead-acid",
        listOf(VoltagePoint(11.80f, 0), VoltagePoint(12.00f, 20), VoltagePoint(12.20f, 40),
            VoltagePoint(12.40f, 60), VoltagePoint(12.60f, 80), VoltagePoint(12.75f, 100))
    ),
    BatteryCurveDefinition(
        BatteryCurve.AGM,
        "AGM",
        listOf(VoltagePoint(11.80f, 0), VoltagePoint(12.05f, 20), VoltagePoint(12.30f, 40),
            VoltagePoint(12.50f, 60), VoltagePoint(12.70f, 80), VoltagePoint(12.90f, 100))
    ),
    BatteryCurveDefinition(
        BatteryCurve.EFB,
        "EFB",
        listOf(VoltagePoint(11.80f, 0), VoltagePoint(12.05f, 20), VoltagePoint(12.25f, 40),
            VoltagePoint(12.45f, 60), VoltagePoint(12.65f, 80), VoltagePoint(12.80f, 100))
    ),
    BatteryCurveDefinition(
        BatteryCurve.GEL,
        "Gel lead-acid",
        listOf(VoltagePoint(11.80f, 0), VoltagePoint(12.05f, 20), VoltagePoint(12.30f, 40),
            VoltagePoint(12.50f, 60), VoltagePoint(12.70f, 80), VoltagePoint(12.85f, 100))
    ),
    BatteryCurveDefinition(
        BatteryCurve.LIFEPO4,
        "LiFePO4 (4S)",
        listOf(VoltagePoint(12.00f, 0), VoltagePoint(12.80f, 10), VoltagePoint(13.00f, 20),
            VoltagePoint(13.10f, 40), VoltagePoint(13.20f, 60), VoltagePoint(13.30f, 80),
            VoltagePoint(13.40f, 90), VoltagePoint(13.60f, 100))
    )
)

fun batteryPercent(voltage: Float, curve: BatteryCurve): Int {
    val points = BATTERY_CURVES.first { it.type == curve }.points
    if (voltage <= points.first().volts) return points.first().percent
    if (voltage >= points.last().volts) return points.last().percent
    val upperIndex = points.indexOfFirst { voltage <= it.volts }
    val lower = points[upperIndex - 1]
    val upper = points[upperIndex]
    val fraction = (voltage - lower.volts) / (upper.volts - lower.volts)
    return (lower.percent + fraction * (upper.percent - lower.percent)).toInt().coerceIn(0, 100)
}
