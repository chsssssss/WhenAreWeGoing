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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.chsssssss.eonje.ui.theme.EonjeColors
import com.github.chsssssss.eonje.ui.theme.Typography

/**
 * Stand-in for the design's `<image-slot>` until real thumbnails/Coil are wired up.
 */
@Composable
fun PlaceholderImage(
    modifier: Modifier = Modifier,
    circle: Boolean = false,
    cornerRadius: Dp = 12.dp,
    label: String? = null,
) {
    val shape = if (circle) CircleShape else RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(shape)
            .background(EonjeColors.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
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
}
