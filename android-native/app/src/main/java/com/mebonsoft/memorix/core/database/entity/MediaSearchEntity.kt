package com.mebonsoft.memorix.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Fts4
@Entity(tableName = "media_search")
data class MediaSearchEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid") val rowId: Long,
    val title: String,
    val note: String,
    val ocrText: String,
)
