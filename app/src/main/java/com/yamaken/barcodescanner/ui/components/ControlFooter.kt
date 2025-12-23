// ui/components/ControlFooter.kt
package com.yamaken.barcodescanner.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ControlFooter(
    isCameraMode: Boolean,
    barcodesDetected: Boolean,
    barcodeSelected: Boolean,
    detectedCount: Int,
    scannedCode: String,
    codeType: String,
    isDetecting: Boolean,
    isScanning: Boolean,
    isSaving: Boolean,
    isSaved: Boolean,
    errorMessage: String,
    saveMessage: String,
    onRetakeClick: () -> Unit,
    onDetectClick: () -> Unit,
    onScanClick: () -> Unit,
    onShutterClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ボタンエリア
            ButtonArea(
                isCameraMode = isCameraMode,
                barcodesDetected = barcodesDetected,
                barcodeSelected = barcodeSelected,
                scannedCode = scannedCode,
                isDetecting = isDetecting,
                isScanning = isScanning,
                onRetakeClick = onRetakeClick,
                onDetectClick = onDetectClick,
                onScanClick = onScanClick,
                onShutterClick = onShutterClick
            )

            // メッセージエリア
            MessageArea(
                isCameraMode = isCameraMode,
                barcodesDetected = barcodesDetected,
                barcodeSelected = barcodeSelected,
                detectedCount = detectedCount,
                scannedCode = scannedCode,
                codeType = codeType,
                errorMessage = errorMessage,
                saveMessage = saveMessage,
                isSaving = isSaving,
                isSaved = isSaved,
                onSaveClick = onSaveClick
            )
        }
    }
}