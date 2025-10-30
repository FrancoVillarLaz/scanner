package com.inncome.scanner.data.entities

data class Nomina(
    val id: Long,
    val status: String,
    val incomeType: String,
    val actividad: Actividad,
    val operario: Operario
)
