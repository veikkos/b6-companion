package com.vsoininen.b6companion

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.vsoininen.b6companion.data.CapacityPreferences
import com.vsoininen.b6companion.model.ChargerReading
import com.vsoininen.b6companion.model.ChargingPrediction
import com.vsoininen.b6companion.ocr.ChargerDisplayParser
import com.vsoininen.b6companion.physics.ChargingEstimator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val parser = ChargerDisplayParser()
    private val estimator = ChargingEstimator()
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val capacityPrefs = CapacityPreferences(application)

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

    private val _batteryCapacityMah = MutableStateFlow(capacityPrefs.getCapacityMah())
    val batteryCapacityMah: StateFlow<Int> = _batteryCapacityMah

    fun onBatteryCapacityChanged(capacityMah: Int) {
        val clamped = capacityMah.coerceIn(100, 30000)
        _batteryCapacityMah.value = clamped
        capacityPrefs.setCapacityMah(clamped)
        // Re-run prediction if we already have a reading
        _chargerReading.value?.let { reading ->
            _prediction.value = estimator.estimate(reading, clamped)
        }
    }

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
                Log.d("B6Companion", "OCR raw text: '$text'")

                val reading = parser.parse(text)
                Log.d("B6Companion", "Parse result: $reading")
                if (reading != null) {
                    _chargerReading.value = reading
                    _prediction.value = estimator.estimate(reading, _batteryCapacityMah.value)
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
