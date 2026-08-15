package com.mebonsoft.memorix.feature.home

import com.mebonsoft.memorix.core.database.entity.MediaItemEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class RecentActivityBucket(
    val date: LocalDate,
    val count: Int,
)

internal fun buildRecentActivityBuckets(
    items: List<MediaItemEntity>,
    today: LocalDate = LocalDate.now(),
    days: Int = 30,
): List<RecentActivityBucket> {
    val safeDays = days.coerceAtLeast(1)
    val counts = items.groupingBy { item ->
        Instant.ofEpochMilli(item.takenAt).atZone(ZoneId.systemDefault()).toLocalDate()
    }.eachCount()
    return (safeDays - 1 downTo 0).map { offset ->
        val date = today.minusDays(offset.toLong())
        RecentActivityBucket(
            date = date,
            count = counts[date] ?: 0,
        )
    }
}
