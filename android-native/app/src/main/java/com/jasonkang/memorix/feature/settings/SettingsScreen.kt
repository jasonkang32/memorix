package com.jasonkang.memorix.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class SettingsSection(
    val title: String,
    val items: List<SettingsRowModel>,
)

data class SettingsRowModel(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
)

@Composable
fun SettingsScreen() {
    val sections = listOf(
        SettingsSection(
            title = "보안",
            items = listOf(
                SettingsRowModel(Icons.Outlined.Lock, "앱 잠금", "PIN 및 잠금 해제 흐름을 Flutter UX에 맞춰 이식 중"),
                SettingsRowModel(Icons.Outlined.Fingerprint, "생체 인증", "지문/Face ID 기반 빠른 해제 시나리오"),
                SettingsRowModel(Icons.Outlined.Home, "Personal 별도 잠금", "개인 공간 추가 보호 흐름"),
            ),
        ),
        SettingsSection(
            title = "콘텐츠 관리",
            items = listOf(
                SettingsRowModel(Icons.Outlined.Tag, "태그 관리", "Work·Personal 태그 관리 화면 연결 예정"),
                SettingsRowModel(Icons.Outlined.Storage, "저장소", "용량, 저장 경로, 정리 도구"),
            ),
        ),
        SettingsSection(
            title = "앱 정보",
            items = listOf(
                SettingsRowModel(Icons.Outlined.Info, "Memorix", "Flutter에서 설계한 정보 구조를 Native에 맞게 복원 중"),
            ),
        ),
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column(
                modifier = Modifier.padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("설정", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "Flutter에서 정의했던 보안, 저장소, 관리 흐름을 Android Native 화면 구조로 다시 정렬했습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(sections) { section ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    section.items.forEach { item ->
                        SettingsRow(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(item: SettingsRowModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
