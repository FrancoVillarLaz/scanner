package com.inncome.scanner.data.response

data class NominasResponse(
    val id: Long,
    val createdAt: String,
    val accessType: String,
    val pdfName: String?,
    val nomina: NominaDetail
)
