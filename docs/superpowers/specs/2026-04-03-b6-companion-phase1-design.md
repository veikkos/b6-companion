# B6 Companion — Phase 1 Design

## Overview

Android app that lets users photograph a SkyRC B6/e6650 balance charger display, OCRs the values, and shows a charging prediction with ETA/ETE based on LiPo physics.

## Target Display Format

SkyRC e6650 two-line LCD:
```
Li4S  3.3A  15.65V
 BAL  002:27  00133
```

Line 1: battery type + cell count, charge current (A), pack voltage (V)
Line 2: mode (BAL/CHG/DIS/STO), elapsed time (HHH:MM), capacity charged (mAh)

## Architecture

Single-activity, single-screen Compose app. No navigation framework needed.

### Dependencies

| Library | Purpose |
|---------|---------|
| ML Kit Text Recognition (`com.google.mlkit:text-recognition`) | On-device OCR |
| Coil (`io.coil-kt:coil-compose`) | Image loading in Compose |
| Vico (`com.patrykandpatrick.vico:compose-m3`) | Charting library for charging curve |

### Data Model

```kotlin
data class ChargerReading(
    val batteryType: String,       // "Li"
    val cellCount: Int,            // 4
    val current: Double,           // 3.3 (amps)
    val voltage: Double,           // 15.65 (volts)
    val mode: String,              // "BAL", "CHG", "DIS", "STO"
    val elapsedTime: Duration,     // 2m 27s
    val mAhCharged: Int            // 133
)

data class ChargingPrediction(
    val perCellVoltage: Double,        // voltage / cellCount
    val estimatedSocPercent: Double,   // state of charge %
    val estimatedTotalCapacity: Int,   // mAh, derived
    val ete: Duration,                 // estimated time to end
    val eta: LocalDateTime,            // wall-clock completion time
    val curvePoints: List<CurvePoint>  // for the graph
)

data class CurvePoint(
    val timeMinutes: Double,
    val voltage: Double,
    val current: Double
)
```

## Components

### 1. Image Capture/Pick

- Bottom area shows two buttons: "Take Photo" (camera) and "Pick Photo" (gallery)
- Uses `ActivityResultContracts.TakePicture` for camera
- Uses `ActivityResultContracts.PickVisualMedia` for gallery
- Captured image URI stored in ViewModel state
- Image displayed at top of screen, aspect-ratio preserved, max ~40% screen height

### 2. OCR Parser (`ChargerDisplayParser`)

Takes ML Kit `Text` result and extracts a `ChargerReading`.

Parsing strategy:
- ML Kit returns text blocks/lines — concatenate all recognized text
- Apply regex patterns to extract each field:
  - Battery: `(Li|Ni|Pb)(\d)S` — type and cell count
  - Current: `(\d+\.?\d*)\s*A` — amps
  - Voltage: `(\d+\.?\d*)\s*V` — volts
  - Mode: `(BAL|CHG|DIS|STO|FAT|FOR)`
  - Time: `(\d{2,3}):(\d{2})` — elapsed time
  - Capacity: last standalone number group, interpreted as mAh

Returns `ChargerReading?` — null if essential fields can't be parsed.

### 3. Charging Estimator (`ChargingEstimator`)

Computes predictions from a `ChargerReading`.

**LiPo voltage-to-SoC mapping** (per cell, approximate):
| Voltage | SoC % |
|---------|-------|
| 3.00V | 0% |
| 3.30V | 5% |
| 3.50V | 15% |
| 3.60V | 25% |
| 3.70V | 40% |
| 3.75V | 50% |
| 3.80V | 60% |
| 3.85V | 70% |
| 3.90V | 80% |
| 4.00V | 90% |
| 4.10V | 95% |
| 4.20V | 100% |

Uses linear interpolation between points.

**Capacity estimation:**
- Current SoC% derived from per-cell voltage
- If SoC > 0: `estimatedTotalCapacity = mAhCharged / (currentSoC - startSoC)`
  - `startSoC` estimated from `(currentSoC * elapsedMinutes - mAhCharged * 60 / (current * 1000))` or simplified: assume started from storage voltage (~3.85V/cell, ~70%) if no other data
  - Fallback: estimate from typical pack sizes for the cell count (4S packs are commonly 1300-5000mAh)

**CC/CV charging model:**
- CC phase: constant current until ~4.15V/cell → linear voltage rise
- CV phase: voltage held at 4.2V/cell, current tapers exponentially (roughly halves every 15-20 min)
- Charge complete when current drops below ~0.1A or C/10

**ETE calculation:**
- In CC phase: time to reach 4.15V/cell at current charge rate, plus estimated CV phase duration (~20-30 min for typical packs)
- In CV phase: estimate from exponential current decay model
- ETA = now + ETE

**Curve generation:**
- Generate points from elapsed=0 to estimated completion
- Mark current position on the curve
- Show both voltage and current lines

### 4. UI Layout

Single scrollable column (`LazyColumn` or `Column` with `verticalScroll`):

**Section 1: Image**
- Captured photo, rounded corners, slight shadow
- If no photo yet, show a placeholder with charger icon

**Section 2: Parsed Values**
- Material3 cards in a grid (2 columns):
  - Battery: "LiPo 4S" with battery icon
  - Voltage: "15.65V (3.91V/cell)" with bolt icon
  - Current: "3.3A" with current icon
  - Mode: "Balance Charge" with mode icon
  - Charge: "133 mAh" with capacity icon
  - Time: "2:27" with clock icon
- Below the grid, highlighted card showing:
  - Estimated SoC: "~75%"
  - ETE: "~45 min remaining"
  - ETA: "~11:15 AM"

**Section 3: Charging Curve**
- Line chart (Vico) showing:
  - X-axis: time (minutes)
  - Y-axis left: voltage
  - Y-axis right: current (optional, if Vico supports dual axis)
  - Vertical dashed line at current position
  - Shaded region for projected/estimated portion
- Legend below the chart

**Bottom: Action Buttons**
- "Take Photo" and "Pick Photo" buttons
- Persistent at bottom (not scrollable), or as a FAB

### 5. ViewModel (`MainViewModel`)

```kotlin
class MainViewModel : ViewModel() {
    val imageUri: StateFlow<Uri?>
    val chargerReading: StateFlow<ChargerReading?>
    val prediction: StateFlow<ChargingPrediction?>
    val isProcessing: StateFlow<Boolean>
    val errorMessage: StateFlow<String?>

    fun onImageCaptured(uri: Uri, context: Context)
    // Triggers: ML Kit OCR → parse → estimate → update state
}
```

## Permissions

- `android.permission.CAMERA` — for taking photos
- No internet permission needed (ML Kit on-device)

## Error Handling

- OCR fails to parse: show "Could not read charger display. Try again with better lighting/angle." below the image
- Partial parse: show what was extracted, mark missing fields as "—"
- No image yet: show placeholder UI with instructions

## Not In Scope (Phase 1)

- Photo history or persistence
- Video / continuous tracking
- Non-SkyRC charger support
- Settings or configuration
- Individual cell voltage reading
- Cloud sync or sharing
