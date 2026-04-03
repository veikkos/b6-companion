package com.vsoininen.b6companion.physics

import com.vsoininen.b6companion.model.ChargerReading
import com.vsoininen.b6companion.model.ChargingPrediction
import com.vsoininen.b6companion.model.CurvePoint
import java.time.LocalDateTime
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.minutes

class ChargingEstimator {

    private val voltageSocTable = listOf(
        3.00 to 0.0,
        3.30 to 5.0,
        3.50 to 15.0,
        3.60 to 25.0,
        3.70 to 40.0,
        3.75 to 50.0,
        3.80 to 60.0,
        3.85 to 70.0,
        3.90 to 80.0,
        4.00 to 90.0,
        4.10 to 95.0,
        4.20 to 100.0
    )

    private val cvTransitionVoltage = 4.15
    private val fullVoltage = 4.20
    private val cvDurationMinutes = 25.0

    companion object {
        // Estimated per-cell internal resistance (ohms).
        // Used to approximate OCV from the under-load charging voltage.
        // Typical range: 3–25mΩ depending on cell capacity and age.
        // 15mΩ is a reasonable default for a ~2000–3000mAh LiPo cell.
        const val ESTIMATED_CELL_IR_OHMS = 0.015
    }

    fun estimate(reading: ChargerReading, batteryCapacityMah: Int): ChargingPrediction {
        val irDrop = reading.current * ESTIMATED_CELL_IR_OHMS
        val cellVoltage = (reading.perCellVoltage - irDrop).coerceIn(3.0, 4.2)
        val currentSoc = voltageToSoc(cellVoltage)
        val totalCapacity = batteryCapacityMah
        val eteMinutes = estimateEte(reading, cellVoltage, currentSoc, totalCapacity)
        val ete = max(0.0, eteMinutes).minutes
        val eta = LocalDateTime.now().plusMinutes(ete.inWholeMinutes)
        val curvePoints = generateCurve(reading, cellVoltage, eteMinutes)

        return ChargingPrediction(
            estimatedSocPercent = currentSoc,
            estimatedTotalCapacityMah = totalCapacity,
            ete = ete,
            eta = eta,
            curvePoints = curvePoints
        )
    }

    private fun voltageToSoc(cellVoltage: Double): Double {
        if (cellVoltage <= voltageSocTable.first().first) return 0.0
        if (cellVoltage >= voltageSocTable.last().first) return 100.0

        for (i in 0 until voltageSocTable.size - 1) {
            val (v1, s1) = voltageSocTable[i]
            val (v2, s2) = voltageSocTable[i + 1]
            if (cellVoltage in v1..v2) {
                val ratio = (cellVoltage - v1) / (v2 - v1)
                return s1 + ratio * (s2 - s1)
            }
        }
        return 0.0
    }

    private fun socToVoltage(soc: Double): Double {
        if (soc <= 0.0) return voltageSocTable.first().first
        if (soc >= 100.0) return voltageSocTable.last().first

        for (i in 0 until voltageSocTable.size - 1) {
            val (v1, s1) = voltageSocTable[i]
            val (v2, s2) = voltageSocTable[i + 1]
            if (soc in s1..s2) {
                val ratio = (soc - s1) / (s2 - s1)
                return v1 + ratio * (v2 - v1)
            }
        }
        return voltageSocTable.last().first
    }

    private fun estimateEte(
        reading: ChargerReading,
        cellVoltage: Double,
        currentSoc: Double,
        totalCapacity: Int
    ): Double {
        if (currentSoc >= 99.5) return 0.0

        return if (cellVoltage < cvTransitionVoltage) {
            val ccRemainingMah = totalCapacity * (voltageToSoc(cvTransitionVoltage) - currentSoc) / 100.0
            val ccTimeMinutes = if (reading.current > 0) {
                (ccRemainingMah / (reading.current * 1000)) * 60
            } else 0.0
            ccTimeMinutes + cvDurationMinutes
        } else {
            val cvProgress = (cellVoltage - cvTransitionVoltage) / (fullVoltage - cvTransitionVoltage)
            cvDurationMinutes * (1.0 - cvProgress.coerceIn(0.0, 1.0))
        }
    }

    private fun generateCurve(
        reading: ChargerReading,
        currentCellVoltage: Double,
        eteMinutes: Double
    ): List<CurvePoint> {
        if (eteMinutes <= 0) return emptyList()

        val points = mutableListOf<CurvePoint>()
        val steps = 50

        // How much of the remaining time is CC vs CV
        val ccMinutes: Double
        val cvMinutes: Double
        if (currentCellVoltage < cvTransitionVoltage) {
            // Still in CC phase — need to reach cvTransitionVoltage, then CV
            val ccRemainingFraction =
                (cvTransitionVoltage - currentCellVoltage) / (cvTransitionVoltage - 3.0)
            ccMinutes = max(0.0, eteMinutes - cvDurationMinutes)
            cvMinutes = cvDurationMinutes
        } else {
            // Already in CV phase
            ccMinutes = 0.0
            cvMinutes = eteMinutes
        }

        for (i in 0..steps) {
            val t = (eteMinutes * i) / steps
            val voltage: Double
            val current: Double

            if (ccMinutes > 0 && t <= ccMinutes) {
                // CC phase: voltage ramps from current to cvTransitionVoltage
                val ccProgress = t / ccMinutes
                voltage = currentCellVoltage + (cvTransitionVoltage - currentCellVoltage) * ccProgress
                current = reading.current
            } else {
                // CV phase: voltage ramps from cvTransitionVoltage to fullVoltage
                val cvTime = if (ccMinutes > 0) t - ccMinutes else t
                val cvProgress = (cvTime / cvMinutes).coerceIn(0.0, 1.0)
                val cvStart = if (currentCellVoltage >= cvTransitionVoltage) currentCellVoltage else cvTransitionVoltage
                voltage = cvStart + (fullVoltage - cvStart) * cvProgress
                current = reading.current * exp(-3.0 * cvProgress)
            }

            points.add(CurvePoint(
                timeMinutes = t,
                voltage = min(fullVoltage, voltage),
                current = max(0.05, current)
            ))
        }

        return points
    }
}
