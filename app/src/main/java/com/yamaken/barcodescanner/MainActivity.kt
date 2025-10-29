// MainActivity.kt
package com.yamaken.barcodescanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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

        var isCameraMode by remember { mutableStateOf(true) }
        var capturedImage by remember { mutableStateOf<Bitmap?>(null) }
        var detectionBox by remember { mutableStateOf<android.graphics.Rect?>(null) }
        var barcodeDetected by remember { mutableStateOf(false) }
        var scannedCode by remember { mutableStateOf("") }
        var codeType by remember { mutableStateOf("") }
        var isDetecting by remember { mutableStateOf(false) }
        var isScanning by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf("") }

        var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
        var cameraProvider: ProcessCameraProvider? by remember { mutableStateOf(null) }

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // カメラ/静止画表示エリア
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (isCameraMode) {
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                            cameraProviderFuture.addListener({
                                cameraProvider = cameraProviderFuture.get()

                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }

                                val imageCaptureBuilder = ImageCapture.Builder()
                                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                    .setTargetRotation(android.view.Surface.ROTATION_0)
                                imageCapture = imageCaptureBuilder.build()

                                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                                try {
                                    cameraProvider?.unbindAll()
                                    cameraProvider?.bindToLifecycle(
                                        lifecycleOwner,
                                        cameraSelector,
                                        preview,
                                        imageCapture
                                    )
                                } catch (e: Exception) {
                                    Log.e("CameraX", "カメラの起動に失敗", e)
                                }
                            }, ContextCompat.getMainExecutor(ctx))

                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    DisposableEffect(Unit) {
                        onDispose {
                            if (!isCameraMode) {
                                cameraProvider?.unbindAll()
                            }
                        }
                    }
                } else {
                    capturedImage?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "撮影した画像",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    if (barcodeDetected && detectionBox != null) {
                        androidx.compose.foundation.Canvas(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            val box = detectionBox!!
                            val bitmap = capturedImage!!

                            val scaleX = size.width / bitmap.width.toFloat()
                            val scaleY = size.height / bitmap.height.toFloat()
                            val scale = maxOf(scaleX, scaleY)

                            val scaledWidth = bitmap.width * scale
                            val scaledHeight = bitmap.height * scale
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
            }

            // フッター（固定高さ）
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // ボタンエリア
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
                                    onClick = {
                                        isCameraMode = true
                                        capturedImage = null
                                        detectionBox = null
                                        barcodeDetected = false
                                        scannedCode = ""
                                        codeType = ""
                                        errorMessage = ""
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(text = "再撮影")
                                }

                                Button(
                                    onClick = {
                                        isDetecting = true
                                        errorMessage = ""
                                        capturedImage?.let { bitmap ->
                                            detectBarcodeInBitmap(bitmap) { barcodes ->
                                                if (barcodes.isNotEmpty()) {
                                                    barcodeDetected = true
                                                    detectionBox = barcodes.first().boundingBox
                                                } else {
                                                    barcodeDetected = false
                                                    detectionBox = null
                                                    errorMessage = "バーコードが見つかりませんでした"
                                                }
                                                isDetecting = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = !isDetecting && !barcodeDetected && scannedCode.isEmpty()
                                ) {
                                    Text(text = if (isDetecting) "検知中..." else "検知")
                                }

                                Button(
                                    onClick = {
                                        isScanning = true
                                        capturedImage?.let { bitmap ->
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
                                    enabled = barcodeDetected && !isScanning
                                ) {
                                    Text(text = if (isScanning) "実行中..." else "スキャン")
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    Log.d("Camera", "シャッターボタンクリック")
                                    imageCapture?.let { capture ->
                                        Log.d("Camera", "撮影開始")
                                        capture.takePicture(
                                            cameraExecutor,
                                            object : ImageCapture.OnImageCapturedCallback() {
                                                override fun onCaptureSuccess(image: ImageProxy) {
                                                    try {
                                                        Log.d("Camera", "撮影成功")
                                                        val bitmap = imageProxyToBitmap(image)
                                                        Log.d("Camera", "Bitmap変換完了")

                                                        // メインスレッドで状態を更新
                                                        ContextCompat.getMainExecutor(context).execute {
                                                            cameraProvider?.unbindAll()
                                                            capturedImage = bitmap
                                                            isCameraMode = false
                                                            errorMessage = ""
                                                        }
                                                    } catch (e: Exception) {
                                                        Log.e("Camera", "エラー", e)
                                                    } finally {
                                                        image.close()
                                                    }
                                                }

                                                override fun onError(exception: ImageCaptureException) {
                                                    Log.e("Camera", "撮影失敗: ${exception.message}")
                                                }
                                            }
                                        )
                                    } ?: run {
                                        Log.e("Camera", "ImageCapture is null")
                                    }
                                },
                                modifier = Modifier
                                    .size(70.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Gray
                                )
                            ) {}
                        }
                    }

                    // メッセージエリア
                    Column {
                        if (!isCameraMode) {
                            Text(
                                text = when {
                                    scannedCode.isNotEmpty() -> "読み取り完了"
                                    barcodeDetected -> "バーコード検知完了"
                                    errorMessage.isNotEmpty() -> "エラー"
                                    else -> "検知ボタンを押してください"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = if (errorMessage.isNotEmpty())
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            if (scannedCode.isNotEmpty()) {
                                Text(text = "種類: $codeType", style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "内容: $scannedCode", style = MaterialTheme.typography.bodyLarge)
                            } else if (errorMessage.isNotEmpty()) {
                                Text(
                                    text = errorMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else if (barcodeDetected) {
                                Text(
                                    text = "スキャンボタンを押してください",
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
            }
        }
    }

    private fun detectBarcodeInBitmap(bitmap: Bitmap, onResult: (List<Barcode>) -> Unit) {
        val scanner = BarcodeScanning.getClient()
        val image = InputImage.fromBitmap(bitmap, 0)

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                onResult(barcodes)
            }
            .addOnFailureListener { e ->
                Log.e("BarcodeDetector", "検知失敗", e)
                onResult(emptyList())
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

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val width = imageProxy.width
        val height = imageProxy.height

        try {
            if (imageProxy.planes.size == 1) {
                val buffer = imageProxy.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                if (bitmap != null) {
                    val rotation = imageProxy.imageInfo.rotationDegrees
                    if (rotation != 0) {
                        val matrix = Matrix()
                        matrix.postRotate(rotation.toFloat())
                        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    }
                    return bitmap
                }
            }

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.RED)
            return bitmap

        } catch (e: Exception) {
            Log.e("BarcodeAnalyzer", "Bitmap変換エラー", e)
            val errorBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            errorBitmap.eraseColor(android.graphics.Color.BLUE)
            return errorBitmap
        }
    }
}

// build.gradle.kts (Module: app) に以下を追加:
/*
dependencies {
    // CameraX
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")
    
    // ML Kit Barcode Scanning
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    
    // Jetpack Compose
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.material3:material3:1.1.2")
    implementation("androidx.compose.ui:ui:1.5.4")
}
*/

// AndroidManifest.xml に以下を追加:
/*
<uses-feature android:name="android.hardware.camera" />
<uses-permission android:name="android.permission.CAMERA" />
*/