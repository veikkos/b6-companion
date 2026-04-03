# B6 Companion Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an Android app that OCRs a SkyRC balance charger display photo and shows parsed values with LiPo charging predictions.

**Architecture:** Single-activity Compose app with ViewModel. ML Kit for on-device OCR, regex-based parser for SkyRC display format, LiPo physics engine for charging predictions, Vico for charting.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, ML Kit Text Recognition, Coil, Vico, ActivityResult APIs

---

## File Structure

```
app/src/main/java/com/vsoininen/b6companion/
├── MainActivity.kt                          (modify — wire up ViewModel + UI)
├── MainViewModel.kt                         (create — state management, OCR orchestration)
├── model/
│   ├── ChargerReading.kt                    (create — parsed display data class)
│   └── ChargingPrediction.kt               (create — prediction + curve data classes)
├── ocr/
│   └── ChargerDisplayParser.kt             (create — ML Kit text → ChargerReading)
├── physics/
│   └── ChargingEstimator.kt                (create — LiPo physics, ETE/ETA calculation)
└── ui/
    ├── MainScreen.kt                        (create — top-level screen composable)
    ├── ImageSection.kt                      (create — photo display + placeholder)
    ├── ValuesSection.kt                     (create — parsed values cards grid)
    ├── PredictionCard.kt                    (create — ETE/ETA/SoC highlight card)
    ├── ChargingCurveChart.kt               (create — Vico line chart)
    └── theme/                               (existing — no changes)

app/src/main/AndroidManifest.xml             (modify — add CAMERA permission)
app/build.gradle.kts                         (modify — add dependencies)
gradle/libs.versions.toml                    (modify — add version catalog entries)

app/src/test/java/com/vsoininen/b6companion/
├── ocr/
│   └── ChargerDisplayParserTest.kt          (create — parser unit tests)
└── physics/
    └── ChargingEstimatorTest.kt             (create — estimator unit tests)
```

---

### Task 1: Add Dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add version catalog entries**

Add to `gradle/libs.versions.toml`:

```toml
[versions]
# ... existing entries ...
mlkitTextRecognition = "16.0.1"
coil = "3.1.0"
vicoCompose = "2.1.0-beta.1"

[libraries]
# ... existing entries ...
mlkit-text-recognition = { group = "com.google.mlkit", name = "text-recognition", version.ref = "mlkitTextRecognition" }
coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }
vico-compose-m3 = { group = "com.patrykandpatrick.vico", name = "compose-m3", version.ref = "vicoCompose" }
```

- [ ] **Step 2: Add dependencies to app build**

Add to `app/build.gradle.kts` in the `dependencies` block:

```kotlin
implementation(libs.mlkit.text.recognition)
implementation(libs.coil.compose)
implementation(libs.vico.compose.m3)
```

- [ ] **Step 3: Add camera permission to manifest**

Add to `app/src/main/AndroidManifest.xml` inside `<manifest>`, before `<application>`:

```xml
<uses-feature android:name="android.hardware.camera" android:required="false" />
<uses-permission android:name="android.permission.CAMERA" />
```

- [ ] **Step 4: Sync and verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml
git commit -m "feat: add ML Kit, Coil, Vico dependencies and camera permission"
```

---

### Task 2: Data Models

**Files:**
- Create: `app/src/main/java/com/vsoininen/b6companion/model/ChargerReading.kt`
- Create: `app/src/main/java/com/vsoininen/b6companion/model/ChargingPrediction.kt`

- [ ] **Step 1: Create ChargerReading data class**

Create `app/src/main/java/com/vsoininen/b6companion/model/ChargerReading.kt`:

```kotlin
package com.vsoininen.b6companion.model

import kotlin.time.Duration

