package com.mebonsoft.memorix.feature.settings

data class ManagedTag(
    val id: Long,
    val label: String,
    val usageCount: Int,
)

data class ManagedTagRow(
    val id: Long,
    val label: String,
    val usageCount: Int,
    val usageLabel: String,
)

object TagManagementSupport {
    val emptyMessage: String = "중복되거나 불필요한 태그가 생기면 여기에서 삭제해 업무·개인 태그 선택 목록을 정리할 수 있습니다."

    fun sortedRows(tags: List<ManagedTag>): List<ManagedTagRow> = tags
        .sortedWith(compareBy<ManagedTag> { it.label.lowercase() }.thenBy { it.id })
        .map { tag ->
            ManagedTagRow(
                id = tag.id,
                label = tag.label,
                usageCount = tag.usageCount,
                usageLabel = usageLabel(tag.usageCount),
            )
        }

    fun deleteWarning(tag: ManagedTag): String = buildString {
        append("#${tag.label} 태그를 삭제할까요?\n\n")
        append("현재 ${usageLabel(tag.usageCount)}에 연결되어 있습니다. ")
        append("삭제해도 미디어 기록은 삭제되지 않습니다. 태그 연결만 제거됩니다.")
    }

    private fun usageLabel(usageCount: Int): String = if (usageCount <= 0) "미사용" else "${usageCount}개 기록"
}
