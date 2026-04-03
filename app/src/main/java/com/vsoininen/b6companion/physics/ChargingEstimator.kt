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

    fun estimate(reading: ChargerReading): ChargingPrediction {
        val cellVoltage = reading.perCellVoltage.coerceIn(3.0, 4.2)
        val currentSoc = voltageToSoc(cellVoltage)
        val totalCapacity = estimateTotalCapacity(reading, currentSoc)
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

    private fun estimateTotalCapacity(reading: ChargerReading, currentSoc: Double): Int {
        val elapsedMinutes = reading.elapsedTime.inWholeSeconds / 60.0
        if (reading.mAhCharged > 0 && elapsedMinutes > 0.5) {
            val chargeRateMahPerMin = reading.mAhCharged / elapsedMinutes
            if (chargeRateMahPerMin > 0 && currentSoc < 95) {
                val assumedStartSoc = max(0.0, currentSoc - (reading.mAhCharged.toDouble() / (reading.current * 1000) * 100))
                    .coerceIn(0.0, currentSoc - 1.0)
                val socDelta = currentSoc - max(0.0, assumedStartSoc)
                if (socDelta > 0.5) {
                    return ((reading.mAhCharged / socDelta) * 100).toInt().coerceIn(100, 10000)
                }
            }
        }
        return when (reading.cellCount) {
            1 -> 1000
            2 -> 1300
            3 -> 1500
            4 -> 1500
            5 -> 2200
            6 -> 2200
            else -> 1500
        }
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
        val points = mutableListOf<CurvePoint>()
        val elapsedMinutes = reading.elapsedTime.inWholeSeconds / 60.0
        val totalMinutes = elapsedMinutes + eteMinutes
        val steps = 50

        for (i in 0..steps) {
            val t = (totalMinutes * i) / steps
            val progress = if (totalMinutes > 0) t / totalMinutes else 1.0

            val ccEndProgress = if (totalMinutes > 0 && eteMinutes > 0) {
                val ccDuration = if (currentCellVoltage < cvTransitionVoltage) {
                    totalMinutes - cvDurationMinutes
                } else {
                    elapsedMinutes * 0.7
                }
                (ccDuration / totalMinutes).coerceIn(0.0, 1.0)
            } else 0.5

            val voltage: Double
            val current: Double

            if (progress <= ccEndProgress) {
                val startVoltage = 3.5
                voltage = startVoltage + (cvTransitionVoltage - startVoltage) * (progress / ccEndProgress)
                current = reading.current
            } else {
                voltage = cvTransitionVoltage + (fullVoltage - cvTransitionVoltage) *
                    ((progress - ccEndProgress) / (1.0 - ccEndProgress)).coerceIn(0.0, 1.0)
                val cvProgress = ((progress - ccEndProgress) / (1.0 - ccEndProgress)).coerceIn(0.0, 1.0)
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
