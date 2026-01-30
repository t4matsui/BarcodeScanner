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
import kotlinx.coroutines.launch
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
    val scope = rememberCoroutineScope()

    // 状態管理
    var isCameraMode by remember { mutableStateOf(true) }
    var capturedImage by remember { mutableStateOf<Bitmap?>(null) }
    var detectedBarcodes by remember { mutableStateOf<List<Barcode>>(emptyList()) }
    var detectionBoxes by remember { mutableStateOf<List<Rect>>(emptyList()) }
    var selectedBoxIndex by remember { mutableStateOf<Int?>(null) }
    var isDetecting by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var processMessage by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var currentImageCapture: ImageCapture? by remember { mutableStateOf(null) }

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
                        if (isProcessing) return@CapturedImageArea

                        selectedBoxIndex = index
                        isProcessing = true
                        processMessage = "スキャン中..."
                        errorMessage = ""

                        // 選択されたバーコードをスキャン
                        val selectedBarcode = detectedBarcodes[index]
                        val result = barcodeProcessor.scanSpecificBarcode(selectedBarcode)
                        val scannedCode = result.first
                        val codeType = result.second

                        if (scannedCode.isEmpty()) {
                            isProcessing = false
                            processMessage = ""
                            errorMessage = "スキャン失敗"
                            selectedBoxIndex = null
                            return@CapturedImageArea
                        }

                        processMessage = "保存中..."

                        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                        val scanTimestamp = sdf.format(Date())

                        capturedImage?.let { bitmap ->
                            val selectedBox = detectionBoxes[index]

                            // 本体ストレージに保存
                            storage.saveScanResult(
                                bitmap = bitmap,
                                detectionBox = selectedBox,
                                scanCode = scannedCode,
                                scanType = codeType,
                                timestamp = scanTimestamp
                            ) { localSuccess, localMessage ->
                                if (localSuccess) {
                                    processMessage = "保存完了"

                                    // 1秒後にカメラモードに戻る
                                    scope.launch {
                                        kotlinx.coroutines.delay(1000)

                                        // リセット
                                        isCameraMode = true
                                        capturedImage = null
                                        detectedBarcodes = emptyList()
                                        detectionBoxes = emptyList()
                                        selectedBoxIndex = null
                                        isProcessing = false
                                        processMessage = ""
                                        errorMessage = ""
                                    }
                                } else {
                                    isProcessing = false
                                    processMessage = ""
                                    errorMessage = "保存失敗: $localMessage"
                                    selectedBoxIndex = null
                                }
                            }
                        }
                    }
                )
            }
        }

        // フッター
        ControlFooter(
            isCameraMode = isCameraMode,
            isDetecting = isDetecting,
            detectedCount = detectedBarcodes.size,
            isProcessing = isProcessing,
            processMessage = processMessage,
            errorMessage = errorMessage,
            onRetakeClick = {
                // リセット
                isCameraMode = true
                capturedImage = null
                detectedBarcodes = emptyList()
                detectionBoxes = emptyList()
                selectedBoxIndex = null
                isProcessing = false
                processMessage = ""
                errorMessage = ""
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
                            isProcessing = false
                            processMessage = ""

                            // リセット
                            detectedBarcodes = emptyList()
                            detectionBoxes = emptyList()
                            selectedBoxIndex = null

                            // 自動的に検知開始
                            isDetecting = true
                            barcodeProcessor.detectBarcodes(bitmap) { barcodes ->
                                if (barcodes.isNotEmpty()) {
                                    detectedBarcodes = barcodes
                                    detectionBoxes = barcodes.mapNotNull { it.boundingBox }
                                } else {
                                    errorMessage = "バーコードが見つかりませんでした"
                                }
                                isDetecting = false
                            }
                        },
                        onError = { e ->
                            errorMessage = "撮影失敗"
                        }
                    )
                }
            }
        )
    }
}