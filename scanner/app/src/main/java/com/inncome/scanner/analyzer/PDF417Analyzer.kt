package com.inncome.scanner.analyzer

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer

class PDF417Analyzer(
    private val onBarcodeDetected: (String) -> Unit,
    private val onError: ((Exception) -> Unit)? = null
) : ImageAnalysis.Analyzer {

    private var lastProcessedTime = 0L
    private val processingInterval = 500L

    private val reader = MultiFormatReader().apply {
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.PDF_417),
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.PURE_BARCODE to false,
            DecodeHintType.ALSO_INVERTED to true
        )
        setHints(hints)
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        // Control de frecuencia
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastProcessedTime < processingInterval) {
            imageProxy.close()
            return
        }
        lastProcessedTime = currentTime

        try {
            val (binaryBitmap, source) = preprocessImage(imageProxy)
            val result = decodeWithMultipleStrategies(binaryBitmap, source)

            result?.let {
                onBarcodeDetected(it.text)
            }

        } catch (e: Exception) {
            onError?.invoke(e)
        } finally {
            imageProxy.close()
        }
    }

    private fun preprocessImage(imageProxy: ImageProxy): Pair<BinaryBitmap, PlanarYUVLuminanceSource> {
        val buffer = imageProxy.planes[0].buffer
        val data = buffer.toByteArray()

        val width = imageProxy.width
        val height = imageProxy.height

        // Analizar solo el centro de la imagen (80% ancho, 60% alto)
        val cropWidth = (width * 0.8).toInt()
        val cropHeight = (height * 0.6).toInt()
        val cropX = (width - cropWidth) / 2
        val cropY = (height - cropHeight) / 2

        val source = PlanarYUVLuminanceSource(
            data,
            width,
            height,
            cropX, cropY,
            cropWidth,
            cropHeight,
            false
        )

        val bitmap = BinaryBitmap(HybridBinarizer(source))
        return Pair(bitmap, source)
    }

    private fun decodeWithMultipleStrategies(
        bitmap: BinaryBitmap,
        source: PlanarYUVLuminanceSource
    ): Result? {
        return try {
            reader.decode(bitmap)
        } catch (e: NotFoundException) {
            try {
                val alternateBinarizer = HybridBinarizer(source)
                val alternateBitmap = BinaryBitmap(alternateBinarizer)
                reader.decode(alternateBitmap)
            } catch (e2: NotFoundException) {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()
        val data = ByteArray(remaining())
        get(data)
        return data
    }
}