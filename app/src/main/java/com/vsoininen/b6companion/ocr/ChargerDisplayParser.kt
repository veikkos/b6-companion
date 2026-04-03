package com.vsoininen.b6companion.ocr

import com.vsoininen.b6companion.model.ChargerReading
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ChargerDisplayParser {

    // OCR often misreads 'S' as '5' or '$' on LCD displays
    private val batteryRegex = Regex("""(Li|Ni|Pb)(\d)[S5s$]""")
    // Current: number followed by A
    private val currentRegex = Regex("""(\d+\.?\d*)\s*A""")
    // Voltage: decimal number optionally followed by V (OCR often drops the V)
    private val voltageRegex = Regex("""(\d+\.\d+)\s*V?""")
    // Mode: OCR may misread B as 8, so also accept common OCR variants
    private val modeRegex = Regex("""\b(BAL|CHG|DIS|STO|FAT|FOR)\b""")
    // Time: OCR often misreads 0 as O/B/8/D, so accept any char before digits
    private val timeRegex = Regex("""(\w{2,3}):[\s]?(\d{2})""")
    private val capacityRegex = Regex("""(\d{3,5})""")

    // LCD segment '0' is often misread as 8, 9, O, D, B by OCR
    private val lcdZeroChars = setOf('8', '9', 'O', 'D', 'B')

    fun parse(text: String): ChargerReading? {
        val batteryMatch = batteryRegex.find(text) ?: return null
        val currentMatch = currentRegex.find(text) ?: return null

        // Voltage must be a different match than current — find decimal numbers
        // that aren't part of the current match (which has 'A' suffix)
        val voltageMatch = voltageRegex.findAll(text)
            .filter { !it.range.intersects(currentMatch.range) }
            .firstOrNull() ?: return null

        val modeMatch = modeRegex.find(text)
        val timeMatch = timeRegex.find(text)

        // Find capacity: the standalone number group that isn't part of other matches
        val usedRanges = listOfNotNull(
            batteryMatch.range,
            currentMatch.range,
            voltageMatch.range,
            modeMatch?.range,
            timeMatch?.range
        )
        val capacityMatch = capacityRegex.findAll(text)
            .filter { match -> usedRanges.none { it.intersects(match.range) } }
            .lastOrNull()

        // Time group 1 may contain OCR artifacts (e.g. "B82" for "002"),
        // so fix misread leading zeros then extract digits
        val minutesRaw = timeMatch?.groupValues?.get(1)
            ?.let { fixLeadingZeros(it) }
            ?.filter { it.isDigit() }
        val minutes = minutesRaw?.toIntOrNull() ?: 0
        val seconds = timeMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0

        // Fix capacity leading zeros (e.g. "90133" → "00133" → 133)
        val rawCapacity = capacityMatch?.groupValues?.get(1) ?: "0"
        val fixedCapacity = fixLeadingZeros(rawCapacity).toIntOrNull() ?: 0

        return ChargerReading(
            batteryType = batteryMatch.groupValues[1],
            cellCount = batteryMatch.groupValues[2].toInt(),
            current = currentMatch.groupValues[1].toDouble(),
            voltage = voltageMatch.groupValues[1].toDouble(),
            mode = modeMatch?.groupValues?.get(1) ?: "CHG",
            elapsedTime = minutes.minutes + seconds.seconds,
            mAhCharged = fixedCapacity
        )
    }

    /**
     * LCD segment '0' is often misread by OCR as 8, 9, O, D, B.
     * Replace leading occurrences of these chars with '0'.
     * Stops at the first char that looks like a real non-zero digit.
     */
    private fun fixLeadingZeros(s: String): String {
        val chars = s.toCharArray()
        for (i in chars.indices) {
            if (chars[i].uppercaseChar() in lcdZeroChars) {
                chars[i] = '0'
            } else {
                break
            }
        }
        return String(chars)
    }

    private fun IntRange.intersects(other: IntRange): Boolean {
        return this.first <= other.last && other.first <= this.last
    }
}
