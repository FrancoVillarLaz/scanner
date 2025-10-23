package com.inncome.scanner.data.response

import com.inncome.scanner.data.IngresoData

data class RegistrarIngresoResponse(
    val success: Boolean,
    val message: String,
    val ingreso: IngresoData?
)
