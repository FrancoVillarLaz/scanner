package com.inncome.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.inncome.scanner.adapter.HistoryAdapter
import com.inncome.scanner.databinding.ActivityMainBinding
import com.inncome.scanner.analyzer.PDF417Analyzer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.view.View
import androidx.activity.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.inncome.scanner.data.entities.Nomina
import com.inncome.scanner.dialog.NominaSelectionDialog
import com.inncome.scanner.ui.HistoryState
import com.inncome.scanner.ui.HistoryViewModel
import com.inncome.scanner.ui.ValidationResult
import kotlinx.coroutines.launch
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.*
import java.nio.ByteBuffer

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PDF417Scanner"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<*>
    private lateinit var historyAdapter: HistoryAdapter

    private val historyViewModel: HistoryViewModel by viewModels()

    private var lastScanTime = 0L
    private var isProcessing = false
    private val establecimientoId: Long = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        setupBottomSheet()
        setupRecyclerView()
        observeViewModel()

        // ✅ Cargar historial inicial
        historyViewModel.cargarHistorialInicial(establecimientoId)

        // ✅ Iniciar cámara
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // ✅ Configurar scan frame
        setupScanFrame()
    }

    // ✅ Configurar RecyclerView y paginación
    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter()

        binding.rvHistoryIngresos.apply {
            adapter = historyAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)

            // ✅ Callback de paginación
            historyAdapter.onLoadMore = {
                historyViewModel.cargarMasDatos(establecimientoId)
            }
        }
    }

    // ✅ Observar cambios del ViewModel
    private fun observeViewModel() {
        // Observar estado del historial
        lifecycleScope.launch {
            historyViewModel.historial.collect { state ->
                when (state) {
                    is HistoryState.Loading -> {
                        // Opcional: Mostrar loading
                    }
                    is HistoryState.Success -> {
                        historyAdapter.submitList(state.items)
                        historyAdapter.setLoadingState(false)

                        binding.tvHistoryCount.text =
                            "${state.totalCount} registro${if (state.totalCount != 1) "s" else ""}"
                        binding.emptyStateHistory.visibility = View.GONE
                        binding.rvHistoryIngresos.visibility = View.VISIBLE

                        Log.d(TAG, "✅ Historial actualizado: ${state.items.size} items")
                    }
                    is HistoryState.Error -> {
                        Toast.makeText(this@MainActivity, state.message, Toast.LENGTH_SHORT).show()
                        historyAdapter.setLoadingState(false)
                    }
                    is HistoryState.Empty -> {
                        mostrarEstadoVacio()
                        historyAdapter.setLoadingState(false)
                    }
                }
            }
        }

        // Observar resultado de validación
        lifecycleScope.launch {
            historyViewModel.validationResult.collect { result ->
                result?.let {
                    handleValidationResult(it)
                    historyViewModel.clearValidationResult()
                }
            }
        }

        // Observar estado de carga
        lifecycleScope.launch {
            historyViewModel.isLoading.collect { isLoading ->
                historyAdapter.setLoadingState(isLoading)
            }
        }
    }

    // ✅ Manejar resultados de validación
    private fun handleValidationResult(result: ValidationResult) {
        when (result) {
            is ValidationResult.MultipleNominas -> {
                mostrarDialogoSeleccionNomina(result.dni, result.nominas)
            }
            is ValidationResult.IngresoRegistrado -> {
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()

                // ✅ Recargar último ingreso después de un pequeño delay
                binding.root.postDelayed({
                    historyViewModel.cargarUltimoIngreso(establecimientoId)
                    animarContador()
                }, 500)

                isProcessing = false
            }
            is ValidationResult.Error -> {
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                isProcessing = false
            }
            is ValidationResult.SingleNomina -> {
                // Este caso podría no usarse si el backend siempre retorna 201 o 200
                isProcessing = false
            }
        }
    }

    // ✅ Configurar BottomSheet
    private fun setupBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheetHistory)

        bottomSheetBehavior.apply {
            peekHeight = 220
            state = BottomSheetBehavior.STATE_COLLAPSED
            isHideable = false
            isFitToContents = false

            addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    when (newState) {
                        BottomSheetBehavior.STATE_EXPANDED -> {
                            Log.d(TAG, "BottomSheet expandido")
                        }
                        BottomSheetBehavior.STATE_COLLAPSED -> {
                            Log.d(TAG, "BottomSheet colapsado")
                        }
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {}
            })
        }
    }

    // ✅ Configurar frame de escaneo
    private fun setupScanFrame() {
        binding.root.post {
            val loc = IntArray(2)
            binding.scanFrame.getLocationOnScreen(loc)

            binding.overlayView.scanRect = RectF(
                loc[0].toFloat(),
                loc[1].toFloat(),
                loc[0] + binding.scanFrame.width.toFloat(),
                loc[1] + binding.scanFrame.height.toFloat()
            )

            binding.overlayView.startLaserAnimation()
            binding.overlayView.invalidate()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // ✅ DETECTAR DISPOSITIVO DE BAJA GAMA
            val isLowEndDevice = isLowEndDevice()

            val previewResolution = if (isLowEndDevice) Size(640, 480) else Size(1280, 720)
            val analysisResolution = if (isLowEndDevice) Size(640, 480) else Size(1280, 720)

            val preview = Preview.Builder()
                .setTargetResolution(previewResolution)
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(analysisResolution)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, createOptimizedPDF417Analyzer(isLowEndDevice))
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
                setupAutoFocus(camera)
            } catch (e: Exception) {
                Log.e(TAG, "Error al iniciar cámara", e)
                // ✅ FALLBACK: intentar con resolución más baja
                tryFallbackCameraSetup(cameraProvider)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun isLowEndDevice(): Boolean {
        return (Runtime.getRuntime().availableProcessors() <= 4 ||
                android.os.Build.MANUFACTURER.contains("motorola", ignoreCase = true) ||
                android.os.Build.MODEL.contains("moto e", ignoreCase = true))
    }

    private fun tryFallbackCameraSetup(cameraProvider: ProcessCameraProvider) {
        try {
            val preview = Preview.Builder()
                .setTargetResolution(Size(640, 480))
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(480, 360)) // ✅ RESOLUCIÓN MUY BAJA
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, createLowEndPDF417Analyzer())
                }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalyzer
            )

            Log.d(TAG, "✅ Cámara iniciada en modo baja resolución")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error incluso en modo fallback", e)
            Toast.makeText(this, "Error crítico de cámara", Toast.LENGTH_LONG).show()
        }
    }
    // ✅ CONFIGURAR AUTOFOCUS MÁS SIMPLE
    private fun setupAutoFocus(camera: Camera) {
        binding.viewFinder.post {
            try {
                val factory = binding.viewFinder.meteringPointFactory
                val point = factory.createPoint(0.8f, 0.8f) // ✅ SOLO CENTRO

                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                    .setAutoCancelDuration(2, java.util.concurrent.TimeUnit.SECONDS) // ✅ MENOS TIEMPO
                    .build()

                camera.cameraControl.startFocusAndMetering(action)

                // ✅ INTERVALO MAYOR EN BAJA GAMA
                val interval = if (isLowEndDevice()) 2000L else 1000L
                binding.viewFinder.postDelayed({
                    setupAutoFocus(camera)
                }, interval)

            } catch (e: Exception) {
                Log.e(TAG, "Error en autofocus: ${e.message}")
                // ✅ NO REINTENTAR SI FALLA
            }
        }
    }
    // ✅ AGREGAR ESTA VARIABLE
    private var processingTimeoutRunnable: Runnable? = null

    private fun procesarPDF417(data: String) {
        // ✅ LIMPIAR TIMEOUT ANTERIOR
        processingTimeoutRunnable?.let { binding.root.removeCallbacks(it) }

        Log.d(TAG, "🔍 Procesando código: $data")

        val dni = extraerDNI(data)
        Log.d(TAG, "📋 DNI extraído: '$dni'")

        if (dni.isEmpty()) {
            Log.d(TAG, "❌ No se pudo extraer DNI")
            resetProcessingState()
            return
        }

        // ✅ TIMEOUT POR SI FALLA EL VIEWMODEL
        processingTimeoutRunnable = Runnable {
            Log.e(TAG, "⏰ Timeout en procesamiento")
            resetProcessingState()
            Toast.makeText(this, "Timeout en escaneo", Toast.LENGTH_SHORT).show()
        }
        binding.root.postDelayed(processingTimeoutRunnable!!, 10000) // 10 segundos

        // ✅ Delegar al ViewModel
        Log.d(TAG, "✅ DNI válido: $dni, enviando a validación...")
        historyViewModel.validarDni(dni, establecimientoId)
    }

    private fun resetProcessingState() {
        isProcessing = false
        processingTimeoutRunnable?.let { binding.root.removeCallbacks(it) }
        processingTimeoutRunnable = null
    }

    private fun mostrarDialogoSeleccionNomina(dni: String, nominas: List<Nomina>) {
        val dialog = NominaSelectionDialog(this, nominas) { nominaSeleccionada ->
            historyViewModel.registrarIngresoPorNomina(
                establecimientoId,
                nominaSeleccionada.id.toString()
            )
        }
        dialog.show()
    }




    private fun createLowEndPDF417Analyzer(): ImageAnalysis.Analyzer {
        return ImageAnalysis.Analyzer { imageProxy ->
            val currentTime = System.currentTimeMillis()

            // 🛡️ 1. Control de Tasa (Throttling) para baja gama
            // Espera 3 segundos (3000 ms) entre escaneos para no saturar la CPU.
            if (currentTime - lastScanTime < 3000 || isProcessing) {
                imageProxy.close()
                return@Analyzer
            }

            // 🛡️ Bloquea el procesamiento para evitar analizar el mismo frame más de una vez
            isProcessing = true

            try {
                // 💡 SOLUCIÓN AQUÍ: Convertir de ImageProxy (YUV) a un solo ByteArray (NV21)
                // Necesitas esta función para manejar los 3 planos YUV y el padding
                val data = imageProxy.toNV21ByteArray()

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

                val reader = MultiFormatReader().apply {
                    val hints = mapOf(
                        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.PDF_417),
                        DecodeHintType.TRY_HARDER to false, // ✅ Optimización clave: Desactivado para velocidad
                        DecodeHintType.PURE_BARCODE to true
                    )
                    setHints(hints)
                }

                val result = reader.decode(binaryBitmap)

                lastScanTime = currentTime
                // isProcessing se reiniciará en el runOnUiThread para evitar bloqueos

                // 📞 Enviar resultado al hilo principal (UI)
                runOnUiThread {
                    Log.d(TAG, "📦 Código detectado en baja gama: ${result.text}")
                    procesarPDF417(result.text)
                    isProcessing = false // Permite el siguiente escaneo
                }

            } catch (e: NotFoundException) {
                // Silencioso, el código no fue encontrado en este frame
                isProcessing = false
            } catch (e: Exception) {
                Log.e(TAG, "Error en análisis baja gama: ${e.message}")
                // Aseguramos que el bloqueo se libere ante otros errores
                runOnUiThread {
                    isProcessing = false
                }
            } finally {
                // MUY IMPORTANTE: liberar el buffer de la imagen
                imageProxy.close()
            }
        }
    }

