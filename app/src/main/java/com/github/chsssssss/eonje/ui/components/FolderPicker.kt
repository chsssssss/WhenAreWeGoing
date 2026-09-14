package com.github.chsssssss.eonje.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.github.chsssssss.eonje.data.local.FolderEntity
import com.github.chsssssss.eonje.ui.theme.EonjeColors
import com.github.chsssssss.eonje.ui.theme.Typography

private const val UNCLASSIFIED_LABEL = "미분류"

/**
 * 폴더는 장소당 하나만 붙는 단일 선택 목록이라, 다중 선택 칩이었던 태그 피커와 달리
 * 라디오 형태의 세로 리스트로 보여준다. `folderId == null`은 미분류를 의미한다.
 */
@Composable
fun FolderPickerContent(
    folders: List<FolderEntity>,
    selectedFolderId: String?,
    onSelectFolder: (String?) -> Unit,
    onCreateFolder: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var newFolderName by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp, 4.dp, 20.dp, 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("폴더", style = Typography.titleLarge, color = EonjeColors.textPrimary)

        LazyColumn(
            modifier = Modifier.heightIn(max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                FolderRow(
                    label = UNCLASSIFIED_LABEL,
                    selected = selectedFolderId == null,
                    onClick = { onSelectFolder(null) },
                )
            }
            items(folders, key = { it.id }) { folder ->
                FolderRow(
                    label = folder.name,
                    selected = selectedFolderId == folder.id,
                    onClick = { onSelectFolder(folder.id) },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(EonjeColors.surfaceVariant)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicTextField(
                value = newFolderName,
                onValueChange = { newFolderName = it },
                textStyle = TextStyle(color = EonjeColors.textPrimary, fontSize = Typography.bodyLarge.fontSize),
                singleLine = true,
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (newFolderName.isEmpty()) {
                        Text("새 폴더 이름", style = Typography.bodyLarge, color = EonjeColors.textMuted)
                    }
                    inner()
                },
            )
            Icon(
                Icons.Filled.Check,
                contentDescription = "폴더 추가",
                tint = EonjeColors.accent,
                modifier = Modifier
                    .clickable(enabled = newFolderName.isNotBlank()) {
                        onCreateFolder(newFolderName)
                        newFolderName = ""
                    },
            )
        }
    }
}

@Composable
private fun FolderRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp, 11.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = Typography.bodyLarge,
            color = if (selected) EonjeColors.accent else EonjeColors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = EonjeColors.accent)
        }
    }
}

/** 장소 상세·리스트 행에서 현재 폴더를 보여주고 탭하면 피커를 여는 칩. */
@Composable
fun FolderChip(folderName: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(EonjeColors.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp)
            .height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(folderName ?: UNCLASSIFIED_LABEL, style = Typography.labelMedium, color = EonjeColors.textSecondary)
    }
}
