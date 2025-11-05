// MainActivity.kt
package com.yamaken.barcodescanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.yamaken.barcodescanner.barcode.BarcodeProcessor
import com.yamaken.barcodescanner.camera.CameraManager
import com.yamaken.barcodescanner.storage.ScanResultStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val barcodeProcessor = BarcodeProcessor()
    private lateinit var cameraManager: CameraManager
    private lateinit var storage: ScanResultStorage

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

        cameraManager = CameraManager(this, cameraExecutor)
        storage = ScanResultStorage(this)

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
        barcodeProcessor.release()
    }

    @Composable
    fun BarcodeScannerScreen() {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current

        var isCameraMode by remember { mutableStateOf(true) }
        var capturedImage by remember { mutableStateOf<Bitmap?>(null) }
        var detectionBox by remember { mutableStateOf<Rect?>(null) }
        var barcodeDetected by remember { mutableStateOf(false) }
        var scannedCode by remember { mutableStateOf("") }
        var codeType by remember { mutableStateOf("") }
        var isDetecting by remember { mutableStateOf(false) }
        var isScanning by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf("") }
        var isSaving by remember { mutableStateOf(false) }
        var saveMessage by remember { mutableStateOf("") }
        var scanTimestamp by remember { mutableStateOf("") }
        var detectedBox by remember { mutableStateOf<Rect?>(null) }

        var currentImageCapture: ImageCapture? by remember { mutableStateOf(null) }

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
                            cameraManager.startCamera(previewView, lifecycleOwner) { imageCapture ->
                                currentImageCapture = imageCapture
                            }
                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    DisposableEffect(Unit) {
                        onDispose {
                            if (!isCameraMode) {
                                cameraManager.stopCamera()
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
                        Canvas(
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
                                            barcodeProcessor.detectBarcode(bitmap) { barcodes ->
                                                if (barcodes.isNotEmpty()) {
                                                    barcodeDetected = true
                                                    detectionBox = barcodes.first().boundingBox
                                                    detectedBox = barcodes.first().boundingBox
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
                                            barcodeProcessor.scanBarcode(bitmap) { barcode ->
                                                barcode?.let {
                                                    scannedCode = it.rawValue ?: ""
                                                    codeType = barcodeProcessor.getBarcodeTypeName(it.format)
                                                    // スキャン時刻を記録
                                                    val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                                                    scanTimestamp = sdf.format(Date())
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
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .background(Color.Gray, CircleShape)
                                    .border(4.dp, Color.Gray, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Button(
                                    onClick = {
                                        Log.d("Camera", "シャッターボタンクリック")
                                        currentImageCapture?.let { imageCapture ->
                                            Log.d("Camera", "撮影開始")
                                            cameraManager.takePicture(
                                                imageCapture,
                                                onSuccess = { bitmap ->
                                                    Log.d("Camera", "撮影成功")
                                                    cameraManager.stopCamera()
                                                    capturedImage = bitmap
                                                    isCameraMode = false
                                                    errorMessage = ""
                                                },
                                                onError = { e ->
                                                    Log.e("Camera", "撮影失敗", e)
                                                }
                                            )
                                        } ?: run {
                                            Log.e("Camera", "ImageCapture is null")
                                        }
                                    },
                                    modifier = Modifier
                                        .size(64.dp),
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(0.dp)
                                ) {}
                            }
                        }
                    }

                    // メッセージエリア
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
                                        barcodeDetected -> "バーコード検知完了"
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

                                // 保存ボタン（スキャン完了時のみ表示）
                                if (scannedCode.isNotEmpty()) {
                                    Button(
                                        onClick = {
                                            isSaving = true
                                            saveMessage = ""
                                            capturedImage?.let { bitmap ->
                                                storage.saveScanResult(
                                                    bitmap = bitmap,
                                                    detectionBox = detectedBox,
                                                    scanCode = scannedCode,
                                                    scanType = codeType,
                                                    timestamp = scanTimestamp
                                                ) { success, message ->
                                                    isSaving = false
                                                    saveMessage = message
                                                }
                                            }
                                        },
                                        enabled = !isSaving,
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text(text = if (isSaving) "保存中..." else "保存")
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
}

// 新しいクラスファイルを作成してください：
// - barcode/BarcodeProcessor.kt
// - camera/CameraManager.kt
// - storage/ScanResultStorage.kt
