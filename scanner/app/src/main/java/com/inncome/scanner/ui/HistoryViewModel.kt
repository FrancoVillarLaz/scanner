package com.inncome.scanner.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inncome.scanner.data.entities.HistoryItem
import com.inncome.scanner.data.repository.HistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.util.Log
import com.inncome.scanner.data.entities.Nomina
import kotlinx.coroutines.flow.asStateFlow

sealed class HistoryState {
    data object Loading : HistoryState()
    data class Success(val items: List<HistoryItem>, val hasMore: Boolean, val totalCount: Int) : HistoryState()
    data class Error(val message: String) : HistoryState()
    data object Empty : HistoryState()
}

sealed class ValidationResult {
    data class SingleNomina(val dni: String) : ValidationResult()
    data class MultipleNominas(val dni: String, val nominas: List<Nomina>) : ValidationResult()
    data class IngresoRegistrado(val message: String) : ValidationResult()
    data class Error(val code: Int, val message: String) : ValidationResult()
}

class HistoryViewModel(
    private val repository: HistoryRepository = HistoryRepository()
) : ViewModel() {

    companion object {
        private const val TAG = "HistoryViewModel"
    }

    private val _historial = MutableStateFlow<HistoryState>(HistoryState.Loading)
    val historial: StateFlow<HistoryState> = _historial.asStateFlow()

    private val _validationResult = MutableStateFlow<ValidationResult?>(null)
    val validationResult: StateFlow<ValidationResult?> = _validationResult.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var currentPage = 0
    private var hasMore = true
    private val pageSize = 10

    fun cargarHistorialInicial(establecimientoId: Long) {
        if (_isLoading.value) return

        _isLoading.value = true
        currentPage = 0
        hasMore = true

        viewModelScope.launch {
            try {
                val response = repository.obtenerHistorial(establecimientoId, currentPage, pageSize)

                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!
                    val listaValidada = validarIntegridadDatos(data.content)

                    hasMore = data.pagination.hasNext
                    currentPage++

                    if (listaValidada.isNotEmpty()) {
                        _historial.value = HistoryState.Success(
                            items = listaValidada,
                            hasMore = hasMore,
                            totalCount = data.pagination.totalElements
                        )
                    } else {
                        _historial.value = HistoryState.Empty
                    }

                    Log.d(TAG, "✅ Historial inicial cargado: ${listaValidada.size} items")
                } else {
                    _historial.value = HistoryState.Error("Error al cargar historial: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al cargar historial inicial", e)
                _historial.value = HistoryState.Error(e.message ?: "Error desconocido")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun cargarMasDatos(establecimientoId: Long) {
        if (_isLoading.value || !hasMore) return

        _isLoading.value = true

        viewModelScope.launch {
            try {
                val response = repository.obtenerHistorial(establecimientoId, currentPage, pageSize)

                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!
                    val nuevosItems = validarIntegridadDatos(data.content)

                    if (nuevosItems.isNotEmpty()) {
                        val estadoActual = _historial.value
                        if (estadoActual is HistoryState.Success) {
                            val listaActualizada = estadoActual.items + nuevosItems

                            hasMore = data.pagination.hasNext
                            currentPage++

                            _historial.value = HistoryState.Success(
                                items = listaActualizada,
                                hasMore = hasMore,
                                totalCount = data.pagination.totalElements
                            )

                            Log.d(TAG, "✅ Página $currentPage cargada. Total: ${listaActualizada.size}")
                        }
                    } else {
                        hasMore = false
                        Log.d(TAG, "🔭 No hay más páginas disponibles")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al cargar más datos", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun validarDni(dni: String, establecimientoId: Long) {
        viewModelScope.launch {
            try {
                val response = repository.validarDni(dni, establecimientoId)

                Log.d(TAG, "Response code: ${response.code()}")

                when (response.code()) {
                    202 -> {
                        val body = response.body()
                        if (body?.nominas != null && body.nominas.isNotEmpty()) {
                            val nominasValidas = body.nominas.filter { it.operario != null }

                            if (nominasValidas.isNotEmpty()) {
                                _validationResult.value = ValidationResult.MultipleNominas(dni, nominasValidas)
                            } else {
                                _validationResult.value = ValidationResult.Error(
                                    200,
                                    "Nóminas encontradas pero sin información del operario"
                                )
                            }
                        } else if (body?.ingreso != null) {
                            _validationResult.value = ValidationResult.IngresoRegistrado(
                                "✓ Ingreso registrado exitosamente"
                            )
                        } else {
                            _validationResult.value = ValidationResult.Error(
                                200,
                                "No se encontraron nóminas ni ingresos para el DNI proporcionado"
                            )
                        }
                    }
                    404 -> {
                        _validationResult.value = ValidationResult.Error(
                            404,
                            "Operario no encontrado o sin nóminas activas"
                        )
                    }
                    401 -> {
                        _validationResult.value = ValidationResult.Error(
                            401,
                            "No autorizado para realizar esta consulta"
                        )
                    }
                    else -> {
                        _validationResult.value = ValidationResult.Error(
                            response.code(),
                            response.message()
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al validar DNI", e)
                _validationResult.value = ValidationResult.Error(
                    0,
                    "Error de conexión: ${e.message}"
                )
            }
        }
    }

    fun registrarIngresoPorNomina(establecimientoId: Long, nominaId: String) {
        viewModelScope.launch {
            try {
                val response = repository.registrarIngresoPorNomina(establecimientoId, nominaId)

                if (response.code() == 201) {
                    Log.i("ingreso por nomina", "✓ Ingreso registrado exitosamente")
                    _validationResult.value = ValidationResult.IngresoRegistrado("Ingreso por nómina exitoso")
                } else {
                    _validationResult.value = ValidationResult.Error(
                        response.code(),
                        "Error al registrar ingreso: ${response.code()}"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al registrar ingreso", e)
                _validationResult.value = ValidationResult.Error(
                    0,
                    "Error: ${e.message}"
                )
            }
        }
    }

    fun cargarUltimoIngreso(establecimientoId: Long) {
        viewModelScope.launch {
            try {
                val response = repository.obtenerUltimoIngreso(establecimientoId)

                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!
                    if (data.content.isNotEmpty()) {
                        val ultimoIngreso = data.content.first()

                        if (ultimoIngreso.nomina.operario != null) {
                            agregarIngresoAlInicio(ultimoIngreso, data.pagination.totalElements)
                            Log.d(TAG, "✅ Último ingreso agregado: ID ${ultimoIngreso.id}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al cargar último ingreso", e)
            }
        }
    }

    private fun agregarIngresoAlInicio(ingreso: HistoryItem, totalCount: Int) {
        val estadoActual = _historial.value

        if (estadoActual is HistoryState.Success) {
            val existeIngreso = estadoActual.items.any { it.id == ingreso.id }

            if (!existeIngreso) {
                val listaActualizada = listOf(ingreso) + estadoActual.items

                _historial.value = HistoryState.Success(
                    items = listaActualizada,
                    hasMore = estadoActual.hasMore,
                    totalCount = totalCount
                )

                Log.d(TAG, "✅ Ingreso ID ${ingreso.id} agregado al inicio")
            }
        } else if (estadoActual is HistoryState.Empty) {
            _historial.value = HistoryState.Success(
                items = listOf(ingreso),
                hasMore = false,
                totalCount = totalCount
            )
        }
    }

    fun clearValidationResult() {
        _validationResult.value = null
    }

    private fun validarIntegridadDatos(lista: List<HistoryItem>): List<HistoryItem> {
        val itemsValidos = lista.filter { item ->
            val esValido = item.nomina.operario != null &&
                    item.nomina.operario.documentNumber.isNotBlank() &&
                    item.nomina.actividad != null

            if (!esValido) {
                Log.w(TAG, "❌ Item inválido filtrado - ID: ${item.id}")
            }
            esValido
        }

        Log.d(TAG, "Validación: ${lista.size} → ${itemsValidos.size} items válidos")
        return itemsValidos
    }

    fun resetPagination() {
        currentPage = 0
        hasMore = true
    }
}
