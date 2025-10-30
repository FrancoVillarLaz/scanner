package com.inncome.scanner.service

import com.inncome.scanner.data.entities.Ingreso
import com.inncome.scanner.data.request.RegistrarIngresoRequest
import com.inncome.scanner.data.request.ValidarDniRequest
import com.inncome.scanner.data.response.HistoryResponse
import com.inncome.scanner.data.response.NominasResponse
import com.inncome.scanner.data.response.IngresoGeneradoResponse
import com.inncome.scanner.data.response.ValidarDniDetailResponse
import retrofit2.http.GET
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

interface  TrabajadoresApi {

    @Headers(
        "Content-Type: application/json",
        "Accept: application/json",
        "X-Rol: GUARDIA"
    )
    @POST("trabajadores-service/operario/document-number")
    suspend fun validarDniOperario(
        @Header ("X-Establecimiento-id") idEstablecimiento: Long,
        @Body request: ValidarDniRequest
    ): Response<ValidarDniDetailResponse>

    @Headers(
        "Content-Type: application/json",
        "Accept: application/json",
        "X-Rol: GUARDIA"
    )
    @POST("trabajadores-service/ingreso/")
    suspend fun registrarIngresoPorNomina(
        @Header ("X-Establecimiento-id") idEstablecimiento: Long,
        @Body request: RegistrarIngresoRequest
    ): Response<Ingreso>

    @Headers(
        "Content-Type: application/json",
        "Accept: application/json",
        "X-Rol: ADMINISTRADOR_BARRIO"
    )
    @GET("trabajadores-service/ingreso/district")
    suspend fun obtenerHistorialIngresos(
        @Query("sort") sortBy: String = "id,DESC",
        @Query("page") page: Int?,
        @Query("size") size: Int?,
        @Header("X-Establecimiento-Id") establecimientoId: Long
    ): Response<HistoryResponse>

    fun obtenerNominasPorDni(establecimientoId: Long, validarDniRequest: com.inncome.scanner.data.request.ValidarDniRequest): Any

}