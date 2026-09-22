package com.github.chsssssss.eonje.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.chsssssss.eonje.data.local.FolderEntity
import com.github.chsssssss.eonje.domain.model.FolderVisuals
import com.github.chsssssss.eonje.ui.theme.EonjeColors
import com.github.chsssssss.eonje.ui.theme.Typography

private const val UNCLASSIFIED_LABEL = "미분류"

/**
 * 폴더는 장소당 하나만 붙는 단일 선택 목록이라, 다중 선택 칩이었던 태그 피커와 달리
 * 라디오 형태의 세로 리스트로 보여준다. `folderId == null`은 미분류를 의미한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerContent(
    folders: List<FolderEntity>,
    selectedFolderId: String?,
    onSelectFolder: (String?) -> Unit,
    onCreateFolder: (String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var newFolderName by remember { mutableStateOf("") }
    var folderPendingDelete by remember { mutableStateOf<FolderEntity?>(null) }

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
                    onDeleteClick = null,
                )
            }
            items(folders, key = { it.id }) { folder ->
                FolderRow(
                    label = folder.name,
                    selected = selectedFolderId == folder.id,
                    onClick = { onSelectFolder(folder.id) },
                    onDeleteClick = { folderPendingDelete = folder },
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

    val target = folderPendingDelete
    if (target != null) {
        AlertDialog(
            onDismissRequest = { folderPendingDelete = null },
            title = { Text("'${target.name}' 폴더를 삭제할까요?") },
            text = { Text("이 폴더에 있던 장소는 미분류로 이동해요.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteFolder(target.id)
                    folderPendingDelete = null
                }) {
                    Text("삭제", color = EonjeColors.warning)
                }
            },
            dismissButton = {
                TextButton(onClick = { folderPendingDelete = null }) {
                    Text("취소")
                }
            },
            containerColor = EonjeColors.surface,
        )
    }
}

@Composable
private fun FolderRow(label: String, selected: Boolean, onClick: () -> Unit, onDeleteClick: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp, 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = Typography.bodyLarge,
            color = if (selected) EonjeColors.accent else EonjeColors.textPrimary,
            modifier = Modifier.weight(1f).padding(vertical = 7.dp),
        )
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = EonjeColors.accent)
        }
        if (onDeleteClick != null) {
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = "폴더 삭제", tint = EonjeColors.iconMuted)
            }
        }
    }
}

/**
 * 상세화면 헤더의 마커 버튼으로 여는 폴더 변경 시트 — [FolderPickerContent]와 달리 체크만으로는
 * 바로 반영되지 않고 저장 버튼을 눌러야 적용된다. 아무 것도 안 체크한 채 저장하면 배정을
 * 지우는 것과 같아서 버튼 라벨이 "저장삭제"로 바뀐다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderAssignSheetContent(
    placeName: String,
    folders: List<FolderEntity>,
    selectedFolderId: String?,
    willDeletePlace: Boolean,
    onToggleFolder: (String) -> Unit,
    onSave: () -> Unit,
    onDeleteFolder: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var folderPendingDelete by remember { mutableStateOf<FolderEntity?>(null) }
    var showDeletePlaceConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp, 4.dp, 20.dp, 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("폴더 변경", style = Typography.titleLarge, color = EonjeColors.textPrimary)

        LazyColumn(
            modifier = Modifier.heightIn(max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(folders, key = { it.id }) { folder ->
                CheckableFolderRow(
                    folder = folder,
                    checked = selectedFolderId == folder.id,
                    onClick = { onToggleFolder(folder.id) },
                    onDeleteClick = { folderPendingDelete = folder },
                )
            }
        }

        FilledPillButton(
            text = if (selectedFolderId == null) "저장삭제" else "저장",
            onClick = { if (willDeletePlace) showDeletePlaceConfirm = true else onSave() },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (showDeletePlaceConfirm) {
        AlertDialog(
            onDismissRequest = { showDeletePlaceConfirm = false },
            title = { Text("'$placeName'을(를) 삭제할까요?") },
            text = { Text("이미 미분류라 지울 폴더 배정이 없어요. 저장하면 장소 자체가 삭제되고 되돌릴 수 없어요.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeletePlaceConfirm = false
                    onSave()
                }) {
                    Text("삭제", color = EonjeColors.warning)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePlaceConfirm = false }) {
                    Text("취소")
                }
            },
            containerColor = EonjeColors.surface,
        )
    }

    val target = folderPendingDelete
    if (target != null) {
        AlertDialog(
            onDismissRequest = { folderPendingDelete = null },
            title = { Text("'${target.name}' 폴더를 삭제할까요?") },
            text = { Text("이 폴더에 있던 장소는 미분류로 이동해요.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteFolder(target.id)
                    folderPendingDelete = null
                }) {
                    Text("삭제", color = EonjeColors.warning)
                }
            },
            dismissButton = {
                TextButton(onClick = { folderPendingDelete = null }) {
                    Text("취소")
                }
            },
            containerColor = EonjeColors.surface,
        )
    }
}

@Composable
private fun CheckableFolderRow(folder: FolderEntity, checked: Boolean, onClick: () -> Unit, onDeleteClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp, 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FolderMarkerIcon(color = folder.color, icon = folder.iconKey)
        Text(
            folder.name,
            style = Typography.bodyLarge,
            color = if (checked) EonjeColors.accent else EonjeColors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (checked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
            contentDescription = null,
            tint = if (checked) EonjeColors.accent else EonjeColors.iconMuted,
        )
        IconButton(onClick = onDeleteClick) {
            Icon(Icons.Filled.DeleteOutline, contentDescription = "폴더 삭제", tint = EonjeColors.iconMuted)
        }
    }
}

/** 지도 마커와 동일하게 폴더 색상 원 위에 이모지 아이콘을 얹어서 보여준다 — 상세화면 헤더 버튼과 폴더 체크박스 행에서 재사용. */
@Composable
fun FolderMarkerIcon(color: Int, icon: String, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(color))
            .border((size.value * 0.05f).dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(icon, fontSize = (size.value * 0.5f).sp)
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

/** FAB로 진입하는 "새 폴더 만들기" — 이름과 함께 지도 마커에 쓸 색상·아이콘을 고른다. */
@Composable
fun AddFolderContent(onCreate: (name: String, color: Int, iconKey: String) -> Unit, modifier: Modifier = Modifier) {
    var name by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(FolderVisuals.colorPalette.first()) }
    var selectedIcon by remember { mutableStateOf(FolderVisuals.iconOptions.first()) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp, 4.dp, 20.dp, 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("새 폴더", style = Typography.titleLarge, color = EonjeColors.textPrimary)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 지도에 실제로 찍힐 마커 모양을 색·아이콘 고르는 동안 바로 확인할 수 있게 미리 보여준다.
            FolderMarkerIcon(color = selectedColor, icon = selectedIcon, size = 48.dp)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(EonjeColors.surfaceVariant)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    textStyle = TextStyle(color = EonjeColors.textPrimary, fontSize = Typography.bodyLarge.fontSize),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (name.isEmpty()) {
                            Text("폴더 이름", style = Typography.bodyLarge, color = EonjeColors.textMuted)
                        }
                        inner()
                    },
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("마커 색상", style = Typography.labelMedium, color = EonjeColors.textMuted)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FolderVisuals.colorPalette.forEach { color ->
                    ColorSwatch(color = color, selected = color == selectedColor, onClick = { selectedColor = color })
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("마커 아이콘", style = Typography.labelMedium, color = EonjeColors.textMuted)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FolderVisuals.iconOptions.forEach { icon ->
                    IconSwatch(icon = icon, selected = icon == selectedIcon, onClick = { selectedIcon = icon })
                }
            }
        }

        FilledPillButton(
            text = "폴더 만들기",
            onClick = { if (name.isNotBlank()) onCreate(name, selectedColor, selectedIcon) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ColorSwatch(color: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(color))
            .then(
                if (selected) Modifier.border(2.dp, EonjeColors.textPrimary, CircleShape) else Modifier
            )
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun IconSwatch(icon: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (selected) EonjeColors.accent.copy(alpha = 0.18f) else EonjeColors.surfaceVariant)
            .then(
                if (selected) Modifier.border(2.dp, EonjeColors.accent, CircleShape) else Modifier
            )
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, style = Typography.titleMedium)
    }
}
