package com.mebonsoft.memorix.feature.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mebonsoft.memorix.core.locale.CountryCatalog

/** 누르면 국가 검색·선택 대화상자가 열리는 읽기 전용 입력란. 값은 한국어 국가명이다. */
@Composable
fun CountryPickerField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(4.dp),
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("국가") },
            placeholder = { Text(CountryCatalog.defaultCountryName) },
            leadingIcon = { Icon(Icons.Outlined.Public, contentDescription = null, modifier = Modifier.size(20.dp)) },
            trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, contentDescription = "국가 선택") },
            shape = shape,
            singleLine = true,
        )
        // readOnly 입력란은 클릭을 받지 않으므로 위에 투명한 클릭 영역을 둔다.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { showPicker = true },
        )
    }
    if (showPicker) {
        CountryPickerDialog(
            selected = value,
            onSelect = {
                onValueChange(it)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun CountryPickerDialog(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query) { CountryCatalog.search(query) }
    val trimmed = query.trim()
    // 목록에 없는 국가·지역명도 남길 수 있게 검색어 그대로 선택하는 항목을 둔다.
    val canUseQuery = trimmed.isNotEmpty() && results.none { it.koreanName == trimmed }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("국가 선택", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("국가 검색 (예: 일본, Japan, JP)") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                )
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).padding(top = 8.dp)) {
                    if (canUseQuery) {
                        item(key = "custom") {
                            CountryRow(name = "\"$trimmed\" 직접 입력", detail = "", isSelected = false) { onSelect(trimmed) }
                            HorizontalDivider()
                        }
                    }
                    itemsIndexed(results, key = { _, option -> option.code }) { index, option ->
                        val showOthersHeader = trimmed.isEmpty() && !option.isMajor && index > 0 && results[index - 1].isMajor
                        if (showOthersHeader) {
                            Text(
                                "전체 국가",
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        CountryRow(
                            name = option.koreanName,
                            detail = option.englishName,
                            isSelected = option.koreanName == selected,
                        ) { onSelect(option.koreanName) }
                    }
                    if (results.isEmpty() && !canUseQuery) {
                        item(key = "empty") {
                            Text("검색 결과가 없습니다.", modifier = Modifier.padding(vertical = 12.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

@Composable
private fun CountryRow(name: String, detail: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
            if (detail.isNotBlank()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (isSelected) {
            Icon(Icons.Outlined.Check, contentDescription = "선택됨", tint = MaterialTheme.colorScheme.primary)
        }
    }
}
