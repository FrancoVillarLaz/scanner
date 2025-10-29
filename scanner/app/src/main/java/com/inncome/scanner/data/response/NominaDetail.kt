package com.inncome.scanner.data.response

import com.inncome.scanner.data.entities.Actividad

data class NominaDetail(
    val id: Long,
    val status: String,
    val incomeType: String,
    val operario: OperarioDetail,
    val actividad: Actividad
)