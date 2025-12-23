// ui/BarcodeScannerScreen.kt
package com.yamaken.barcodescanner.ui

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.common.Barcode
import com.yamaken.barcodescanner.barcode.BarcodeProcessor
import com.yamaken.barcodescanner.camera.CameraManager
import com.yamaken.barcodescanner.storage.ScanResultStorage
import com.yamaken.barcodescanner.ui.components.CameraPreviewArea
import com.yamaken.barcodescanner.ui.components.CapturedImageArea
import com.yamaken.barcodescanner.ui.components.ControlFooter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BarcodeScannerScreen(
    cameraManager: CameraManager,
    barcodeProcessor: BarcodeProcessor,
    storage: ScanResultStorage
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // 状態管理
    var isCameraMode by remember { mutableStateOf(true) }
    var capturedImage by remember { mutableStateOf<Bitmap?>(null) }
    var detectedBarcodes by remember { mutableStateOf<List<Barcode>>(emptyList()) }
    var detectionBoxes by remember { mutableStateOf<List<Rect>>(emptyList()) }
    var selectedBoxIndex by remember { mutableStateOf<Int?>(null) }
    var scannedCode by remember { mutableStateOf("") }
    var codeType by remember { mutableStateOf("") }
    var isDetecting by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf("") }
    var scanTimestamp by remember { mutableStateOf("") }
    var currentImageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var isSaved by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // カメラ/静止画表示エリア
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (isCameraMode) {
                CameraPreviewArea(
                    cameraManager = cameraManager,
                    lifecycleOwner = lifecycleOwner,
                    onImageCaptureReady = { imageCapture ->
                        currentImageCapture = imageCapture
                    }
                )
            } else {
                CapturedImageArea(
                    bitmap = capturedImage,
                    detectionBoxes = detectionBoxes,
                    selectedBoxIndex = selectedBoxIndex,
                    onBoxSelected = { index ->
                        selectedBoxIndex = index
                    }
                )
            }
        }

        // フッター
        ControlFooter(
            isCameraMode = isCameraMode,
            barcodesDetected = detectedBarcodes.isNotEmpty(),
            barcodeSelected = selectedBoxIndex != null,
            detectedCount = detectedBarcodes.size,
            scannedCode = scannedCode,
            codeType = codeType,
            isDetecting = isDetecting,
            isScanning = isScanning,
            isSaving = isSaving,
            isSaved = isSaved,
            errorMessage = errorMessage,
            saveMessage = saveMessage,
            onRetakeClick = {
                // リセット
                isCameraMode = true
                capturedImage = null
                detectedBarcodes = emptyList()
                detectionBoxes = emptyList()
                selectedBoxIndex = null
                scannedCode = ""
                codeType = ""
                errorMessage = ""
                saveMessage = ""
                isSaved = false
            },
            onDetectClick = {
                isDetecting = true
                errorMessage = ""
                capturedImage?.let { bitmap ->
                    barcodeProcessor.detectBarcodes(bitmap) { barcodes ->
                        if (barcodes.isNotEmpty()) {
                            detectedBarcodes = barcodes
                            detectionBoxes = barcodes.mapNotNull { it.boundingBox }
                            selectedBoxIndex = null // 選択をリセット
                        } else {
                            detectedBarcodes = emptyList()
                            detectionBoxes = emptyList()
                            errorMessage = "バーコードが見つかりませんでした"
                        }
                        isDetecting = false
                    }
                }
            },
            onScanClick = {
                isScanning = true
                selectedBoxIndex?.let { index ->
                    val selectedBarcode = detectedBarcodes[index]
                    val result = barcodeProcessor.scanSpecificBarcode(selectedBarcode)
                    scannedCode = result.first
                    codeType = result.second

                    val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    scanTimestamp = sdf.format(Date())

                    isScanning = false
                    isSaved = false
                }
            },
            onShutterClick = {
                currentImageCapture?.let { imageCapture ->
                    cameraManager.takePicture(
                        imageCapture,
                        onSuccess = { bitmap ->
                            cameraManager.stopCamera()
                            capturedImage = bitmap
                            isCameraMode = false
                            errorMessage = ""
                            // リセット
                            detectedBarcodes = emptyList()
                            detectionBoxes = emptyList()
                            selectedBoxIndex = null
                            scannedCode = ""
                            codeType = ""
                        },
                        onError = { e ->
                            errorMessage = "撮影失敗"
                        }
                    )
                }
            },
            onSaveClick = {
                isSaved = false
                isSaving = true
                saveMessage = ""

                capturedImage?.let { bitmap ->
                    selectedBoxIndex?.let { index ->
                        val selectedBox = detectionBoxes[index]
                        storage.saveScanResult(
                            bitmap = bitmap,
                            detectionBox = selectedBox,
                            scanCode = scannedCode,
                            scanType = codeType,
                            timestamp = scanTimestamp
                        ) { success, message ->
                            isSaved = true
                            isSaving = false
                            saveMessage = message
                        }
                    }
                }
            }
        )
    }
}