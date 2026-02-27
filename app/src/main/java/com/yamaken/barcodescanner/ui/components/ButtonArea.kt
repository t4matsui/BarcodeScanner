// ui/components/ButtonArea.kt
package com.yamaken.barcodescanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ButtonArea(
    isCameraMode: Boolean,
    isProcessing: Boolean,
    onRetakeClick: () -> Unit,
    onShutterClick: () -> Unit,
    onAdoptClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        if (isCameraMode) {
            // カメラ表示中：シャッターボタン（中央）
            ShutterButton(onClick = onShutterClick)
        } else {
            // 静止画レビュー中：←ボタン（左）＋写真保存ボタン（中央寄り）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ← やり直しボタン
                IconButton(
                    onClick = onRetakeClick,
                    modifier = Modifier
                        .size(48.dp)
                        .border(width = 1.dp, color = Color.LightGray, shape = CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "やり直し",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // 写真保存ボタン
                Button(
                    onClick = onAdoptClick,
                    enabled = !isProcessing,
                    modifier = Modifier
                        .height(48.dp)
                        .widthIn(min = 120.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = Color.LightGray
                    )
                ) {
                    Text(
                        text = if (isProcessing) "保存中..." else "写真保存",
                        fontSize = 16.sp
                    )
                }

                // 左右対称の余白
                Spacer(modifier = Modifier.size(48.dp))
            }
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
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            contentPadding = PaddingValues(0.dp)
        ) {}
    }
}