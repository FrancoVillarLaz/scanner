package com.inncome.scanner.data

/**
 * Clase de datos para almacenar información del DNI argentino
 */
data class DniData(
    val tipoDocumento: String,
    val apellido: String,
    val nombre: String,
    val sexo: String,
    val dni: String,
    val ejemplar: String,
    val fechaNacimiento: String,
    val fechaEmision: String,
    val numeroTramite: String,
    val rawData: String
) {
    fun getNombreCompleto(): String = "$nombre $apellido".trim()

    fun isValid(): Boolean {
        return dni.isNotEmpty() &&
                dni.matches(Regex("\\d{7,9}")) &&
                apellido.isNotEmpty() &&
                nombre.isNotEmpty()
    }
}