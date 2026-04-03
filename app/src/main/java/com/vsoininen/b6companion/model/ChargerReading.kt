package com.vsoininen.b6companion.model

import kotlin.time.Duration

data class ChargerReading(
    val batteryType: String,       // "Li", "Ni", "Pb"
    val cellCount: Int,            // e.g. 4
    val current: Double,           // amps, e.g. 3.3
    val voltage: Double,           // volts, e.g. 15.65
    val mode: String,              // "BAL", "CHG", "DIS", "STO"
    val elapsedTime: Duration,     // e.g. 2m 27s
    val mAhCharged: Int            // e.g. 133
) {
    val perCellVoltage: Double get() = if (cellCount > 0) voltage / cellCount else 0.0
}
