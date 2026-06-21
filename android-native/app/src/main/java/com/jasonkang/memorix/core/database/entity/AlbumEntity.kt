package com.jasonkang.memorix.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val memo: String = "",
    val dateStart: Long? = null,
    val dateEnd: Long? = null,
    val coverMediaId: Long? = null,
    val createdAt: Long,
)
