// camera/CameraManager.kt
package com.yamaken.barcodescanner.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService

class CameraManager(
    private val context: Context,
    private val cameraExecutor: ExecutorService
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null

    /**
     * カメラを初期化してプレビューを開始
     */
    fun startCamera(
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner,
        onReady: (ImageCapture) -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

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

                imageCapture?.let { onReady(it) }
            } catch (e: Exception) {
                Log.e("CameraManager", "カメラの起動に失敗", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * 写真を撮影
     */
    fun takePicture(
        imageCapture: ImageCapture,
        onSuccess: (Bitmap) -> Unit,
        onError: (Exception) -> Unit
    ) {
        imageCapture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val bitmap = imageProxyToBitmap(image)
                        ContextCompat.getMainExecutor(context).execute {
                            onSuccess(bitmap)
                        }
                    } catch (e: Exception) {
                        Log.e("CameraManager", "Bitmap変換失敗", e)
                        onError(e)
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraManager", "撮影失敗: ${exception.message}")
                    onError(exception)
                }
            }
        )
    }

    /**
     * カメラを停止
     */
    fun stopCamera() {
        cameraProvider?.unbindAll()
    }

    /**
     * ImageProxyをBitmapに変換
     */
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
            Log.e("CameraManager", "Bitmap変換エラー", e)
            val errorBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            errorBitmap.eraseColor(android.graphics.Color.BLUE)
            return errorBitmap
        }
    }
}