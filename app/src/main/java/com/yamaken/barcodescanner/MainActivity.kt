package com.yamaken.barcodescanner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "カメラ権限が許可されました", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "カメラ権限が必要です", Toast.LENGTH_SHORT).show()
        }
    }

    private fun scanBitmapForBarcode(bitmap: Bitmap, onResult: (Barcode?) -> Unit) {
        val scanner = BarcodeScanning.getClient()
        val image = InputImage.fromBitmap(bitmap, 0)

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                onResult(barcodes.firstOrNull())
            }
            .addOnFailureListener { e ->
                Log.e("BarcodeScanner", "スキャン失敗", e)
                onResult(null)
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setContent {
            MaterialTheme {
                BarcodeScannerScreen()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    @Composable
    fun BarcodeScannerScreen() {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        var scannedCode by remember { mutableStateOf("") }
        var codeType by remember { mutableStateOf("") }

        // 検知モード：バーコードを探している状態
        var isDetecting by remember { mutableStateOf(true) }
        // 検知完了：バーコードを捉えた状態（カメラ停止）
        var barcodeDetected by remember { mutableStateOf(false) }
        // スキャン中：読み取り実行中
        var isScanning by remember { mutableStateOf(false) }

        var detectionBox by remember { mutableStateOf<android.graphics.Rect?>(null) }
        var previewWidth by remember { mutableStateOf(0) }
        var previewHeight by remember { mutableStateOf(0) }
        var imageWidth by remember { mutableStateOf(0) }
        var imageHeight by remember { mutableStateOf(0) }
        var rotation by remember { mutableStateOf(0) }
        var capturedImage by remember { mutableStateOf<Bitmap?>(null) }

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // カメラプレビュー
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // カメラプレビュー（検知モード時のみ表示）
                if (!barcodeDetected) {
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()

                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }

                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                    .also {
                                        it.setAnalyzer(cameraExecutor, BarcodeAnalyzer(
                                            shouldDetect = { isDetecting && !barcodeDetected },
                                            shouldScan = { isScanning },
                                            onBarcodeDetected = { barcodes, imgWidth, imgHeight, imgRotation, bitmap ->
                                                imageWidth = imgWidth
                                                imageHeight = imgHeight
                                                rotation = imgRotation

                                                if (isDetecting && !barcodeDetected) {
                                                    // 検知モード：バーコードを見つけたら停止
                                                    if (barcodes.isNotEmpty()) {
                                                        barcodeDetected = true
                                                        isDetecting = false
                                                        detectionBox = barcodes.first().boundingBox
                                                        capturedImage = bitmap  // 静止画を保存
                                                    }
                                                } else if (isScanning) {
                                                    // スキャンモード：読み取り実行
                                                    barcodes.firstOrNull()?.let { barcode ->
                                                        scannedCode = barcode.rawValue ?: ""
                                                        codeType = when (barcode.format) {
                                                            Barcode.FORMAT_QR_CODE -> "QRコード"
                                                            Barcode.FORMAT_EAN_13 -> "EAN-13"
                                                            Barcode.FORMAT_EAN_8 -> "EAN-8"
                                                            Barcode.FORMAT_CODE_128 -> "CODE-128"
                                                            Barcode.FORMAT_CODE_39 -> "CODE-39"
                                                            else -> "バーコード"
                                                        }
                                                        isScanning = false
                                                    }
                                                }
                                            }
                                        ))
                                    }

                                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                                try {
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        cameraSelector,
                                        preview,
                                        imageAnalysis
                                    )
                                } catch (e: Exception) {
                                    Log.e("CameraX", "カメラの起動に失敗", e)
                                }
                            }, ContextCompat.getMainExecutor(ctx))

                            previewView
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { coordinates ->
                                previewWidth = coordinates.size.width
                                previewHeight = coordinates.size.height
                            }
                    )
                }

                // 静止画表示（検知完了時）
                capturedImage?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "検知した画像",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                // スキャンガイド（検知モード時のみ）
                if (isDetecting && !barcodeDetected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(250.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            shape = MaterialTheme.shapes.medium
                        ) {}
                    }
                }

                // バーコード検知枠（検知完了時）
                if (barcodeDetected && detectionBox != null && previewWidth > 0 && imageWidth > 0) {
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val box = detectionBox!!

                        val displayWidth = if (rotation == 90 || rotation == 270) imageHeight else imageWidth
                        val displayHeight = if (rotation == 90 || rotation == 270) imageWidth else imageHeight

                        val scaleX = size.width / displayWidth.toFloat()
                        val scaleY = size.height / displayHeight.toFloat()
                        val scale = minOf(scaleX, scaleY)

                        val scaledWidth = displayWidth * scale
                        val scaledHeight = displayHeight * scale
                        val offsetX = (size.width - scaledWidth) / 2
                        val offsetY = (size.height - scaledHeight) / 2

                        drawRect(
                            color = androidx.compose.ui.graphics.Color.Green,
                            topLeft = androidx.compose.ui.geometry.Offset(
                                offsetX + box.left * scale,
                                offsetY + box.top * scale
                            ),
                            size = androidx.compose.ui.geometry.Size(
                                box.width() * scale,
                                box.height() * scale
                            ),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8f)
                        )
                    }
                }
            }

            // コントロール部分
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // ボタン群
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 再検知ボタン
                        OutlinedButton(
                            onClick = {
                                isDetecting = true
                                barcodeDetected = false
                                detectionBox = null
                                capturedImage = null
                                scannedCode = ""
                                codeType = ""
                            },
                            modifier = Modifier.weight(1f),
                            enabled = barcodeDetected && !isScanning
                        ) {
                            Text(text = "再検知")
                        }

                        // スキャンボタン
                        Button(
                            onClick = {
                                // 保存された画像から直接スキャン
                                capturedImage?.let { bitmap ->
                                    isScanning = true
                                    scanBitmapForBarcode(bitmap) { barcode ->
                                        barcode?.let {
                                            scannedCode = it.rawValue ?: ""
                                            codeType = when (it.format) {
                                                Barcode.FORMAT_QR_CODE -> "QRコード"
                                                Barcode.FORMAT_EAN_13 -> "EAN-13"
                                                Barcode.FORMAT_EAN_8 -> "EAN-8"
                                                Barcode.FORMAT_CODE_128 -> "CODE-128"
                                                Barcode.FORMAT_CODE_39 -> "CODE-39"
                                                else -> "バーコード"
                                            }
                                        }
                                        isScanning = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = barcodeDetected && !isScanning && capturedImage != null
                        ) {
                            Text(
                                text = if (isScanning) "実行中..." else "スキャン"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 状態表示
                    Text(
                        text = when {
                            isScanning -> "読み取り中..."
                            barcodeDetected -> "バーコード検知完了"
                            isDetecting -> "バーコードを探しています..."
                            else -> "待機中"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 読み取り結果
                    if (scannedCode.isNotEmpty()) {
                        Text(
                            text = "種類: $codeType",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "内容: $scannedCode",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    } else {
                        Text(
                            text = when {
                                barcodeDetected -> "スキャンボタンを押してください"
                                isDetecting -> "バーコードをカメラに映してください"
                                else -> ""
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    private class BarcodeAnalyzer(
        private val shouldDetect: () -> Boolean,
        private val shouldScan: () -> Boolean,
        private val onBarcodeDetected: (List<Barcode>, Int, Int, Int, Bitmap?) -> Unit
    ) : ImageAnalysis.Analyzer {
        private val scanner = BarcodeScanning.getClient()

        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null && (shouldDetect() || shouldScan())) {
                val image = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )

                // 画像をBitmapに変換
                val bitmap = if (shouldDetect()) {
                    imageProxyToBitmap(imageProxy)
                } else null

                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        onBarcodeDetected(
                            barcodes,
                            mediaImage.width,
                            mediaImage.height,
                            imageProxy.imageInfo.rotationDegrees,
                            bitmap
                        )
                    }
                    .addOnFailureListener { e ->
                        Log.e("BarcodeAnalyzer", "バーコード検出失敗", e)
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }

        private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
            // RGBAに直接変換する方法
            val bitmap = Bitmap.createBitmap(imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888)

            try {
                // ImageProxyからYUV_420_888形式のデータを取得
                val yPlane = imageProxy.planes[0]
                val uPlane = imageProxy.planes[1]
                val vPlane = imageProxy.planes[2]

                val yBuffer = yPlane.buffer
                val uBuffer = uPlane.buffer
                val vBuffer = vPlane.buffer

                val yRowStride = yPlane.rowStride
                val uvRowStride = uPlane.rowStride
                val uvPixelStride = uPlane.pixelStride

                val width = imageProxy.width
                val height = imageProxy.height

                val pixels = IntArray(width * height)

                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val yIndex = y * yRowStride + x
                        val uvIndex = (y / 2) * uvRowStride + (x / 2) * uvPixelStride

                        val Y = (yBuffer.get(yIndex).toInt() and 0xFF) - 16
                        val U = (uBuffer.get(uvIndex).toInt() and 0xFF) - 128
                        val V = (vBuffer.get(uvIndex).toInt() and 0xFF) - 128

                        // YUV to RGB conversion
                        var R = (1.164f * Y + 1.596f * V).toInt()
                        var G = (1.164f * Y - 0.391f * U - 0.813f * V).toInt()
                        var B = (1.164f * Y + 2.018f * U).toInt()

                        R = R.coerceIn(0, 255)
                        G = G.coerceIn(0, 255)
                        B = B.coerceIn(0, 255)

                        pixels[y * width + x] = (0xFF shl 24) or (R shl 16) or (G shl 8) or B
                    }
                }

                bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

            } catch (e: Exception) {
                Log.e("BarcodeAnalyzer", "Bitmap変換エラー", e)
            }

            // 回転を適用
            val matrix = Matrix()
            matrix.postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
    }
}
