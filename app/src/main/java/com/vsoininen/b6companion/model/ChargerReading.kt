package com.vsoininen.b6companion.model

import kotlin.math.max
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

    /**
     * Cross-checks whether the reported elapsed time is physically consistent with
     * the amount of charge delivered at the displayed current. If OCR misreads a
     * digit of the time (e.g. "002" → "082"), the two quantities will disagree by
     * a wide margin. Returns true when the values agree within [toleranceFactor]x,
     * or when one side is unavailable (charge just started, current reads zero, etc.).
     */
    fun isElapsedConsistent(toleranceFactor: Double = 2.0): Boolean {
        if (current <= 0) return true
        if (elapsedTime.inWholeSeconds <= 0) return true
        if (mAhCharged <= 0) return true
        val expectedMah = current * elapsedTime.inWholeSeconds / 3600.0 * 1000.0
        if (expectedMah <= 0) return true
        val ratio = max(expectedMah / mAhCharged, mAhCharged.toDouble() / expectedMah)
        return ratio <= toleranceFactor
    }
}
