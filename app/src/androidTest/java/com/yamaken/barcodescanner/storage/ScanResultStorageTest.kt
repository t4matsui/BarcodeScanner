package com.yamaken.barcodescanner.storage

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.provider.MediaStore
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.OutputStream

class ScanResultStorageTest {

    private lateinit var scanResultStorage: ScanResultStorage
    private lateinit var mockContext: Context
    private lateinit var mockContentResolver: ContentResolver
    private lateinit var mockBitmap: Bitmap
    private lateinit var mockOutputStream: OutputStream

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        mockContext = mockk(relaxed = true)
        mockContentResolver = mockk(relaxed = true)
        mockBitmap = mockk(relaxed = true)
        mockOutputStream = mockk(relaxed = true)

        every { mockContext.contentResolver } returns mockContentResolver
        every { mockBitmap.width } returns 1920
        every { mockBitmap.height } returns 1080
        every { mockBitmap.copy(any(), any()) } returns mockBitmap
        every { mockOutputStream.write(any<ByteArray>()) } just Runs
        every { mockOutputStream.close() } just Runs

        mockkStatic(Bitmap::class)
        every { Bitmap.createScaledBitmap(any(), any(), any(), any()) } returns mockBitmap

        mockkConstructor(android.graphics.Canvas::class)
        every { anyConstructed<android.graphics.Canvas>().drawRect(any(), any(), any(), any(), any()) } just Runs

        scanResultStorage = ScanResultStorage(mockContext)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun saveScanResult_savesSuccessfully() {
        // Given
        val detectionBox = Rect(100, 100, 200, 200)
        val scanCode = "1234567890"
        val scanType = "EAN-13"
        val timestamp = "20250101_123045"

        var resultSuccess = false
        var resultMessage = ""
        val onComplete: (Boolean, String) -> Unit = { success, message ->
            resultSuccess = success
            resultMessage = message
        }

        val mockTextUri = mockk<Uri>(relaxed = true)
        val mockImageUri = mockk<Uri>(relaxed = true)

        every {
            mockContentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                any()
            )
        } returnsMany listOf(mockTextUri, mockImageUri)

        every { mockContentResolver.openOutputStream(any()) } returns mockOutputStream
        every { mockBitmap.compress(any(), any(), any()) } returns true

        // When
        scanResultStorage.saveScanResult(
            mockBitmap,
            detectionBox,
            scanCode,
            scanType,
            timestamp,
            onComplete
        )

