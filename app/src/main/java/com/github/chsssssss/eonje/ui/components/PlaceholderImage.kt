package com.github.chsssssss.eonje.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.github.chsssssss.eonje.ui.theme.EonjeColors
import com.github.chsssssss.eonje.ui.theme.Typography

/**
 * [imageUrl]이 있으면 Coil로 실제 이미지를 불러오고, 없거나 로드에 실패하면
 * 디자인의 `<image-slot>` 자리를 채우는 아이콘 플레이스홀더를 보여준다.
 */
@Composable
fun PlaceholderImage(
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    circle: Boolean = false,
    cornerRadius: Dp = 12.dp,
    label: String? = null,
) {
    val shape = if (circle) CircleShape else RoundedCornerShape(cornerRadius)
    if (imageUrl.isNullOrBlank()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .clip(shape)
                .background(EonjeColors.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            PlaceholderIcon(label)
        }
    } else {
        SubcomposeAsyncImage(
            model = imageUrl,
            contentDescription = label,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .fillMaxSize()
                .clip(shape)
                .background(EonjeColors.surfaceVariant),
            loading = { PlaceholderIconSlot(label) },
            error = { PlaceholderIconSlot(label) },
        )
    }
}

@Composable
private fun PlaceholderIconSlot(label: String?) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        PlaceholderIcon(label)
    }
}

@Composable
private fun PlaceholderIcon(label: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Outlined.Photo,
            contentDescription = null,
            tint = EonjeColors.iconMuted,
            modifier = Modifier.padding(4.dp)
        )
        if (label != null) {
            Text(
                text = label,
                style = Typography.labelSmall,
                color = EonjeColors.iconMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}
