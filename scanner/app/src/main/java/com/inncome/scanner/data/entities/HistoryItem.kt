package com.inncome.scanner.data.entities

data class HistoryItem(
    val id: Long,
    val createdAt: String,
    val accessType: String,
    val pdfName: String,
    val nomina: Nomina
)