package com.inncome.scanner.data.repository

import com.inncome.scanner.config.RetrofitClient
import com.inncome.scanner.data.request.RegistrarIngresoRequest
import com.inncome.scanner.data.request.ValidarDniRequest

class HistoryRepository {
    private val apiService = RetrofitClient.getApiService()

    suspend fun obtenerHistorial(
        establecimientoId: Long,
        page: Int,
        pageSize: Int
    ) = apiService.obtenerHistorialIngresos(
        sortBy = "id,DESC",
        page = page,
        size = pageSize,
        establecimientoId = establecimientoId
    )

    suspend fun obtenerUltimoIngreso(establecimientoId: Long) =
        apiService.obtenerHistorialIngresos(
            sortBy = "id,DESC",
            page = 0,
            size = 1,
            establecimientoId = establecimientoId
        )

    suspend fun validarDni(dni: String, establecimientoId: Long) =
        apiService.validarDniOperario(
            establecimientoId,
            ValidarDniRequest(dni)
        )

    suspend fun registrarIngresoPorNomina(idEstablecimiento: Long, nominaId: String) =
        apiService.registrarIngresoPorNomina(
            idEstablecimiento,
            RegistrarIngresoRequest( nominaId )
        )

    suspend fun obtenerContador(establecimientoId: Long) =
        apiService.obtenerHistorialIngresos(
            sortBy = "id,DESC",
            page = 0,
            size = 1,
            establecimientoId = establecimientoId
        )
}