// --- Función de Extensión Necesaria (Reemplaza a buffer.toByteArray()) ---

    /**
     * Convierte los 3 planos (YUV) de un ImageProxy a un solo ByteArray en formato NV21.
     * NOTA: Esta implementación debe estar en un archivo de utilidades o en la misma clase.
     */
    fun ImageProxy.toNV21ByteArray(): ByteArray {
        val planes = this.planes
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        // Mover el buffer al inicio para leerlo
        yBuffer.rewind()
        uBuffer.rewind()
        vBuffer.rewind()

        val ySize = yBuffer.remaining()

        // La luminancia (Y) tiene todos los datos, la crominancia (U/V) suele ser 1/4 del total.
        val data = ByteArray(ySize + ySize / 2)

        // Copia de Y (Luminancia)
        yBuffer.get(data, 0, ySize)

        // Copia de V y U (Crominancia) en formato intercalado (NV21 es YYYYVUVU)
        var offset = ySize
        val vRowStride = vPlane.rowStride
        val uRowStride = uPlane.rowStride
        val vPixelStride = vPlane.pixelStride
        val uPixelStride = uPlane.pixelStride

        // Si los strides no son simples, se debe copiar byte por byte (más lento, pero necesario)
        if (vPixelStride == 1 && uPixelStride == 1 && vRowStride == uRowStride) {
            // Optimización para el caso ideal (más rápido)
            vBuffer.get(data, offset, ySize / 4)
            uBuffer.get(data, offset + ySize / 4, ySize / 4)
        } else {
            // Caso lento y seguro (maneja el padding en los lados de la imagen)
            for (i in 0 until this.height / 2) {
                vBuffer.get(data, offset, this.width / 2)
                offset += this.width / 2

                uBuffer.get(data, offset, this.width / 2)
                offset += this.width / 2

                // Salta los bytes de padding de la cámara si existen
                if (i < this.height / 2 - 1) {
                    vBuffer.position(vBuffer.position() + vRowStride - this.width / 2)
                    uBuffer.position(uBuffer.position() + uRowStride - this.width / 2)
                }
            }
        }

        return data
    }
