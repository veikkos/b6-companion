package com.vsoininen.b6companion.physics

import com.vsoininen.b6companion.model.ChargerReading
import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ChargingEstimatorTest {

    private val estimator = ChargingEstimator()

    private fun makeReading(
        cellCount: Int = 4,
        current: Double = 3.3,
        voltage: Double = 15.65,
        elapsedMinutes: Int = 2,
        elapsedSeconds: Int = 27,
        mAh: Int = 133
    ) = ChargerReading(
        batteryType = "Li",
        cellCount = cellCount,
        current = current,
        voltage = voltage,
        mode = "BAL",
        elapsedTime = elapsedMinutes.minutes + elapsedSeconds.seconds,
        mAhCharged = mAh
    )

    @Test
    fun `calculates SoC for mid-charge 4S battery`() {
        // 3.91V/cell under 3.3A load maps to ~72% SoC for a healthy cell (~15mΩ IR)
        // and ~55% for a tired cell (~40mΩ IR). Range captures both realistic extremes.
        val reading = makeReading()
        val prediction = estimator.estimate(reading, 2200)
        assertTrue(prediction.estimatedSocPercent > 50.0)
        assertTrue(prediction.estimatedSocPercent < 85.0)
    }

    @Test
    fun `calculates SoC for fully charged battery`() {
        // Full pack voltage AND current tapered to near I_term = truly full.
        // A pack at 4.20V/cell still accepting 3.3A is at CV start, not full.
        val reading = makeReading(voltage = 16.8, current = 0.1)
        val prediction = estimator.estimate(reading, 2200)
        assertEquals(100.0, prediction.estimatedSocPercent, 2.0)
    }

    @Test
    fun `calculates SoC for empty battery`() {
        val reading = makeReading(voltage = 12.0)
        val prediction = estimator.estimate(reading, 2200)
        assertEquals(0.0, prediction.estimatedSocPercent, 1.0)
    }

    @Test
    fun `ETE is positive for mid-charge battery`() {
        val reading = makeReading()
        val prediction = estimator.estimate(reading, 2200)
        assertTrue(prediction.ete.inWholeMinutes > 0)
    }

    @Test
    fun `ETE is near zero for nearly full battery`() {
        // Nearly-full means current has tapered close to I_term (~0.11A for 2200mAh).
        // At I_now=0.3A (~3×I_term), t = tau * ln(2.73) ≈ 5 min.
        val reading = makeReading(voltage = 16.72, current = 0.3, mAh = 1400, elapsedMinutes = 30)
        val prediction = estimator.estimate(reading, 2200)
        assertTrue(prediction.ete.inWholeMinutes < 15)
    }

    @Test
    fun `generates curve points`() {
        val reading = makeReading()
        val prediction = estimator.estimate(reading, 2200)
        assertTrue(prediction.curvePoints.isNotEmpty())
        assertTrue(prediction.curvePoints.first().timeMinutes >= 0.0)
        val lastVoltage = prediction.curvePoints.last().voltage
        assertTrue(lastVoltage >= 4.15)
    }

    @Test
    fun `uses provided battery capacity`() {
        val reading = makeReading(mAh = 500, elapsedMinutes = 10, voltage = 15.0)
        val prediction = estimator.estimate(reading, 2200)
        assertEquals(2200, prediction.estimatedTotalCapacityMah)
    }

    @Test
    fun `handles 6S battery`() {
        val reading = makeReading(cellCount = 6, voltage = 24.0, current = 2.0, mAh = 800)
        val prediction = estimator.estimate(reading, 2200)
        assertTrue(prediction.estimatedSocPercent > 80.0)
        assertTrue(prediction.estimatedSocPercent < 95.0)
    }

    @Test
    fun `CV remaining time scales with current toward I_term, not fixed duration`() {
        // At start of CV (display just at threshold) with 1C charging current,
        // I_term-based model predicts t = tau * ln(I_now / I_term) which is well
        // below the old fixed 25-min ceiling.
        val reading = makeReading(voltage = 4 * 4.18, current = 2.2) // 4S at 4.18V/cell, 1C for 2200mAh
        val prediction = estimator.estimate(reading, 2200)
        assertTrue(
            "CV ETE (${prediction.ete.inWholeMinutes}m) should be below old fixed 25m ceiling",
            prediction.ete.inWholeMinutes < 20
        )
    }

    @Test
    fun `CV ETE is zero when current has dropped below I_term`() {
        // I_term = 0.05C = 110mA for 2200mAh. Current at 50mA means charger has
        // already hit termination - remaining time should be effectively zero.
        val reading = makeReading(voltage = 4 * 4.20, current = 0.05)
        val prediction = estimator.estimate(reading, 2200)
        assertTrue(
            "ETE (${prediction.ete.inWholeMinutes}m) should be near zero at/below I_term",
            prediction.ete.inWholeMinutes <= 1
        )
    }

    @Test
    fun `detects CV phase from displayed voltage even when IR drop pushes OCV below 4_15`() {
        // Small-capacity cell at high C-rate: IR drop is large enough to drag the
        // IR-corrected OCV below the 4.15V CV threshold even though the charger is
        // clearly clamped at 4.20V display (CV phase in progress).
        // Phase detection must use the display voltage, not the synthesized OCV.
        val reading = makeReading(cellCount = 3, voltage = 12.60, current = 3.0, mAh = 400)
        val prediction = estimator.estimate(reading, 500)
        // If CV is correctly detected, the curve starts in the CV plateau (>=4.15V).
        // If the old OCV-based check is used, the curve starts at the CC voltage (~4.02V).
        val firstVoltage = prediction.curvePoints.first().voltage
        assertTrue(
            "First curve point should be in CV plateau, was $firstVoltage",
            firstVoltage >= 4.15
        )
    }

    @Test
    fun `uses higher internal resistance for smaller battery capacity`() {
        // Same reading, different capacities → IR should scale inversely with capacity.
        // A small cell has higher IR → larger voltage drop → lower inferred OCV → lower SoC.
        // Voltage 3.80V/cell is on the sloped part of the SoC curve so differences are observable.
        val reading = makeReading(cellCount = 3, voltage = 11.40, current = 2.0, mAh = 100)
        val predictionSmall = estimator.estimate(reading, 500)
        val predictionLarge = estimator.estimate(reading, 5000)
        assertTrue(
            "Small-cell SoC (${predictionSmall.estimatedSocPercent}) should be meaningfully lower " +
                "than large-cell SoC (${predictionLarge.estimatedSocPercent}) due to higher IR",
            predictionLarge.estimatedSocPercent - predictionSmall.estimatedSocPercent > 5.0
        )
    }
}
