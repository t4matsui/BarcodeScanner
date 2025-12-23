package com.yamaken.barcodescanner.barcode

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class BarcodeProcessorTest {

    private lateinit var barcodeProcessor: BarcodeProcessor
    private lateinit var mockScanner: BarcodeScanner
    private lateinit var mockBitmap: Bitmap
    private lateinit var mockTask: Task<List<Barcode>>

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        // BarcodeScanning.getClient()のモック
        mockScanner = mockk(relaxed = true)
        mockkStatic(BarcodeScanning::class)
        every { BarcodeScanning.getClient() } returns mockScanner

        // Bitmapのモック
        mockBitmap = mockk(relaxed = true)
        every { mockBitmap.width } returns 100
        every { mockBitmap.height } returns 100

        // Taskのモック
        mockTask = mockk(relaxed = true)

        // InputImage.fromBitmapのモック
        mockkStatic(InputImage::class)
        every { InputImage.fromBitmap(any(), any()) } returns mockk(relaxed = true)

        barcodeProcessor = BarcodeProcessor()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun detectBarcodes_returnsMultipleBarcodesOnSuccess() {
        // Given
        val mockBarcode1 = mockk<Barcode>(relaxed = true)
        val mockBarcode2 = mockk<Barcode>(relaxed = true)
        val mockBarcode3 = mockk<Barcode>(relaxed = true)
        val expectedBarcodes = listOf(mockBarcode1, mockBarcode2, mockBarcode3)

        var resultBarcodes: List<Barcode>? = null
        val callback: (List<Barcode>) -> Unit = { barcodes ->
            resultBarcodes = barcodes
        }

        // Taskが成功した場合の振る舞いを設定
        every { mockScanner.process(any<InputImage>()) } returns mockTask
        every { mockTask.addOnSuccessListener(any()) } answers {
            val listener = firstArg<OnSuccessListener<List<Barcode>>>()
            listener.onSuccess(expectedBarcodes)
            mockTask
        }
        every { mockTask.addOnFailureListener(any()) } returns mockTask

        // When
        barcodeProcessor.detectBarcodes(mockBitmap, callback)

        // Then
        assertEquals(expectedBarcodes, resultBarcodes)
        assertEquals(3, resultBarcodes?.size)
    }

    @Test
    fun detectBarcodes_returnsEmptyListOnFailure() {
        // Given
        var resultBarcodes: List<Barcode>? = null
        val callback: (List<Barcode>) -> Unit = { barcodes ->
            resultBarcodes = barcodes
        }
        val exception = RuntimeException("Detection failed")

        every { mockScanner.process(any<InputImage>()) } returns mockTask
        every { mockTask.addOnSuccessListener(any()) } returns mockTask
        every { mockTask.addOnFailureListener(any()) } answers {
            val listener = firstArg<OnFailureListener>()
            listener.onFailure(exception)
            mockTask
        }

        // When
        barcodeProcessor.detectBarcodes(mockBitmap, callback)

        // Then
        assertNotNull(resultBarcodes)
        assertTrue(resultBarcodes!!.isEmpty())
    }

    @Test
    fun detectBarcodes_returnsEmptyListWhenNoBarcodesFound() {
        // Given
        var resultBarcodes: List<Barcode>? = null
        val callback: (List<Barcode>) -> Unit = { barcodes ->
            resultBarcodes = barcodes
        }

        every { mockScanner.process(any<InputImage>()) } returns mockTask
        every { mockTask.addOnSuccessListener(any()) } answers {
            val listener = firstArg<OnSuccessListener<List<Barcode>>>()
            listener.onSuccess(emptyList())
            mockTask
        }
        every { mockTask.addOnFailureListener(any()) } returns mockTask

        // When
        barcodeProcessor.detectBarcodes(mockBitmap, callback)

        // Then
        assertNotNull(resultBarcodes)
        assertTrue(resultBarcodes!!.isEmpty())
    }

    @Test
    fun scanSpecificBarcode_returnsCodeAndType() {
        // Given
        val mockBarcode = mockk<Barcode>(relaxed = true)
        every { mockBarcode.rawValue } returns "1234567890123"
        every { mockBarcode.format } returns Barcode.FORMAT_EAN_13

        // When
        val result = barcodeProcessor.scanSpecificBarcode(mockBarcode)

        // Then
        assertEquals("1234567890123", result.first)
        assertEquals("EAN-13", result.second)
    }

    @Test
    fun scanSpecificBarcode_returnsEmptyStringWhenRawValueIsNull() {
        // Given
        val mockBarcode = mockk<Barcode>(relaxed = true)
        every { mockBarcode.rawValue } returns null
        every { mockBarcode.format } returns Barcode.FORMAT_QR_CODE

        // When
        val result = barcodeProcessor.scanSpecificBarcode(mockBarcode)

        // Then
        assertEquals("", result.first)
        assertEquals("QRコード", result.second)
    }

    @Test
    fun scanSpecificBarcode_handlesQRCode() {
        // Given
        val mockBarcode = mockk<Barcode>(relaxed = true)
        every { mockBarcode.rawValue } returns "https://example.com"
        every { mockBarcode.format } returns Barcode.FORMAT_QR_CODE

        // When
        val result = barcodeProcessor.scanSpecificBarcode(mockBarcode)

        // Then
        assertEquals("https://example.com", result.first)
        assertEquals("QRコード", result.second)
    }

    @Test
    fun getBarcodeTypeName_returnsQRCodeForQRFormat() {
        // When
        val result = barcodeProcessor.getBarcodeTypeName(Barcode.FORMAT_QR_CODE)

        // Then
        assertEquals("QRコード", result)
    }

    @Test
    fun getBarcodeTypeName_returnsEAN13ForEAN13Format() {
        // When
        val result = barcodeProcessor.getBarcodeTypeName(Barcode.FORMAT_EAN_13)

        // Then
        assertEquals("EAN-13", result)
    }

    @Test
    fun getBarcodeTypeName_returnsEAN8ForEAN8Format() {
        // When
        val result = barcodeProcessor.getBarcodeTypeName(Barcode.FORMAT_EAN_8)

        // Then
        assertEquals("EAN-8", result)
    }

    @Test
    fun getBarcodeTypeName_returnsCODE128ForCODE128Format() {
        // When
        val result = barcodeProcessor.getBarcodeTypeName(Barcode.FORMAT_CODE_128)

        // Then
        assertEquals("CODE-128", result)
    }

    @Test
    fun getBarcodeTypeName_returnsCODE39ForCODE39Format() {
        // When
        val result = barcodeProcessor.getBarcodeTypeName(Barcode.FORMAT_CODE_39)

        // Then
        assertEquals("CODE-39", result)
    }

    @Test
    fun getBarcodeTypeName_returnsCODE93ForCODE93Format() {
        // When
        val result = barcodeProcessor.getBarcodeTypeName(Barcode.FORMAT_CODE_93)

        // Then
        assertEquals("CODE-93", result)
    }

    @Test
    fun getBarcodeTypeName_returnsCODABARForCODABARFormat() {
        // When
        val result = barcodeProcessor.getBarcodeTypeName(Barcode.FORMAT_CODABAR)

        // Then
        assertEquals("CODABAR", result)
    }

    @Test
    fun getBarcodeTypeName_returnsITFForITFFormat() {
        // When
        val result = barcodeProcessor.getBarcodeTypeName(Barcode.FORMAT_ITF)

        // Then
        assertEquals("ITF", result)
    }

    @Test
    fun getBarcodeTypeName_returnsUPCAForUPCAFormat() {
        // When
        val result = barcodeProcessor.getBarcodeTypeName(Barcode.FORMAT_UPC_A)

        // Then
        assertEquals("UPC-A", result)
    }

    @Test
    fun getBarcodeTypeName_returnsUPCEForUPCEFormat() {
        // When
        val result = barcodeProcessor.getBarcodeTypeName(Barcode.FORMAT_UPC_E)

        // Then
        assertEquals("UPC-E", result)
    }

    @Test
    fun getBarcodeTypeName_returnsDefaultForUnknownFormat() {
        // When
        val result = barcodeProcessor.getBarcodeTypeName(999)

        // Then
        assertEquals("バーコード", result)
    }

    @Test
    fun release_closesScanner() {
        // When
        barcodeProcessor.release()

        // Then
        verify { mockScanner.close() }
    }
}