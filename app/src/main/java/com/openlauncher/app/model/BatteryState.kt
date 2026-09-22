package com.openlauncher.app.model

data class BatteryState(
    val voltage: Float? = null,
    val chargePercent: Int? = null,
    val highestVoltage: Float? = null,
    val lowestVoltage: Float? = null,
    val connected: Boolean = false,
    val isCharging: Boolean = false,
    val isSettling: Boolean = false
)
