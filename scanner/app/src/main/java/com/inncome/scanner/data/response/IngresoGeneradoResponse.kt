package com.inncome.scanner.data.response

data class IngresoGeneradoResponse(
    val success: Boolean,
    val message: String,
    val data: IngresoGeneradoData?
)