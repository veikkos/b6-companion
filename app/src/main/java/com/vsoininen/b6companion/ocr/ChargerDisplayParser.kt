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

    // Non-digit chars that LCD-0 is often misread as (unambiguous misreads)
    private val nonDigitZeroChars = setOf('O', 'D', 'B', 'G')
    // Digit chars that can be misread-0 on 7-segment LCDs. Only replace these
    // when the next char is already '0' (padding context), since otherwise they
    // are likely real digits.
    private val ambiguousDigitZeroChars = setOf('8', '9')

    fun parse(rawText: String): ChargerReading? {
        val text = normalizeLcdMisreads(rawText)
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
     * LCD-segment '0' is often OCR'd as O/D/B/G. When such a character sits
     * sandwiched between two digit/dot characters (e.g. "12.B2" or "00B6"),
     * it is almost certainly a misread '0'. Replace it in-place so downstream
     * regexes can match the numeric fields. Leading-position misreads are
     * handled separately by [fixLeadingZeros].
     */
    private fun normalizeLcdMisreads(text: String): String {
        if (text.length < 3) return text
        val out = text.toCharArray()
        for (i in 1 until out.size - 1) {
            if (out[i].uppercaseChar() in nonDigitZeroChars &&
                isDigitOrDot(out[i - 1]) &&
                isDigitOrDot(out[i + 1])
            ) {
                out[i] = '0'
            }
        }
        return String(out)
    }

    private fun isDigitOrDot(c: Char): Boolean = c.isDigit() || c == '.'

    /**
     * LCD '0' can be OCR'd as 'O'/'D'/'B' (non-digits, unambiguous) or as '8'/'9'
     * (real digits, ambiguous). Non-digit candidates are always replaced.
     * Ambiguous digit candidates are only replaced when followed by a real '0'
     * — that pattern indicates padding, where the leading digit is more likely
     * a misread zero than a real value.
     */
    private fun fixLeadingZeros(s: String): String {
        val chars = s.toCharArray()
        for (i in chars.indices) {
            val upper = chars[i].uppercaseChar()
            when {
                upper in nonDigitZeroChars -> chars[i] = '0'
                upper in ambiguousDigitZeroChars && i + 1 < chars.size && chars[i + 1] == '0' ->
                    chars[i] = '0'
                else -> return String(chars)
            }
        }
        return String(chars)
    }

    private fun IntRange.intersects(other: IntRange): Boolean {
        return this.first <= other.last && other.first <= this.last
    }
}
