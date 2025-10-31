package com.inncome.scanner.activities

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.activity.viewModels
import com.inncome.scanner.analyzer.PDF417Analyzer
import com.inncome.scanner.databinding.ActivityScannerBinding
import com.inncome.scanner.dialog.NominaSelectionDialog
import com.inncome.scanner.data.entities.Nomina
import com.inncome.scanner.ui.HistoryViewModel
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScannerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SELECTED_NOMINA = "SELECTED_NOMINA"
    }

    private lateinit var binding: ActivityScannerBinding
    private lateinit var cameraExecutor: ExecutorService
    private val historyViewModel: HistoryViewModel by viewModels()

    private var lastScanTime = 0L
    private var isProcessing = false
    private val establecimientoId: Long = 1L

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else {
            Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.btnClose.setOnClickListener { finish() }
        binding.tvTitle.text = "Escanear PDF417"
        binding.tvStatus.text = "Listo para escanear"

        observeValidationResult()
        checkCameraPermission()
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED ->
                startCamera()
            else -> requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .setTargetResolution(Size(640, 480))
                .build()
                .also { it.setSurfaceProvider(binding.previewView.surfaceProvider) }

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, createPDF417Analyzer())
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
            } catch (e: Exception) {
                Toast.makeText(this, "Error al iniciar cámara: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun createPDF417Analyzer(): PDF417Analyzer {
        return PDF417Analyzer(
            onBarcodeDetected = { text ->
                val now = System.currentTimeMillis()
                if (now - lastScanTime > 2000 && !isProcessing) {
                    lastScanTime = now
                    isProcessing = true
                    runOnUiThread {
                        binding.tvStatus.text = "Código detectado"
                    }
                    val dni = extraerDNI(text)
                    if (dni.isBlank()) {
                        runOnUiThread {
                            Toast.makeText(this, "No se pudo extraer el DNI", Toast.LENGTH_SHORT).show()
                            binding.tvStatus.text = "Listo para escanear"
                        }
                        isProcessing = false
                    } else {
                        // Delegar validación al ViewModel
                        historyViewModel.validarDni(dni, establecimientoId)
                    }
                }
            },
            onError = { _ ->
                runOnUiThread {
                    binding.tvStatus.text = "Error en el análisis"
                    isProcessing = false
                }
            }
        )
    }

    private fun observeValidationResult() {
        lifecycleScope.launch {
            historyViewModel.validationResult.collect { result ->
                result?.let {
                    when (it) {
                        is com.inncome.scanner.ui.ValidationResult.MultipleNominas -> {
                            runOnUiThread {
                                binding.tvStatus.text = "Seleccione nómina"
                                showNominaSelectionDialog(it.nominas)
                            }
                        }
                        is com.inncome.scanner.ui.ValidationResult.IngresoRegistrado -> {
                            runOnUiThread {
                                Toast.makeText(this@ScannerActivity, it.message, Toast.LENGTH_SHORT).show()
                                binding.tvStatus.text = "Ingreso registrado"
                            }
                            // Actualizar historial si aplica
                            historyViewModel.cargarUltimoIngreso(establecimientoId)
                            isProcessing = false
                            historyViewModel.clearValidationResult()
                        }
                        is com.inncome.scanner.ui.ValidationResult.Error -> {
                            runOnUiThread {
                                Toast.makeText(this@ScannerActivity, it.message, Toast.LENGTH_LONG).show()
                                binding.tvStatus.text = "Listo para escanear"
                            }
                            isProcessing = false
                            historyViewModel.clearValidationResult()
                        }
                        is com.inncome.scanner.ui.ValidationResult.SingleNomina -> {
                            // Si se usa, manejarlo aquí
                            isProcessing = false
                            historyViewModel.clearValidationResult()
                        }
                    }
                }
            }
        }
    }

    private fun showNominaSelectionDialog(nominas: List<Nomina>) {
        NominaSelectionDialog(this, nominas) { selected ->
            // Delegar registro al ViewModel
            historyViewModel.registrarIngresoPorNomina(establecimientoId, selected.id.toString())
            historyViewModel.clearValidationResult()
            binding.tvStatus.text = "Registrando ingreso..."
        }.show()
    }

    private fun extraerDNI(data: String): String {
        return if (data.contains("@")) {
            val campos = data.split("@")
            if (campos.size >= 5) campos[4].trim() else ""
        } else {
            ""
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
