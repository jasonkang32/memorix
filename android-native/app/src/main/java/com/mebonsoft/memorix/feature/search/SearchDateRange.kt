package com.mebonsoft.memorix.feature.search

import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant

data class SearchDateRange(val from: LocalDate? = null, val to: LocalDate? = null) {
    val isValid: Boolean get() = from == null || to == null || !from.isAfter(to)
    val isActive: Boolean get() = from != null || to != null
    fun contains(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (!isValid) return false
        val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
        return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to))
    }
}
