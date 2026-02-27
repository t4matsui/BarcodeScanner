// storage/ScanResultStorage.kt
package com.yamaken.barcodescanner.storage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Environment
import android.provider.MediaStore
import android.util.Log

class ScanResultStorage(private val context: Context) {

    /**
     * スキャン結果を保存（バーコード・QRコードモード）
     * 保存先: Download/BarcodeScanner/yyyyMMdd/
     */
    fun saveScanResult(
        bitmap: Bitmap,
        detectionBox: Rect?,
        scanCode: String,
        scanType: String,
        timestamp: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        val textContent = "種類: $scanType\n内容: $scanCode\n日時: $timestamp"
        saveFiles(
            bitmap = bitmap,
            detectionBox = detectionBox,
            textContent = textContent,
            timestamp = timestamp,
            onComplete = onComplete
        )
    }

    /**
     * 写真として保存（採用ボタン）
     * 保存先: Download/BarcodeScanner/yyyyMMdd/
     */
    fun savePhotoResult(
        bitmap: Bitmap,
        timestamp: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        val textContent = "種類: 写真\n内容: \n日時: $timestamp"
        saveFiles(
            bitmap = bitmap,
            detectionBox = null,
            textContent = textContent,
            timestamp = timestamp,
            onComplete = onComplete
        )
    }

    /**
     * 画像＋テキストファイルを共有ストレージに保存する共通処理
     */
    private fun saveFiles(
        bitmap: Bitmap,
        detectionBox: Rect?,
        textContent: String,
        timestamp: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        try {
            val bitmapToSave = if (detectionBox != null) {
                createBitmapWithFrame(bitmap, detectionBox)
            } else {
                bitmap
            }
            val resizedBitmap = resizeBitmap(bitmapToSave, 1024)

            val dateFolder = timestamp.substring(0, 8)  // yyyyMMdd
            val fileName = timestamp.substring(9)        // HHmmss
            val savePath = "${Environment.DIRECTORY_DOWNLOADS}/BarcodeScanner/$dateFolder"

            // テキストファイル保存
            val textValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "$fileName.txt")
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, savePath)
            }
            val textUri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, textValues
            )
            textUri?.let { uri ->
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(textContent.toByteArray())
                }
                Log.d("ScanResultStorage", "テキスト保存成功: $uri")
            }

            // 画像ファイル保存
            val imageValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "$fileName.jpg")
                put(MediaStore.Downloads.MIME_TYPE, "image/jpeg")
                put(MediaStore.Downloads.RELATIVE_PATH, savePath)
            }
            val imageUri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, imageValues
            )
            imageUri?.let { uri ->
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                Log.d("ScanResultStorage", "画像保存成功: $uri")
            }

            if (textUri != null && imageUri != null) {
                onComplete(true, "保存完了: Download/BarcodeScanner/$dateFolder/")
            } else {
                onComplete(false, "保存失敗: URIの作成に失敗")
            }
        } catch (e: Exception) {
            Log.e("ScanResultStorage", "保存失敗", e)
            onComplete(false, "保存失敗: ${e.message}")
        }
    }

    /**
     * Bitmapに緑枠を描画
     */
    private fun createBitmapWithFrame(originalBitmap: Bitmap, detectionBox: Rect?): Bitmap {
        val mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(mutableBitmap)

        detectionBox?.let { box ->
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(153, 0, 255, 0)
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 8f
            }
            canvas.drawRect(
                box.left.toFloat(), box.top.toFloat(),
                box.right.toFloat(), box.bottom.toFloat(),
                paint
            )
        }

        return mutableBitmap
    }

    /**
     * Bitmapをリサイズ（縦を指定サイズに）
     */
    private fun resizeBitmap(bitmap: Bitmap, targetHeight: Int): Bitmap {
        val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val targetWidth = (targetHeight * aspectRatio).toInt()
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }
}