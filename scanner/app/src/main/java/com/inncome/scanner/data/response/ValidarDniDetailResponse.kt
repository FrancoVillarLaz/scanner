package com.inncome.scanner.data.response

import com.inncome.scanner.data.NominaDetail

data class ValidarDniDetailResponse(
    val success: Boolean,
    val message: String,
    val nominas: List<NominaDetail>? = null,  // Para código 200
    val data: IngresoGeneradoData? = null      // Para código 201
)