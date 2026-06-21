package com.jasonkang.memorix.core.database.entity

data class AlbumSummary(
    val id: Long,
    val title: String,
    val memo: String,
    val dateStart: Long?,
    val dateEnd: Long?,
    val coverMediaId: Long?,
    val createdAt: Long,
    val itemCount: Int,
    val coverPath: String?,
)
