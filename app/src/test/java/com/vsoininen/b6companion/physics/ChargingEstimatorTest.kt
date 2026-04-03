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
        val reading = makeReading()
        val prediction = estimator.estimate(reading)
        assertTrue(prediction.estimatedSocPercent > 70.0)
        assertTrue(prediction.estimatedSocPercent < 90.0)
    }

    @Test
    fun `calculates SoC for fully charged battery`() {
        val reading = makeReading(voltage = 16.8)
        val prediction = estimator.estimate(reading)
        assertEquals(100.0, prediction.estimatedSocPercent, 1.0)
    }

    @Test
    fun `calculates SoC for empty battery`() {
        val reading = makeReading(voltage = 12.0)
        val prediction = estimator.estimate(reading)
        assertEquals(0.0, prediction.estimatedSocPercent, 1.0)
    }

    @Test
    fun `ETE is positive for mid-charge battery`() {
        val reading = makeReading()
        val prediction = estimator.estimate(reading)
        assertTrue(prediction.ete.inWholeMinutes > 0)
    }

    @Test
    fun `ETE is near zero for nearly full battery`() {
        val reading = makeReading(voltage = 16.72, mAh = 1400, elapsedMinutes = 30)
        val prediction = estimator.estimate(reading)
        assertTrue(prediction.ete.inWholeMinutes < 15)
    }

    @Test
    fun `generates curve points`() {
        val reading = makeReading()
        val prediction = estimator.estimate(reading)
        assertTrue(prediction.curvePoints.isNotEmpty())
        assertTrue(prediction.curvePoints.first().timeMinutes >= 0.0)
        val lastVoltage = prediction.curvePoints.last().voltage
        assertTrue(lastVoltage >= 4.15)
    }

    @Test
    fun `estimates reasonable total capacity`() {
        val reading = makeReading(mAh = 500, elapsedMinutes = 10, voltage = 15.0)
        val prediction = estimator.estimate(reading)
        assertTrue(prediction.estimatedTotalCapacityMah > 500)
        assertTrue(prediction.estimatedTotalCapacityMah < 10000)
    }

    @Test
    fun `handles 6S battery`() {
        val reading = makeReading(cellCount = 6, voltage = 24.0, current = 2.0, mAh = 800)
        val prediction = estimator.estimate(reading)
        assertTrue(prediction.estimatedSocPercent > 80.0)
        assertTrue(prediction.estimatedSocPercent < 95.0)
    }
}
