package com.inncome.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.inncome.scanner.adapter.HistoryAdapter
import com.inncome.scanner.databinding.ActivityMainBinding
import com.inncome.scannertest.PDF417Analyzer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.view.View
import androidx.activity.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
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

    // ✅ Iniciar cámara
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, PDF417Analyzer { result ->
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastScanTime > 2000 && !isProcessing) {
                            lastScanTime = currentTime
                            isProcessing = true
                            runOnUiThread {
                                procesarPDF417(result)
                            }
                        }
                    })
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
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // ✅ Configurar autofocus
    private fun setupAutoFocus(camera: Camera) {
        binding.viewFinder.post {
            val factory = binding.viewFinder.meteringPointFactory

            val location = IntArray(2)
            binding.viewFinder.getLocationOnScreen(location)
            val vfX = location[0]
            val vfY = location[1]

            val scanLoc = IntArray(2)
            binding.scanFrame.getLocationOnScreen(scanLoc)

            val centerX = (scanLoc[0] - vfX + binding.scanFrame.width / 2f) / binding.viewFinder.width
            val centerY = (scanLoc[1] - vfY + binding.scanFrame.height / 2f) / binding.viewFinder.height

            val point = factory.createPoint(centerX, centerY)

            val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            camera.cameraControl.startFocusAndMetering(action)

            binding.viewFinder.postDelayed({
                setupAutoFocus(camera)
            }, 1000)
        }
    }

    // ✅ Procesar código PDF417
    private fun procesarPDF417(data: String) {
        val dni = extraerDNI(data)

        if (dni.isEmpty()) {
            Toast.makeText(this, "No se pudo extraer el DNI", Toast.LENGTH_SHORT).show()
            isProcessing = false
            return
        }

        // ✅ Delegar al ViewModel
        historyViewModel.validarDni(dni, establecimientoId)
    }

    // ✅ Mostrar diálogo de selección de nómina
    private fun mostrarDialogoSeleccionNomina(dni: String, nominas: List<com.inncome.scanner.data.entities.Nomina>) {
        val dialog = NominaSelectionDialog(this, nominas) { nominaSeleccionada ->
            historyViewModel.registrarIngresoPorNomina(
                establecimientoId,
                nominaSeleccionada.id.toString()
            )
        }
        dialog.show()
    }

    // ✅ Extraer DNI del código PDF417
    private fun extraerDNI(data: String): String {
        return if (data.contains("@")) {
            val campos = data.split("@")
            if (campos.size >= 5) {
                campos[4].trim()
            } else {
                ""
            }
        } else {
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

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}