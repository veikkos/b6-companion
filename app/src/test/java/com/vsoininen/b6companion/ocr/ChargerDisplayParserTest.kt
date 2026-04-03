package com.vsoininen.b6companion.ocr

import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ChargerDisplayParserTest {

    private val parser = ChargerDisplayParser()

    @Test
    fun `parses standard SkyRC balance charge display`() {
        val text = "Li4S 3.3A 15.65V\nBAL 002:27 00133"
        val reading = parser.parse(text)

        assertNotNull(reading)
        reading!!
        assertEquals("Li", reading.batteryType)
        assertEquals(4, reading.cellCount)
        assertEquals(3.3, reading.current, 0.01)
        assertEquals(15.65, reading.voltage, 0.01)
        assertEquals("BAL", reading.mode)
        assertEquals(2.minutes + 27.seconds, reading.elapsedTime)
        assertEquals(133, reading.mAhCharged)
    }

    @Test
    fun `parses display with different cell count`() {
        val text = "Li6S 2.0A 24.50V\nCHG 010:15 01250"
        val reading = parser.parse(text)

        assertNotNull(reading)
        reading!!
        assertEquals(6, reading.cellCount)
        assertEquals(2.0, reading.current, 0.01)
        assertEquals(24.50, reading.voltage, 0.01)
        assertEquals("CHG", reading.mode)
        assertEquals(10.minutes + 15.seconds, reading.elapsedTime)
        assertEquals(1250, reading.mAhCharged)
    }

    @Test
    fun `parses display with OCR artifacts and spacing variations`() {
        val text = "Li4S  3.3A  15.65V\n BAL  002:27  00133"
        val reading = parser.parse(text)

        assertNotNull(reading)
        reading!!
        assertEquals(4, reading.cellCount)
        assertEquals(3.3, reading.current, 0.01)
    }

    @Test
    fun `returns null for unrecognizable text`() {
        val text = "Hello World some random text"
        val reading = parser.parse(text)

        assertNull(reading)
    }

    @Test
    fun `parses per cell voltage correctly`() {
        val text = "Li4S 3.3A 15.65V\nBAL 002:27 00133"
        val reading = parser.parse(text)!!

        assertEquals(3.9125, reading.perCellVoltage, 0.001)
    }

    @Test
    fun `parses storage mode`() {
        val text = "Li4S 1.0A 15.20V\nSTO 005:00 00500"
        val reading = parser.parse(text)

        assertNotNull(reading)
        assertEquals("STO", reading!!.mode)
    }

    @Test
    fun `parses real OCR output with artifacts from LCD display`() {
        // Actual ML Kit output from a SKYRC e6650 photo
        val text = """7EVD0 NOT GOVERI
Li45 3.3A 15.65
BAL B82: 27 90133
AC/DC Input. Muiti-Chemistry
Balance Charger/Discharger
SKYRC
SOCK"""
        val reading = parser.parse(text)

        assertNotNull(reading)
        reading!!
        assertEquals("Li", reading.batteryType)
        assertEquals(4, reading.cellCount)
        assertEquals(3.3, reading.current, 0.01)
        assertEquals(15.65, reading.voltage, 0.01)
        assertEquals("BAL", reading.mode)
        // "B82" → fixLeadingZeros → "082" → 82 minutes
        assertEquals(82.minutes + 27.seconds, reading.elapsedTime)
        // "90133" → fixLeadingZeros → "00133" → 133
        assertEquals(133, reading.mAhCharged)
    }

    @Test
    fun `parses when S is read as 5 in battery type`() {
        val text = "Li45 2.0A 12.50V\nCHG 010:15 01250"
        val reading = parser.parse(text)

        assertNotNull(reading)
        reading!!
        assertEquals("Li", reading.batteryType)
        assertEquals(4, reading.cellCount)
    }

    @Test
    fun `parses voltage without V suffix`() {
        val text = "Li4S 3.3A 15.65\nBAL 002:27 00133"
        val reading = parser.parse(text)

        assertNotNull(reading)
        reading!!
        assertEquals(15.65, reading.voltage, 0.01)
    }
}
