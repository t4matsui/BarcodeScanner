// ui/components/ButtonArea.kt
package com.yamaken.barcodescanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ButtonArea(
    isCameraMode: Boolean,
    barcodesDetected: Boolean,
    barcodeSelected: Boolean,
    scannedCode: String,
    isDetecting: Boolean,
    isScanning: Boolean,
    onRetakeClick: () -> Unit,
    onDetectClick: () -> Unit,
    onScanClick: () -> Unit,
    onShutterClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        if (!isCameraMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRetakeClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "再撮影")
                }

                Button(
                    onClick = onDetectClick,
                    modifier = Modifier.weight(1f),
                    enabled = !isDetecting && !barcodesDetected && scannedCode.isEmpty()
                ) {
                    Text(text = if (isDetecting) "検知中..." else "検知")
                }

                Button(
                    onClick = onScanClick,
                    modifier = Modifier.weight(1f),
                    enabled = barcodeSelected && !isScanning
                ) {
                    Text(text = if (isScanning) "実行中..." else "スキャン")
                }
            }
        } else {
            ShutterButton(onClick = onShutterClick)
        }
    }
}

@Composable
fun ShutterButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .background(Color.Gray, CircleShape)
            .border(4.dp, Color.Gray, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White
            ),
            contentPadding = PaddingValues(0.dp)
        ) {}
    }
}