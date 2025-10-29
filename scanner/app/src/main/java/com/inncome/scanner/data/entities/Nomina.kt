package com.inncome.scanner.data.entities

import com.inncome.scanner.data.response.OperarioDetail

data class Nomina(
    val id: Long,
    val status: String,
    val incomeType: String,
    val actividad: Actividad,
    val operario: OperarioDetail
)
