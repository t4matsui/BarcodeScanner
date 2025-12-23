// ui/components/MessageArea.kt
package com.yamaken.barcodescanner.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MessageArea(
    isCameraMode: Boolean,
    barcodesDetected: Boolean,
    barcodeSelected: Boolean,
    detectedCount: Int,
    scannedCode: String,
    codeType: String,
    errorMessage: String,
    saveMessage: String,
    isSaving: Boolean,
    isSaved: Boolean,
    onSaveClick: () -> Unit
) {
    Column {
        if (!isCameraMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        scannedCode.isNotEmpty() -> "読み取り完了"
                        barcodeSelected -> "スキャンボタンを押してください"
                        barcodesDetected -> "枠を選択してください (${detectedCount}個検知)"
                        errorMessage.isNotEmpty() -> "エラー"
                        else -> "検知ボタンを押してください"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (errorMessage.isNotEmpty())
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )

                // 保存ボタン
                if (scannedCode.isNotEmpty()) {
                    Button(
                        onClick = onSaveClick,
                        enabled = !isSaving && !isSaved,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(text = if (isSaving) "保存中..." else if (isSaved) "保存済み" else "保存")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (scannedCode.isNotEmpty()) {
                Text(text = "種類: $codeType", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "内容: $scannedCode", style = MaterialTheme.typography.bodyLarge)
                if (saveMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = saveMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (saveMessage.contains("成功"))
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                }
            } else if (errorMessage.isNotEmpty()) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (barcodesDetected && !barcodeSelected) {
                Text(
                    text = "画面上の緑枠をタップして選択してください",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Text(
                text = "シャッターボタンで撮影",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "バーコードまたはQRコードを画面に収めてシャッターを押してください",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}