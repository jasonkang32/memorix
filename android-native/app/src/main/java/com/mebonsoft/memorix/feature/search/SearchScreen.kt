package com.mebonsoft.memorix.feature.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mebonsoft.memorix.core.database.entity.MediaType
import com.mebonsoft.memorix.feature.home.component.MediaGrid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onMediaClick: (Long) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "검색",
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.headlineSmall,
        )
        OutlinedTextField(
            value = uiState.query,
            onValueChange = viewModel::updateQuery,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("제목, 메모, OCR 검색") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
        )

        FilterRow(
            title = "앨범",
            options = buildList {
                add(FilterOption(id = null, label = "전체"))
                addAll(uiState.albums.map { FilterOption(id = it.id, label = it.title) })
            },
            selectedId = uiState.selectedAlbumId,
            onSelect = { viewModel.updateAlbum(it) },
        )

        FilterRow(
            title = "유형",
            options = listOf(
                FilterOption<MediaType?>(null, "전체"),
                FilterOption(MediaType.PHOTO, "사진"),
                FilterOption(MediaType.VIDEO, "영상"),
                FilterOption(MediaType.DOCUMENT, "문서"),
            ),
            selectedId = uiState.selectedMediaType,
            onSelect = { viewModel.updateMediaType(it) },
        )

        Text(
            text = uiState.summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (uiState.query.isEmpty()) {
            Text(
                text = "검색어와 필터를 조합해 원하는 미디어를 빠르게 찾으세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (uiState.results.isEmpty()) {
            Text(
                text = "검색 결과가 없습니다. 다른 키워드나 필터를 시도해보세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            MediaGrid(
                items = uiState.results,
                modifier = Modifier.weight(1f),
                onItemClick = { item -> onMediaClick(item.id) },
            )
        }
    }
}

private data class FilterOption<T>(
    val id: T,
    val label: String,
)

@Composable
private fun <T> FilterRow(
    title: String,
    options: List<FilterOption<T>>,
    selectedId: T,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                AssistChip(
                    onClick = { onSelect(option.id) },
                    label = { Text(option.label) },
                    leadingIcon = null,
                )
            }
        }
    }
}
