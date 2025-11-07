// ui/components/CapturedImageArea.kt
package com.yamaken.barcodescanner.ui.components

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale

@Composable
fun CapturedImageArea(
    bitmap: Bitmap?,
    detectionBox: Rect?,
    barcodeDetected: Boolean
) {
    bitmap?.let { bmp ->
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "撮影した画像",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }

    if (barcodeDetected && detectionBox != null && bitmap != null) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val box = detectionBox

            val scaleX = size.width / bitmap.width.toFloat()
            val scaleY = size.height / bitmap.height.toFloat()
            val scale = maxOf(scaleX, scaleY)

            val scaledWidth = bitmap.width * scale
            val scaledHeight = bitmap.height * scale
            val offsetX = (size.width - scaledWidth) / 2
            val offsetY = (size.height - scaledHeight) / 2

            drawRect(
                color = Color.Green.copy(alpha = 0.6f),
                topLeft = Offset(
                    offsetX + box.left * scale,
                    offsetY + box.top * scale
                ),
                size = Size(
                    box.width() * scale,
                    box.height() * scale
                ),
                style = Stroke(width = 4f)
            )
        }
    }
}