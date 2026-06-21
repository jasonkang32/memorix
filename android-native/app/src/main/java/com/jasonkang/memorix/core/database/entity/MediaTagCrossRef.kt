package com.jasonkang.memorix.core.database.entity

import androidx.room.Entity

@Entity(
    tableName = "media_tags",
    primaryKeys = ["mediaId", "tagId"],
)
data class MediaTagCrossRef(
    val mediaId: Long,
    val tagId: Long,
)
