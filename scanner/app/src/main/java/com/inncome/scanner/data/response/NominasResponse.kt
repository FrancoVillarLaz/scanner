package com.inncome.scanner.data.response

import com.inncome.scanner.data.entities.Nomina

data class NominasResponse(
    val id: Long,
    val createdAt: String,
    val accessType: String,
    val pdfName: String?,
    val nomina: Nomina
)
