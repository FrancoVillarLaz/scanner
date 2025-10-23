package com.inncome.scanner.data.response

data class IngresoGeneradoData(
    val dni: String,
    val nombre: String,
    val tipo: String,
    val timestamp: String,
    val actividad: String?
)