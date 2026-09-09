package com.github.chsssssss.eonje.ui.placedetail

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.chsssssss.eonje.data.local.TagEntity
import com.github.chsssssss.eonje.ui.components.FilledPillButton
import com.github.chsssssss.eonje.ui.components.PlaceholderImage
import com.github.chsssssss.eonje.ui.theme.EonjeColors
import com.github.chsssssss.eonje.ui.theme.EonjeTheme
import com.github.chsssssss.eonje.ui.theme.Typography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceDetailScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaceDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.toastMessages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    PlaceDetailContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onDirectionsClick = viewModel::onDirectionsClick,
        onAddTagClick = viewModel::onAddTagClick,
        modifier = modifier,
    )

    if (uiState.showTagPicker) {
        ModalBottomSheet(
            onDismissRequest = viewModel::onDismissTagPicker,
            sheetState = rememberModalBottomSheetState(),
            containerColor = EonjeColors.surface,
        ) {
            TagPickerContent(
                attachedTags = uiState.tags,
                allTags = uiState.allTags,
                onToggleTag = viewModel::onToggleTag,
                onCreateTag = viewModel::onCreateTag,
            )
        }
    }
}

@Composable
private fun PlaceDetailContent(
    uiState: PlaceDetailUiState,
    onNavigateBack: () -> Unit,
    onDirectionsClick: () -> Unit,
    onAddTagClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState.isLoading) {
        Box(
            modifier = modifier.fillMaxSize().background(EonjeColors.background),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = EonjeColors.accent)
        }
        return
    }

    Column(modifier = modifier.fillMaxSize().background(EonjeColors.background)) {
        Column(modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.fillMaxWidth().height(260.dp)) {
                PlaceholderImage(modifier = Modifier.fillMaxSize(), cornerRadius = 0.dp, label = "대표 사진")
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to EonjeColors.background.copy(alpha = 0.55f),
                                0.45f to Color.Transparent,
                                1f to EonjeColors.background,
                            )
                        )
                )
                RoundIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "뒤로가기",
                    onClick = onNavigateBack,
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                )
                RoundIconButton(
                    icon = Icons.Filled.MoreVert,
                    contentDescription = "더보기",
                    onClick = {},
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                )
            }

            Column(
                modifier = Modifier.padding(24.dp, 4.dp, 24.dp, 0.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(uiState.name, style = Typography.headlineMedium, color = EonjeColors.textPrimary)
                    if (uiState.category.isNotBlank()) {
                        Text(uiState.category, style = Typography.bodyMedium, color = EonjeColors.textTertiary)
                    }
                    Text(uiState.address, style = Typography.bodyMedium, color = EonjeColors.textMuted)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.tags.forEach { tag -> Tag(tag.name) }
                    AddTagChip(onClick = onAddTagClick)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(EonjeColors.surface)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("메모", style = Typography.labelSmall, color = EonjeColors.textMuted)
                    Text(
                        uiState.memo.ifBlank { "메모가 없어요" },
                        style = Typography.bodyMedium,
                        color = EonjeColors.textSecondary,
                    )
                }
            }

            Column(
                modifier = Modifier.padding(24.dp, 22.dp, 24.dp, 0.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    Text("이 가게가 나온 게시물", style = Typography.titleMedium, color = EonjeColors.textPrimary)
                    Text("${uiState.posts.size}", style = Typography.bodyMedium, color = EonjeColors.textMuted)
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.posts, key = { it.id }) { post -> PlacePostRow(post) }
                }
            }
        }

        Row(
            modifier = Modifier.padding(20.dp, 14.dp, 20.dp, 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RoundIconButton(
                icon = Icons.Filled.Share,
                contentDescription = "공유",
                onClick = {},
                size = 56.dp,
                outlined = true,
            )
            FilledPillButton(
                text = "길찾기",
                icon = Icons.Filled.Place,
                onClick = onDirectionsClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    outlined: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (outlined) Modifier.border(1.dp, EonjeColors.border, CircleShape)
                else Modifier.background(EonjeColors.background.copy(alpha = 0.6f))
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = EonjeColors.textSecondary)
    }
}

@Composable
private fun Tag(label: String) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(EonjeColors.accent.copy(alpha = 0.13f))
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Typography.labelMedium, color = EonjeColors.accent)
    }
}

@Composable
private fun AddTagChip(onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(shape)
            .border(1.dp, EonjeColors.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("+ 태그", style = Typography.labelMedium, color = EonjeColors.textMuted)
    }
}

@Composable
private fun PlacePostRow(post: PlacePost) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(EonjeColors.surface)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaceholderImage(modifier = Modifier.size(56.dp), cornerRadius = 12.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(post.label, style = Typography.bodyMedium, color = EonjeColors.textSecondary)
            Text(post.savedAt, style = Typography.labelSmall, color = EonjeColors.textMuted)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = EonjeColors.iconMuted)
    }
}

@Composable
private fun TagPickerContent(
    attachedTags: List<TagEntity>,
    allTags: List<TagEntity>,
    onToggleTag: (TagEntity) -> Unit,
    onCreateTag: (String) -> Unit,
) {
    var newTagName by remember { mutableStateOf("") }
    val attachedIds = attachedTags.map { it.id }.toSet()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp, 4.dp, 20.dp, 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("태그", style = Typography.titleLarge, color = EonjeColors.textPrimary)

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(allTags, key = { it.id }) { tag ->
                SelectableTagChip(
                    label = tag.name,
                    selected = tag.id in attachedIds,
                    onClick = { onToggleTag(tag) },
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
                value = newTagName,
                onValueChange = { newTagName = it },
                textStyle = TextStyle(color = EonjeColors.textPrimary, fontSize = Typography.bodyLarge.fontSize),
                singleLine = true,
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (newTagName.isEmpty()) {
                        Text("새 태그 이름", style = Typography.bodyLarge, color = EonjeColors.textMuted)
                    }
                    inner()
                },
            )
            Icon(
                Icons.Filled.Check,
                contentDescription = "태그 추가",
                tint = EonjeColors.accent,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(enabled = newTagName.isNotBlank()) {
                        onCreateTag(newTagName)
                        newTagName = ""
                    },
            )
        }
    }
}

@Composable
private fun SelectableTagChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(shape)
            .background(if (selected) EonjeColors.accent else EonjeColors.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = Typography.labelMedium,
            color = if (selected) EonjeColors.onAccent else EonjeColors.textSecondary,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121316)
@Composable
private fun PlaceDetailScreenPreview() {
    EonjeTheme {
        PlaceDetailContent(
            uiState = PlaceDetailUiState(
                isLoading = false,
                name = "소하염",
                category = "한식 · 성수동",
                address = "서울 성동구 연무장길 45 1층",
                memo = "6시 전에 가면 웨이팅 없음. 염통구이랑 물회 시킬 것.",
                posts = listOf(
                    PlacePost("1", "https://instagram.com/p/abc/", "2026. 9. 5. 저장"),
                    PlacePost("2", "https://instagram.com/p/def/", "2026. 7. 22. 저장"),
                ),
            ),
            onNavigateBack = {},
            onDirectionsClick = {},
            onAddTagClick = {},
        )
    }
}
