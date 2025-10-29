package com.inncome.scanner.data.response

import com.inncome.scanner.data.entities.Ingreso

data class IngresoGeneradoResponse(
    val message: String,
    val data: Ingreso?
)
