// ui/components/MessageArea.kt
package com.yamaken.barcodescanner.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MessageArea(
    isCameraMode: Boolean,
    isDetecting: Boolean,
    detectedCount: Int,
    isProcessing: Boolean,
    processMessage: String,
    errorMessage: String
) {
    Column {
        if (!isCameraMode) {
            // 静止画表示モード
            Text(
                text = when {
                    isProcessing -> "処理中"
                    errorMessage.isNotEmpty() -> "エラー"
                    detectedCount > 0 -> "バーコード検知: ${detectedCount}個"
                    isDetecting -> "検知中..."
                    else -> "検知中..."
                },
                style = MaterialTheme.typography.titleMedium,
                color = when {
                    errorMessage.isNotEmpty() -> MaterialTheme.colorScheme.error
                    isProcessing -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (errorMessage.isNotEmpty()) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (processMessage.isNotEmpty()) {
                Text(
                    text = processMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (detectedCount > 0) {
                Text(
                    text = "スキャンしたいバーコードをタップしてください",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (isDetecting) {
                Text(
                    text = "バーコードを検知しています...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // カメラモード
            Text(
                text = "バーコード、QRコードを撮影してください。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}