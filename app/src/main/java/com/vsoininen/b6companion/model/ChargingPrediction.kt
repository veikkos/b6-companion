package com.vsoininen.b6companion.model

import java.time.LocalDateTime
import kotlin.time.Duration

data class ChargingPrediction(
    val estimatedSocPercent: Double,
    val estimatedTotalCapacityMah: Int,
    val ete: Duration,
    val eta: LocalDateTime,
    val curvePoints: List<CurvePoint>
)

data class CurvePoint(
    val timeMinutes: Double,
    val voltage: Double,
    val current: Double
)
