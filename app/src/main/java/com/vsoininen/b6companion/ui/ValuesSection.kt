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
