package com.inncome.scanner.data.response

import com.inncome.scanner.data.entities.Ingreso
import com.inncome.scanner.data.entities.Nomina

data class ValidarDniDetailResponse(
    val ingreso: Ingreso? = null,
    val nominas: List<Nomina>? = null
)