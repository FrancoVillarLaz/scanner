package com.inncome.scanner.data

import com.inncome.scanner.data.response.IngresoResponse

data class NominaDetail(
    val id: Long,
    val createdAt: String,
    val userId: Long,
    val districtId: Long,
    val status: String,
    val incomeType: String,
    val actividad: ActividadData?,
    val ingresos: List<IngresoResponse>
)
