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
import com.inncome.scanner.config.RetrofitClient
import com.inncome.scanner.data.entities.HistoryItem
import com.inncome.scanner.data.entities.Nomina
import com.inncome.scanner.databinding.ActivityMainBinding
import com.inncome.scanner.dialog.NominaSelectionDialog
import com.inncome.scannertest.PDF417Analyzer
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.inncome.scanner.data.request.RegistrarIngresoRequest
import com.inncome.scanner.data.request.ValidarDniRequest

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var lastScanTime = 0L
    private var isProcessing = false

    // BottomSheet
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<*>
    private lateinit var historyAdapter: HistoryAdapter

    // Obtener del token JWT
    private val establecimientoId: Long = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Configurar BottomSheet
        setupBottomSheet()

        // Cargar historial inicial
        cargarHistorialIngresos()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

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

    private fun setupBottomSheet() {
        // Configurar adapter
        historyAdapter = HistoryAdapter()

        binding.rvHistoryIngresos.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = historyAdapter
        }

        // Configurar BottomSheet behavior
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheetHistory)

        bottomSheetBehavior.apply {
            // Mostrar solo el peek (primera card)
            peekHeight = 220 // Ajusta según necesites
            state = BottomSheetBehavior.STATE_COLLAPSED
            isHideable = false

            // Callback para estados
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

                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    // Opcional: animar algo mientras se desliza
                }
            })
        }
    }

    private fun cargarHistorialIngresos() {
        lifecycleScope.launch {
            try {
                val apiService = RetrofitClient.getApiService()
                val response = apiService.obtenerHistorialIngresos("id,DESC", establecimientoId)

                Log.d(TAG, "URL de la petición: ${response.raw().request.url}")
                Log.d(TAG, "Response type: ${response.body()?.content?.javaClass}")

                if (response.isSuccessful && response.body() != null) {
                    val historyResponse = response.body()!!
                    val historial = historyResponse.content

                    Log.d(TAG, "Historial type: ${historial::class.java}")
                    Log.d(TAG, "First item type: ${historial.firstOrNull()?.javaClass}")

                    if (historial.isNotEmpty()) {
                        historyAdapter.submitList(historial)
                        binding.tvHistoryCount.text = "${historyResponse.pagination.totalElements} registro${if (historyResponse.pagination.totalElements != 1) "s" else ""}"
                        binding.emptyStateHistory.visibility = View.GONE
                        binding.rvHistoryIngresos.visibility = View.VISIBLE
                    } else {
                        mostrarEstadoVacio()
                    }
                } else {
                    Log.e(TAG, "Error al cargar historial: ${response.code()}. Mensaje: ${response.message()}")
                    mostrarEstadoVacio()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al cargar historial (Excepción)", e)
                mostrarEstadoVacio()
            }
        }
    }

    private fun mostrarEstadoVacio() {
        binding.emptyStateHistory.visibility = View.VISIBLE
        binding.rvHistoryIngresos.visibility = View.GONE
        binding.tvHistoryCount.text = "0 registros"
    }

    private fun agregarIngresoAlHistorial(ingreso: HistoryItem) {
        val listaActual = historyAdapter.currentList.toMutableList()
        listaActual.add(0, ingreso)
        historyAdapter.submitList(listaActual)

        // Actualizar contador
        binding.tvHistoryCount.text = "${listaActual.size} registro${if (listaActual.size != 1) "s" else ""}"

        // Mostrar RecyclerView si estaba vacío
        if (binding.emptyStateHistory.visibility == View.VISIBLE) {
            binding.emptyStateHistory.visibility = View.GONE
            binding.rvHistoryIngresos.visibility = View.VISIBLE
        }

        // Scroll al inicio para ver el nuevo registro
        binding.rvHistoryIngresos.scrollToPosition(0)

        // Expandir brevemente el bottomSheet para mostrar el nuevo ingreso
        if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            binding.root.postDelayed({
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }, 2500)
        }
    }

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

    private fun procesarPDF417(data: String) {
        val dni = extraerDNI(data)

        if (dni.isEmpty()) {
            Toast.makeText(this, "No se pudo extraer el DNI", Toast.LENGTH_SHORT).show()
            isProcessing = false
            return
        }

        lifecycleScope.launch {
            try {
                val apiService = RetrofitClient.getApiService()
                val response = apiService.validarDniOperario(
                    establecimientoId,
                    ValidarDniRequest(dni)
                )

                Log.d(TAG, "Response code: ${response.code()}")

                when (response.code()) {
                    200 -> {
                        // Múltiples nóminas - mostrar diálogo de selección
                        val body = response.body()
                        Log.d(TAG, "Body: $body")

                        if (body?.nominas != null && body.nominas.isNotEmpty()) {
                            mostrarDialogoSeleccionNomina(dni, body.nominas)
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "No se encontraron nóminas",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    201 -> {
                        // Ingreso generado exitosamente
                        val body = response.body()
                        Log.d(TAG, "Ingreso exitoso: $body")

                        // TODO: Aquí necesitas crear un HistoryItem con los datos de la respuesta
                        // Por ahora solo mostramos el toast
                        Toast.makeText(
                            this@MainActivity,
                            "✓ Ingreso registrado exitosamente",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    401 -> {
                        Toast.makeText(
                            this@MainActivity,
                            "No autorizado para realizar esta consulta",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    404 -> {
                        Toast.makeText(
                            this@MainActivity,
                            "Operario no encontrado o sin nóminas activas",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    else -> {
                        Toast.makeText(
                            this@MainActivity,
                            "Error: ${response.code()} - ${response.message()}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al validar DNI", e)
                Toast.makeText(
                    this@MainActivity,
                    "Error de conexión: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                isProcessing = false
            }
        }
    }

    private fun mostrarDialogoSeleccionNomina(dni: String, nominas: List<Nomina>) {
        val dialog = NominaSelectionDialog(this, nominas) { nominaSeleccionada ->
            registrarIngresoPorNomina(dni, nominaSeleccionada.id)
        }
        dialog.show()
    }

    private fun registrarIngresoPorNomina(dni: String, nominaId: Long) {
        lifecycleScope.launch {
            try {
                val apiService = RetrofitClient.getApiService()
                val response = apiService.registrarIngresoPorNomina(
                    nominaId,
                    RegistrarIngresoRequest(
                        dni = dni,
                        tipo = "ENTRADA"
                    )
                )

                if (response.isSuccessful && response.body()?.data != null) {
                    val data = response.body()!!.data!!

                    // TODO: Aquí necesitas crear un HistoryItem con los datos de la respuesta
                    // Por ahora solo mostramos el toast
                    Toast.makeText(
                        this@MainActivity,
                        "✓ Ingreso registrado exitosamente",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Error al registrar ingreso: ${response.code()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al registrar ingreso", e)
                Toast.makeText(
                    this@MainActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

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

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
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

    companion object {
        private const val TAG = "PDF417Scanner"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}