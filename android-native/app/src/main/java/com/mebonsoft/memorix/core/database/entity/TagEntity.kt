package com.mebonsoft.memorix.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tags",
    indices = [Index(value = ["key"], unique = true)]
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val label: String,
    val colorHex: String,
    val iconName: String,
    val isCustom: Boolean = false,
)
