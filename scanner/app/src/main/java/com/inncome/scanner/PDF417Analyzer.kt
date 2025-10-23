package com.inncome.scannertest

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer


class PDF417Analyzer(
    private val onBarcodeDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.PDF_417),
            DecodeHintType.TRY_HARDER to true
        )
        setHints(hints)
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val buffer = imageProxy.planes[0].buffer
        val data = buffer.toByteArray()
        val source = PlanarYUVLuminanceSource(
            data,
            imageProxy.width,
            imageProxy.height,
            0, 0,
            imageProxy.width,
            imageProxy.height,
            false
        )

        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

        try {
            val result = reader.decode(binaryBitmap)
            onBarcodeDetected(result.text)
        } catch (e: NotFoundException) {
            // No se encontró código, continuar
        } catch (e: Exception) {
            // Error al procesar
        } finally {
            imageProxy.close()
        }
    }

    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()
        val data = ByteArray(remaining())
        get(data)
        return data
    }
}