data class ChargerReading(
    val batteryType: String,       // "Li", "Ni", "Pb"
    val cellCount: Int,            // e.g. 4
    val current: Double,           // amps, e.g. 3.3
    val voltage: Double,           // volts, e.g. 15.65
    val mode: String,              // "BAL", "CHG", "DIS", "STO"
    val elapsedTime: Duration,     // e.g. 2m 27s
    val mAhCharged: Int            // e.g. 133
) {
    val perCellVoltage: Double get() = if (cellCount > 0) voltage / cellCount else 0.0
}
```

- [ ] **Step 2: Create ChargingPrediction data class**

Create `app/src/main/java/com/vsoininen/b6companion/model/ChargingPrediction.kt`:

```kotlin
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
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/vsoininen/b6companion/model/
git commit -m "feat: add ChargerReading and ChargingPrediction data models"
```

---

### Task 3: Charger Display Parser

**Files:**
- Create: `app/src/test/java/com/vsoininen/b6companion/ocr/ChargerDisplayParserTest.kt`
- Create: `app/src/main/java/com/vsoininen/b6companion/ocr/ChargerDisplayParser.kt`

- [ ] **Step 1: Write parser unit tests**

Create `app/src/test/java/com/vsoininen/b6companion/ocr/ChargerDisplayParserTest.kt`:

```kotlin
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
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.vsoininen.b6companion.ocr.ChargerDisplayParserTest" --info`
Expected: FAIL — class not found

- [ ] **Step 3: Implement ChargerDisplayParser**

Create `app/src/main/java/com/vsoininen/b6companion/ocr/ChargerDisplayParser.kt`:

