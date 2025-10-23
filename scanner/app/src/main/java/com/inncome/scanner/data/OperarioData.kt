package com.inncome.scanner.data

data class OperarioData (
    val id: Int,
    val firstName: String,
    val lastName: String,
    val documentType: String,
    val documentNumber: String,
    val email: String,
    val phone: String,
    val dateOfBirth: String
)