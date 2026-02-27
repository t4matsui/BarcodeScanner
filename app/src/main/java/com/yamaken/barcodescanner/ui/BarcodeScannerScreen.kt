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

    /** タイムスタンプ生成 */
    fun makeTimestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

    /** カメラ状態にリセット */
    fun resetToCamera() {
        isCameraMode = true
        capturedImage = null
        detectedBarcodes = emptyList()
        detectionBoxes = emptyList()
        selectedBoxIndex = null
        isProcessing = false
        processMessage = ""
        errorMessage = ""
        isDetecting = false
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // カメラ / 静止画エリア
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (isCameraMode) {
                CameraPreviewArea(
                    cameraManager = cameraManager,
                    lifecycleOwner = lifecycleOwner,
                    onImageCaptureReady = { currentImageCapture = it }
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

                        val selectedBarcode = detectedBarcodes[index]
                        val (scannedCode, codeType) = barcodeProcessor.scanSpecificBarcode(selectedBarcode)

                        if (scannedCode.isEmpty()) {
                            isProcessing = false
                            processMessage = ""
                            errorMessage = "スキャン失敗"
                            selectedBoxIndex = null
                            return@CapturedImageArea
                        }

                        processMessage = "保存中..."
                        val timestamp = makeTimestamp()

                        capturedImage?.let { bitmap ->
                            storage.saveScanResult(
                                bitmap = bitmap,
                                detectionBox = detectionBoxes[index],
                                scanCode = scannedCode,
                                scanType = codeType,
                                timestamp = timestamp
                            ) { success, message ->
                                if (success) {
                                    processMessage = "保存完了"
                                    scope.launch {
                                        kotlinx.coroutines.delay(1000)
                                        resetToCamera()
                                    }
                                } else {
                                    isProcessing = false
                                    processMessage = ""
                                    errorMessage = "保存失敗: $message"
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

            // やり直しボタン
            onRetakeClick = { resetToCamera() },

            // シャッターボタン
            onShutterClick = {
                currentImageCapture?.let { ic ->
                    cameraManager.takePicture(
                        ic,
                        onSuccess = { bitmap ->
                            cameraManager.stopCamera()
                            capturedImage = bitmap
                            isCameraMode = false
                            errorMessage = ""
                            isProcessing = false
                            processMessage = ""
                            detectedBarcodes = emptyList()
                            detectionBoxes = emptyList()
                            selectedBoxIndex = null

                            // 自動でバーコード検知開始
                            isDetecting = true
                            barcodeProcessor.detectBarcodes(bitmap) { barcodes ->
                                detectedBarcodes = barcodes
                                detectionBoxes = barcodes.mapNotNull { it.boundingBox }
                                // バーコードが見つからなくてもエラーは出さない（採用ボタンで写真保存できる）
                                isDetecting = false
                            }
                        },
                        onError = { errorMessage = "撮影失敗" }
                    )
                }
            },

            // 採用ボタン（写真として保存）
            onAdoptClick = {
                if (isProcessing) return@ControlFooter

                isProcessing = true
                processMessage = "保存中..."
                errorMessage = ""
                val timestamp = makeTimestamp()

                capturedImage?.let { bitmap ->
                    storage.savePhotoResult(
                        bitmap = bitmap,
                        timestamp = timestamp
                    ) { success, message ->
                        if (success) {
                            processMessage = "保存完了"
                            scope.launch {
                                kotlinx.coroutines.delay(1000)
                                resetToCamera()
                            }
                        } else {
                            isProcessing = false
                            processMessage = ""
                            errorMessage = "保存失敗: $message"
                        }
                    }
                }
            }
        )
    }
}