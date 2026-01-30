package com.yamaken.barcodescanner.camera

import android.content.Context
import android.graphics.Bitmap
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService

class CameraManagerTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var cameraManager: CameraManager
    private lateinit var mockContext: Context
    private lateinit var mockExecutor: ExecutorService
    private lateinit var mockPreviewView: PreviewView
    private lateinit var mockLifecycleOwner: LifecycleOwner
    private lateinit var mockCameraProvider: ProcessCameraProvider
    private lateinit var mockImageCapture: ImageCapture
    private lateinit var mockProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var mockPreviewBuilder: Preview.Builder
    private lateinit var mockImageCaptureBuilder: ImageCapture.Builder

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        mockContext = mockk(relaxed = true)
        mockExecutor = mockk(relaxed = true)
        mockPreviewView = mockk(relaxed = true)
        mockLifecycleOwner = mockk(relaxed = true)
        mockCameraProvider = mockk(relaxed = true)
        mockImageCapture = mockk(relaxed = true)
        mockProviderFuture = mockk(relaxed = true)
        mockPreviewBuilder = mockk(relaxed = true)
        mockImageCaptureBuilder = mockk(relaxed = true)

        // ContextCompat.getMainExecutorのモック
        mockkStatic(ContextCompat::class)
        val mockMainExecutor = mockk<Executor>(relaxed = true)
        every { ContextCompat.getMainExecutor(any()) } returns mockMainExecutor
        every { mockMainExecutor.execute(any()) } answers {
            firstArg<Runnable>().run()
        }

        // ProcessCameraProvider.getInstanceのモック
        mockkStatic(ProcessCameraProvider::class)
        every { ProcessCameraProvider.getInstance(any()) } returns mockProviderFuture
        every { mockProviderFuture.get() } returns mockCameraProvider

        cameraManager = CameraManager(mockContext, mockExecutor)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun startCamera_startsSuccessfully() {
        // Given
        var capturedImageCapture: ImageCapture? = null
        val onReady: (ImageCapture) -> Unit = { imageCapture ->
            capturedImageCapture = imageCapture
        }

        val mockPreview = mockk<Preview>(relaxed = true)
        val mockCamera = mockk<Camera>(relaxed = true)

        // Preview.Builderのモック
        every { mockPreview.setSurfaceProvider(any()) } just Runs
        mockkConstructor(Preview.Builder::class)
        every { constructedWith<Preview.Builder>().build() } returns mockPreview

        // ImageCapture.Builderのモック
        mockkConstructor(ImageCapture.Builder::class)
        every { constructedWith<ImageCapture.Builder>().setCaptureMode(any()) } returns mockImageCaptureBuilder
        every { constructedWith<ImageCapture.Builder>().setTargetRotation(any()) } returns mockImageCaptureBuilder
        every { constructedWith<ImageCapture.Builder>().build() } returns mockImageCapture
        every { mockImageCaptureBuilder.setCaptureMode(any()) } returns mockImageCaptureBuilder
        every { mockImageCaptureBuilder.setTargetRotation(any()) } returns mockImageCaptureBuilder
        every { mockImageCaptureBuilder.build() } returns mockImageCapture

        every { mockCameraProvider.unbindAll() } just Runs
        every {
            mockCameraProvider.bindToLifecycle(
                any(),
                any(),
                any(),
                any()
            )
        } returns mockCamera

        every { mockProviderFuture.addListener(any(), any()) } answers {
            val runnable = firstArg<Runnable>()
            runnable.run()
        }

        // When
        cameraManager.startCamera(mockPreviewView, mockLifecycleOwner, onReady)

        // Then
        assertNotNull(capturedImageCapture)
        verify { mockCameraProvider.unbindAll() }
        verify {
            mockCameraProvider.bindToLifecycle(
                mockLifecycleOwner,
                any(),
                mockPreview,
                mockImageCapture
            )
        }
    }

    @Test
    fun startCamera_logsErrorOnException() {
        // Given
        var onReadyCalled = false
        val onReady: (ImageCapture) -> Unit = {
            onReadyCalled = true
        }
        val exception = RuntimeException("Camera initialization failed")

        every { mockProviderFuture.addListener(any(), any()) } answers {
            val runnable = firstArg<Runnable>()
            runnable.run()
        }

        every { mockCameraProvider.unbindAll() } throws exception

        val mockPreview = mockk<Preview>(relaxed = true)
        every { mockPreview.setSurfaceProvider(any()) } just Runs
        mockkConstructor(Preview.Builder::class)
        every { constructedWith<Preview.Builder>().build() } returns mockPreview

        // When
        cameraManager.startCamera(mockPreviewView, mockLifecycleOwner, onReady)

        // Then
        // 例外が発生してもクラッシュしない
        assertFalse(onReadyCalled)
    }

    @Test
    fun takePicture_returnsBitmapOnSuccess() {
        // Given
        var resultBitmap: Bitmap? = null
        val onSuccess: (Bitmap) -> Unit = { bitmap ->
            resultBitmap = bitmap
        }
        val onError: (Exception) -> Unit = mockk(relaxed = true)

        val mockImageProxy = mockk<ImageProxy>(relaxed = true)
        val mockPlane = mockk<ImageProxy.PlaneProxy>(relaxed = true)
        val mockBuffer = mockk<java.nio.ByteBuffer>(relaxed = true)
        val mockImageInfo = mockk<ImageInfo>(relaxed = true)

        every { mockImageProxy.width } returns 100
        every { mockImageProxy.height } returns 100
        every { mockImageProxy.planes } returns arrayOf(mockPlane)
        every { mockImageProxy.imageInfo } returns mockImageInfo
        every { mockImageInfo.rotationDegrees } returns 0
        every { mockPlane.buffer } returns mockBuffer
        every { mockBuffer.remaining() } returns 100
        every { mockBuffer.get(any<ByteArray>()) } returns mockBuffer
        every { mockImageProxy.close() } returns Unit

        mockkStatic(android.graphics.BitmapFactory::class)
        val mockBitmap = mockk<Bitmap>(relaxed = true)
        every { android.graphics.BitmapFactory.decodeByteArray(any(), any(), any()) } returns mockBitmap

        every { mockExecutor.execute(any()) } answers {
            firstArg<Runnable>().run()
        }

        every { mockImageCapture.takePicture(any(), any()) } answers {
            val callback = secondArg<ImageCapture.OnImageCapturedCallback>()
            callback.onCaptureSuccess(mockImageProxy)
        }

        // When
        cameraManager.takePicture(mockImageCapture, onSuccess, onError)

        // Then
        assertNotNull(resultBitmap)
        verify { mockImageProxy.close() }
        verify(exactly = 0) { onError(any()) }
    }

    @Test
    fun takePicture_callsOnErrorOnFailure() {
        // Given
        val onSuccess: (Bitmap) -> Unit = mockk(relaxed = true)
        var capturedError: Exception? = null
        val onError: (Exception) -> Unit = { error ->
            capturedError = error
        }

        val mockException = mockk<ImageCaptureException>(relaxed = true)
        every { mockException.message } returns "Capture failed"

        every { mockImageCapture.takePicture(any(), any()) } answers {
            val callback = secondArg<ImageCapture.OnImageCapturedCallback>()
            callback.onError(mockException)
        }

        // When
        cameraManager.takePicture(mockImageCapture, onSuccess, onError)

        // Then
        assertNotNull(capturedError)
        assertTrue(capturedError is ImageCaptureException)
        verify(exactly = 0) { onSuccess(any()) }
    }

    @Test
    fun takePicture_returnsErrorBitmapOnBitmapConversionFailure() {
        // Given
        var resultBitmap: Bitmap? = null
        val onSuccess: (Bitmap) -> Unit = { bitmap ->
            resultBitmap = bitmap
        }
        val onError: (Exception) -> Unit = mockk(relaxed = true)

        val mockImageProxy = mockk<ImageProxy>(relaxed = true)
        every { mockImageProxy.width } returns 100
        every { mockImageProxy.height } returns 100
        every { mockImageProxy.planes } throws RuntimeException("Conversion error")
        every { mockImageProxy.close() } returns Unit

        // エラービットマップをモック
        val mockErrorBitmap = mockk<Bitmap>(relaxed = true)
        mockkStatic(Bitmap::class)
        every {
            Bitmap.createBitmap(any<Int>(), any<Int>(), any<Bitmap.Config>())
        } returns mockErrorBitmap
        every { mockErrorBitmap.eraseColor(any()) } just Runs

        every { mockExecutor.execute(any()) } answers {
            firstArg<Runnable>().run()
        }

        every { mockImageCapture.takePicture(any(), any()) } answers {
            val callback = secondArg<ImageCapture.OnImageCapturedCallback>()
            callback.onCaptureSuccess(mockImageProxy)
        }

        // When
        cameraManager.takePicture(mockImageCapture, onSuccess, onError)

        // Then
        // エラー時もBitmapが返される（エラービットマップ）
        assertNotNull(resultBitmap)
        verify { mockImageProxy.close() }
        // onErrorは呼ばれない（エラービットマップで代替）
        verify(exactly = 0) { onError(any()) }
    }

    @Test
    fun stopCamera_unbindsCamera() {
        // Given
        every { mockProviderFuture.addListener(any(), any()) } answers {
            val runnable = firstArg<Runnable>()
            runnable.run()
        }
        every { mockCameraProvider.unbindAll() } returns Unit

        val mockPreview = mockk<Preview>(relaxed = true)
        every { mockPreview.setSurfaceProvider(any()) } returns Unit
        mockkConstructor(Preview.Builder::class)
        every { constructedWith<Preview.Builder>().build() } returns mockPreview

        mockkConstructor(ImageCapture.Builder::class)
        every { constructedWith<ImageCapture.Builder>().setCaptureMode(any()) } returns mockImageCaptureBuilder
        every { constructedWith<ImageCapture.Builder>().setTargetRotation(any()) } returns mockImageCaptureBuilder
        every { constructedWith<ImageCapture.Builder>().build() } returns mockImageCapture

        every { mockCameraProvider.bindToLifecycle(any(), any(), any(), any()) } returns mockk(relaxed = true)

        // カメラを起動してcameraProviderを初期化
        cameraManager.startCamera(mockPreviewView, mockLifecycleOwner) {}

        // When
        cameraManager.stopCamera()

        // Then
        verify(atLeast = 1) { mockCameraProvider.unbindAll() }
    }

    @Test
    fun stopCamera_safeToCallWhenCameraNotStarted() {
        // When/Then - 例外が発生しないことを確認
        cameraManager.stopCamera()
    }
}