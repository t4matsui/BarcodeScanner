// ui/components/CameraPreviewArea.kt
package com.yamaken.barcodescanner.ui.components

import androidx.camera.core.ImageCapture
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import com.yamaken.barcodescanner.camera.CameraManager

@Composable
fun CameraPreviewArea(
    cameraManager: CameraManager,
    lifecycleOwner: LifecycleOwner,
    onImageCaptureReady: (ImageCapture) -> Unit
) {
    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            cameraManager.startCamera(previewView, lifecycleOwner) { imageCapture ->
                onImageCaptureReady(imageCapture)
            }
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )

    DisposableEffect(Unit) {
        onDispose {
            cameraManager.stopCamera()
        }
    }
}