        // Then
        assertTrue(resultSuccess)
        assertTrue(resultMessage.contains("保存成功"))
        assertTrue(resultMessage.contains("20250101"))
        verify(exactly = 2) { mockContentResolver.insert(any(), any()) }
        verify(exactly = 2) { mockContentResolver.openOutputStream(any()) }
    }

    @Test
    fun saveScanResult_savesSuccessfullyWhenDetectionBoxIsNull() {
        // Given
        val scanCode = "TEST123"
        val scanType = "QRコード"
        val timestamp = "20250115_180530"

        var resultSuccess = false
        val onComplete: (Boolean, String) -> Unit = { success, _ ->
            resultSuccess = success
        }

        val mockTextUri = mockk<Uri>(relaxed = true)
        val mockImageUri = mockk<Uri>(relaxed = true)

        every {
            mockContentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                any()
            )
        } returnsMany listOf(mockTextUri, mockImageUri)

        every { mockContentResolver.openOutputStream(any()) } returns mockOutputStream
        every { mockBitmap.compress(any(), any(), any()) } returns true

        // When
        scanResultStorage.saveScanResult(
            mockBitmap,
            null,
            scanCode,
            scanType,
            timestamp,
            onComplete
        )

        // Then
        assertTrue(resultSuccess)
    }

    @Test
    fun saveScanResult_failsWhenTextUriIsNull() {
        // Given
        val scanCode = "FAIL001"
        val scanType = "CODE-128"
        val timestamp = "20250201_090000"

        var resultSuccess = true
        var resultMessage = ""
        val onComplete: (Boolean, String) -> Unit = { success, message ->
            resultSuccess = success
            resultMessage = message
        }

        every {
            mockContentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                any()
            )
        } returns null

        // When
        scanResultStorage.saveScanResult(
            mockBitmap,
            null,
            scanCode,
            scanType,
            timestamp,
            onComplete
        )

        // Then
        assertFalse(resultSuccess)
        assertTrue(resultMessage.contains("URIの作成に失敗"))
    }

    @Test
    fun saveScanResult_failsWhenImageUriIsNull() {
        // Given
        val scanCode = "FAIL002"
        val scanType = "CODE-39"
        val timestamp = "20250301_150000"

        var resultSuccess = true
        var resultMessage = ""
        val onComplete: (Boolean, String) -> Unit = { success, message ->
            resultSuccess = success
            resultMessage = message
        }

        val mockTextUri = mockk<Uri>(relaxed = true)

        every {
            mockContentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                any()
            )
        } returnsMany listOf(mockTextUri, null)

        every { mockContentResolver.openOutputStream(mockTextUri) } returns mockOutputStream

        // When
        scanResultStorage.saveScanResult(
            mockBitmap,
            null,
            scanCode,
            scanType,
            timestamp,
            onComplete
        )

        // Then
        assertFalse(resultSuccess)
        assertTrue(resultMessage.contains("URIの作成に失敗"))
    }

    @Test
    fun saveScanResult_failsOnException() {
        // Given
        val scanCode = "ERROR001"
        val scanType = "EAN-8"
        val timestamp = "20250401_120000"

        var resultSuccess = true
        var resultMessage = ""
        val onComplete: (Boolean, String) -> Unit = { success, message ->
            resultSuccess = success
            resultMessage = message
        }

        every {
            mockContentResolver.insert(any(), any())
        } throws RuntimeException("Database error")

        // When
        scanResultStorage.saveScanResult(
            mockBitmap,
            null,
            scanCode,
            scanType,
            timestamp,
            onComplete
        )

        // Then
        assertFalse(resultSuccess)
        assertTrue(resultMessage.contains("保存失敗"))
        assertTrue(resultMessage.contains("Database error"))
    }

    @Test
    fun saveScanResult_splitsFolderAndFileNameCorrectlyFromTimestamp() {
        // Given
        val timestamp = "20250512_143025"
        val expectedDateFolder = "20250512"
        val expectedFileName = "143025"

        val mockTextUri = mockk<Uri>(relaxed = true)
        val mockImageUri = mockk<Uri>(relaxed = true)

        val capturedContentValues = mutableListOf<ContentValues>()

        every {
            mockContentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                capture(capturedContentValues)
            )
        } returnsMany listOf(mockTextUri, mockImageUri)

        every { mockContentResolver.openOutputStream(any()) } returns mockOutputStream
        every { mockBitmap.compress(any(), any(), any()) } returns true

        // When
        scanResultStorage.saveScanResult(
            mockBitmap,
            null,
            "TEST",
            "QRコード",
            timestamp
        ) { _, _ -> }

        // Then
        assertEquals(2, capturedContentValues.size)

        val textValues = capturedContentValues[0]
        val imageValues = capturedContentValues[1]

        assertTrue(textValues.getAsString(MediaStore.Downloads.DISPLAY_NAME).contains(expectedFileName))
        assertTrue(textValues.getAsString(MediaStore.Downloads.RELATIVE_PATH).contains(expectedDateFolder))

        assertTrue(imageValues.getAsString(MediaStore.Downloads.DISPLAY_NAME).contains(expectedFileName))
        assertTrue(imageValues.getAsString(MediaStore.Downloads.RELATIVE_PATH).contains(expectedDateFolder))
    }

    @Test
    fun saveScanResult_writesCorrectContentToTextFile() {
        // Given
        val scanCode = "9876543210"
        val scanType = "EAN-13"
        val timestamp = "20250601_100000"

        val mockTextUri = mockk<Uri>(relaxed = true)
        val mockImageUri = mockk<Uri>(relaxed = true)

        val capturedBytes = slot<ByteArray>()

        every {
            mockContentResolver.insert(any(), any())
        } returnsMany listOf(mockTextUri, mockImageUri)

        every { mockContentResolver.openOutputStream(mockTextUri) } returns mockOutputStream
        every { mockContentResolver.openOutputStream(mockImageUri) } returns mockOutputStream
        every { mockOutputStream.write(capture(capturedBytes)) } just Runs
        every { mockBitmap.compress(any(), any(), any()) } returns true

        // When
        scanResultStorage.saveScanResult(
            mockBitmap,
            null,
            scanCode,
            scanType,
            timestamp
        ) { _, _ -> }

        // Then
        val writtenText = String(capturedBytes.captured)
        assertTrue(writtenText.contains("種類: $scanType"))
        assertTrue(writtenText.contains("内容: $scanCode"))
        assertTrue(writtenText.contains("日時: $timestamp"))
    }

    @Test
    fun saveScanResult_resizesBitmapCorrectly() {
        // Given
        val originalWidth = 3000
        val originalHeight = 2000
        val targetHeight = 1024
        val expectedWidth = (targetHeight * originalWidth.toFloat() / originalHeight).toInt()

        every { mockBitmap.width } returns originalWidth
        every { mockBitmap.height } returns originalHeight

        val mockTextUri = mockk<Uri>(relaxed = true)
        val mockImageUri = mockk<Uri>(relaxed = true)

        every {
            mockContentResolver.insert(any(), any())
        } returnsMany listOf(mockTextUri, mockImageUri)

        every { mockContentResolver.openOutputStream(any()) } returns mockOutputStream
        every { mockBitmap.compress(any(), any(), any()) } returns true

        // When
        scanResultStorage.saveScanResult(
            mockBitmap,
            null,
            "TEST",
            "QRコード",
            "20250701_120000"
        ) { _, _ -> }

        // Then
        verify {
            Bitmap.createScaledBitmap(
                mockBitmap,
                expectedWidth,
                targetHeight,
                true
            )
        }
    }
}