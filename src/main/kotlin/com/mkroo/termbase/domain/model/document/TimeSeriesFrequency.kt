package com.mkroo.termbase.domain.model.document

import java.time.LocalDate

data class TimeSeriesFrequency(
    val date: LocalDate,
    val count: Long,
)

enum class TimeSeriesInterval {
    DAY,
    WEEK,
    MONTH,
}