```kotlin
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.vsoininen.b6companion.ocr.ChargerDisplayParserTest" --info`
Expected: All 6 tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/vsoininen/b6companion/ocr/ app/src/test/java/com/vsoininen/b6companion/ocr/
git commit -m "feat: add ChargerDisplayParser with SkyRC display format support"
```

---

### Task 4: Charging Estimator (LiPo Physics)

**Files:**
- Create: `app/src/test/java/com/vsoininen/b6companion/physics/ChargingEstimatorTest.kt`
- Create: `app/src/main/java/com/vsoininen/b6companion/physics/ChargingEstimator.kt`

- [ ] **Step 1: Write estimator unit tests**

Create `app/src/test/java/com/vsoininen/b6companion/physics/ChargingEstimatorTest.kt`:

```kotlin
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
        // 15.65V / 4 = 3.9125V per cell — should be around 80% SoC
        val reading = makeReading()
        val prediction = estimator.estimate(reading)

        assertTrue(prediction.estimatedSocPercent > 70.0)
        assertTrue(prediction.estimatedSocPercent < 90.0)
    }

    @Test
    fun `calculates SoC for fully charged battery`() {
        // 4.2V * 4 = 16.8V
        val reading = makeReading(voltage = 16.8)
        val prediction = estimator.estimate(reading)

        assertEquals(100.0, prediction.estimatedSocPercent, 1.0)
    }

    @Test
    fun `calculates SoC for empty battery`() {
        // 3.0V * 4 = 12.0V
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
        // 4.18V * 4 = 16.72V, high mAh charged
        val reading = makeReading(voltage = 16.72, mAh = 1400, elapsedMinutes = 30)
        val prediction = estimator.estimate(reading)

        assertTrue(prediction.ete.inWholeMinutes < 15)
    }

    @Test
    fun `generates curve points`() {
        val reading = makeReading()
        val prediction = estimator.estimate(reading)

        assertTrue(prediction.curvePoints.isNotEmpty())
        // First point should be near start voltage
        assertTrue(prediction.curvePoints.first().timeMinutes >= 0.0)
        // Last point should be near 4.2V/cell
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
        // 6S at 24.0V = 4.0V/cell
        val reading = makeReading(cellCount = 6, voltage = 24.0, current = 2.0, mAh = 800)
        val prediction = estimator.estimate(reading)

        assertTrue(prediction.estimatedSocPercent > 80.0)
        assertTrue(prediction.estimatedSocPercent < 95.0)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.vsoininen.b6companion.physics.ChargingEstimatorTest" --info`
Expected: FAIL — class not found

- [ ] **Step 3: Implement ChargingEstimator**

Create `app/src/main/java/com/vsoininen/b6companion/physics/ChargingEstimator.kt`:

```kotlin
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

    // LiPo per-cell voltage to SoC% mapping
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

    private val cvTransitionVoltage = 4.15  // per cell, CC→CV transition
    private val fullVoltage = 4.20          // per cell
    private val cvDurationMinutes = 25.0    // typical CV phase duration

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
            // Estimate start SoC from charge rate: mAh charged maps to SoC delta
            // Try to derive: startSoC = currentSoC - (mAhCharged / totalCap * 100)
            // We need totalCap, so use charge rate to estimate:
            // chargeRate = mAhCharged / elapsedMinutes (mAh/min)
            // Remaining to 100% SoC at current rate gives us a rough capacity
            val chargeRateMahPerMin = reading.mAhCharged / elapsedMinutes
            if (chargeRateMahPerMin > 0 && currentSoc < 95) {
                // Rough: assume linear charge for CC phase
                // socDelta ≈ mAhCharged / totalCap * 100
                // We assume storage voltage start (~3.85V = 70% SoC) as default
                val assumedStartSoc = max(0.0, currentSoc - (reading.mAhCharged.toDouble() / (reading.current * 1000) * 100))
                    .coerceIn(0.0, currentSoc - 1.0)
                val socDelta = currentSoc - max(0.0, assumedStartSoc)
                if (socDelta > 0.5) {
                    return ((reading.mAhCharged / socDelta) * 100).toInt().coerceIn(100, 10000)
                }
            }
        }
        // Fallback: typical capacity based on cell count
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

        val remainingMah = totalCapacity * (100.0 - currentSoc) / 100.0

        return if (cellVoltage < cvTransitionVoltage) {
            // CC phase: time to reach CV transition + CV phase time
            val ccRemainingMah = totalCapacity * (voltageToSoc(cvTransitionVoltage) - currentSoc) / 100.0
            val ccTimeMinutes = if (reading.current > 0) {
                (ccRemainingMah / (reading.current * 1000)) * 60
            } else 0.0
            ccTimeMinutes + cvDurationMinutes
        } else {
            // Already in CV phase: exponential decay model
            // Current tapers — estimate remaining time from position in CV phase
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

            // Simple model: voltage rises linearly in CC, flattens in CV
            val voltage: Double
            val current: Double

            val ccEndProgress = if (totalMinutes > 0 && eteMinutes > 0) {
                val ccDuration = if (currentCellVoltage < cvTransitionVoltage) {
                    totalMinutes - cvDurationMinutes
                } else {
                    elapsedMinutes * 0.7  // already past CC
                }
                (ccDuration / totalMinutes).coerceIn(0.0, 1.0)
            } else 0.5

            if (progress <= ccEndProgress) {
                // CC phase
                val startVoltage = 3.5  // typical start
                voltage = startVoltage + (cvTransitionVoltage - startVoltage) * (progress / ccEndProgress)
                current = reading.current
            } else {
                // CV phase
                voltage = cvTransitionVoltage + (fullVoltage - cvTransitionVoltage) *
                    ((progress - ccEndProgress) / (1.0 - ccEndProgress)).coerceIn(0.0, 1.0)
                val cvProgress = ((progress - ccEndProgress) / (1.0 - ccEndProgress)).coerceIn(0.0, 1.0)
                current = reading.current * exp(-3.0 * cvProgress)  // exponential taper
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.vsoininen.b6companion.physics.ChargingEstimatorTest" --info`
Expected: All 8 tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/vsoininen/b6companion/physics/ app/src/test/java/com/vsoininen/b6companion/physics/
git commit -m "feat: add ChargingEstimator with LiPo CC/CV physics model"
```

---

### Task 5: ViewModel

**Files:**
- Create: `app/src/main/java/com/vsoininen/b6companion/MainViewModel.kt`

- [ ] **Step 1: Create MainViewModel**

Create `app/src/main/java/com/vsoininen/b6companion/MainViewModel.kt`:

```kotlin
package com.vsoininen.b6companion

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.vsoininen.b6companion.model.ChargerReading
import com.vsoininen.b6companion.model.ChargingPrediction
import com.vsoininen.b6companion.ocr.ChargerDisplayParser
import com.vsoininen.b6companion.physics.ChargingEstimator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainViewModel : ViewModel() {

    private val parser = ChargerDisplayParser()
    private val estimator = ChargingEstimator()
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val _imageUri = MutableStateFlow<Uri?>(null)
    val imageUri: StateFlow<Uri?> = _imageUri

    private val _chargerReading = MutableStateFlow<ChargerReading?>(null)
    val chargerReading: StateFlow<ChargerReading?> = _chargerReading

    private val _prediction = MutableStateFlow<ChargingPrediction?>(null)
    val prediction: StateFlow<ChargingPrediction?> = _prediction

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _rawOcrText = MutableStateFlow<String?>(null)
    val rawOcrText: StateFlow<String?> = _rawOcrText

    fun onImageCaptured(uri: Uri, context: Context) {
        _imageUri.value = uri
        _errorMessage.value = null
        _chargerReading.value = null
        _prediction.value = null

        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val image = InputImage.fromFilePath(context, uri)
                val result = recognizer.process(image).await()
                val text = result.textBlocks.joinToString("\n") { it.text }
                _rawOcrText.value = text

                val reading = parser.parse(text)
                if (reading != null) {
                    _chargerReading.value = reading
                    _prediction.value = estimator.estimate(reading)
                } else {
                    _errorMessage.value = "Could not read charger display. Try again with better lighting or angle."
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error processing image: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        recognizer.close()
    }
}
```

- [ ] **Step 2: Add kotlinx-coroutines-play-services dependency**

Add to `gradle/libs.versions.toml`:

```toml
[versions]
coroutinesPlayServices = "1.7.3"

[libraries]
kotlinx-coroutines-play-services = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-play-services", version.ref = "coroutinesPlayServices" }
```

Add to `app/build.gradle.kts` dependencies:

```kotlin
implementation(libs.kotlinx.coroutines.play.services)
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/vsoininen/b6companion/MainViewModel.kt gradle/libs.versions.toml app/build.gradle.kts
git commit -m "feat: add MainViewModel with OCR orchestration"
```

---

### Task 6: UI — Image Section

**Files:**
- Create: `app/src/main/java/com/vsoininen/b6companion/ui/ImageSection.kt`

- [ ] **Step 1: Create ImageSection composable**

Create `app/src/main/java/com/vsoininen/b6companion/ui/ImageSection.kt`:

```kotlin
package com.vsoininen.b6companion.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@Composable
fun ImageSection(
    imageUri: Uri?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (imageUri != null) {
            AsyncImage(
                model = imageUri,
                contentDescription = "Charger display photo",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.FillWidth
            )
        } else {
            Text(
                text = "Take or pick a photo of your charger display",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(32.dp)
            )
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/vsoininen/b6companion/ui/ImageSection.kt
git commit -m "feat: add ImageSection composable"
```

---

### Task 7: UI — Values Section and Prediction Card

**Files:**
- Create: `app/src/main/java/com/vsoininen/b6companion/ui/ValuesSection.kt`
- Create: `app/src/main/java/com/vsoininen/b6companion/ui/PredictionCard.kt`

- [ ] **Step 1: Create ValuesSection composable**

Create `app/src/main/java/com/vsoininen/b6companion/ui/ValuesSection.kt`:

```kotlin
package com.vsoininen.b6companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vsoininen.b6companion.model.ChargerReading

@Composable
fun ValuesSection(
    reading: ChargerReading,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Charger Reading",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ValueCard(
                label = "Battery",
                value = "${reading.batteryType}Po ${reading.cellCount}S",
                modifier = Modifier.weight(1f)
            )
            ValueCard(
                label = "Mode",
                value = modeDisplayName(reading.mode),
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ValueCard(
                label = "Voltage",
                value = "%.2fV (%.2fV/cell)".format(reading.voltage, reading.perCellVoltage),
                modifier = Modifier.weight(1f)
            )
            ValueCard(
                label = "Current",
                value = "%.1fA".format(reading.current),
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ValueCard(
                label = "Charged",
                value = "${reading.mAhCharged} mAh",
                modifier = Modifier.weight(1f)
            )
            ValueCard(
                label = "Elapsed",
                value = formatDuration(reading.elapsedTime),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ValueCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun modeDisplayName(mode: String): String = when (mode) {
    "BAL" -> "Balance"
    "CHG" -> "Charge"
    "DIS" -> "Discharge"
    "STO" -> "Storage"
    "FAT" -> "Fast"
    "FOR" -> "Formation"
    else -> mode
}

private fun formatDuration(duration: kotlin.time.Duration): String {
    val totalSeconds = duration.inWholeSeconds
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
```

- [ ] **Step 2: Create PredictionCard composable**

Create `app/src/main/java/com/vsoininen/b6companion/ui/PredictionCard.kt`:

```kotlin
package com.vsoininen.b6companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vsoininen.b6companion.model.ChargingPrediction
import java.time.format.DateTimeFormatter

@Composable
fun PredictionCard(
    prediction: ChargingPrediction,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Charging Prediction",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PredictionItem(
                    label = "State of Charge",
                    value = "%.0f%%".format(prediction.estimatedSocPercent)
                )
                PredictionItem(
                    label = "Est. Capacity",
                    value = "${prediction.estimatedTotalCapacityMah} mAh"
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PredictionItem(
                    label = "Time Remaining",
                    value = formatEte(prediction.ete)
                )
                PredictionItem(
                    label = "Done At",
                    value = prediction.eta.format(DateTimeFormatter.ofPattern("HH:mm"))
                )
            }
        }
    }
}

@Composable
private fun PredictionItem(
    label: String,
    value: String
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

private fun formatEte(duration: kotlin.time.Duration): String {
    val totalMinutes = duration.inWholeMinutes
    return if (totalMinutes >= 60) {
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        "${hours}h ${minutes}m"
    } else {
        "${totalMinutes}m"
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/vsoininen/b6companion/ui/ValuesSection.kt app/src/main/java/com/vsoininen/b6companion/ui/PredictionCard.kt
git commit -m "feat: add ValuesSection and PredictionCard composables"
```

---

### Task 8: UI — Charging Curve Chart

**Files:**
- Create: `app/src/main/java/com/vsoininen/b6companion/ui/ChargingCurveChart.kt`

- [ ] **Step 1: Create ChargingCurveChart composable**

Create `app/src/main/java/com/vsoininen/b6companion/ui/ChargingCurveChart.kt`:

```kotlin
package com.vsoininen.b6companion.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.vsoininen.b6companion.model.CurvePoint
import kotlinx.coroutines.runBlocking

@Composable
fun ChargingCurveChart(
    curvePoints: List<CurvePoint>,
    currentTimeMinutes: Double,
    modifier: Modifier = Modifier
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    remember(curvePoints) {
        runBlocking {
            modelProducer.runTransaction {
                lineSeries {
                    series(
                        curvePoints.map { it.timeMinutes },
                        curvePoints.map { it.voltage }
                    )
                }
            }
        }
    }

    val lineColor = MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Charging Curve",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(
                        lineProvider = LineCartesianLayer.LineProvider.series(
                            LineCartesianLayer.rememberLine(
                                fill = LineCartesianLayer.LineFill.single(fill(lineColor))
                            )
                        )
                    ),
                    startAxis = VerticalAxis.rememberStart(),
                    bottomAxis = HorizontalAxis.rememberBottom()
                ),
                modelProducer = modelProducer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )

            Text(
                text = "Voltage (V) over Time (min)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/vsoininen/b6companion/ui/ChargingCurveChart.kt
git commit -m "feat: add ChargingCurveChart composable with Vico"
```

---

### Task 9: UI — Main Screen and Wire Up

**Files:**
- Create: `app/src/main/java/com/vsoininen/b6companion/ui/MainScreen.kt`
- Modify: `app/src/main/java/com/vsoininen/b6companion/MainActivity.kt`

- [ ] **Step 1: Create MainScreen composable**

Create `app/src/main/java/com/vsoininen/b6companion/ui/MainScreen.kt`:

```kotlin
package com.vsoininen.b6companion.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vsoininen.b6companion.model.ChargerReading
import com.vsoininen.b6companion.model.ChargingPrediction

@Composable
fun MainScreen(
    imageUri: Uri?,
    chargerReading: ChargerReading?,
    prediction: ChargingPrediction?,
    isProcessing: Boolean,
    errorMessage: String?,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ImageSection(imageUri = imageUri)

        if (isProcessing) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(16.dp)
            )
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        if (chargerReading != null) {
            ValuesSection(reading = chargerReading)
        }

        if (prediction != null) {
            PredictionCard(prediction = prediction)

            ChargingCurveChart(
                curvePoints = prediction.curvePoints,
                currentTimeMinutes = chargerReading?.elapsedTime?.inWholeSeconds?.div(60.0) ?: 0.0
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onTakePhoto,
                modifier = Modifier.weight(1f)
            ) {
                Text("Take Photo")
            }
            OutlinedButton(
                onClick = onPickPhoto,
                modifier = Modifier.weight(1f)
            ) {
                Text("Pick Photo")
            }
        }
    }
}
```

- [ ] **Step 2: Rewrite MainActivity to wire everything together**

Replace contents of `app/src/main/java/com/vsoininen/b6companion/MainActivity.kt`:

```kotlin
package com.vsoininen.b6companion

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vsoininen.b6companion.ui.MainScreen
import com.vsoininen.b6companion.ui.theme.B6CompanionTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private var pendingCameraUri: Uri? = null

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            pendingCameraUri?.let { uri ->
                viewModel.onImageCaptured(uri, this)
            }
        }
    }

    private val pickMediaLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.onImageCaptured(it, this) }
    }

    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            B6CompanionTheme {
                val vm: MainViewModel = viewModel()
                viewModel = vm

                val imageUri by vm.imageUri.collectAsState()
                val chargerReading by vm.chargerReading.collectAsState()
                val prediction by vm.prediction.collectAsState()
                val isProcessing by vm.isProcessing.collectAsState()
                val errorMessage by vm.errorMessage.collectAsState()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        imageUri = imageUri,
                        chargerReading = chargerReading,
                        prediction = prediction,
                        isProcessing = isProcessing,
                        errorMessage = errorMessage,
                        onTakePhoto = ::takePhoto,
                        onPickPhoto = ::pickPhoto,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun takePhoto() {
        val photoFile = File(cacheDir, "charger_photo_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
        pendingCameraUri = uri
        takePictureLauncher.launch(uri)
    }

    private fun pickPhoto() {
        pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
}
```

- [ ] **Step 3: Create FileProvider XML**

Create `app/src/main/res/xml/file_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="cache" path="." />
</paths>
```

- [ ] **Step 4: Register FileProvider in AndroidManifest.xml**

Add inside the `<application>` tag in `app/src/main/AndroidManifest.xml`:

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

- [ ] **Step 5: Verify build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/vsoininen/b6companion/ui/MainScreen.kt app/src/main/java/com/vsoininen/b6companion/MainActivity.kt app/src/main/res/xml/file_paths.xml app/src/main/AndroidManifest.xml
git commit -m "feat: wire up MainScreen with camera/gallery integration"
```

---

### Task 10: Final Build Verification and Init Commit

- [ ] **Step 1: Run all unit tests**

Run: `./gradlew test --info`
Expected: All tests PASS

- [ ] **Step 2: Run full debug build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit any remaining changes**

```bash
git add -A
git status
# Only commit if there are uncommitted changes
git commit -m "chore: final build verification"
```
