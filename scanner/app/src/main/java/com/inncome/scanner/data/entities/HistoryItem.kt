package com.inncome.scanner.data.entities

data class HistoryItem(
    val id: Long,
    val createdAt: String,
    val status: String,
    val incomeType: String,
    val userId: Long,
    val districtId: Long,
    val operario: Operario,
    val actividad: Actividad,
    val ingresos: List<Ingreso>?
)