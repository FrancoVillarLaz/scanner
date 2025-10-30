package com.inncome.scanner.data.entities

data class Ingreso(
    val id: Long,
    val createdAt: String,
    val accessType: AccessType,
    val pdfName: String?,
    val nomina: String?
)
