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
        if (isCameraMode) {
            // カメラモード
            Text(
                text = "バーコード、QRコード、写真を撮影してください。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // 静止画レビューモード
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

            when {
                errorMessage.isNotEmpty() -> Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                processMessage.isNotEmpty() -> Text(
                    text = processMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                detectedCount > 0 -> Text(
                    text = "バーコードをタップしてスキャン、または「写真保存」",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                isDetecting -> Text(
                    text = "バーコードを検知しています...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> Text(
                    text = "「写真保存」で写真として保存できます",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}