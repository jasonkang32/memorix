package com.mebonsoft.memorix.feature.work.compose

internal data class ComposeMediaPreviewRow(
    val sourceIndex: Int,
    val orderLabel: String,
)

internal fun buildComposeMediaPreviewRows(selectedCount: Int): List<ComposeMediaPreviewRow> =
    (0 until minOf(selectedCount, 6)).map { index ->
        ComposeMediaPreviewRow(
            sourceIndex = index,
            orderLabel = "${index + 1}/$selectedCount",
        )
    }

internal fun hiddenComposeMediaPreviewCount(selectedCount: Int): Int =
    (selectedCount - 6).coerceAtLeast(0)
