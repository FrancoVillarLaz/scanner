package com.inncome.scanner.service

import android.R
import com.inncome.scanner.data.request.RegistrarIngresoRequest
import com.inncome.scanner.data.request.ValidarDniRequest
import com.inncome.scanner.data.IngresoData
import com.inncome.scanner.data.response.IngresoGeneradoResponse
import com.inncome.scanner.data.response.ValidarDniDetailResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

interface ApiService {

    @Headers(
        "Content-Type: application/json",
        "Accept: application/json",
        "X-Rol: GUARDIA"
    )
    @POST("auth-service/auth/operario/document-number")
    suspend fun validarDniOperario(@Header ("X-Establecimiento-id") idEstablecimiento: Int,  @Body request: ValidarDniRequest): Response<ValidarDniDetailResponse>

    @Headers(
        "Content-Type: application/json",
        "Accept: application/json",
        "X-Rol: GUARDIA"
    )
    @POST("trabajadores-service/ingreso/nomina/{nominaId}")
    suspend fun registrarIngresoPorNomina(
        @Header ("X-Establecimiento-id") idEstablecimiento: Int,
        @Body request: RegistrarIngresoRequest
    ): Response<IngresoGeneradoResponse>

    @Headers(
        "Content-Type: application/json",
        "Accept: application/json",
        "X-Rol: GUARDIA"
    )
    @GET("trabajadores-service/ingreso")
    suspend fun obtenerIngresos( @Header ("X-Establecimiento-id") idEstablecimiento: Int,): List<IngresoData>

}