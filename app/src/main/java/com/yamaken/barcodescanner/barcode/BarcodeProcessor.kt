// barcode/BarcodeProcessor.kt
package com.yamaken.barcodescanner.barcode

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

class BarcodeProcessor {
    private val scanner = BarcodeScanning.getClient()

    /**
     * Bitmapから全てのバーコードを検知
     */
    fun detectBarcodes(bitmap: Bitmap, onResult: (List<Barcode>) -> Unit) {
        val image = InputImage.fromBitmap(bitmap, 0)

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                onResult(barcodes)
            }
            .addOnFailureListener { e ->
                Log.e("BarcodeProcessor", "検知失敗", e)
                onResult(emptyList())
            }
    }

    /**
     * 特定のバーコードをスキャン（Barcodeオブジェクトを直接指定）
     */
    fun scanSpecificBarcode(barcode: Barcode): Pair<String, String> {
        val code = barcode.rawValue ?: ""
        val type = getBarcodeTypeName(barcode.format)
        return Pair(code, type)
    }

    /**
     * バーコードの種類を日本語で取得
     */
    fun getBarcodeTypeName(format: Int): String {
        return when (format) {
            Barcode.FORMAT_QR_CODE -> "QRコード"
            Barcode.FORMAT_EAN_13 -> "EAN-13"
            Barcode.FORMAT_EAN_8 -> "EAN-8"
            Barcode.FORMAT_CODE_128 -> "CODE-128"
            Barcode.FORMAT_CODE_39 -> "CODE-39"
            Barcode.FORMAT_CODE_93 -> "CODE-93"
            Barcode.FORMAT_CODABAR -> "CODABAR"
            Barcode.FORMAT_ITF -> "ITF"
            Barcode.FORMAT_UPC_A -> "UPC-A"
            Barcode.FORMAT_UPC_E -> "UPC-E"
            else -> "バーコード"
        }
    }

    fun release() {
        scanner.close()
    }
}