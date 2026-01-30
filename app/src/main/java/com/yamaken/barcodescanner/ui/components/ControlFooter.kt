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
    isDetecting: Boolean,
    detectedCount: Int,
    isProcessing: Boolean,
    processMessage: String,
    errorMessage: String,
    onRetakeClick: () -> Unit,
    onShutterClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
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
                onRetakeClick = onRetakeClick,
                onShutterClick = onShutterClick
            )

            // メッセージエリア
            MessageArea(
                isCameraMode = isCameraMode,
                isDetecting = isDetecting,
                detectedCount = detectedCount,
                isProcessing = isProcessing,
                processMessage = processMessage,
                errorMessage = errorMessage
            )
        }
    }
}