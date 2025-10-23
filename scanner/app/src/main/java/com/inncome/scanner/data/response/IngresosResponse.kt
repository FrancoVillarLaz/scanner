package com.inncome.scanner.data.response

data class IngresoResponse(
    val id: Long,
    val createdAt: String,
    val accessType: String,
    val pdfName: String
)