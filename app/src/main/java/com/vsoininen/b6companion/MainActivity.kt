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
