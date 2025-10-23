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
import com.inncome.scanner.config.RetrofitClient
import com.inncome.scanner.data.NominaDetail
import com.inncome.scanner.data.request.ValidarDniRequest
import com.inncome.scanner.databinding.ActivityMainBinding
import com.inncome.scanner.dialog.NominaSelectionDialog
import com.inncome.scannertest.PDF417Analyzer
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.graphics.Color

import android.view.View
import com.inncome.scanner.data.request.RegistrarIngresoRequest
import com.inncome.scanner.data.response.IngresoGeneradoData


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var lastScanTime = 0L
    private var isProcessing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

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
        // Extraer DNI del PDF417
        val dni = extraerDNI(data)

        if (dni.isEmpty()) {
            Toast.makeText(this, "No se pudo extraer el DNI", Toast.LENGTH_SHORT).show()
            isProcessing = false
            return
        }

        // Mostrar DNI en el resultCard para debug
        binding.resultText.text = "Procesando DNI: $dni"

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.validarDniOperario(1,
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

                        if (body?.data != null) {
                            mostrarIngresoExitoso(body.data)
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "Ingreso registrado pero sin datos",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
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

    private fun mostrarDialogoSeleccionNomina(dni: String, nominas: List<NominaDetail>) {
        val dialog = NominaSelectionDialog(this, nominas) { nominaSeleccionada ->
            registrarIngresoPorNomina(dni, nominaSeleccionada.id)
        }
        dialog.show()
    }

    private fun registrarIngresoPorNomina(dni: String, nominaId: Long) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.registrarIngresoPorNomina(
                    nominaId,
                    RegistrarIngresoRequest(
                        dni = dni,
                        tipo = "ENTRADA" // TODO: Determinar si es ENTRADA o SALIDA
                    )
                )

                if (response.isSuccessful && response.body()?.data != null) {
                    mostrarIngresoExitoso(response.body()!!.data!!)
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

    private fun mostrarIngresoExitoso(data: IngresoGeneradoData) {
        binding.apply {
            // Mostrar la card de ingreso exitoso
            cardIngresoExitoso.visibility = View.VISIBLE

            tvIngresoDni.text = "DNI: ${data.dni}"
            tvIngresoNombre.text = data.nombre
            tvIngresoTipo.text = data.tipo
            tvIngresoFecha.text = data.timestamp

            // Mostrar actividad si existe
            if (data.actividad != null) {
                tvIngresoActividad.visibility = View.VISIBLE
                tvIngresoActividad.text = "Actividad: ${data.actividad}"
            } else {
                tvIngresoActividad.visibility = View.GONE
            }

            // Color según tipo
            val color = when (data.tipo.uppercase()) {
                "ENTRADA" -> Color.parseColor("#00FF00")
                "SALIDA" -> Color.parseColor("#FF0000")
                else -> Color.parseColor("#FFAA00")
            }
            tvIngresoTipo.setTextColor(color)

            Toast.makeText(
                this@MainActivity,
                "✓ Ingreso registrado exitosamente",
                Toast.LENGTH_SHORT
            ).show()

            // Ocultar la card después de 5 segundos
            cardIngresoExitoso.postDelayed({
                cardIngresoExitoso.visibility = View.GONE
            }, 5000)
        }
    }

    private fun extraerDNI(data: String): String {
        // Extraer DNI del formato PDF417
        // Formato esperado: campo[4] contiene el DNI
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

/*
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var lastScanTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

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
                        if (currentTime - lastScanTime > 1000) {
                            lastScanTime = currentTime
                            runOnUiThread {
                                showResult(result)
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

    private fun showResult(text: String) {
        binding.resultText.text = parsePDF417(text)
        Toast.makeText(this, "✓ PDF417 Leído", Toast.LENGTH_SHORT).show()
    }

    private fun parsePDF417(data: String): String {
        val sb = StringBuilder()
        sb.append("═══════════════════════════════\n")
        sb.append("PDF417 DETECTADO\n")
        sb.append("═══════════════════════════════\n\n")

        if (data.contains("@")) {
            val campos = data.split("@")
            if (campos.size >= 8) {
                sb.append("📄 Trámite: ${campos[0]}\n")
                sb.append("👤 Apellido: ${campos[1]}\n")
                sb.append("👤 Nombre: ${campos[2]}\n")
                sb.append("⚥ Sexo: ${campos[3]}\n")
                sb.append("🆔 DNI: ${campos[4]}\n")
                sb.append("📋 Ejemplar: ${campos[5]}\n")
                sb.append("🎂 Nacimiento: ${campos[6]}\n")
                sb.append("📅 Emisión: ${campos[7]}\n")
            } else {
                sb.append(data)
            }
        } else {
            sb.append(data)
        }

        sb.append("\n═══════════════════════════════")
        return sb.toString()
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
*/