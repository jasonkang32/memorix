package com.mebonsoft.memorix.feature.work.compose

internal data class ComposeMediaPreviewRow(
    val sourceIndex: Int,
    val orderLabel: String,
)

internal fun buildComposeMediaPreviewRows(selectedCount: Int): List<ComposeMediaPreviewRow> =
    (0 until selectedCount).map { index ->
        ComposeMediaPreviewRow(
            sourceIndex = index,
            orderLabel = "${index + 1}/$selectedCount",
        )
    }
