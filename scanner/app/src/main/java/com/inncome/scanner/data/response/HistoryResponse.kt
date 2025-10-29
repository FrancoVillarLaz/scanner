package com.inncome.scanner.data.response

import com.inncome.scanner.data.Pagination
import com.inncome.scanner.data.entities.HistoryItem

data class HistoryResponse(
    val content: List<HistoryItem>,
    val pagination: Pagination
)
