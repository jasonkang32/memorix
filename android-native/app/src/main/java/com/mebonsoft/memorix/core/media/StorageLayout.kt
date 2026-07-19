package com.mebonsoft.memorix.core.media

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

object StorageLayout {
    private val yearFormatter = DateTimeFormatter.ofPattern("yyyy")
    private val monthFormatter = DateTimeFormatter.ofPattern("MM")

    fun originalsDir(root: File, now: LocalDateTime = LocalDateTime.now()): File =
        File(root, "originals/${now.format(yearFormatter)}/${now.format(monthFormatter)}")

    fun thumbsDir(root: File, now: LocalDateTime = LocalDateTime.now()): File =
        File(root, "thumbs/${now.format(yearFormatter)}/${now.format(monthFormatter)}")

    fun originalFile(
        root: File,
        extension: String,
        uuid: String = UUID.randomUUID().toString(),
        now: LocalDateTime = LocalDateTime.now(),
    ): File = File(originalsDir(root, now), "$uuid.$extension")

    fun thumbFile(
        root: File,
        uuid: String = UUID.randomUUID().toString(),
        now: LocalDateTime = LocalDateTime.now(),
    ): File = File(thumbsDir(root, now), "$uuid.jpg")
}
