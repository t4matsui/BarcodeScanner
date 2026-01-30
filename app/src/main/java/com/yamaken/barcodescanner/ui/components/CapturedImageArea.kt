// ui/components/CapturedImageArea.kt
package com.yamaken.barcodescanner.ui.components

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale

@Composable
fun CapturedImageArea(
    bitmap: Bitmap?,
    detectionBoxes: List<Rect>,
    selectedBoxIndex: Int?,
    onBoxSelected: (Int) -> Unit
) {
    bitmap?.let { bmp ->
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "撮影した画像",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }

    if (detectionBoxes.isNotEmpty() && bitmap != null) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        // タップ位置がどの枠に含まれるか判定
                        val tappedBoxIndex = findTappedBox(
                            offset,
                            detectionBoxes,
                            bitmap,
                            size.width.toInt(),
                            size.height.toInt()
                        )

                        if (tappedBoxIndex != -1) {
                            onBoxSelected(tappedBoxIndex)
                        }
                    }
                }
        ) {
            val scaleX = size.width / bitmap.width.toFloat()
            val scaleY = size.height / bitmap.height.toFloat()
            val scale = maxOf(scaleX, scaleY)

            val scaledWidth = bitmap.width * scale
            val scaledHeight = bitmap.height * scale
            val offsetX = (size.width - scaledWidth) / 2
            val offsetY = (size.height - scaledHeight) / 2

            // 全ての枠を描画
            detectionBoxes.forEachIndexed { index, box ->
                val isSelected = selectedBoxIndex == index
                val color = if (isSelected) {
                    Color(0xFFFF0066)  // 赤枠（選択時）
                } else {
                    Color.Green  // 緑枠（未選択時）
                }

                drawRect(
                    color = color.copy(alpha = 0.6f),
                    topLeft = Offset(
                        offsetX + box.left * scale,
                        offsetY + box.top * scale
                    ),
                    size = Size(
                        box.width() * scale,
                        box.height() * scale
                    ),
                    style = Stroke(width = 8f)
                )
            }
        }
    }
}

/**
 * タップ位置がどの枠に含まれるか判定
 */
private fun findTappedBox(
    tapOffset: Offset,
    boxes: List<Rect>,
    bitmap: Bitmap,
    canvasWidth: Int,
    canvasHeight: Int
): Int {
    val scaleX = canvasWidth / bitmap.width.toFloat()
    val scaleY = canvasHeight / bitmap.height.toFloat()
    val scale = maxOf(scaleX, scaleY)

    val scaledWidth = bitmap.width * scale
    val scaledHeight = bitmap.height * scale
    val offsetX = (canvasWidth - scaledWidth) / 2
    val offsetY = (canvasHeight - scaledHeight) / 2

    boxes.forEachIndexed { index, box ->
        val left = offsetX + box.left * scale
        val top = offsetY + box.top * scale
        val right = left + box.width() * scale
        val bottom = top + box.height() * scale

        if (tapOffset.x in left..right && tapOffset.y in top..bottom) {
            return index
        }
    }

    return -1 // 枠外をタップ
}