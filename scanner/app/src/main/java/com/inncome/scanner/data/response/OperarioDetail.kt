package com.inncome.scanner.data.response

data class OperarioDetail(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val documentType: String,
    val documentNumber: String,
    val email: String? = null,
    val phone: String? = null,
    val dateOfBirth: String? = null
)