//

    private fun createOptimizedPDF417Analyzer(forLowEnd: Boolean = false): ImageAnalysis.Analyzer {
        return if (forLowEnd) {
            createLowEndPDF417Analyzer()
        } else {
            // Tu analyzer normal para dispositivos buenos
            PDF417Analyzer(
                onBarcodeDetected = { barcodeText ->
                    val now = System.currentTimeMillis()
                    if (now - lastScanTime > 2000 && !isProcessing) {
                        lastScanTime = now
                        isProcessing = true
                        runOnUiThread {
                            procesarPDF417(barcodeText)
                        }
                    }
                },
                onError = { error ->
                    runOnUiThread {
                        Log.e(TAG, "Error en análisis: ${error.message}")
                        isProcessing = false
                    }
                }
            )
        }
    }

    private fun extraerDNI(data: String): String {
        Log.d(TAG, "🎯 Intentando extraer DNI de: $data")

        return if (data.contains("@")) {
            val campos = data.split("@")
            Log.d(TAG, "📊 Campos encontrados: ${campos.size}")

            campos.forEachIndexed { index, campo ->
                Log.d(TAG, "   [$index]: '$campo' (longitud: ${campo.length})")
            }

            // Buscar campo que contenga solo números (DNI)
            val dniCampo = campos.find { campo ->
                campo.trim().matches(Regex("\\d+")) && campo.trim().length in 7..9
            }

            // Si no encontramos por patrón, usar campo 4 como fallback
            val dni = dniCampo ?: if (campos.size >= 5) campos[4].trim() else ""

            Log.d(TAG, "🎯 DNI extraído: '$dni'")
            dni
        } else {
            Log.d(TAG, "❌ No se encontró el caracter @ en el código")
            ""
        }
    }

    // ✅ Mostrar estado vacío
    private fun mostrarEstadoVacio() {
        binding.emptyStateHistory.visibility = View.VISIBLE
        binding.rvHistoryIngresos.visibility = View.GONE
        binding.tvHistoryCount.text = "0 registros"
    }

    // ✅ Animación del contador
    private fun animarContador() {
        binding.tvHistoryCount.animate()
            .scaleX(1.2f)
            .scaleY(1.2f)
            .setDuration(200)
            .withEndAction {
                binding.tvHistoryCount.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(200)
                    .start()
            }
            .start()
    }

    // ✅ Verificar permisos
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permisos denegados", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
    private fun createOptimizedPDF417Analyzer(): PDF417Analyzer {
        return PDF417Analyzer(
            onBarcodeDetected = { barcodeText ->
                val now = System.currentTimeMillis()
                if (now - lastScanTime > 2000 && !isProcessing) {
                    lastScanTime = now
                    isProcessing = true
                    runOnUiThread {
                        Log.d(TAG, "📦 Código crudo detectado: $barcodeText")
                        procesarPDF417(barcodeText)
                    }
                }
            },
            onError = { error ->
                runOnUiThread {
                    Log.e(TAG, "Error en análisis: ${error.message}")
                    isProcessing = false
                }
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        processingTimeoutRunnable?.let { binding.root.removeCallbacks(it) }
        cameraExecutor.shutdown()
    }
}