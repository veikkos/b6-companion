package com.vsoininen.b6companion.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ChargerReadingTest {

    private fun makeReading(
        current: Double = 3.3,
        elapsedMinutes: Int = 2,
        elapsedSeconds: Int = 27,
        mAh: Int = 133
    ) = ChargerReading(
        batteryType = "Li",
        cellCount = 4,
        current = current,
        voltage = 15.65,
        mode = "BAL",
        elapsedTime = elapsedMinutes.minutes + elapsedSeconds.seconds,
        mAhCharged = mAh
    )

    @Test
    fun `is consistent when delivered mAh matches current x elapsed`() {
        // 3.3A over 2:27 = 135 mAh expected, 133 actual → within 2x
        val reading = makeReading()
        assertTrue(reading.isElapsedConsistent())
    }

    @Test
    fun `is inconsistent when elapsed is much larger than mAhCharged implies`() {
        // OCR misread: 82:27 but only 133 mAh charged at 3.3A.
        // Expected ~4530 mAh, actual 133 → ratio 34x → inconsistent.
        val reading = makeReading(elapsedMinutes = 82)
        assertFalse(reading.isElapsedConsistent())
    }

    @Test
    fun `is consistent when current is zero`() {
        // Can't validate without current; don't flag.
        val reading = makeReading(current = 0.0)
        assertTrue(reading.isElapsedConsistent())
    }

    @Test
    fun `is consistent when mAhCharged is zero`() {
        // Charge just started, nothing delivered yet; don't flag.
        val reading = makeReading(mAh = 0, elapsedSeconds = 5)
        assertTrue(reading.isElapsedConsistent())
    }

    @Test
    fun `is consistent when elapsed is zero`() {
        // Just started; no elapsed to check against.
        val reading = makeReading(elapsedMinutes = 0, elapsedSeconds = 0)
        assertTrue(reading.isElapsedConsistent())
    }
}
