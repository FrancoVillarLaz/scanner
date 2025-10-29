package com.inncome.scanner.data.entities

data class Operario(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val documentType: String,
    val documentNumber: String,
    val email: String?,
    val phone: String?,
    val dateOfBirth: String?,
    val nominas: List<Nomina>?
)