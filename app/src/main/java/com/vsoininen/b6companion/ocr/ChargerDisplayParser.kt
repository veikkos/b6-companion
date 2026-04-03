package com.vsoininen.b6companion.ocr

import com.vsoininen.b6companion.model.ChargerReading
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ChargerDisplayParser {

    private val batteryRegex = Regex("""(Li|Ni|Pb)(\d)S""")
    private val currentRegex = Regex("""(\d+\.?\d*)\s*A""")
    private val voltageRegex = Regex("""(\d+\.\d+)\s*V""")
    private val modeRegex = Regex("""\b(BAL|CHG|DIS|STO|FAT|FOR)\b""")
    private val timeRegex = Regex("""(\d{2,3}):(\d{2})""")
    private val capacityRegex = Regex("""(\d{4,5})""")

    fun parse(text: String): ChargerReading? {
        val batteryMatch = batteryRegex.find(text) ?: return null
        val currentMatch = currentRegex.find(text) ?: return null
        val voltageMatch = voltageRegex.find(text) ?: return null
        val modeMatch = modeRegex.find(text)
        val timeMatch = timeRegex.find(text)

        // Find capacity: the standalone number group that isn't part of time or voltage
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

        val minutes = timeMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val seconds = timeMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0

        return ChargerReading(
            batteryType = batteryMatch.groupValues[1],
            cellCount = batteryMatch.groupValues[2].toInt(),
            current = currentMatch.groupValues[1].toDouble(),
            voltage = voltageMatch.groupValues[1].toDouble(),
            mode = modeMatch?.groupValues?.get(1) ?: "CHG",
            elapsedTime = minutes.minutes + seconds.seconds,
            mAhCharged = capacityMatch?.groupValues?.get(1)?.toInt() ?: 0
        )
    }

    private fun IntRange.intersects(other: IntRange): Boolean {
        return this.first <= other.last && other.first <= this.last
    }
}
