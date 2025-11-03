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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.view.View
import androidx.activity.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.launch
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.inncome.scanner.analyzer.PDF417Analyzer
import com.inncome.scanner.data.DniData
import com.inncome.scanner.data.entities.Nomina
import com.inncome.scanner.dialog.NominaSelectionDialog
import com.inncome.scanner.ui.HistoryState
import com.inncome.scanner.ui.HistoryViewModel
import com.inncome.scanner.ui.ValidationResult

import kotlinx.coroutines.launch

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

    private var lastFocusTime = 0L
    private var lastSharp = false
    private var camera: Camera? = null
    private var processingTimeoutRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        setupBottomSheet()
        setupRecyclerView()
        observeViewModel()

        historyViewModel.cargarHistorialInicial(establecimientoId)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        setupScanFrame()
    }

    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter()

        binding.rvHistoryIngresos.apply {
            adapter = historyAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)

            historyAdapter.onLoadMore = {
                historyViewModel.cargarMasDatos(establecimientoId)
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            historyViewModel.historial.collect { state ->
                when (state) {
                    is HistoryState.Loading -> {
                        // Mostrar loading si querés
                    }

                    is HistoryState.Success -> {
                        historyAdapter.submitList(state.items) {
                            binding.rvHistoryIngresos.post {
                                binding.rvHistoryIngresos.smoothScrollToPosition(0)
                            }
                        }

                        historyAdapter.setLoadingState(false)
                        binding.tvHistoryCount.text =
                            "${state.totalCount} registro${if (state.totalCount != 1) "s" else ""}"

                        binding.emptyStateHistory.visibility = View.GONE
                        binding.rvHistoryIngresos.visibility = View.VISIBLE
                    }

                    is HistoryState.Empty -> {
                        historyAdapter.submitList(emptyList())
                        binding.tvHistoryCount.text = "0 registros"
                        binding.emptyStateHistory.visibility = View.VISIBLE
                        binding.rvHistoryIngresos.visibility = View.GONE
                    }

                    is HistoryState.Error -> {
                        Log.e("History", "Error cargando historial: ${state.message}")
                        // Podés mostrar un toast o un banner más adelante
                    }
                }
            }
        }

        lifecycleScope.launch {
            historyViewModel.validationResult.collect { result ->
                result?.let {
                    handleValidationResult(it)
                    historyViewModel.clearValidationResult()
                }
            }
        }

        lifecycleScope.launch {
            historyViewModel.isLoading.collect { isLoading ->
                historyAdapter.setLoadingState(isLoading)
            }
        }
    }

    private fun handleValidationResult(result: ValidationResult) {
        when (result) {
            is ValidationResult.MultipleNominas -> {
                mostrarDialogoSeleccionNomina(result.dni, result.nominas)
            }
            is ValidationResult.IngresoRegistrado -> {
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()

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
                isProcessing = false
            }
        }
    }

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
                    it.setAnalyzer(cameraExecutor, createMLKitAnalyzer())
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
                setupSmartAutoFocus()
            } catch (e: Exception) {
                Log.e(TAG, "Error al iniciar cámara", e)
                tryFallbackCameraSetup(cameraProvider)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Configura el autofocus en la parte SUPERIOR de la cámara
     * Esto mejora el escaneo de códigos PDF417 que están en la parte superior del DNI
     */
    private fun setupSmartAutoFocus() {
        binding.viewFinder.post {
            try {
                val factory = binding.viewFinder.meteringPointFactory

                val isLowEnd = isLowEndDevice()
                val topPoint = if (isLowEnd) factory.createPoint(0.5f, 0.35f) // más central, más estable
                else factory.createPoint(0.5f, 0.25f)
                val upperPoint = if (!isLowEnd) factory.createPoint(0.5f, 0.15f) else null

                val builder = FocusMeteringAction.Builder(topPoint, FocusMeteringAction.FLAG_AF)
                upperPoint?.let { builder.addPoint(it, FocusMeteringAction.FLAG_AF) }
                builder.setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)

                val action = builder.build()
                camera?.cameraControl?.startFocusAndMetering(action)

                Log.d(TAG, "🎯 Auto-focus aplicado (LowEnd=$isLowEnd)")

                if (!isLowEnd) {
                    val interval = 2000L
                    binding.viewFinder.postDelayed({ setupSmartAutoFocus() }, interval)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error en auto-focus: ${e.message}")
            }
        }
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
                .setTargetResolution(Size(480, 360))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, createMLKitAnalyzer())
                }

            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalyzer
            )
            setupSmartAutoFocus()

            Log.d(TAG, "✅ Cámara iniciada en modo baja resolución")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error incluso en modo fallback", e)
            Toast.makeText(this, "Error crítico de cámara", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Crea el analyzer con ML Kit de Google
     */
    private fun createMLKitAnalyzer(): PDF417Analyzer {
        return PDF417Analyzer(
            onBarcodeDetected = { rawData, dniData ->
                val now = System.currentTimeMillis()
                if (now - lastScanTime > 2000 && !isProcessing) {
                    lastScanTime = now
                    isProcessing = true

                    runOnUiThread {
                        procesarDNI(rawData, dniData)
                    }
                }
            },
            onError = { error ->
                runOnUiThread {
                    Log.e(TAG, "Error en ML Kit: ${error.message}")
                    isProcessing = false
                }
            }
        )
    }

    /**
     * Procesa el DNI detectado y lo envía a validación
     */
    private fun procesarDNI(rawData: String, dniData: DniData?) {
        processingTimeoutRunnable?.let { binding.root.removeCallbacks(it) }

        Log.d(TAG, "🔍 ==========================================")
        Log.d(TAG, "🔍 PROCESANDO DNI CON ML KIT")
        Log.d(TAG, "🔍 ==========================================")

        if (dniData == null) {
            Log.e(TAG, "❌ No se pudo parsear el DNI")
            Log.d(TAG, "📦 Datos crudos: $rawData")
            Toast.makeText(this, "DNI no válido", Toast.LENGTH_SHORT).show()
            resetProcessingState()
            return
        }

        // Validar que el DNI sea válido
        if (!dniData.isValid()) {
            Log.e(TAG, "❌ DNI inválido después de parseo")
            Toast.makeText(this, "DNI inválido: ${dniData.dni}", Toast.LENGTH_SHORT).show()
            resetProcessingState()
            return
        }

        Log.d(TAG, "✅ DNI VÁLIDO - Enviando a validación")
        Log.d(TAG, "🆔 DNI: ${dniData.dni}")
        Log.d(TAG, "👤 Nombre completo: ${dniData.getNombreCompleto()}")

        // Timeout de seguridad
        processingTimeoutRunnable = Runnable {
            Log.e(TAG, "⏰ Timeout en procesamiento")
            resetProcessingState()
            Toast.makeText(this, "Timeout en escaneo", Toast.LENGTH_SHORT).show()
        }
        binding.root.postDelayed(processingTimeoutRunnable!!, 10000)

        // Enviar a validación
        historyViewModel.validarDni(dniData.dni, establecimientoId)
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

    private fun mostrarEstadoVacio() {
        binding.emptyStateHistory.visibility = View.VISIBLE
        binding.rvHistoryIngresos.visibility = View.GONE
        binding.tvHistoryCount.text = "0 registros"
    }

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

    override fun onDestroy() {
        super.onDestroy()
        processingTimeoutRunnable?.let { binding.root.removeCallbacks(it) }
        cameraExecutor.shutdown()
    }
}