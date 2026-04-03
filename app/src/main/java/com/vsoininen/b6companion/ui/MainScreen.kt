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
