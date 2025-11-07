// MainActivity.kt
package com.yamaken.barcodescanner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.core.content.ContextCompat
import com.yamaken.barcodescanner.barcode.BarcodeProcessor
import com.yamaken.barcodescanner.camera.CameraManager
import com.yamaken.barcodescanner.storage.ScanResultStorage
import com.yamaken.barcodescanner.ui.BarcodeScannerScreen
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val barcodeProcessor = BarcodeProcessor()
    private lateinit var cameraManager: CameraManager
    private lateinit var storage: ScanResultStorage

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "カメラ権限が許可されました", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "カメラ権限が必要です", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cameraManager = CameraManager(this, cameraExecutor)
        storage = ScanResultStorage(this)

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setContent {
            MaterialTheme {
                BarcodeScannerScreen(
                    cameraManager = cameraManager,
                    barcodeProcessor = barcodeProcessor,
                    storage = storage
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        barcodeProcessor.release()
    }
}