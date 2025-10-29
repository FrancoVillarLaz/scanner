package com.inncome.scanner.data.response

import com.inncome.scanner.data.entities.Nomina

data class ValidarDniDetailResponse(
    val operario: OperarioDetail,
    val nominas: List<Nomina>
)