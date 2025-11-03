package com.inncome.scanner.analyzer

import android.annotation.SuppressLint
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.inncome.scanner.data.DniData

class PDF417Analyzer(
    private val onBarcodeDetected: (String, DniData?) -> Unit,
    private val onError: ((Exception) -> Unit)? = null
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "MLKitPDF417"
    }

    private var lastProcessedTime = 0L
    private val processingInterval = 500L

    private val scanner = BarcodeScanning.getClient()

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastProcessedTime < processingInterval) {
            imageProxy.close()
            return
        }
        lastProcessedTime = currentTime

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        if (barcode.format == Barcode.FORMAT_PDF417) {
                            val rawValue = barcode.rawValue ?: continue
                            Log.d(TAG, "📦 Código PDF417 detectado")

                            // Parsear datos del DNI argentino
                            val dniData = parseDniArgentino(rawValue)

                            onBarcodeDetected(rawValue, dniData)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error en ML Kit: ${e.message}")
                    onError?.invoke(e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    /**
     * Parsea los datos del DNI argentino desde el código PDF417
     * Formato DNI Argentino (ejemplo):
     * @APELLIDO@NOMBRE@SEXO@DNI@EJEMPLAR@FECHA_NAC@FECHA_EMISION@...
     */
    private fun parseDniArgentino(rawData: String): DniData? {
        return try {
            if (!rawData.contains("@")) {
                Log.w(TAG, "⚠️ Formato no válido: no contiene separadores @")
                return null
            }

            val campos = rawData.split("@")
            Log.d(TAG, "📊 Total de campos encontrados: ${campos.size}")

            campos.forEachIndexed { index, campo ->
                Log.d(TAG, "   Campo[$index]: '$campo' (${campo.length} chars)")
            }

            // Estructura típica del DNI argentino PDF417:
            // [0] = Tipo de documento (vacío o código)
            // [1] = Apellido(s)
            // [2] = Nombre(s)
            // [3] = Sexo (M/F)
            // [4] = DNI (número)
            // [5] = Ejemplar (A, B, etc.)
            // [6] = Fecha de nacimiento (DD/MM/AAAA)
            // [7] = Fecha de emisión (DD/MM/AAAA)
            // [8+] = Otros datos (tramite, etc.)

            if (campos.size < 8) {
                Log.w(TAG, "⚠️ Formato incompleto: solo ${campos.size} campos")
                return null
            }

            val dniData = DniData(
                tipoDocumento = campos.getOrNull(0)?.trim() ?: "",
                apellido = campos.getOrNull(1)?.trim() ?: "",
                nombre = campos.getOrNull(2)?.trim() ?: "",
                sexo = campos.getOrNull(3)?.trim() ?: "",
                dni = campos.getOrNull(4)?.trim() ?: "",
                ejemplar = campos.getOrNull(5)?.trim() ?: "",
                fechaNacimiento = campos.getOrNull(6)?.trim() ?: "",
                fechaEmision = campos.getOrNull(7)?.trim() ?: "",
                numeroTramite = campos.getOrNull(8)?.trim() ?: "",
                rawData = rawData
            )

            Log.d(TAG, "✅ DNI PARSEADO:")
            Log.d(TAG, "   📄 Tipo: ${dniData.tipoDocumento}")
            Log.d(TAG, "   👤 Nombre: ${dniData.nombre} ${dniData.apellido}")
            Log.d(TAG, "   🆔 DNI: ${dniData.dni}")
            Log.d(TAG, "   ⚧ Sexo: ${dniData.sexo}")
            Log.d(TAG, "   📅 Fecha Nac: ${dniData.fechaNacimiento}")
            Log.d(TAG, "   📋 Ejemplar: ${dniData.ejemplar}")
            Log.d(TAG, "   🗓️ Emisión: ${dniData.fechaEmision}")
            Log.d(TAG, "   #️⃣ Trámite: ${dniData.numeroTramite}")

            // Validación del DNI
            if (dniData.dni.isEmpty() || !dniData.dni.matches(Regex("\\d{7,9}"))) {
                Log.e(TAG, "❌ DNI inválido: '${dniData.dni}'")
                return null
            }

            dniData
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error parseando DNI: ${e.message}", e)
            null
        }
    }
}
