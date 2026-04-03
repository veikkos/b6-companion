package com.vsoininen.b6companion

import android.Manifest
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import com.vsoininen.b6companion.ui.MainScreen
import com.vsoininen.b6companion.ui.theme.B6CompanionTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var pendingCameraUri: Uri? = null

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            Toast.makeText(this, "Camera permission required to take photos", Toast.LENGTH_SHORT).show()
        }
    }

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            B6CompanionTheme {
                val imageUri by viewModel.imageUri.collectAsState()
                val chargerReading by viewModel.chargerReading.collectAsState()
                val prediction by viewModel.prediction.collectAsState()
                val isProcessing by viewModel.isProcessing.collectAsState()
                val errorMessage by viewModel.errorMessage.collectAsState()
                val batteryCapacityMah by viewModel.batteryCapacityMah.collectAsState()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        imageUri = imageUri,
                        chargerReading = chargerReading,
                        prediction = prediction,
                        isProcessing = isProcessing,
                        errorMessage = errorMessage,
                        batteryCapacityMah = batteryCapacityMah,
                        onBatteryCapacityChanged = viewModel::onBatteryCapacityChanged,
                        onTakePhoto = ::takePhoto,
                        onPickPhoto = ::pickPhoto,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun takePhoto() {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun launchCamera() {
        val photoFile = File(cacheDir, "charger_photo_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
        pendingCameraUri = uri
        takePictureLauncher.launch(uri)
    }

    private fun pickPhoto() {
        pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
}
